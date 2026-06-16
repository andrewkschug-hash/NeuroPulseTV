package com.neuropulse.tv.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(StreamPlaybackStatus.IDLE)
    val status: StateFlow<StreamPlaybackStatus> = _status.asStateFlow()

    private var player: ExoPlayer? = null
    private var evaluateJob: Job? = null
    private var stallJob: Job? = null
    private var tuningClearJob: Job? = null
    private var renderedFirstFrame = false
    private var attached = false
    private var tuning = false
    private var intentionalPause = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
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
                    if (_status.value != StreamPlaybackStatus.UNAVAILABLE) {
                        _status.value = StreamPlaybackStatus.LOADING
                    }
                }
                Player.STATE_READY -> {
                    tuning = false
                    scheduleEvaluation()
                }
                Player.STATE_ENDED -> {
                    if (tuning) {
                        _status.value = StreamPlaybackStatus.LOADING
                    } else {
                        _status.value = StreamPlaybackStatus.ERROR
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                intentionalPause = false
                scheduleEvaluation()
                watchForStall()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            tuning = false
            intentionalPause = false
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
        tuningClearJob?.cancel()
        player?.removeListener(playerListener)
        player?.removeAnalyticsListener(analyticsListener)
        player = null
        attached = false
        tuning = false
        intentionalPause = false
    }

    fun onTuneStarted(streamUrl: String) {
        evaluateJob?.cancel()
        stallJob?.cancel()
        tuningClearJob?.cancel()
        renderedFirstFrame = false
        intentionalPause = false
        tuning = streamUrl.isNotBlank()
        _status.value = if (streamUrl.isBlank()) {
            tuning = false
            StreamPlaybackStatus.UNAVAILABLE
        } else {
            StreamPlaybackStatus.LOADING
        }
        if (streamUrl.isNotBlank()) {
            scheduleEvaluation(delayMs = 12_000)
            tuningClearJob = scope.launch {
                delay(20_000)
                tuning = false
            }
        }
    }

    fun onPlaybackPaused() {
        evaluateJob?.cancel()
        stallJob?.cancel()
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

    /** Same stream already tuned (e.g. guide preview -> fullscreen) — keep playback status accurate. */
    fun onStreamContinued(exo: ExoPlayer) {
        intentionalPause = false
        tuning = false
        evaluateJob?.cancel()
        stallJob?.cancel()
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
            delay(15_000)
            if (intentionalPause || tuning) return@launch
            if (!exo.isPlaying) return@launch
            if (exo.currentPosition <= startPosition + 500 && !renderedFirstFrame) {
                _status.value = StreamPlaybackStatus.NO_SIGNAL
            } else if (exo.currentPosition <= startPosition + 500) {
                _status.value = StreamPlaybackStatus.STALLED
            }
        }
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
}
