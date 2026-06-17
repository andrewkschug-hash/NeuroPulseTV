package com.grid.tv.feature.health.intelligence.simulation

import com.grid.tv.feature.health.intelligence.HealthTier
import com.grid.tv.feature.health.intelligence.PlaybackSessionRecord
import com.grid.tv.feature.health.intelligence.StreamHealthScoringEngine
import com.grid.tv.feature.health.intelligence.StreamSourceId
import kotlin.math.roundToInt
import kotlin.random.Random

data class StreamHealthSimulationReport(
    val sessionsGenerated: Int,
    val channelsSimulated: Int,
    val providersSimulated: Int,
    val averageHealthScore: Double,
    val healthiestChannels: List<ChannelSimResult>,
    val leastReliableChannels: List<ChannelSimResult>,
    val failureDistribution: Map<String, Int>,
    val tierDistribution: Map<HealthTier, Int>,
    val aggregationValidated: Boolean,
    val rankingConsistent: Boolean
)

data class ChannelSimResult(
    val channelId: Long,
    val healthScore: Int,
    val tier: HealthTier,
    val sessionCount: Int,
    val streamScores: Map<String, Int>
)

/**
 * Generates synthetic playback sessions and validates scoring, aggregation, and ranking.
 */
class StreamHealthSimulator(
    private val scoringEngine: StreamHealthScoringEngine = StreamHealthScoringEngine(),
    private val random: Random = Random(42)
) {
    fun run(
        sessionCount: Int = 10_000,
        channelCount: Int = 200,
        providerCount: Int = 5,
        streamsPerChannel: Int = 3
    ): StreamHealthSimulationReport {
        val channelToProvider = (1L..channelCount.toLong()).associateWith { ((it - 1) % providerCount) + 1 }
        val streamIds = StreamSourceId.entries.take(streamsPerChannel).map { it.storageKey }

        val sessions = (1..sessionCount).map {
            val channelId = random.nextLong(1, channelCount + 1L)
            val providerId = channelToProvider[channelId] ?: 1L
            val streamId = streamIds.random(random)
            val isFailure = random.nextDouble() < failureRateForStream(streamId)
            val startup = when {
                isFailure -> random.nextLong(3_000, 12_000)
                streamId == StreamSourceId.PRIMARY.storageKey -> random.nextLong(400, 2_500)
                else -> random.nextLong(800, 5_000)
            }
            val buffers = if (isFailure) random.nextInt(2, 12) else random.nextInt(0, 4)
            val bufferMs = buffers * random.nextLong(500, 4_000)
            val watchMs = if (isFailure) random.nextLong(5_000, 120_000) else random.nextLong(300_000, 7_200_000)
            val start = System.currentTimeMillis() - watchMs - random.nextLong(0, 86_400_000)
            PlaybackSessionRecord(
                channelId = channelId,
                streamId = streamId,
                providerId = providerId,
                sessionStart = start,
                sessionEnd = start + watchMs,
                watchDurationMs = watchMs,
                startupTimeMs = startup,
                bufferingEventCount = buffers,
                bufferingDurationMs = bufferMs,
                playbackErrorCount = if (isFailure) random.nextInt(1, 4) else 0,
                streamSwitchCount = if (random.nextDouble() < 0.08) random.nextInt(1, 3) else 0,
                reconnectAttempts = if (isFailure) random.nextInt(0, 3) else 0,
                playbackSuccess = !isFailure
            )
        }

        val streamScores = mutableMapOf<Pair<Long, String>, com.grid.tv.feature.health.intelligence.HealthScoreSnapshot>()
        val channelStreamScores = mutableMapOf<Long, MutableMap<String, com.grid.tv.feature.health.intelligence.HealthScoreSnapshot>>()

        sessions.forEach { session ->
            val sessionScore = scoringEngine.scoreSession(session)
            val key = session.channelId to session.streamId
            val prev = streamScores[key]
            val merged = scoringEngine.mergeScores(prev, sessionScore, session)
            streamScores[key] = merged
            channelStreamScores.getOrPut(session.channelId) { mutableMapOf() }[session.streamId] = merged
        }

        val channelScores = channelStreamScores.mapValues { (_, streams) ->
            val score = scoringEngine.weightedChannelScore(streams.values.toList())
            com.grid.tv.feature.health.intelligence.HealthScoreSnapshot(
                score = score,
                tier = HealthTier.fromScore(score),
                sessionCount = streams.values.sumOf { it.sessionCount }
            )
        }

        val providerScores = channelToProvider.values.distinct().associateWith { providerId ->
            val snapshots = channelScores.filter { (ch, _) ->
                channelToProvider[ch] == providerId
            }.values.toList()
            scoringEngine.weightedProviderScore(snapshots)
        }

        val ranked = channelScores.entries
            .map { (id, snap) ->
                ChannelSimResult(
                    channelId = id,
                    healthScore = snap.score,
                    tier = snap.tier,
                    sessionCount = snap.sessionCount,
                    streamScores = channelStreamScores[id]?.mapValues { it.value.score } ?: emptyMap()
                )
            }
            .sortedByDescending { it.healthScore }

        val tierDist = channelScores.values.groupingBy { it.tier }.eachCount()
        val failureDist = mapOf(
            "success" to sessions.count { it.playbackSuccess },
            "failure" to sessions.count { !it.playbackSuccess },
            "buffering" to sessions.count { it.bufferingEventCount > 0 },
            "reconnect" to sessions.count { it.reconnectAttempts > 0 },
            "stream_switch" to sessions.count { it.streamSwitchCount > 0 }
        )

        val aggregationValidated = validateAggregation(channelStreamScores, channelScores, providerScores, channelToProvider)
        val rankingConsistent = validateRanking(ranked)

        return StreamHealthSimulationReport(
            sessionsGenerated = sessionCount,
            channelsSimulated = channelCount,
            providersSimulated = providerCount,
            averageHealthScore = channelScores.values.map { it.score }.average(),
            healthiestChannels = ranked.take(10),
            leastReliableChannels = ranked.takeLast(10).reversed(),
            failureDistribution = failureDist,
            tierDistribution = tierDist,
            aggregationValidated = aggregationValidated,
            rankingConsistent = rankingConsistent
        )
    }

    fun formatReport(report: StreamHealthSimulationReport): String = buildString {
        appendLine("=== Stream Health Simulation Report ===")
        appendLine("Sessions: ${report.sessionsGenerated}")
        appendLine("Channels: ${report.channelsSimulated}, Providers: ${report.providersSimulated}")
        appendLine("Average health score: ${"%.2f".format(report.averageHealthScore)}")
        appendLine("Aggregation validated: ${report.aggregationValidated}")
        appendLine("Ranking consistent: ${report.rankingConsistent}")
        appendLine()
        appendLine("Tier distribution:")
        report.tierDistribution.forEach { (tier, count) ->
            appendLine("  ${tier.label}: $count channels")
        }
        appendLine()
        appendLine("Failure distribution:")
        report.failureDistribution.forEach { (key, count) ->
            appendLine("  $key: $count sessions")
        }
        appendLine()
        appendLine("Healthiest channels:")
        report.healthiestChannels.forEach {
            appendLine("  ch=${it.channelId} score=${it.healthScore} (${it.tier.label}) streams=${it.streamScores}")
        }
        appendLine()
        appendLine("Least reliable channels:")
        report.leastReliableChannels.forEach {
            appendLine("  ch=${it.channelId} score=${it.healthScore} (${it.tier.label}) streams=${it.streamScores}")
        }
    }

    private fun failureRateForStream(streamId: String): Double = when (streamId) {
        StreamSourceId.PRIMARY.storageKey -> 0.04
        StreamSourceId.BACKUP_1.storageKey -> 0.10
        StreamSourceId.BACKUP_2.storageKey -> 0.18
        else -> 0.25
    }

    private fun validateAggregation(
        streamScores: Map<Long, MutableMap<String, com.grid.tv.feature.health.intelligence.HealthScoreSnapshot>>,
        channelScores: Map<Long, com.grid.tv.feature.health.intelligence.HealthScoreSnapshot>,
        providerScores: Map<Long, Int>,
        channelToProvider: Map<Long, Long>
    ): Boolean {
        if (streamScores.isEmpty() || channelScores.isEmpty()) return false
        streamScores.forEach { (channelId, streams) ->
            val expected = scoringEngine.weightedChannelScore(streams.values.toList())
            val actual = channelScores[channelId]?.score ?: return false
            if (kotlin.math.abs(expected - actual) > 1) return false
        }
        providerScores.forEach { (providerId, score) ->
            val channels = channelScores.filter { channelToProvider[it.key] == providerId }
            val expected = scoringEngine.weightedProviderScore(channels.values.toList())
            if (kotlin.math.abs(expected - score) > 1) return false
        }
        return true
    }

    private fun validateRanking(ranked: List<ChannelSimResult>): Boolean {
        if (ranked.size < 2) return true
        for (i in 0 until ranked.lastIndex) {
            if (ranked[i].healthScore < ranked[i + 1].healthScore) return false
        }
        return ranked.first().healthScore >= ranked.last().healthScore
    }
}
