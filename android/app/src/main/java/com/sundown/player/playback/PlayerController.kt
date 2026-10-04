package com.sundown.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sundown.player.data.ArtworkStore
import com.sundown.player.data.db.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class PlayerSnapshot(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: Int = 0,
    val genre: String = "",
    val artId: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: String = "off",
    val hasSource: Boolean = false,
)

/** Frequently changing playhead data stays out of the library/player shell state. */
@Immutable
data class PlaybackProgress(
    val elapsedMs: Long = 0L,
    val durationMs: Long = 0L,
)

/** Media3 controller adapter for the native background playback session. */
class PlayerController(
    private val context: Context,
    private val artwork: ArtworkStore,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var connection: ListenableFuture<MediaController>? = null
    private var queue: List<TrackEntity> = emptyList()
    private var queueById: Map<String, TrackEntity> = emptyMap()
    private var queueIds: List<String> = emptyList()
    private var queueRestorationPending = false
    private val pendingQueueMutations = mutableListOf<QueueMutation>()
    val isQueueRestorationPending: Boolean get() = queueRestorationPending
    private var lastVolume = 0.8f
    private var progressJob: Job? = null
    private var artworkJob: Job? = null
    private var pendingArtworkTrack: String? = null
    private var lastReportedDuration: Pair<String, Int>? = null
    private var pendingPlay: (() -> Unit)? = null

    private sealed interface QueueMutation {
        data class Append(val tracks: List<TrackEntity>) : QueueMutation
        object Clear : QueueMutation
    }

    private val _state = MutableStateFlow(PlayerSnapshot())
    val state: StateFlow<PlayerSnapshot> = _state.asStateFlow()
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()
    private val _queueState = MutableStateFlow<List<TrackEntity>>(emptyList())
    val queueState: StateFlow<List<TrackEntity>> = _queueState.asStateFlow()

    var onDurationResolved: ((String, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(onReady: () -> Unit = {}) {
        if (controller != null) {
            onReady()
            return
        }
        if (connection != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        connection = future
        future.addListener({
            val result = runCatching { future.get() }
            mainHandler.post {
                if (connection !== future) {
                    result.getOrNull()?.release()
                    return@post
                }
                connection = null
                result.onSuccess { connected ->
                    controller = connected.also { it.addListener(listener) }
                    publish()
                    progressJob?.cancel()
                    progressJob = scope.launch {
                        while (isActive) {
                            delay(PROGRESS_INTERVAL_MS)
                            if (controller?.isPlaying == true) publishProgress()
                        }
                    }
                    onReady()
                    pendingPlay?.also { queued ->
                        pendingPlay = null
                        queued()
                    }
                }.onFailure {
                    pendingPlay = null
                    onError?.invoke("The playback service could not be started. Close and reopen Sundown to try again.")
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        progressJob?.cancel()
        artworkJob?.cancel()
        pendingArtworkTrack = null
        pendingPlay = null
        connection?.cancel(true)
        connection = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        scope.cancel()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            onError?.invoke("This file could not be played. It may have moved or use an unsupported codec.")
            publish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            publish()
            loadArtworkFor(mediaItem)
            val activePlayer = controller ?: return
            val id = mediaItem?.mediaId ?: return
            reportDuration(id, activePlayer.duration)
        }
    }

    /** Hold queue edits until the persisted queue has been reconciled with the live session. */
    fun beginQueueRestoration() {
        queueRestorationPending = true
    }

    /** Applies queue edits made while restoration was in flight, in their original order. */
    fun finishQueueRestoration(): Boolean {
        if (!queueRestorationPending) return false
        val activePlayer = controller ?: return false
        queueRestorationPending = false
        if (pendingQueueMutations.isEmpty()) return false

        var changed = false
        pendingQueueMutations.forEach { mutation ->
            when (mutation) {
                is QueueMutation.Append -> if (mutation.tracks.isNotEmpty()) {
                    activePlayer.addMediaItems(mutation.tracks.map(::toMediaItem))
                    updateQueue(queue + mutation.tracks)
                    changed = true
                }
                QueueMutation.Clear -> {
                    clearQueueNow(activePlayer)
                    changed = true
                }
            }
        }
        pendingQueueMutations.clear()
        if (changed) publish()
        return changed
    }

    fun play(tracks: List<TrackEntity>, startIndex: Int, autoplay: Boolean = true) {
        if (tracks.isEmpty()) return
        // Starting a new selection replaces any queue edits made before restoration.
        if (queueRestorationPending) pendingQueueMutations.clear()
        val activePlayer = controller
        if (activePlayer == null) {
            val deferredTracks = tracks.toList()
            val deferredIndex = startIndex
            pendingPlay = { play(deferredTracks, deferredIndex, autoplay) }
            connect()
            return
        }
        updateQueue(tracks)
        cancelArtworkLoad()
        activePlayer.setMediaItems(tracks.map(::toMediaItem), startIndex.coerceIn(0, tracks.lastIndex), 0L)
        activePlayer.prepare()
        activePlayer.playWhenReady = autoplay
        publish()
        loadArtworkFor(activePlayer.currentMediaItem)
    }

    /** Restores a saved queue/position without restarting playback. */
    fun restore(tracks: List<TrackEntity>, startIndex: Int, positionMs: Long) {
        val activePlayer = controller ?: return
        if (tracks.isEmpty()) return
        updateQueue(tracks)
        cancelArtworkLoad()
        activePlayer.setMediaItems(
            tracks.map(::toMediaItem),
            startIndex.coerceIn(0, tracks.lastIndex),
            positionMs.coerceAtLeast(0L),
        )
        activePlayer.prepare()
        activePlayer.playWhenReady = false
        publish()
        loadArtworkFor(activePlayer.currentMediaItem)
    }

    /** Attaches Room rows to a session already playing in the background. */
    fun attachTracks(tracks: List<TrackEntity>): Boolean {
        val activePlayer = controller ?: return false
        val byId = tracks.associateBy(TrackEntity::id)
        updateQueue((0 until activePlayer.mediaItemCount)
            .mapNotNull { index -> byId[activePlayer.getMediaItemAt(index).mediaId] })
        publish()
        loadArtworkFor(activePlayer.currentMediaItem)
        return activePlayer.mediaItemCount > 0
    }

    private fun updateQueue(tracks: List<TrackEntity>) {
        queue = tracks.toList()
        queueById = queue.associateBy(TrackEntity::id)
        queueIds = queue.map(TrackEntity::id)
        _queueState.value = queue
    }

    fun containsFolderTracks(): Boolean {
        if (queue.any { it.id.startsWith("fs:") }) return true
        val activePlayer = controller ?: return false
        return (0 until activePlayer.mediaItemCount).any { index ->
            activePlayer.getMediaItemAt(index).mediaId.startsWith("fs:")
        }
    }

    /** Current queue in play order as Room track records. */
    fun queueTracks(): List<TrackEntity> = queue

    /** Current queue IDs for lightweight preference checkpoints. */
    fun queueIds(): List<String> = queueIds

    /** Jump to a queued item without rebuilding the queue. */
    fun jumpTo(trackId: String): Boolean {
        val activePlayer = controller ?: return false
        val index = queue.indexOfFirst { it.id == trackId }
        if (index < 0) return false
        activePlayer.seekTo(index, 0L)
        activePlayer.play()
        return true
    }

    fun removeFromQueue(trackId: String): Boolean {
        val activePlayer = controller ?: return false
        val index = queue.indexOfFirst { it.id == trackId }
        if (index < 0) return false
        activePlayer.removeMediaItem(index)
        updateQueue(queue.filterIndexed { i, _ -> i != index })
        publish()
        return true
    }

    /** Append without disturbing the current item. */
    fun enqueue(tracks: List<TrackEntity>): Boolean {
        if (tracks.isEmpty()) return false
        if (queueRestorationPending) {
            pendingQueueMutations += QueueMutation.Append(tracks.toList())
            return true
        }
        val activePlayer = controller ?: return false
        activePlayer.addMediaItems(tracks.map(::toMediaItem))
        updateQueue(queue + tracks)
        publish()
        return true
    }

    fun clearQueue() {
        if (queueRestorationPending) {
            pendingQueueMutations += QueueMutation.Clear
            return
        }
        controller?.let(::clearQueueNow)
        updateQueue(emptyList())
        publish()
    }

    private fun clearQueueNow(activePlayer: MediaController) {
        cancelArtworkLoad()
        activePlayer.pause()
        activePlayer.clearMediaItems()
        activePlayer.playWhenReady = false
        updateQueue(emptyList())
    }

    fun toggle() {
        val activePlayer = controller ?: return
        if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    /** Restart the current track when it is more than 3 seconds into playback. */
    fun previous() {
        val activePlayer = controller ?: return
        if (activePlayer.currentPosition > 3_000L) activePlayer.seekTo(0L)
        else activePlayer.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0L)) }

    fun seekFraction(fraction: Float) {
        val activePlayer = controller ?: return
        val duration = activePlayer.duration
        if (duration > 0) activePlayer.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun setVolume(level: Float) {
        val activePlayer = controller ?: return
        val clamped = level.coerceIn(0f, 1f)
        if (clamped > 0f) lastVolume = clamped
        activePlayer.volume = clamped
        publish()
    }

    fun restoreVolume(level: Float, muted: Boolean) {
        lastVolume = level.coerceIn(0f, 1f).takeIf { it > 0f } ?: 0.8f
        controller?.volume = if (muted) 0f else lastVolume
        publish()
    }

    fun toggleMute() {
        val activePlayer = controller ?: return
        if (activePlayer.volume > 0f) {
            lastVolume = activePlayer.volume
            activePlayer.volume = 0f
        } else {
            activePlayer.volume = lastVolume.takeIf { it > 0f } ?: 0.8f
        }
        publish()
    }

    fun toggleShuffle() {
        val activePlayer = controller ?: return
        activePlayer.shuffleModeEnabled = !activePlayer.shuffleModeEnabled
        publish()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        publish()
    }

    fun cycleRepeat() {
        val activePlayer = controller ?: return
        activePlayer.repeatMode = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }

    fun setRepeatMode(mode: String) {
        controller?.repeatMode = when (mode) {
            "all" -> Player.REPEAT_MODE_ALL
            "one" -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }


    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    fun currentIndex(): Int = controller?.currentMediaItemIndex ?: 0

    private fun toMediaItem(track: TrackEntity): MediaItem = MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(Uri.parse(track.docUri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist.ifBlank { "Unknown Artist" })
                .setAlbumTitle(track.album)
                .setAlbumArtist(track.albumArtist.ifBlank { track.artist })
                .setGenre(track.genre.takeIf(String::isNotBlank))
                .setRecordingYear(track.year.takeIf { it > 0 })
                .setTrackNumber(track.trackNo.takeIf { it > 0 })
                .setDiscNumber(track.discNo.takeIf { it > 0 })
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun loadArtworkFor(mediaItem: MediaItem?) {
        val activePlayer = controller ?: return
        val id = mediaItem?.mediaId ?: return
        val track = queueById[id] ?: return
        val artId = track.artId ?: return
        if (mediaItem.mediaMetadata.artworkData != null || pendingArtworkTrack == id) return

        artworkJob?.cancel()
        pendingArtworkTrack = id
        artworkJob = scope.launch(Dispatchers.IO) {
            val bytes = artwork.largeBytes(artId)
            mainHandler.post {
                if (pendingArtworkTrack == id) pendingArtworkTrack = null
                val current = controller ?: return@post
                val index = current.currentMediaItemIndex
                val currentItem = current.currentMediaItem
                if (bytes == null || currentItem?.mediaId != id || index < 0 || currentItem.mediaMetadata.artworkData != null) {
                    return@post
                }
                val metadata = currentItem.mediaMetadata.buildUpon()
                    .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
                current.replaceMediaItem(index, currentItem.buildUpon().setMediaMetadata(metadata).build())
                publish()
            }
        }
    }

    private fun cancelArtworkLoad() {
        artworkJob?.cancel()
        artworkJob = null
        pendingArtworkTrack = null
    }

    private fun reportDuration(trackId: String, durationMs: Long) {
        val seconds = (durationMs / 1_000L).takeIf { it > 0L }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: return
        val resolved = trackId to seconds
        if (lastReportedDuration == resolved) return
        lastReportedDuration = resolved
        onDurationResolved?.invoke(trackId, seconds)
    }

    private fun publish() {
        val activePlayer = controller ?: run {
            _state.value = PlayerSnapshot()
            _progress.value = PlaybackProgress()
            return
        }
        val id = activePlayer.currentMediaItem?.mediaId
        val track = queueById[id]
        val metadata = activePlayer.mediaMetadata
        val duration = activePlayer.duration
        if (id != null) reportDuration(id, duration)
        _state.value = PlayerSnapshot(
            trackId = id,
            title = track?.title ?: metadata.title?.toString().orEmpty(),
            artist = track?.artist ?: metadata.artist?.toString().orEmpty(),
            album = track?.album ?: metadata.albumTitle?.toString().orEmpty(),
            year = track?.year ?: metadata.recordingYear ?: 0,
            genre = track?.genre ?: metadata.genre?.toString().orEmpty(),
            artId = track?.artId,
            playing = activePlayer.isPlaying,
            loading = activePlayer.playbackState == Player.STATE_BUFFERING,
            volume = activePlayer.volume,
            muted = activePlayer.volume <= 0f,
            shuffle = activePlayer.shuffleModeEnabled,
            repeat = when (activePlayer.repeatMode) {
                Player.REPEAT_MODE_ALL -> "all"
                Player.REPEAT_MODE_ONE -> "one"
                else -> "off"
            },
            hasSource = activePlayer.mediaItemCount > 0,
        )
        publishProgress()
    }

    private fun publishProgress() {
        val activePlayer = controller ?: run {
            _progress.value = PlaybackProgress()
            return
        }
        val duration = activePlayer.duration.takeIf { it > 0L } ?: 0L
        val elapsed = activePlayer.currentPosition.coerceAtLeast(0L)
            .let { if (duration > 0L) it.coerceAtMost(duration) else it }
        _progress.value = PlaybackProgress(elapsedMs = elapsed, durationMs = duration)
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
