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
            .build()
        player.skipSilenceEnabled = false

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

    private suspend fun persistCheckpoint() {
        val player = session?.player ?: return
        if (player.mediaItemCount == 0) return
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val currentId = player.currentMediaItem?.mediaId
        val position = player.currentPosition.coerceAtLeast(0L)
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
                currentId = currentId ?: current.currentId,
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
