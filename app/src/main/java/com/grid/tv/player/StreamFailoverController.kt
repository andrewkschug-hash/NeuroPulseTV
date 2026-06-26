package com.grid.tv.player

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryCollector
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.allStreamUrls
import com.grid.tv.domain.model.sourceIdForUrl
import com.grid.tv.feature.startup.StartupDependencyProbe
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StreamFailoverUiState(
    val isRecovering: Boolean = false,
    val showRestored: Boolean = false,
    val message: String? = null
)

interface StreamFailoverPlaybackActions {
    fun reconnectSameStream()
    fun switchToStream(url: String)
}

enum class PlaybackFailureCategory {
    NETWORK,
    HTTP,
    BUFFERING_TIMEOUT,
    DECODER,
    UNKNOWN
}

/**
 * Automatic stream failover: reconnect → alternate URLs, bounded by [AppSettings.streamRetries].
 * Monitors errors, stalls, excessive buffering, and tune timeouts.
 */
@Singleton
class StreamFailoverController @Inject constructor(
    private val analytics: StreamFailoverAnalytics,
    private val playbackMetrics: PlaybackMetricsLogger,
    private val playbackTelemetry: PlaybackTelemetryCollector,
    private val healthAggregator: com.grid.tv.feature.health.intelligence.StreamHealthAggregator,
    private val playbackNetworkCoordinator: PlaybackNetworkCoordinator
) {
    init {
        StartupDependencyProbe.traceInjectedInit("StreamFailoverController")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(StreamFailoverUiState())
    val uiState: StateFlow<StreamFailoverUiState> = _uiState.asStateFlow()

    private var channel: Channel? = null
    private var streamUrls: List<String> = emptyList()
    private var activeUrl: String? = null
    private var player: ExoPlayer? = null
    private var actions: StreamFailoverPlaybackActions? = null

    private var recoveryJob: Job? = null
    private var monitorJob: Job? = null
    private var restoredHideJob: Job? = null
    private var wasRecovering = false
    private var unhealthySinceMs: Long = 0L
    private var tuneStartedAtMs: Long = 0L
    private var autoReconnectEnabled: Boolean = true
    private var intentionalIdle = false
    private var maxStreamRetries: Int = DEFAULT_STREAM_RETRIES
    private var recoveryBlockedUntilMs: Long = 0L
    private var recoveryGeneration: Int = 0
    private var sessionFailoverCount: Int = 0
    private val blockedUrlUntilMs = mutableMapOf<String, Long>()
    private var tuneSettleUntilMs: Long = 0L
    private var fatalErrorNotified = false

    fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) {
            cancelRecovery()
            recoveryBlockedUntilMs = 0L
            _uiState.value = StreamFailoverUiState()
        }
    }

    fun setStreamRetries(count: Int) {
        maxStreamRetries = count.coerceIn(MIN_STREAM_RETRIES, MAX_STREAM_RETRIES)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    if (!autoReconnectEnabled) return
                    if (!intentionalIdle && !recoveryJob.isActive()) {
                        requestRecovery(
                            trigger = FailoverTrigger.PLAYBACK_IDLE,
                            category = PlaybackFailureCategory.UNKNOWN
                        )
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!autoReconnectEnabled) return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                val exo = player ?: return
                playbackMetrics.logBehindLiveWindowRecovery()
                exo.seekToDefaultPosition()
                exo.prepare()
                return
            }
            val category = error.failureCategory()
            val recoverable = error.isRecoverableForPlayback()
            if (!recoverable) {
                PlaybackHttpFailure.logHttpFailure(error, activeUrl, LOG_TAG)
                blockRecoveryForSession()
                notifyFatalPlaybackError(error)
                Log.w(
                    LOG_TAG,
                    "non-recoverable playback error code=${error.errorCode} url=$activeUrl",
                    error
                )
                return
            }
            Log.w(
                LOG_TAG,
                "playback error category=$category code=${error.errorCode} url=$activeUrl",
                error
            )
            requestRecovery(FailoverTrigger.PLAYBACK_ERROR, category)
        }
    }

    fun configure(
        channel: Channel,
        activeStreamUrl: String?,
        orderedStreamUrls: List<String>? = null
    ) {
        this.channel = channel
        streamUrls = orderedStreamUrls
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: channel.allStreamUrls()
        activeUrl = activeStreamUrl ?: streamUrls.firstOrNull()
        tuneStartedAtMs = System.currentTimeMillis()
        unhealthySinceMs = 0L
        intentionalIdle = false
        recoveryBlockedUntilMs = 0L
        sessionFailoverCount = 0
        blockedUrlUntilMs.clear()
        tuneSettleUntilMs = 0L
        fatalErrorNotified = false
        cancelRecovery()
        clearRestoredBanner()
        _uiState.value = StreamFailoverUiState()
        Log.i(
            LOG_TAG,
            "configured channel=${channel.id} active=$activeUrl urls=${streamUrls.size} " +
                "order=${streamUrls.joinToString { it.take(48) }}"
        )
    }

    suspend fun configureWithHealthRanking(channel: Channel, activeStreamUrl: String?) {
        val ordered = healthAggregator.orderedStreamUrls(channel)
        configure(channel, activeStreamUrl, ordered)
    }

    fun attach(exoPlayer: ExoPlayer, playbackActions: StreamFailoverPlaybackActions) {
        detach()
        player = exoPlayer
        actions = playbackActions
        intentionalIdle = false
        exoPlayer.addListener(playerListener)
        startMonitoring()
    }

    fun detach() {
        recoveryJob?.cancel()
        monitorJob?.cancel()
        restoredHideJob?.cancel()
        player?.removeListener(playerListener)
        player = null
        actions = null
        intentionalIdle = true
    }

    fun onPlaybackPaused() {
        intentionalIdle = true
        cancelRecovery()
        monitorJob?.cancel()
    }

    fun onPlaybackResumed() {
        intentionalIdle = false
        tuneStartedAtMs = System.currentTimeMillis()
        startMonitoring()
    }

    fun onPlaybackStatus(status: StreamPlaybackStatus) {
        if (intentionalIdle || recoveryJob.isActive()) return

        when (status) {
            StreamPlaybackStatus.PLAYING, StreamPlaybackStatus.AUDIO_ONLY -> onHealthyPlayback()
            StreamPlaybackStatus.LOADING -> evaluateLoadingTimeout()
            StreamPlaybackStatus.ERROR,
            StreamPlaybackStatus.UNAVAILABLE,
            StreamPlaybackStatus.NO_SIGNAL,
            StreamPlaybackStatus.STALLED -> evaluateUnhealthyStatus(status)
            StreamPlaybackStatus.IDLE -> Unit
        }
    }

    fun activeStreamUrl(): String? = activeUrl

    fun onStreamSwitched(url: String) {
        activeUrl = url
        tuneStartedAtMs = System.currentTimeMillis()
        unhealthySinceMs = 0L
        recoveryBlockedUntilMs = 0L
        onStreamTuneStarted()
    }

    /** Called when a new stream load begins — suppresses failover until playback settles. */
    fun onStreamTuneStarted() {
        tuneSettleUntilMs = System.currentTimeMillis() + TUNE_SETTLE_MS
        fatalErrorNotified = false
        cancelRecovery()
    }

    fun cancelRecoveryAndBlockSession() {
        blockRecoveryForSession()
    }

    /**
     * Called after a failover-driven URL switch completes and media is loaded.
     * Refreshes active URL, blocks the previous URL temporarily, and reorders failover candidates.
     */
    fun onSuccessfulUrlSwitch(url: String) {
        val previous = activeUrl
        activeUrl = url
        previous?.takeIf { it != url }?.let { markUrlBlocked(it) }
        blockedUrlUntilMs.remove(url)
        streamUrls = StreamRecoveryPlanner.reorderWithActiveFirst(streamUrls, url)
        tuneStartedAtMs = System.currentTimeMillis()
        unhealthySinceMs = 0L
        recoveryBlockedUntilMs = 0L
        onStreamTuneStarted()
        playbackTelemetry.onStreamSwitch(channel?.sourceIdForUrl(url) ?: url)
        Log.i(LOG_TAG, "failover state refreshed active=$url blocked=${blockedUrlUntilMs.keys}")
    }

    fun sessionFailoverCount(): Int = sessionFailoverCount

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                delay(2_000)
                if (intentionalIdle || recoveryJob.isActive()) continue
                evaluateLoadingTimeout()
            }
        }
    }

    private fun evaluateLoadingTimeout() {
        if (!autoReconnectEnabled) return
        val elapsed = System.currentTimeMillis() - tuneStartedAtMs
        if (elapsed >= TUNE_TIMEOUT_MS) {
            requestRecovery(
                trigger = FailoverTrigger.TUNE_TIMEOUT,
                category = PlaybackFailureCategory.BUFFERING_TIMEOUT
            )
        }
    }

    private fun evaluateUnhealthyStatus(status: StreamPlaybackStatus) {
        if (!autoReconnectEnabled) return
        if (unhealthySinceMs == 0L) unhealthySinceMs = System.currentTimeMillis()
        val threshold = when (status) {
            StreamPlaybackStatus.ERROR, StreamPlaybackStatus.UNAVAILABLE -> 0L
            else -> UNHEALTHY_GRACE_MS
        }
        if (System.currentTimeMillis() - unhealthySinceMs >= threshold) {
            val trigger = when (status) {
                StreamPlaybackStatus.STALLED -> FailoverTrigger.STALL
                StreamPlaybackStatus.NO_SIGNAL -> FailoverTrigger.NO_SIGNAL
                else -> FailoverTrigger.PLAYBACK_ERROR
            }
            val category = when (status) {
                StreamPlaybackStatus.ERROR, StreamPlaybackStatus.UNAVAILABLE ->
                    PlaybackFailureCategory.HTTP
                StreamPlaybackStatus.STALLED,
                StreamPlaybackStatus.NO_SIGNAL -> PlaybackFailureCategory.BUFFERING_TIMEOUT
                else -> PlaybackFailureCategory.UNKNOWN
            }
            requestRecovery(trigger, category)
        }
    }

    private fun onHealthyPlayback() {
        unhealthySinceMs = 0L
        tuneStartedAtMs = System.currentTimeMillis()
        recoveryBlockedUntilMs = 0L

        if (wasRecovering || recoveryJob.isActive()) {
            wasRecovering = false
            invalidateRecovery()
            showRestoredBanner()
        }
        if (_uiState.value.isRecovering) {
            _uiState.value = StreamFailoverUiState()
        }
    }

    private fun requestRecovery(trigger: FailoverTrigger, category: PlaybackFailureCategory) {
        if (!autoReconnectEnabled) return
        if (recoveryJob.isActive()) return
        if (channel == null || streamUrls.isEmpty()) return
        if (intentionalIdle) return
        if (System.currentTimeMillis() < recoveryBlockedUntilMs) return
        if (System.currentTimeMillis() < tuneSettleUntilMs) return
        if (playbackNetworkCoordinator.isFailoverBlocked()) return

        val maxAttempts = maxStreamRetries
        if (maxAttempts <= 0) return

        val steps = StreamRecoveryPlanner.buildSteps(
            streamUrls = streamUrls,
            activeUrl = activeUrl,
            maxAttempts = maxAttempts,
            urlAvailable = ::isUrlAvailable
        )
        if (steps.isEmpty()) return

        val generation = ++recoveryGeneration
        recoveryJob = scope.launch {
            wasRecovering = true
            val channelId = channel?.id
            sessionFailoverCount++
            channelId?.let { analytics.recordFailover(it) }
            _uiState.value = StreamFailoverUiState(isRecovering = true)

            var attempt = 0
            for (step in steps) {
                if (generation != recoveryGeneration) return@launch
                attempt++
                playbackTelemetry.onReconnectAttempt()
                analytics.logRetryAttempt(
                    channelId = channelId,
                    trigger = trigger.name,
                    category = category.name,
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    step = stepLabel(step)
                )
                when (step) {
                    StreamRecoveryPlanner.Step.Reconnect -> actions?.reconnectSameStream()
                    is StreamRecoveryPlanner.Step.SwitchUrl -> {
                        activeUrl = step.url
                        actions?.switchToStream(step.url)
                    }
                }
                tuneStartedAtMs = System.currentTimeMillis()
                unhealthySinceMs = 0L
                if (waitForHealthy(STEP_TIMEOUT_MS, generation)) {
                    analytics.logRecoverySuccess(
                        channelId = channelId,
                        trigger = trigger.name,
                        category = category.name,
                        attemptsUsed = attempt,
                        maxAttempts = maxAttempts
                    )
                    channelId?.let {
                        analytics.recordSuccessfulRecovery(it, attemptsUsed = attempt)
                    }
                    onHealthyPlayback()
                    return@launch
                }
                activeUrl?.let { markUrlBlocked(it) }
                delay(STEP_GAP_MS)
            }

            if (generation != recoveryGeneration) return@launch

            analytics.logFinalFailure(
                channelId = channelId,
                trigger = trigger.name,
                category = category.name,
                attemptsUsed = attempt,
                maxAttempts = maxAttempts
            )
            channelId?.let { analytics.recordFinalFailure(it, attemptsUsed = attempt) }
            recoveryBlockedUntilMs = System.currentTimeMillis() + RECOVERY_COOLDOWN_MS
            wasRecovering = false
            _uiState.value = StreamFailoverUiState(isRecovering = false)
        }
    }

    private suspend fun waitForHealthy(timeoutMs: Long, generation: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (generation != recoveryGeneration) return false
            val exo = player
            if (exo != null &&
                exo.playbackState == Player.STATE_READY &&
                exo.playWhenReady &&
                (exo.videoFormat != null || exo.audioFormat != null)
            ) {
                return true
            }
            delay(250)
        }
        return false
    }

    private fun showRestoredBanner() {
        restoredHideJob?.cancel()
        _uiState.value = StreamFailoverUiState(
            showRestored = true,
            message = RESTORED_MESSAGE
        )
        restoredHideJob = scope.launch {
            delay(RESTORED_BANNER_MS)
            _uiState.value = StreamFailoverUiState()
        }
    }

    private fun clearRestoredBanner() {
        restoredHideJob?.cancel()
    }

    private fun blockRecoveryForSession() {
        recoveryBlockedUntilMs = Long.MAX_VALUE
        cancelRecovery()
        _uiState.value = StreamFailoverUiState()
    }

    private fun notifyFatalPlaybackError(error: PlaybackException) {
        if (fatalErrorNotified) return
        fatalErrorNotified = true
        val exo = player ?: return
        exo.playWhenReady = false
        exo.stop()
        exo.clearMediaItems()
    }

    private fun cancelRecovery() {
        recoveryJob?.cancel()
        wasRecovering = false
        recoveryGeneration++
    }

    private fun invalidateRecovery() {
        recoveryJob?.cancel()
        recoveryGeneration++
    }

    private fun isUrlAvailable(url: String): Boolean {
        val until = blockedUrlUntilMs[url.trim()] ?: return true
        if (System.currentTimeMillis() >= until) {
            blockedUrlUntilMs.remove(url.trim())
            return true
        }
        return false
    }

    private fun markUrlBlocked(url: String) {
        blockedUrlUntilMs[url.trim()] = System.currentTimeMillis() + FAILED_URL_COOLDOWN_MS
    }

    private enum class FailoverTrigger {
        PLAYBACK_ERROR,
        PLAYBACK_IDLE,
        TUNE_TIMEOUT,
        EXCESSIVE_BUFFERING,
        STALL,
        NO_SIGNAL
    }

    private fun Job?.isActive(): Boolean = this?.isActive == true

    companion object {
        private const val LOG_TAG = "StreamFailover"
        const val RECOVERING_MESSAGE = "Recovering stream..."
        const val RESTORED_MESSAGE = "Stream restored"

        const val DEFAULT_STREAM_RETRIES = 1
        const val MIN_STREAM_RETRIES = 0
        const val MAX_STREAM_RETRIES = 10

        private const val TUNE_TIMEOUT_MS = 30_000L
        private const val TUNE_SETTLE_MS = 8_000L
        private const val UNHEALTHY_GRACE_MS = 4_000L
        private const val STEP_TIMEOUT_MS = 8_000L
        private const val STEP_GAP_MS = 1_200L
        private const val RECOVERY_COOLDOWN_MS = 30_000L
        private const val RESTORED_BANNER_MS = 3_000L
        private const val FAILED_URL_COOLDOWN_MS = 60_000L

        internal fun stepLabel(step: StreamRecoveryPlanner.Step): String = when (step) {
            StreamRecoveryPlanner.Step.Reconnect -> "reconnect"
            is StreamRecoveryPlanner.Step.SwitchUrl -> step.url
        }
    }
}

private fun PlaybackException.failureCategory(): PlaybackFailureCategory = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> PlaybackFailureCategory.NETWORK
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailureCategory.HTTP
    PlaybackException.ERROR_CODE_TIMEOUT,
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackFailureCategory.BUFFERING_TIMEOUT
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> PlaybackFailureCategory.DECODER
    else -> PlaybackFailureCategory.UNKNOWN
}
