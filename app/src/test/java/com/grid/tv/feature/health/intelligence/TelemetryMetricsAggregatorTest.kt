package com.grid.tv.feature.health.intelligence

import com.grid.tv.data.db.entity.PlaybackSessionTelemetryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryMetricsAggregatorTest {

    @Test
    fun summarizeSessions_computesCompletionAndAverages() {
        val sessions = listOf(
            sampleSession(success = true, startup = 1000, bufferMs = 2000, failovers = 0, retries = 1),
            sampleSession(success = false, startup = 5000, bufferMs = 8000, failovers = 2, retries = 3)
        )
        val summary = TelemetryMetricsAggregator.summarizeSessions(sessions)
        assertEquals(2, summary.sessionCount)
        assertEquals(0.5, summary.completionRate, 0.001)
        assertEquals(3000.0, summary.avgStartupTimeMs, 0.001)
        assertEquals(5000.0, summary.avgBufferingDurationMs, 0.001)
        assertEquals(1.0, summary.avgFailoversPerSession, 0.001)
        assertEquals(2.0, summary.avgLoadRetriesPerSession, 0.001)
    }

    @Test
    fun buildSummaryLine_containsKeyMetrics() {
        val summary = TelemetryMetricsAggregator.summarizeSessions(
            listOf(sampleSession(success = true, startup = 1200, bufferMs = 500, failovers = 1, retries = 2))
        )
        val line = TelemetryMetricsAggregator.buildSummaryLine(
            label = "channel=1 News",
            score = 88,
            tier = HealthTier.GOOD,
            metrics = summary
        )
        assertTrue(line.contains("score=88"))
        assertTrue(line.contains("completion=100%"))
        assertTrue(line.contains("startup=1200ms"))
    }

    private fun sampleSession(
        success: Boolean,
        startup: Long,
        bufferMs: Long,
        failovers: Int,
        retries: Int
    ) = PlaybackSessionTelemetryEntity(
        channelId = 1,
        streamId = "primary",
        providerId = 10,
        sessionStart = 1_000L,
        sessionEnd = 60_000L,
        watchDurationMs = 55_000L,
        startupTimeMs = startup,
        bufferingEventCount = 2,
        bufferingDurationMs = bufferMs,
        playbackErrorCount = if (success) 0 else 1,
        streamSwitchCount = failovers,
        reconnectAttempts = 1,
        loadRetryCount = retries,
        playbackSuccess = success
    )
}
