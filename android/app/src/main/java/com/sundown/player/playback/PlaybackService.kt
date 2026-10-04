package com.sundown.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sundown.player.MainActivity
import com.sundown.player.data.prefs.SundownPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground MediaSession service for lock-screen and background playback. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var prefs: SundownPrefs
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkpointJob: Job? = null
    private var checkpointWriteJob: Job? = null
    private var checkpointDirty = false
    private var hasObservedQueue = false
    private var queueSnapshot: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        prefs = SundownPrefs(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.skipSilenceEnabled = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (player.mediaItemCount > 0) hasObservedQueue = true
                if (hasObservedQueue) {
                    // The service main looper owns ExoPlayer; snapshot the queue only
                    // when its timeline changes, not on every position checkpoint.
                    queueSnapshot = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                    requestCheckpoint()
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (hasObservedQueue) requestCheckpoint()
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        checkpointJob = serviceScope.launch {
            while (isActive) {
                delay(CHECKPOINT_INTERVAL_MS)
                persistCheckpoint()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Coalesce queue/track callbacks but always write the newest observed state. */
    private fun requestCheckpoint() {
        checkpointDirty = true
        if (checkpointWriteJob?.isActive == true) return
        checkpointWriteJob = serviceScope.launch {
            while (checkpointDirty && isActive) {
                checkpointDirty = false
                persistCheckpoint()
            }
        }
    }

    private suspend fun persistCheckpoint() {
        val player = session?.player ?: return
        val queue = queueSnapshot
        if (queue.isEmpty() && !hasObservedQueue) return
        val currentId = player.currentMediaItem?.mediaId
        val position = if (queue.isEmpty()) 0L else player.currentPosition.coerceAtLeast(0L)
        val volume = player.volume
        val shuffle = player.shuffleModeEnabled
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> "all"
            Player.REPEAT_MODE_ONE -> "one"
            else -> "off"
        }
        prefs.update { current ->
            current.copy(
                queue = queue,
                currentId = currentId,
                position = position,
                // A muted ExoPlayer volume is zero; keep the previous slider
                // level so unmute and next-launch restoration remain useful.
                volume = if (volume > 0f) volume else current.volume,
                muted = volume <= 0f,
                shuffle = shuffle,
                repeat = repeat,
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        checkpointJob?.cancel()
        serviceScope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 2_000L
    }
}
