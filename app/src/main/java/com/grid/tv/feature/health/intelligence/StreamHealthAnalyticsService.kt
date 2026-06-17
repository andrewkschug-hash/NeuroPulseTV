package com.grid.tv.feature.health.intelligence

import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelHealthAggregateDao
import com.grid.tv.data.db.dao.ProviderHealthAggregateDao
import com.grid.tv.data.db.dao.StreamSourceHealthDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics API for stream health intelligence.
 */
@Singleton
class StreamHealthAnalyticsService @Inject constructor(
    private val channelHealthDao: ChannelHealthAggregateDao,
    private val streamSourceHealthDao: StreamSourceHealthDao,
    private val providerHealthDao: ProviderHealthAggregateDao,
    private val channelDao: ChannelDao,
    private val aggregator: StreamHealthAggregator
) {
    suspend fun getChannelHealth(channelId: Long): ChannelHealthDetail? {
        val aggregate = channelHealthDao.get(channelId) ?: return null
        val streams = streamSourceHealthDao.forChannel(channelId).map {
            StreamHealthDetail(
                channelId = channelId,
                streamId = it.streamId,
                snapshot = HealthScoreSnapshot(
                    score = it.healthScore,
                    tier = HealthTier.valueOf(it.healthTier),
                    sessionCount = it.sessionCount,
                    avgStartupTimeMs = it.avgStartupTimeMs,
                    avgBufferingDurationMs = it.avgBufferingDurationMs,
                    failureRate = it.failureRate,
                    lastUpdated = it.lastUpdated
                )
            )
        }
        return ChannelHealthDetail(
            channelId = channelId,
            snapshot = HealthScoreSnapshot(
                score = aggregate.healthScore,
                tier = HealthTier.valueOf(aggregate.healthTier),
                sessionCount = aggregate.sessionCount,
                lastUpdated = aggregate.lastUpdated
            ),
            streams = streams
        )
    }

    suspend fun getStreamHealth(channelId: Long, streamId: String): StreamHealthDetail? {
        val row = streamSourceHealthDao.get(channelId, streamId) ?: return null
        return StreamHealthDetail(
            channelId = channelId,
            streamId = streamId,
            snapshot = HealthScoreSnapshot(
                score = row.healthScore,
                tier = HealthTier.valueOf(row.healthTier),
                sessionCount = row.sessionCount,
                avgStartupTimeMs = row.avgStartupTimeMs,
                avgBufferingDurationMs = row.avgBufferingDurationMs,
                failureRate = row.failureRate,
                lastUpdated = row.lastUpdated
            )
        )
    }

    suspend fun getProviderHealth(providerId: Long): ProviderHealthDetail? {
        val row = providerHealthDao.get(providerId) ?: return null
        return ProviderHealthDetail(
            providerId = providerId,
            snapshot = HealthScoreSnapshot(
                score = row.healthScore,
                tier = HealthTier.valueOf(row.healthTier),
                sessionCount = row.sessionCount,
                lastUpdated = row.lastUpdated
            ),
            channelCount = row.channelCount
        )
    }

    suspend fun getTopReliableChannels(limit: Int = 10): List<ChannelHealthDetail> =
        channelHealthDao.topReliable(limit).mapNotNull { getChannelHealth(it.channelId) }

    suspend fun getProblemChannels(limit: Int = 10): List<ChannelHealthDetail> =
        channelHealthDao.problemChannels(limit).mapNotNull { getChannelHealth(it.channelId) }

    suspend fun getFailoverRanking(channelId: Long): StreamFailoverRanking =
        aggregator.failoverRanking(channelId)

    suspend fun averageHealthScore(): Double = channelHealthDao.averageScore() ?: 0.0
}
