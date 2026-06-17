package com.grid.tv.feature.health.intelligence

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects playback session telemetry from the player layer.
 * Call [startSession] when tuning begins and [endSession] when playback stops or errors.
 */
@Singleton
class PlaybackTelemetryCollector @Inject constructor(
    private val aggregator: StreamHealthAggregator
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
        var playbackSuccess: Boolean = false,
        var watchDurationMs: Long = 0
    )

    @Volatile
    private var active: ActiveSession? = null

    fun startSession(channelId: Long, providerId: Long, streamId: String = StreamSourceId.PRIMARY.storageKey) {
        active = ActiveSession(
            channelId = channelId,
            streamId = streamId,
            providerId = providerId,
            sessionStart = System.currentTimeMillis()
        )
    }

    fun onStartupComplete(loadMs: Long) {
        active?.startupTimeMs = loadMs.coerceAtLeast(0)
    }

    fun onBufferingStarted() {
        active?.bufferingEventCount = (active?.bufferingEventCount ?: 0) + 1
    }

    fun onBufferingEnded(durationMs: Long) {
        active?.bufferingDurationMs = (active?.bufferingDurationMs ?: 0) + durationMs.coerceAtLeast(0)
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

    fun onPlaybackSuccess() {
        active?.playbackSuccess = true
    }

    fun tickWatchDuration(deltaMs: Long) {
        active?.watchDurationMs = (active?.watchDurationMs ?: 0) + deltaMs.coerceAtLeast(0)
    }

    suspend fun endSession(success: Boolean? = null): PlaybackSessionRecord? {
        val session = active ?: return null
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
            playbackSuccess = success ?: session.playbackSuccess
        )
        aggregator.processSession(record)
        return record
    }

    suspend fun recordCompletedSession(record: PlaybackSessionRecord) {
        aggregator.processSession(record)
    }
}
