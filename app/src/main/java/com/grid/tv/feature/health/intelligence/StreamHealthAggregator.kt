package com.grid.tv.feature.health.intelligence

import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelHealthAggregateDao
import com.grid.tv.data.db.dao.PlaybackSessionTelemetryDao
import com.grid.tv.data.db.dao.ProviderHealthAggregateDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.dao.StreamSourceHealthDao
import com.grid.tv.data.db.entity.ChannelHealthAggregateEntity
import com.grid.tv.data.db.entity.ProviderHealthAggregateEntity
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.data.db.entity.StreamSourceHealthEntity
import com.grid.tv.domain.model.allStreamUrls
import com.grid.tv.domain.model.orderStreamUrlsByHealthScores

class StreamHealthAggregator(
    private val telemetryDao: PlaybackSessionTelemetryDao,
    private val streamSourceHealthDao: StreamSourceHealthDao,
    private val channelHealthDao: ChannelHealthAggregateDao,
    private val providerHealthDao: ProviderHealthAggregateDao,
    private val legacyStreamHealthDao: StreamHealthDao,
    private val channelDao: ChannelDao,
    private val scoringEngine: StreamHealthScoringEngine
) {
    suspend fun processSession(session: PlaybackSessionRecord, persistTelemetry: Boolean = true) {
        if (persistTelemetry) {
            telemetryDao.insert(scoringEngine.toEntity(session))
        }
        applyAggregates(session)
    }

    private suspend fun applyAggregates(session: PlaybackSessionRecord) {
        val sessionScore = scoringEngine.scoreSession(session)

        val prevStream = streamSourceHealthDao.get(session.channelId, session.streamId)?.toSnapshot()
        val streamSnapshot = scoringEngine.mergeScores(prevStream, sessionScore, session)
        streamSourceHealthDao.upsert(streamSnapshot.toStreamEntity(session.channelId, session.streamId))

        val streamRows = streamSourceHealthDao.forChannel(session.channelId)
        val channelScore = scoringEngine.weightedChannelScore(streamRows.map { it.toSnapshot() })
        val channelSnapshot = HealthScoreSnapshot(
            score = channelScore,
            tier = HealthTier.fromScore(channelScore),
            sessionCount = streamRows.sumOf { it.sessionCount },
            lastUpdated = System.currentTimeMillis()
        )
        channelHealthDao.upsert(
            ChannelHealthAggregateEntity(
                channelId = session.channelId,
                healthScore = channelSnapshot.score,
                healthTier = channelSnapshot.tier.name,
                sessionCount = channelSnapshot.sessionCount,
                streamCount = streamRows.size,
                lastUpdated = channelSnapshot.lastUpdated
            )
        )

        syncLegacyChannelHealth(session, channelSnapshot)

        val channelsForProvider = channelDao.getByPlaylist(session.providerId)
        val channelAggregates = channelsForProvider.mapNotNull { channelHealthDao.get(it.id) }
        if (channelAggregates.isNotEmpty()) {
            val providerScore = scoringEngine.weightedProviderScore(
                channelAggregates.map {
                    HealthScoreSnapshot(
                        score = it.healthScore,
                        tier = HealthTier.valueOf(it.healthTier),
                        sessionCount = it.sessionCount,
                        lastUpdated = it.lastUpdated
                    )
                }
            )
            providerHealthDao.upsert(
                ProviderHealthAggregateEntity(
                    providerId = session.providerId,
                    healthScore = providerScore,
                    healthTier = HealthTier.fromScore(providerScore).name,
                    sessionCount = channelAggregates.sumOf { it.sessionCount },
                    channelCount = channelAggregates.size,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun rebuildFromTelemetry(batchSize: Int = 500) {
        streamSourceHealthDao.deleteAll()
        channelHealthDao.deleteAll()
        providerHealthDao.deleteAll()

        var offset = 0
        while (true) {
            val batch = telemetryDao.page(batchSize, offset)
            if (batch.isEmpty()) break
            batch.forEach { row ->
                processSession(row.toRecord(), persistTelemetry = false)
            }
            offset += batch.size
        }
    }

    suspend fun failoverRanking(channelId: Long): StreamFailoverRanking {
        val streams = streamSourceHealthDao.forChannel(channelId)
        val ordered = if (streams.isEmpty()) {
            StreamSourceId.entries.map { it.storageKey }
        } else {
            streams.sortedByDescending { it.healthScore }.map { it.streamId }
        }
        return StreamFailoverRanking(channelId = channelId, orderedStreamIds = ordered)
    }

    /** Health-ranked stream URLs for live failover (matches source ids and legacy URL keys). */
    suspend fun orderedStreamUrls(channel: com.grid.tv.domain.model.Channel): List<String> {
        val default = channel.allStreamUrls()
        if (default.isEmpty()) return default
        val healthRows = streamSourceHealthDao.forChannel(channel.id)
        if (healthRows.isEmpty()) return default
        val scores = healthRows.associate { it.streamId to it.healthScore }
        return channel.orderStreamUrlsByHealthScores(scores)
    }

    private suspend fun syncLegacyChannelHealth(session: PlaybackSessionRecord, snapshot: HealthScoreSnapshot) {
        val existing = legacyStreamHealthDao.get(session.channelId)
        legacyStreamHealthDao.upsert(
            StreamHealthEntity(
                channelId = session.channelId,
                reliabilityScore = snapshot.score,
                averageLoadTimeMs = snapshot.avgStartupTimeMs.toLong(),
                bufferEventsPerSession = (snapshot.avgBufferingDurationMs / 1000.0).toFloat(),
                lastSuccessfulLoad = if (session.playbackSuccess) session.sessionEnd else (existing?.lastSuccessfulLoad ?: 0),
                sessions = snapshot.sessionCount
            )
        )
    }

    private fun StreamSourceHealthEntity.toSnapshot() = HealthScoreSnapshot(
        score = healthScore,
        tier = HealthTier.valueOf(healthTier),
        sessionCount = sessionCount,
        avgStartupTimeMs = avgStartupTimeMs,
        avgBufferingDurationMs = avgBufferingDurationMs,
        failureRate = failureRate,
        lastUpdated = lastUpdated
    )

    private fun HealthScoreSnapshot.toStreamEntity(channelId: Long, streamId: String) =
        StreamSourceHealthEntity(
            channelId = channelId,
            streamId = streamId,
            healthScore = score,
            healthTier = tier.name,
            sessionCount = sessionCount,
            avgStartupTimeMs = avgStartupTimeMs,
            avgBufferingDurationMs = avgBufferingDurationMs,
            failureRate = failureRate,
            lastUpdated = lastUpdated
        )

    private fun com.grid.tv.data.db.entity.PlaybackSessionTelemetryEntity.toRecord() =
        PlaybackSessionRecord(
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
