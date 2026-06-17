package com.grid.tv.feature.health.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHealthScoringEngineTest {
    private val engine = StreamHealthScoringEngine()

    @Test
    fun excellentSession_scoresAbove95() {
        val session = PlaybackSessionRecord(
            channelId = 1,
            streamId = "primary",
            providerId = 1,
            sessionStart = 0,
            sessionEnd = 3_700_000,
            watchDurationMs = 3_600_000,
            startupTimeMs = 800,
            bufferingEventCount = 0,
            bufferingDurationMs = 0,
            playbackErrorCount = 0,
            streamSwitchCount = 0,
            reconnectAttempts = 0,
            playbackSuccess = true
        )
        val score = engine.scoreSession(session)
        assertTrue(score >= 95)
        assertEquals(HealthTier.EXCELLENT, HealthTier.fromScore(score))
    }

    @Test
    fun failureSession_scoresBelow70() {
        val session = PlaybackSessionRecord(
            channelId = 1,
            streamId = "backup_2",
            providerId = 1,
            sessionStart = 0,
            sessionEnd = 60_000,
            watchDurationMs = 30_000,
            startupTimeMs = 8_000,
            bufferingEventCount = 8,
            bufferingDurationMs = 20_000,
            playbackErrorCount = 2,
            streamSwitchCount = 2,
            reconnectAttempts = 2,
            playbackSuccess = false
        )
        val score = engine.scoreSession(session)
        assertTrue(score < 70)
        assertEquals(HealthTier.POOR, HealthTier.fromScore(score))
    }

    @Test
    fun mergeScores_usesEma() {
        val session = PlaybackSessionRecord(
            channelId = 1,
            streamId = "primary",
            providerId = 1,
            sessionStart = 0,
            sessionEnd = 3_600_000,
            watchDurationMs = 3_600_000,
            startupTimeMs = 1_000,
            bufferingEventCount = 0,
            bufferingDurationMs = 0,
            playbackErrorCount = 0,
            streamSwitchCount = 0,
            reconnectAttempts = 0,
            playbackSuccess = true
        )
        val first = engine.mergeScores(null, engine.scoreSession(session), session)
        val worse = session.copy(
            playbackSuccess = false,
            bufferingEventCount = 5,
            playbackErrorCount = 1
        )
        val second = engine.mergeScores(first, engine.scoreSession(worse), worse)
        assertTrue(second.score < first.score)
    }

    @Test
    fun channelAggregation_weightsBySessionCount() {
        val high = HealthScoreSnapshot(score = 98, tier = HealthTier.EXCELLENT, sessionCount = 100)
        val low = HealthScoreSnapshot(score = 60, tier = HealthTier.POOR, sessionCount = 5)
        val blended = engine.weightedChannelScore(listOf(high, low))
        assertTrue(blended > 90)
    }
}
