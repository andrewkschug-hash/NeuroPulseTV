package com.grid.tv.feature.health.intelligence

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects playback session telemetry from the player layer.
 * Call [beginSession] when tuning begins and [endSession] when playback stops or errors.
 */
@Singleton
class PlaybackTelemetryCollector @Inject constructor(
    private val aggregator: StreamHealthAggregator,
    private val reporter: PlaybackTelemetryReporter
) {
    data class ActiveSession(
        val channelId: Long,
        var streamId: String,
        val providerId: Long,
        val sessionStart: Long,
        var startupTimeMs: Long = 0,
        var bufferingEventCount: Int = 0,
        var bufferingDurationMs: Long = 0,
        var playbackErrorCount: Int = 0,
        var streamSwitchCount: Int = 0,
        var reconnectAttempts: Int = 0,
        var loadRetryCount: Int = 0,
        var playbackSuccess: Boolean = false,
        var watchDurationMs: Long = 0
    )

    private val mutex = Mutex()

    @Volatile
    private var active: ActiveSession? = null

    @Volatile
    private var bufferingEpisodeStartMs: Long = 0L

    suspend fun beginSession(channelId: Long, providerId: Long, streamId: String = StreamSourceId.PRIMARY.storageKey) {
        endSession(success = null)
        active = ActiveSession(
            channelId = channelId,
            streamId = streamId,
            providerId = providerId,
            sessionStart = System.currentTimeMillis()
        )
        bufferingEpisodeStartMs = 0L
    }

    /** @deprecated Prefer [beginSession] which closes any open session first. */
    fun startSession(channelId: Long, providerId: Long, streamId: String = StreamSourceId.PRIMARY.storageKey) {
        active = ActiveSession(
            channelId = channelId,
            streamId = streamId,
            providerId = providerId,
            sessionStart = System.currentTimeMillis()
        )
        bufferingEpisodeStartMs = 0L
    }

    fun onStartupComplete(loadMs: Long) {
        active?.startupTimeMs = loadMs.coerceAtLeast(0)
    }

    fun onBufferingStarted() {
        if (bufferingEpisodeStartMs == 0L) {
            bufferingEpisodeStartMs = System.currentTimeMillis()
        }
        active?.bufferingEventCount = (active?.bufferingEventCount ?: 0) + 1
    }

    fun onBufferingEnded(durationMs: Long? = null) {
        val duration = durationMs?.coerceAtLeast(0)
            ?: bufferingEpisodeStartMs.takeIf { it > 0L }?.let { System.currentTimeMillis() - it }
            ?: 0L
        bufferingEpisodeStartMs = 0L
        if (duration > 0L) {
            active?.bufferingDurationMs = (active?.bufferingDurationMs ?: 0) + duration
        }
    }

    fun onPlaybackError() {
        active?.playbackErrorCount = (active?.playbackErrorCount ?: 0) + 1
    }

    fun onStreamSwitch(newStreamId: String) {
        active?.let {
            it.streamSwitchCount += 1
            it.streamId = newStreamId
        }
    }

    fun onReconnectAttempt() {
        active?.reconnectAttempts = (active?.reconnectAttempts ?: 0) + 1
    }

    fun onLoadRetry() {
        active?.loadRetryCount = (active?.loadRetryCount ?: 0) + 1
    }

    fun onPlaybackSuccess() {
        active?.playbackSuccess = true
    }

    fun peekActive(): ActiveSession? = active

    fun tickWatchDuration(deltaMs: Long) {
        active?.watchDurationMs = (active?.watchDurationMs ?: 0) + deltaMs.coerceAtLeast(0)
    }

    suspend fun endSession(success: Boolean? = null): PlaybackSessionRecord? = mutex.withLock {
        val session = active ?: return@withLock null
        onBufferingEnded()
        active = null
        val end = System.currentTimeMillis()
        val record = PlaybackSessionRecord(
            channelId = session.channelId,
            streamId = session.streamId,
            providerId = session.providerId,
            sessionStart = session.sessionStart,
            sessionEnd = end,
            watchDurationMs = session.watchDurationMs.takeIf { it > 0 }
                ?: (end - session.sessionStart).coerceAtLeast(0),
            startupTimeMs = session.startupTimeMs,
            bufferingEventCount = session.bufferingEventCount,
            bufferingDurationMs = session.bufferingDurationMs,
            playbackErrorCount = session.playbackErrorCount,
            streamSwitchCount = session.streamSwitchCount,
            reconnectAttempts = session.reconnectAttempts,
            loadRetryCount = session.loadRetryCount,
            playbackSuccess = success ?: session.playbackSuccess
        )
        aggregator.processSession(record)
        reporter.logSessionCompleted(record)
        record
    }

    suspend fun recordCompletedSession(record: PlaybackSessionRecord) {
        aggregator.processSession(record)
        reporter.logSessionCompleted(record)
    }
}
