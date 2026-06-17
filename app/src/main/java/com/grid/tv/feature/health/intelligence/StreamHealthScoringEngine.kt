package com.grid.tv.feature.health.intelligence

import com.grid.tv.data.db.entity.PlaybackSessionTelemetryEntity
import kotlin.math.roundToInt

/**
 * Computes a 0–100 health score per playback session and aggregates via exponential moving average.
 */
class StreamHealthScoringEngine(
    private val emaAlpha: Double = 0.15
) {
    fun scoreSession(session: PlaybackSessionRecord): Int {
        var score = 100.0

        if (!session.playbackSuccess) score -= 30.0

        score -= (session.startupTimeMs / 100.0).coerceAtMost(25.0)
        score -= session.bufferingEventCount * 3.0
        score -= (session.bufferingDurationMs / 1000.0) * 2.0
        score -= session.playbackErrorCount * 8.0
        score -= session.streamSwitchCount * 5.0
        score -= session.reconnectAttempts * 4.0

        val watchMinutes = session.watchDurationMs / 60_000.0
        if (watchMinutes >= 30) score += 5.0
        if (watchMinutes >= 60) score += 5.0
        if (session.playbackSuccess && session.bufferingEventCount == 0 && session.startupTimeMs < 2_000) {
            score += 3.0
        }

        return score.roundToInt().coerceIn(0, 100)
    }

    fun mergeScores(previous: HealthScoreSnapshot?, sessionScore: Int, session: PlaybackSessionRecord): HealthScoreSnapshot {
        val prevScore = previous?.score?.toDouble() ?: sessionScore.toDouble()
        val merged = if (previous == null) {
            sessionScore.toDouble()
        } else {
            emaAlpha * sessionScore + (1 - emaAlpha) * prevScore
        }
        val score = merged.roundToInt().coerceIn(0, 100)
        val sessions = (previous?.sessionCount ?: 0) + 1
        val prevStartup = previous?.avgStartupTimeMs ?: session.startupTimeMs.toDouble()
        val prevBuffer = previous?.avgBufferingDurationMs ?: session.bufferingDurationMs.toDouble()
        val prevFailures = (previous?.failureRate ?: 0.0) * (sessions - 1).coerceAtLeast(0)
        val failureInc = if (session.playbackSuccess) 0.0 else 1.0

        return HealthScoreSnapshot(
            score = score,
            tier = HealthTier.fromScore(score),
            sessionCount = sessions,
            avgStartupTimeMs = emaAlpha * session.startupTimeMs + (1 - emaAlpha) * prevStartup,
            avgBufferingDurationMs = emaAlpha * session.bufferingDurationMs + (1 - emaAlpha) * prevBuffer,
            failureRate = (prevFailures + failureInc) / sessions,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun weightedChannelScore(streamSnapshots: List<HealthScoreSnapshot>): Int {
        if (streamSnapshots.isEmpty()) return 0
        val totalWeight = streamSnapshots.sumOf { it.sessionCount.coerceAtLeast(1) }
        if (totalWeight == 0) return streamSnapshots.map { it.score }.average().roundToInt()
        val weighted = streamSnapshots.sumOf { it.score * it.sessionCount.coerceAtLeast(1) } / totalWeight.toDouble()
        return weighted.roundToInt().coerceIn(0, 100)
    }

    fun weightedProviderScore(channelSnapshots: List<HealthScoreSnapshot>): Int {
        return weightedChannelScore(channelSnapshots)
    }

    fun toEntity(session: PlaybackSessionRecord): PlaybackSessionTelemetryEntity =
        PlaybackSessionTelemetryEntity(
            channelId = session.channelId,
            streamId = session.streamId,
            providerId = session.providerId,
            sessionStart = session.sessionStart,
            sessionEnd = session.sessionEnd,
            watchDurationMs = session.watchDurationMs,
            startupTimeMs = session.startupTimeMs,
            bufferingEventCount = session.bufferingEventCount,
            bufferingDurationMs = session.bufferingDurationMs,
            playbackErrorCount = session.playbackErrorCount,
            streamSwitchCount = session.streamSwitchCount,
            reconnectAttempts = session.reconnectAttempts,
            playbackSuccess = session.playbackSuccess
        )
}
