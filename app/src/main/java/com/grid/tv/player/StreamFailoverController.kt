package com.grid.tv.player

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.allStreamUrls
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

/**
 * Automatic stream failover: reconnect → retry primary → backup 1 → 2 → 3.
 * Monitors errors, stalls, excessive buffering, and tune timeouts.
 */
@Singleton
class StreamFailoverController @Inject constructor(
    private val analytics: StreamFailoverAnalytics
) {
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
    private var bufferingSinceMs: Long = 0L
    private var unhealthySinceMs: Long = 0L
    private var tuneStartedAtMs: Long = 0L
    private var autoReconnectEnabled: Boolean = true
    private var intentionalIdle = false

    fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) {
            cancelRecovery()
            _uiState.value = StreamFailoverUiState()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    if (!autoReconnectEnabled) return
                    if (!intentionalIdle && !recoveryJob.isActive()) {
                        requestRecovery(FailoverTrigger.PLAYBACK_IDLE)
                    }
                }
                Player.STATE_BUFFERING -> {
                    if (bufferingSinceMs == 0L) bufferingSinceMs = System.currentTimeMillis()
                }
                Player.STATE_READY -> onHealthyPlayback()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (!autoReconnectEnabled) return
            Log.w(LOG_TAG, "playback error code=${error.errorCode} url=$activeUrl", error)
            requestRecovery(FailoverTrigger.PLAYBACK_ERROR)
        }
    }

    fun configure(channel: Channel, activeStreamUrl: String?) {
        this.channel = channel
        streamUrls = channel.allStreamUrls()
        activeUrl = activeStreamUrl ?: streamUrls.firstOrNull()
        tuneStartedAtMs = System.currentTimeMillis()
        bufferingSinceMs = 0L
        unhealthySinceMs = 0L
        intentionalIdle = false
        cancelRecovery()
        clearRestoredBanner()
        _uiState.value = StreamFailoverUiState()
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
        bufferingSinceMs = 0L
        unhealthySinceMs = 0L
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                delay(2_000)
                if (intentionalIdle || recoveryJob.isActive()) continue
                evaluateLoadingTimeout()
                evaluateExcessiveBuffering()
            }
        }
    }

    private fun evaluateLoadingTimeout() {
        if (!autoReconnectEnabled) return
        val elapsed = System.currentTimeMillis() - tuneStartedAtMs
        if (elapsed >= TUNE_TIMEOUT_MS) {
            requestRecovery(FailoverTrigger.TUNE_TIMEOUT)
        }
    }

    private fun evaluateExcessiveBuffering() {
        if (!autoReconnectEnabled) return
        val exo = player ?: return
        if (exo.playbackState != Player.STATE_BUFFERING) {
            bufferingSinceMs = 0L
            return
        }
        if (bufferingSinceMs == 0L) bufferingSinceMs = System.currentTimeMillis()
        if (System.currentTimeMillis() - bufferingSinceMs >= EXCESSIVE_BUFFER_MS) {
            requestRecovery(FailoverTrigger.EXCESSIVE_BUFFERING)
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
            requestRecovery(
                when (status) {
                    StreamPlaybackStatus.STALLED -> FailoverTrigger.STALL
                    StreamPlaybackStatus.NO_SIGNAL -> FailoverTrigger.NO_SIGNAL
                    else -> FailoverTrigger.PLAYBACK_ERROR
                }
            )
        }
    }

    private fun onHealthyPlayback() {
        bufferingSinceMs = 0L
        unhealthySinceMs = 0L
        tuneStartedAtMs = System.currentTimeMillis()

        if (wasRecovering || recoveryJob.isActive()) {
            wasRecovering = false
            recoveryJob?.cancel()
            channel?.id?.let { analytics.recordSuccessfulRecovery(it) }
            showRestoredBanner()
        }
        if (_uiState.value.isRecovering) {
            _uiState.value = StreamFailoverUiState()
        }
    }

    private fun requestRecovery(trigger: FailoverTrigger) {
        if (!autoReconnectEnabled) return
        if (recoveryJob.isActive()) return
        if (channel == null || streamUrls.isEmpty()) return
        if (intentionalIdle) return

        recoveryJob = scope.launch {
            wasRecovering = true
            channel?.id?.let { analytics.recordFailover(it) }
            _uiState.value = StreamFailoverUiState(
                isRecovering = true,
                message = RECOVERING_MESSAGE
            )

            val steps = buildRecoverySteps()
            for (step in steps) {
                when (step) {
                    RecoveryStep.Reconnect -> actions?.reconnectSameStream()
                    is RecoveryStep.SwitchUrl -> {
                        activeUrl = step.url
                        actions?.switchToStream(step.url)
                    }
                }
                tuneStartedAtMs = System.currentTimeMillis()
                bufferingSinceMs = 0L
                unhealthySinceMs = 0L
                if (waitForHealthy(STEP_TIMEOUT_MS)) {
                    onHealthyPlayback()
                    return@launch
                }
                delay(STEP_GAP_MS)
            }
            // Stay in recovering state without surfacing technical errors.
            _uiState.value = StreamFailoverUiState(
                isRecovering = true,
                message = RECOVERING_MESSAGE
            )
        }
    }

    private suspend fun waitForHealthy(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val exo = player
            if (exo != null &&
                exo.playbackState == Player.STATE_READY &&
                (exo.isPlaying || exo.playWhenReady)
            ) {
                return true
            }
            delay(250)
        }
        return false
    }

    private fun buildRecoverySteps(): List<RecoveryStep> {
        val urls = streamUrls
        if (urls.isEmpty()) return emptyList()
        val steps = mutableListOf<RecoveryStep>(RecoveryStep.Reconnect)
        val tried = mutableSetOf<String>()
        activeUrl?.let { tried.add(it) }
        urls.forEach { url ->
            if (tried.add(url)) {
                steps += RecoveryStep.SwitchUrl(url)
            }
        }
        return steps
    }

    private fun showRestoredBanner() {
        restoredHideJob?.cancel()
        _uiState.value = StreamFailoverUiState(
            showRestored = true,
            message = RESTORED_MESSAGE
        )
        restoredHideJob = scope.launch {
            delay(RESTORED_VISIBLE_MS)
            _uiState.value = StreamFailoverUiState()
        }
    }

    private fun clearRestoredBanner() {
        restoredHideJob?.cancel()
    }

    private fun cancelRecovery() {
        recoveryJob?.cancel()
        wasRecovering = false
    }

    private sealed interface RecoveryStep {
        data object Reconnect : RecoveryStep
        data class SwitchUrl(val url: String) : RecoveryStep
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

        private const val TUNE_TIMEOUT_MS = 22_000L
        private const val EXCESSIVE_BUFFER_MS = 18_000L
        private const val UNHEALTHY_GRACE_MS = 4_000L
        private const val STEP_TIMEOUT_MS = 8_000L
        private const val STEP_GAP_MS = 1_200L
        private const val RESTORED_VISIBLE_MS = 2_500L
    }
}
