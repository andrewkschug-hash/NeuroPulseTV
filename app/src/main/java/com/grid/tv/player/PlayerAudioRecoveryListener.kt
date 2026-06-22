package com.grid.tv.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

/**
 * Recovers from silent playback or audio decoder init failures after stream switches.
 * Uses [Handler] callbacks (no standalone coroutine scope) so [detach] fully cancels work.
 */
class PlayerAudioRecoveryListener(
    private val player: ExoPlayer
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var recoveryAttempts = 0
    private var lastRecoveryAtMs = 0L
    private var hasSelectedAudioTrack = false
    private var detached = false

    private val silentCheckRunnable = Runnable {
        if (!detached) validateAudioOutput()
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        recoveryAttempts = 0
    }

    override fun onTracksChanged(tracks: Tracks) {
        hasSelectedAudioTrack = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.isSelected
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        handler.removeCallbacks(silentCheckRunnable)
        if (detached) return
        if (playbackState == Player.STATE_READY) {
            handler.postDelayed(silentCheckRunnable, SILENT_CHECK_DELAY_MS)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (isAudioPipelineError(error)) {
            recoverAudioPipeline("player_error")
        }
    }

    /** Cancel pending work and remove this listener from the player. */
    fun detach() {
        if (detached) return
        detached = true
        handler.removeCallbacks(silentCheckRunnable)
        player.removeListener(this)
    }

    private fun validateAudioOutput() {
        if (detached) return
        if (!player.playWhenReady || player.volume <= 0f) return
        if (!hasSelectedAudioTrack) return
        if (player.isPlaying) return
        if (player.playbackState != Player.STATE_READY) return
        recoverAudioPipeline("silent_ready")
    }

    private fun recoverAudioPipeline(reason: String) {
        if (detached) return
        val now = System.currentTimeMillis()
        if (now - lastRecoveryAtMs < MIN_RECOVERY_INTERVAL_MS) return
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) return

        recoveryAttempts++
        lastRecoveryAtMs = now

        val targetVolume = player.volume.coerceAtLeast(0f)
        player.volume = 0f
        player.volume = targetVolume
        player.trackSelectionParameters = player.trackSelectionParameters

        Log.w(LOG_TAG, "Audio pipeline recovery ($reason) attempt=$recoveryAttempts")
    }

    private fun isAudioPipelineError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED) {
            return true
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause.javaClass.name.contains("AudioSink")) return true
            cause = cause.cause
        }
        return false
    }

    companion object {
        private const val LOG_TAG = "PlayerAudioRecovery"
        private const val SILENT_CHECK_DELAY_MS = 2_500L
        private const val MIN_RECOVERY_INTERVAL_MS = 3_000L
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }
}
