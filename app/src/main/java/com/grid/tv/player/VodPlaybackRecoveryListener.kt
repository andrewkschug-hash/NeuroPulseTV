package com.grid.tv.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Keeps VOD playback intent ([wantsPlayback]) across buffering stalls and transient errors.
 * Restores [Player.playWhenReady] when the stream recovers unless the user explicitly paused.
 *
 * Recovery is lifecycle-safe (Handler on main looper) — not tied to Compose recomposition.
 */
class VodPlaybackRecoveryListener(
    private val player: ExoPlayer,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val onFatalError: (PlaybackException) -> Unit = {},
    private val onRecoverableStall: () -> Unit = {},
    private val onTryNextQualityVariant: ((positionMs: Long) -> Boolean)? = null,
    private val isEmulator: Boolean = false,
) : Player.Listener {

    private var detached = false
    private var wasBuffering = false
    private var userPaused = false
    private var wantsPlayback = true
    private var bufferingStartedAtMs = 0L
    private var sourceErrorRetryCount = 0
    private var idleRecoveryAttempts = 0
    private var bufferingRecoveryAttempts = 0
    private var lastRecoveryAttemptAtMs = 0L
    private var lastObservedPositionMs = -1L
    private var lastProgressAtMs = 0L
    private var stuckRecoverableEmitted = false

    private val bufferingWatchdogRunnable = Runnable { onBufferingWatchdogTick() }
    private val positionWatchdogRunnable = Runnable { onPositionWatchdogTick() }

    fun attach() {
        if (!detached) {
            player.addListener(this)
            lastProgressAtMs = System.currentTimeMillis()
            startPositionWatchdog()
        }
    }

    fun detach() {
        if (detached) return
        detached = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(this)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            userPaused = !playWhenReady
            wantsPlayback = playWhenReady
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                if (!wasBuffering) {
                    wasBuffering = true
                }
                startBufferingWatchdog()
            }
            Player.STATE_READY -> {
                cancelBufferingWatchdog()
                resumeIfNeeded()
                wasBuffering = false
                idleRecoveryAttempts = 0
            }
            Player.STATE_ENDED -> {
                cancelBufferingWatchdog()
                wasBuffering = false
            }
            Player.STATE_IDLE -> {
                cancelBufferingWatchdog()
                wasBuffering = false
                attemptRecoveryIfUnexpected()
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            sourceErrorRetryCount = 0
            bufferingRecoveryAttempts = 0
            stuckRecoverableEmitted = false
            lastProgressAtMs = System.currentTimeMillis()
            lastObservedPositionMs = player.currentPosition
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        attemptErrorRecovery(error)
    }

    private fun onBufferingWatchdogTick() {
        if (detached || userPaused || !wantsPlayback) return
        if (player.playbackState != Player.STATE_BUFFERING) return
        val elapsed = System.currentTimeMillis() - bufferingStartedAtMs
        if (elapsed < BUFFERING_RECOVERY_THRESHOLD_MS) {
            val remaining = BUFFERING_RECOVERY_THRESHOLD_MS - elapsed
            handler.postDelayed(bufferingWatchdogRunnable, remaining.coerceAtLeast(250L))
            return
        }
        attemptBufferingRecovery()
    }

    private fun onPositionWatchdogTick() {
        if (detached) return
        val now = System.currentTimeMillis()
        val position = player.currentPosition
        if (player.isPlaying && position > lastObservedPositionMs + POSITION_PROGRESS_EPSILON_MS) {
            lastObservedPositionMs = position
            lastProgressAtMs = now
            stuckRecoverableEmitted = false
        } else if (lastObservedPositionMs < 0L) {
            lastObservedPositionMs = position
            lastProgressAtMs = now
        }

        if (!userPaused && wantsPlayback) {
            val stalled = now - lastProgressAtMs >= POSITION_STALL_THRESHOLD_MS
            val state = player.playbackState
            val readyButFrozen = state == Player.STATE_READY && !player.isPlaying && player.playWhenReady
            if (stalled && (state == Player.STATE_BUFFERING || readyButFrozen)) {
                attemptLightRecovery("position_stall")
            }
        }
        handler.postDelayed(positionWatchdogRunnable, POSITION_POLL_INTERVAL_MS)
    }

    private fun resumeIfNeeded() {
        if (detached || userPaused || !wantsPlayback) return
        if (!player.playWhenReady) {
            player.playWhenReady = true
        }
        if (!player.isPlaying) {
            player.play()
        }
    }

    private fun startBufferingWatchdog() {
        if (bufferingStartedAtMs == 0L) {
            bufferingStartedAtMs = System.currentTimeMillis()
        }
        handler.removeCallbacks(bufferingWatchdogRunnable)
        handler.postDelayed(bufferingWatchdogRunnable, BUFFERING_RECOVERY_THRESHOLD_MS)
    }

    private fun cancelBufferingWatchdog() {
        handler.removeCallbacks(bufferingWatchdogRunnable)
        bufferingStartedAtMs = 0L
    }

    private fun startPositionWatchdog() {
        handler.removeCallbacks(positionWatchdogRunnable)
        handler.postDelayed(positionWatchdogRunnable, POSITION_POLL_INTERVAL_MS)
    }

    private fun canAttemptRecovery(): Boolean {
        if (detached || userPaused || !wantsPlayback) return false
        val now = System.currentTimeMillis()
        return now - lastRecoveryAttemptAtMs >= RECOVERY_DEBOUNCE_MS
    }

    private fun attemptLightRecovery(reason: String) {
        if (!canAttemptRecovery()) return
        lastRecoveryAttemptAtMs = System.currentTimeMillis()
        Log.w(LOG_TAG, "light recovery reason=$reason positionMs=${player.currentPosition}")
        player.playWhenReady = true
        player.play()
    }

    private fun attemptBufferingRecovery() {
        if (detached || userPaused || !wantsPlayback) return
        if (player.playbackState != Player.STATE_BUFFERING) return
        if (!canAttemptRecovery()) return

        if (bufferingRecoveryAttempts >= MAX_BUFFERING_RECOVERY_ATTEMPTS) {
            if (!stuckRecoverableEmitted) {
                stuckRecoverableEmitted = true
                Log.w(
                    LOG_TAG,
                    "buffering deadlock — recoverable stall after $bufferingRecoveryAttempts attempts"
                )
                onRecoverableStall()
            }
            return
        }

        bufferingRecoveryAttempts++
        lastRecoveryAttemptAtMs = System.currentTimeMillis()
        val position = player.currentPosition.coerceAtLeast(0L)
        Log.w(
            LOG_TAG,
            "buffering recovery attempt=$bufferingRecoveryAttempts seek+play positionMs=$position"
        )
        player.seekTo(position)
        if (bufferingRecoveryAttempts >= MAX_BUFFERING_RECOVERY_ATTEMPTS) {
            player.prepare()
        }
        player.playWhenReady = true
        player.play()
        bufferingStartedAtMs = System.currentTimeMillis()
    }

    private fun attemptRecoveryIfUnexpected() {
        if (detached || userPaused || !wantsPlayback) return
        if (player.mediaItemCount == 0) return
        if (idleRecoveryAttempts >= MAX_IDLE_RECOVERY_ATTEMPTS) return
        if (!canAttemptRecovery()) return
        idleRecoveryAttempts++
        lastRecoveryAttemptAtMs = System.currentTimeMillis()
        Log.w(LOG_TAG, "unexpected IDLE recovery attempt=$idleRecoveryAttempts")
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    private fun failPermanently(error: PlaybackException) {
        player.playWhenReady = false
        player.stop()
        onFatalError(error)
    }

    private fun attemptErrorRecovery(error: PlaybackException) {
        if (detached) return
        if (userPaused || !wantsPlayback) {
            failPermanently(error)
            return
        }

        val position = player.currentPosition.coerceAtLeast(0L)
        when {
            error.isFormatUnsupportedPlaybackError() -> {
                Log.w(
                    LOG_TAG,
                    "FORMAT_UNSUPPORTED (${error.errorCode}) — advancing quality variant, " +
                        "not retrying same URL emulator=$isEmulator"
                )
                if (attemptQualityVariantFallback(position, error)) return
                failPermanently(error)
            }
            error.isSourceRetryPlaybackError() -> {
                if (sourceErrorRetryCount >= MAX_SOURCE_ERROR_RETRIES) {
                    Log.w(
                        LOG_TAG,
                        "SOURCE_ERROR retries exhausted — trying next quality variant " +
                            "code=${error.errorCode} emulator=$isEmulator"
                    )
                    if (attemptQualityVariantFallback(position, error)) return
                    Log.e(LOG_TAG, "error recovery exhausted retries=$sourceErrorRetryCount", error)
                    failPermanently(error)
                    return
                }
                retrySameUrl(error, position)
            }
            error.isCodecCapabilityError() -> {
                if (attemptQualityVariantFallback(position, error)) return
                failPermanently(error)
            }
            else -> {
                if (sourceErrorRetryCount >= MAX_SOURCE_ERROR_RETRIES) {
                    failPermanently(error)
                    return
                }
                retrySameUrl(error, position)
            }
        }
    }

    private fun retrySameUrl(error: PlaybackException, position: Long) {
        val mediaItem = player.currentMediaItem
        if (mediaItem == null) {
            failPermanently(error)
            return
        }
        sourceErrorRetryCount++
        val delayMs = errorBackoffMs(sourceErrorRetryCount)
        Log.w(
            LOG_TAG,
            "SOURCE_ERROR retry scheduled attempt=$sourceErrorRetryCount delayMs=$delayMs " +
                "positionMs=$position code=${error.errorCode} emulator=$isEmulator"
        )
        handler.postDelayed({
            if (detached || userPaused || !wantsPlayback) return@postDelayed
            if (System.currentTimeMillis() - lastRecoveryAttemptAtMs < RECOVERY_DEBOUNCE_MS) return@postDelayed
            lastRecoveryAttemptAtMs = System.currentTimeMillis()
            runCatching {
                player.setMediaItem(mediaItem, position)
                player.prepare()
                player.playWhenReady = true
                player.play()
            }.onFailure { failure ->
                Log.e(LOG_TAG, "SOURCE_ERROR retry prepare failed attempt=$sourceErrorRetryCount", failure)
                if (sourceErrorRetryCount >= MAX_SOURCE_ERROR_RETRIES) {
                    failPermanently(error)
                }
            }
        }, delayMs)
    }

    private fun attemptQualityVariantFallback(positionMs: Long, error: PlaybackException): Boolean {
        val switched = onTryNextQualityVariant?.invoke(positionMs) == true
        if (switched) {
            sourceErrorRetryCount = 0
            Log.i(
                LOG_TAG,
                "switched quality variant after error code=${error.errorCode} emulator=$isEmulator"
            )
        }
        return switched
    }

    companion object {
        private const val LOG_TAG = "VodPlaybackRecovery"
        const val POSITION_POLL_INTERVAL_MS = 2_000L
        const val POSITION_STALL_THRESHOLD_MS = 6_000L
        const val POSITION_PROGRESS_EPSILON_MS = 300L
        const val BUFFERING_RECOVERY_THRESHOLD_MS = 15_000L
        const val RECOVERY_DEBOUNCE_MS = 20_000L
        const val MAX_BUFFERING_RECOVERY_ATTEMPTS = 2
        const val MAX_SOURCE_ERROR_RETRIES = 3
        const val MAX_IDLE_RECOVERY_ATTEMPTS = 2
        private const val ERROR_BACKOFF_BASE_MS = 1_000L

        fun errorBackoffMs(attempt: Int): Long {
            val exponent = (attempt - 1).coerceAtLeast(0)
            return ERROR_BACKOFF_BASE_MS shl exponent.coerceAtMost(4)
        }
    }
}

/** Media3 4003 — decoder cannot handle this format/profile. */
fun PlaybackException.isFormatUnsupportedPlaybackError(): Boolean =
    errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

/**
 * Media3 4001 (decoder init) and transient source/IO failures — retry same URL before fallback.
 */
fun PlaybackException.isSourceRetryPlaybackError(): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_TIMEOUT -> true
    else -> false
}
