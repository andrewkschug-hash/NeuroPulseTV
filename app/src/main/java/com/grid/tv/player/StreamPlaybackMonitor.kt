package com.grid.tv.player

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.grid.tv.util.PerformanceAudit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observes an ExoPlayer instance and infers whether the stream is actually delivering
 * watchable content vs. black screen, audio-only, stall, or failure.
 */
class StreamPlaybackMonitor(
    private val scope: CoroutineScope,
    private val metrics: PlaybackMetricsLogger? = null,
    private val stallThresholdMs: Long = DEFAULT_STALL_THRESHOLD_MS,
    private val cumulativeBuffering: CumulativeBufferingTracker = CumulativeBufferingTracker()
) {
    private val _status = MutableStateFlow(StreamPlaybackStatus.IDLE)
    val status: StateFlow<StreamPlaybackStatus> = _status.asStateFlow()

    private var player: ExoPlayer? = null
    private var evaluateJob: Job? = null
    private var stallJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var cumulativeBudgetJob: Job? = null
    private var tuningClearJob: Job? = null
    private var renderedFirstFrame = false
    private var attached = false
    private var tuning = false
    private var intentionalPause = false
    private var bufferingStartedAtMs: Long = 0L
    private var wasBuffering = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val now = System.currentTimeMillis()
            when (playbackState) {
                Player.STATE_IDLE -> {
                    endBufferingEpisode(now)
                    cancelBufferingWatchdog()
                    if (_status.value == StreamPlaybackStatus.UNAVAILABLE) return
                    if (tuning || intentionalPause) {
                        if (_status.value != StreamPlaybackStatus.UNAVAILABLE) {
                            _status.value = StreamPlaybackStatus.LOADING
                        }
                        return
                    }
                    _status.value = StreamPlaybackStatus.ERROR
                }
                Player.STATE_BUFFERING -> {
                    if (!wasBuffering) {
                        wasBuffering = true
                        cumulativeBuffering.onBufferingStarted(now)
                    }
                    if (_status.value != StreamPlaybackStatus.UNAVAILABLE) {
                        _status.value = StreamPlaybackStatus.LOADING
                    }
                    startBufferingWatchdog()
                }
                Player.STATE_READY -> {
                    endBufferingEpisode(now)
                    if (renderedFirstFrame) {
                        cancelBufferingWatchdog()
                        cumulativeBuffering.onHealthyPlayback(now)
                    }
                    tuning = false
                    scheduleEvaluation()
                    player?.let { exo ->
                        PerformanceAudit.logPlayerBufferSnapshot(
                            label = "STATE_READY",
                            player = exo,
                            maxBufferMs = TimeshiftManager.maxBufferMs,
                            minBufferMs = TimeshiftManager.minBufferMs,
                            profileName = TimeshiftManager.activeProfileName
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    endBufferingEpisode(now)
                    cancelBufferingWatchdog()
                    if (tuning) {
                        _status.value = StreamPlaybackStatus.LOADING
                    } else {
                        _status.value = StreamPlaybackStatus.ERROR
                    }
                }
            }
            evaluateCumulativeBudget(now)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                intentionalPause = false
                scheduleEvaluation()
                watchForStall()
                if (renderedFirstFrame) {
                    cumulativeBuffering.onHealthyPlayback()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            endBufferingEpisode()
            cancelBufferingWatchdog()
            tuning = false
            intentionalPause = false
            Log.w("StreamPlaybackMonitor", "playback error code=${error.errorCode}", error)
            _status.value = StreamPlaybackStatus.ERROR
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long
        ) {
            renderedFirstFrame = true
            tuning = false
            _status.value = StreamPlaybackStatus.PLAYING
            evaluateJob?.cancel()
            cumulativeBuffering.onFirstFrameRendered()
            cumulativeBuffering.onHealthyPlayback()
            startCumulativeBudgetMonitor()
        }
    }

    fun attach(exoPlayer: ExoPlayer) {
        if (attached && player === exoPlayer) return
        detach()
        player = exoPlayer
        exoPlayer.addListener(playerListener)
        exoPlayer.addAnalyticsListener(analyticsListener)
        attached = true
    }

    fun detach() {
        evaluateJob?.cancel()
        stallJob?.cancel()
        cancelBufferingWatchdog()
        cumulativeBudgetJob?.cancel()
        tuningClearJob?.cancel()
        player?.removeListener(playerListener)
        player?.removeAnalyticsListener(analyticsListener)
        player = null
        attached = false
        tuning = false
        intentionalPause = false
        bufferingStartedAtMs = 0L
        wasBuffering = false
    }

    fun onTuneStarted(streamUrl: String) {
        evaluateJob?.cancel()
        stallJob?.cancel()
        cancelBufferingWatchdog()
        cumulativeBudgetJob?.cancel()
        tuningClearJob?.cancel()
        renderedFirstFrame = false
        intentionalPause = false
        wasBuffering = false
        cumulativeBuffering.onTuneStarted()
        tuning = streamUrl.isNotBlank()
        _status.value = if (streamUrl.isBlank()) {
            tuning = false
            StreamPlaybackStatus.UNAVAILABLE
        } else {
            StreamPlaybackStatus.LOADING
        }
        if (streamUrl.isNotBlank()) {
            scheduleEvaluation(delayMs = CumulativeBufferingTracker.DEFAULT_STARTUP_GRACE_MS)
            tuningClearJob = scope.launch {
                delay(20_000)
                tuning = false
            }
        }
    }

    fun onPlaybackPaused() {
        evaluateJob?.cancel()
        stallJob?.cancel()
        cancelBufferingWatchdog()
        cumulativeBudgetJob?.cancel()
        endBufferingEpisode()
        intentionalPause = true
        tuning = false
        _status.value = StreamPlaybackStatus.IDLE
    }

    fun onPreviewResumed() {
        intentionalPause = false
        tuning = false
        val exo = player
        _status.value = when {
            exo == null -> StreamPlaybackStatus.IDLE
            exo.mediaItemCount == 0 -> StreamPlaybackStatus.UNAVAILABLE
            exo.playbackState == Player.STATE_READY && exo.isPlaying && exo.videoFormat != null -> {
                renderedFirstFrame = true
                StreamPlaybackStatus.PLAYING
            }
            exo.playbackState == Player.STATE_READY && exo.isPlaying && exo.audioFormat != null -> {
                StreamPlaybackStatus.AUDIO_ONLY
            }
            else -> StreamPlaybackStatus.LOADING
        }
        if (_status.value == StreamPlaybackStatus.LOADING && exo != null && exo.mediaItemCount > 0) {
            scheduleEvaluation(delayMs = 8_000)
        }
    }

    fun onStreamContinued(exo: ExoPlayer) {
        intentionalPause = false
        tuning = false
        evaluateJob?.cancel()
        stallJob?.cancel()
        cancelBufferingWatchdog()
        tuningClearJob?.cancel()
        _status.value = when (exo.playbackState) {
            Player.STATE_READY -> when {
                renderedFirstFrame -> StreamPlaybackStatus.PLAYING
                exo.videoFormat != null && exo.isPlaying -> {
                    renderedFirstFrame = true
                    StreamPlaybackStatus.PLAYING
                }
                exo.audioFormat != null && exo.videoFormat == null && exo.isPlaying -> {
                    StreamPlaybackStatus.AUDIO_ONLY
                }
                else -> StreamPlaybackStatus.LOADING
            }
            Player.STATE_BUFFERING -> StreamPlaybackStatus.LOADING
            else -> StreamPlaybackStatus.LOADING
        }
        if (_status.value == StreamPlaybackStatus.LOADING) {
            scheduleEvaluation(delayMs = 6_000)
        }
    }

    private fun scheduleEvaluation(delayMs: Long = 2_500) {
        evaluateJob?.cancel()
        evaluateJob = scope.launch {
            delay(delayMs)
            evaluateNow()
        }
    }

    private fun watchForStall() {
        stallJob?.cancel()
        stallJob = scope.launch {
            val exo = player ?: return@launch
            val startPosition = exo.currentPosition
            delay(stallThresholdMs)
            if (intentionalPause || tuning) return@launch
            if (!exo.isPlaying) return@launch
            if (exo.currentPosition <= startPosition + 500 && !renderedFirstFrame) {
                _status.value = StreamPlaybackStatus.NO_SIGNAL
            } else if (exo.currentPosition <= startPosition + 500) {
                metrics?.logWatchdogRecovery("position_stall", stallThresholdMs)
                _status.value = StreamPlaybackStatus.STALLED
            }
        }
    }

    private fun startBufferingWatchdog() {
        if (bufferingWatchdogJob?.isActive == true) return
        if (bufferingStartedAtMs == 0L) {
            bufferingStartedAtMs = System.currentTimeMillis()
        }
        bufferingWatchdogJob = scope.launch {
            delay(stallThresholdMs)
            val exo = player ?: return@launch
            if (intentionalPause || tuning) return@launch
            if (exo.playbackState != Player.STATE_BUFFERING) return@launch
            val elapsed = System.currentTimeMillis() - bufferingStartedAtMs
            metrics?.logWatchdogRecovery("buffering_stall", elapsed)
            _status.value = StreamPlaybackStatus.STALLED
        }
    }

    private fun startCumulativeBudgetMonitor() {
        cumulativeBudgetJob?.cancel()
        cumulativeBudgetJob = scope.launch {
            while (true) {
                delay(2_000)
                evaluateCumulativeBudget()
            }
        }
    }

    private fun evaluateCumulativeBudget(nowMs: Long = System.currentTimeMillis()) {
        if (intentionalPause || tuning) return
        if (cumulativeBuffering.isBudgetExceeded(
                nowMs = nowMs,
                requireFirstFrame = true,
                hasRenderedFirstFrame = renderedFirstFrame
            )
        ) {
            val total = cumulativeBuffering.totalBufferingMs(nowMs)
            metrics?.logCumulativeBufferingBudget(
                totalMs = total,
                windowMs = CumulativeBufferingTracker.DEFAULT_WINDOW_MS
            )
            metrics?.logWatchdogRecovery("cumulative_buffering_budget", total)
            _status.value = StreamPlaybackStatus.STALLED
        }
    }

    private fun endBufferingEpisode(nowMs: Long = System.currentTimeMillis()) {
        if (wasBuffering) {
            wasBuffering = false
            cumulativeBuffering.onBufferingEnded(nowMs)
        }
    }

    private fun cancelBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        bufferingStartedAtMs = 0L
    }

    private fun evaluateNow() {
        val exo = player ?: return
        when (exo.playbackState) {
            Player.STATE_IDLE -> {
                if (_status.value == StreamPlaybackStatus.UNAVAILABLE) return
                if (tuning || intentionalPause) {
                    _status.value = StreamPlaybackStatus.LOADING
                    return
                }
                _status.value = StreamPlaybackStatus.ERROR
            }
            Player.STATE_BUFFERING -> _status.value = StreamPlaybackStatus.LOADING
            Player.STATE_READY -> {
                if (renderedFirstFrame) {
                    _status.value = StreamPlaybackStatus.PLAYING
                    return
                }
                val hasVideo = exo.videoFormat != null
                val hasAudio = exo.audioFormat != null
                _status.value = when {
                    !hasVideo && hasAudio -> StreamPlaybackStatus.AUDIO_ONLY
                    exo.isPlaying -> StreamPlaybackStatus.LOADING
                    else -> StreamPlaybackStatus.LOADING
                }
            }
            else -> Unit
        }
    }

    companion object {
        const val DEFAULT_STALL_THRESHOLD_MS = 15_000L
    }
}
