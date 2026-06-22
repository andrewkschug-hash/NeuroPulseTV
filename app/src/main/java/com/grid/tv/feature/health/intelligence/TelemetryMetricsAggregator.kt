package com.grid.tv.feature.health.intelligence

import com.grid.tv.data.db.entity.PlaybackSessionTelemetryEntity
import kotlin.math.roundToInt

/**
 * Derives diagnostic metrics from persisted playback session rows.
 */
object TelemetryMetricsAggregator {

    fun summarizeSessions(
        sessions: List<PlaybackSessionTelemetryEntity>,
        scoringEngine: StreamHealthScoringEngine = StreamHealthScoringEngine()
    ): SessionMetricsSummary {
        if (sessions.isEmpty()) {
            return SessionMetricsSummary.empty()
        }
        val count = sessions.size
        val successes = sessions.count { it.playbackSuccess }
        return SessionMetricsSummary(
            sessionCount = count,
            completionRate = successes.toDouble() / count,
            failureRate = 1.0 - (successes.toDouble() / count),
            avgStartupTimeMs = sessions.map { it.startupTimeMs.toDouble() }.average(),
            avgBufferingDurationMs = sessions.map { it.bufferingDurationMs.toDouble() }.average(),
            avgBufferingEventsPerSession = sessions.map { it.bufferingEventCount.toDouble() }.average(),
            avgFailoversPerSession = sessions.map { it.streamSwitchCount.toDouble() }.average(),
            avgReconnectAttemptsPerSession = sessions.map { it.reconnectAttempts.toDouble() }.average(),
            avgLoadRetriesPerSession = sessions.map { it.loadRetryCount.toDouble() }.average(),
            avgErrorsPerSession = sessions.map { it.playbackErrorCount.toDouble() }.average(),
            avgWatchDurationMs = sessions.map { it.watchDurationMs.toDouble() }.average(),
            recentSummaries = sessions.take(5).map { row ->
                val record = row.toRecord()
                SessionDiagnosticSummary(
                    sessionStart = row.sessionStart,
                    streamId = row.streamId,
                    startupTimeMs = row.startupTimeMs,
                    bufferingDurationMs = row.bufferingDurationMs,
                    bufferingEventCount = row.bufferingEventCount,
                    streamSwitchCount = row.streamSwitchCount,
                    reconnectAttempts = row.reconnectAttempts,
                    loadRetryCount = row.loadRetryCount,
                    playbackErrorCount = row.playbackErrorCount,
                    watchDurationMs = row.watchDurationMs,
                    playbackSuccess = row.playbackSuccess,
                    sessionScore = scoringEngine.scoreSession(record)
                )
            }
        )
    }

    fun buildSummaryLine(
        label: String,
        score: Int,
        tier: HealthTier,
        metrics: SessionMetricsSummary
    ): String = buildString {
        append(label)
        append(" score=$score tier=${tier.label}")
        append(" sessions=${metrics.sessionCount}")
        append(" completion=${pct(metrics.completionRate)}")
        append(" startup=${metrics.avgStartupTimeMs.roundToInt()}ms")
        append(" buffer=${metrics.avgBufferingDurationMs.roundToInt()}ms")
        append(" failovers=${fmt(metrics.avgFailoversPerSession)}")
        append(" retries=${fmt(metrics.avgLoadRetriesPerSession)}")
        append(" errors=${fmt(metrics.avgErrorsPerSession)}")
    }

    private fun pct(value: Double): String = "${(value * 100).roundToInt()}%"
    private fun fmt(value: Double): String = "%.1f".format(value)

    private fun PlaybackSessionTelemetryEntity.toRecord() = PlaybackSessionRecord(
        channelId = channelId,
        streamId = streamId,
        providerId = providerId,
        sessionStart = sessionStart,
        sessionEnd = sessionEnd,
        watchDurationMs = watchDurationMs,
        startupTimeMs = startupTimeMs,
        bufferingEventCount = bufferingEventCount,
        bufferingDurationMs = bufferingDurationMs,
        playbackErrorCount = playbackErrorCount,
        streamSwitchCount = streamSwitchCount,
        reconnectAttempts = reconnectAttempts,
        loadRetryCount = loadRetryCount,
        playbackSuccess = playbackSuccess
    )
}

data class SessionMetricsSummary(
    val sessionCount: Int,
    val completionRate: Double,
    val failureRate: Double,
    val avgStartupTimeMs: Double,
    val avgBufferingDurationMs: Double,
    val avgBufferingEventsPerSession: Double,
    val avgFailoversPerSession: Double,
    val avgReconnectAttemptsPerSession: Double,
    val avgLoadRetriesPerSession: Double,
    val avgErrorsPerSession: Double,
    val avgWatchDurationMs: Double,
    val recentSummaries: List<SessionDiagnosticSummary>
) {
    companion object {
        fun empty() = SessionMetricsSummary(
            sessionCount = 0,
            completionRate = 0.0,
            failureRate = 0.0,
            avgStartupTimeMs = 0.0,
            avgBufferingDurationMs = 0.0,
            avgBufferingEventsPerSession = 0.0,
            avgFailoversPerSession = 0.0,
            avgReconnectAttemptsPerSession = 0.0,
            avgLoadRetriesPerSession = 0.0,
            avgErrorsPerSession = 0.0,
            avgWatchDurationMs = 0.0,
            recentSummaries = emptyList()
        )
    }
}
