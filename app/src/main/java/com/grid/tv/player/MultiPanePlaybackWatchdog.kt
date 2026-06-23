package com.grid.tv.player

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight shared watchdog for Split View / MultiView pane players.
 * Retries stalled panes (re-prepare same URL) without full live-guide failover.
 */
@Singleton
class MultiPanePlaybackWatchdog @Inject constructor(
    private val streamFormatRegistry: IptvStreamFormatRegistry,
    private val streamFormatProber: IptvStreamFormatProber,
    private val playbackNetworkExclusivity: PlaybackNetworkExclusivity
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val panes = ConcurrentHashMap<Int, PaneWatchState>()
    private var pollJob: Job? = null

    private data class PaneWatchState(
        val streamUrl: String,
        val player: ExoPlayer,
        val bufferingTracker: CumulativeBufferingTracker,
        val listener: Player.Listener,
        val analyticsListener: AnalyticsListener,
        var retryCount: Int = 0,
        var lastRecoveryAtMs: Long = 0L,
        var renderedFirstFrame: Boolean = false,
        var tuneStartedAtMs: Long = System.currentTimeMillis()
    )

    fun attachPane(paneIndex: Int, player: ExoPlayer, streamUrl: String) {
        if (streamUrl.isBlank()) return
        detachPane(paneIndex)

        val bufferingTracker = CumulativeBufferingTracker()
        bufferingTracker.onTuneStarted()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val now = System.currentTimeMillis()
                when (playbackState) {
                    Player.STATE_BUFFERING -> bufferingTracker.onBufferingStarted(now)
                    Player.STATE_READY -> {
                        bufferingTracker.onBufferingEnded(now)
                        if (panes[paneIndex]?.renderedFirstFrame == true) {
                            bufferingTracker.onHealthyPlayback(now)
                        }
                    }
                    Player.STATE_IDLE, Player.STATE_ENDED -> bufferingTracker.onBufferingEnded(now)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                requestRecovery(paneIndex, reason = "player_error")
            }
        }

        val analyticsListener = object : AnalyticsListener {
            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                panes[paneIndex]?.renderedFirstFrame = true
                bufferingTracker.onFirstFrameRendered()
                bufferingTracker.onHealthyPlayback()
            }
        }

        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        panes[paneIndex] = PaneWatchState(
            streamUrl = streamUrl,
            player = player,
            bufferingTracker = bufferingTracker,
            listener = listener,
            analyticsListener = analyticsListener
        )
        ensurePolling()
    }

    fun detachPane(paneIndex: Int) {
        panes.remove(paneIndex)?.let { state ->
            state.player.removeListener(state.listener)
            state.player.removeAnalyticsListener(state.analyticsListener)
        }
        if (panes.isEmpty()) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    fun detachAll() {
        panes.keys.toList().forEach(::detachPane)
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        val pollIntervalMs = LowEndDeviceMode.current().watchdogPollIntervalMs
        pollJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                evaluateAllPanes()
            }
        }
    }

    private fun evaluateAllPanes() {
        val now = System.currentTimeMillis()
        panes.forEach { (index, state) ->
            if (state.player.playbackState == Player.STATE_BUFFERING) {
                if (now - state.tuneStartedAtMs > TUNE_TIMEOUT_MS) {
                    requestRecovery(index, reason = "tune_timeout")
                    return@forEach
                }
            }
            if (state.bufferingTracker.isBudgetExceeded(
                    nowMs = now,
                    requireFirstFrame = true,
                    hasRenderedFirstFrame = state.renderedFirstFrame
                )
            ) {
                requestRecovery(index, reason = "cumulative_buffering")
            }
        }
    }

    private fun requestRecovery(paneIndex: Int, reason: String) {
        val state = panes[paneIndex] ?: return
        val now = System.currentTimeMillis()
        if (state.retryCount >= MAX_RETRIES) return
        if (now - state.lastRecoveryAtMs < RETRY_COOLDOWN_MS) return

        state.retryCount++
        state.lastRecoveryAtMs = now
        state.tuneStartedAtMs = now
        state.renderedFirstFrame = false
        state.bufferingTracker.onTuneStarted(now)

        scope.launch(Dispatchers.Main.immediate) {
            runCatching {
                if (!playbackNetworkExclusivity.shouldSkipPreflightProbe(state.streamUrl)) {
                    streamFormatProber.probeAndRegister(state.streamUrl)
                }
                state.player.stop()
                state.player.clearMediaItems()
                state.player.setMediaItem(
                    IptvLiveMediaItem.build(state.streamUrl, registry = streamFormatRegistry)
                )
                state.player.prepare()
                state.player.playWhenReady = true
                Log.w(
                    TAG,
                    "pane recovery index=$paneIndex reason=$reason attempt=${state.retryCount} " +
                        "url=${state.streamUrl.take(96)}"
                )
            }.onFailure { error ->
                Log.e(TAG, "pane recovery failed index=$paneIndex", error)
            }
        }
    }

    companion object {
        private const val TAG = "MultiPaneWatchdog"
        private const val POLL_INTERVAL_MS = 2_000L
        private const val TUNE_TIMEOUT_MS = 25_000L
        private const val MAX_RETRIES = 2
        private const val RETRY_COOLDOWN_MS = 8_000L
    }
}
