package com.grid.tv.feature.health.intelligence

import android.util.Log
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelHealthAggregateDao
import com.grid.tv.data.db.dao.PlaybackSessionTelemetryDao
import com.grid.tv.data.db.dao.ProviderHealthAggregateDao
import com.grid.tv.data.db.dao.StreamSourceHealthDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics API for stream health intelligence and unstable-source diagnostics.
 */
@Singleton
class StreamHealthAnalyticsService @Inject constructor(
    private val channelHealthDao: ChannelHealthAggregateDao,
    private val streamSourceHealthDao: StreamSourceHealthDao,
    private val providerHealthDao: ProviderHealthAggregateDao,
    private val telemetryDao: PlaybackSessionTelemetryDao,
    private val channelDao: ChannelDao,
    private val aggregator: StreamHealthAggregator,
    private val scoringEngine: StreamHealthScoringEngine,
    private val reporter: PlaybackTelemetryReporter
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

    suspend fun totalRecordedSessions(): Int = telemetryDao.count()

    suspend fun diagnosticsForChannel(channelId: Long, recentLimit: Int = 25): StreamHealthDiagnostics? {
        val aggregate = channelHealthDao.get(channelId) ?: return null
        val channel = channelDao.getById(channelId)
        val sessions = telemetryDao.recentForChannel(channelId, recentLimit)
        val metrics = TelemetryMetricsAggregator.summarizeSessions(sessions, scoringEngine)
        val streamRows = streamSourceHealthDao.forChannel(channelId)
        val unstableStreams = streamRows
            .filter { it.healthScore < 70 || it.failureRate >= 0.25 }
            .sortedBy { it.healthScore }
            .map { it.streamId }
        return StreamHealthDiagnostics(
            scope = HealthDiagnosticsScope.CHANNEL,
            entityId = channelId,
            entityLabel = channel?.name,
            healthScore = aggregate.healthScore,
            tier = HealthTier.valueOf(aggregate.healthTier),
            sessionCount = metrics.sessionCount.coerceAtLeast(aggregate.sessionCount),
            completionRate = metrics.completionRate,
            failureRate = metrics.failureRate,
            avgStartupTimeMs = metrics.avgStartupTimeMs,
            avgBufferingDurationMs = metrics.avgBufferingDurationMs,
            avgBufferingEventsPerSession = metrics.avgBufferingEventsPerSession,
            avgFailoversPerSession = metrics.avgFailoversPerSession,
            avgReconnectAttemptsPerSession = metrics.avgReconnectAttemptsPerSession,
            avgLoadRetriesPerSession = metrics.avgLoadRetriesPerSession,
            avgErrorsPerSession = metrics.avgErrorsPerSession,
            avgWatchDurationMs = metrics.avgWatchDurationMs,
            recentSessions = metrics.recentSummaries,
            unstableStreamIds = unstableStreams,
            summaryLine = TelemetryMetricsAggregator.buildSummaryLine(
                label = "channel=$channelId ${channel?.name.orEmpty()}".trim(),
                score = aggregate.healthScore,
                tier = HealthTier.fromScore(aggregate.healthScore),
                metrics = metrics
            )
        )
    }

    suspend fun diagnosticsForStream(
        channelId: Long,
        streamId: String,
        recentLimit: Int = 25
    ): StreamHealthDiagnostics? {
        val row = streamSourceHealthDao.get(channelId, streamId) ?: return null
        val channel = channelDao.getById(channelId)
        val sessions = telemetryDao.recentForStream(channelId, streamId, recentLimit)
        val metrics = TelemetryMetricsAggregator.summarizeSessions(sessions, scoringEngine)
        return StreamHealthDiagnostics(
            scope = HealthDiagnosticsScope.STREAM,
            entityId = channelId,
            entityLabel = channel?.name,
            streamId = streamId,
            healthScore = row.healthScore,
            tier = HealthTier.valueOf(row.healthTier),
            sessionCount = metrics.sessionCount.coerceAtLeast(row.sessionCount),
            completionRate = metrics.completionRate,
            failureRate = metrics.failureRate,
            avgStartupTimeMs = metrics.avgStartupTimeMs,
            avgBufferingDurationMs = metrics.avgBufferingDurationMs,
            avgBufferingEventsPerSession = metrics.avgBufferingEventsPerSession,
            avgFailoversPerSession = metrics.avgFailoversPerSession,
            avgReconnectAttemptsPerSession = metrics.avgReconnectAttemptsPerSession,
            avgLoadRetriesPerSession = metrics.avgLoadRetriesPerSession,
            avgErrorsPerSession = metrics.avgErrorsPerSession,
            avgWatchDurationMs = metrics.avgWatchDurationMs,
            recentSessions = metrics.recentSummaries,
            summaryLine = TelemetryMetricsAggregator.buildSummaryLine(
                label = "stream=$streamId channel=$channelId",
                score = row.healthScore,
                tier = HealthTier.fromScore(row.healthScore),
                metrics = metrics
            )
        )
    }

    suspend fun diagnosticsForProvider(providerId: Long, recentLimit: Int = 100): StreamHealthDiagnostics? {
        val row = providerHealthDao.get(providerId) ?: return null
        val sessions = telemetryDao.recentForProvider(providerId, recentLimit)
        val metrics = TelemetryMetricsAggregator.summarizeSessions(sessions, scoringEngine)
        return StreamHealthDiagnostics(
            scope = HealthDiagnosticsScope.PROVIDER,
            entityId = providerId,
            entityLabel = "provider:$providerId",
            healthScore = row.healthScore,
            tier = HealthTier.valueOf(row.healthTier),
            sessionCount = metrics.sessionCount.coerceAtLeast(row.sessionCount),
            completionRate = metrics.completionRate,
            failureRate = metrics.failureRate,
            avgStartupTimeMs = metrics.avgStartupTimeMs,
            avgBufferingDurationMs = metrics.avgBufferingDurationMs,
            avgBufferingEventsPerSession = metrics.avgBufferingEventsPerSession,
            avgFailoversPerSession = metrics.avgFailoversPerSession,
            avgReconnectAttemptsPerSession = metrics.avgReconnectAttemptsPerSession,
            avgLoadRetriesPerSession = metrics.avgLoadRetriesPerSession,
            avgErrorsPerSession = metrics.avgErrorsPerSession,
            avgWatchDurationMs = metrics.avgWatchDurationMs,
            recentSessions = metrics.recentSummaries,
            summaryLine = TelemetryMetricsAggregator.buildSummaryLine(
                label = "provider=$providerId",
                score = row.healthScore,
                tier = HealthTier.fromScore(row.healthScore),
                metrics = metrics
            )
        )
    }

    suspend fun unstableSources(limit: Int = 10): List<StreamHealthDiagnostics> =
        channelHealthDao.problemChannels(limit).mapNotNull { diagnosticsForChannel(it.channelId) }

    suspend fun logUnstableSourcesReport(limit: Int = 10) {
        val unstable = unstableSources(limit)
        Log.i("PlaybackTelemetry", "UNSTABLE_SOURCES count=${unstable.size} sessions=${totalRecordedSessions()}")
        unstable.forEach { reporter.logDiagnostics(it) }
    }
}
