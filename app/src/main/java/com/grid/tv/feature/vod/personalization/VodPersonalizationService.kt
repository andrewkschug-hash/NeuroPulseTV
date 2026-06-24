package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.ContinueWatchingDao
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified API for VOD personalization: continue watching, follows, new episodes, feed, notifications.
 */
@Singleton
class VodPersonalizationService @Inject constructor(
    private val watchTracker: VodWatchTracker,
    private val followManager: SeriesFollowManager,
    private val catalogMonitor: CatalogMonitor,
    private val newEpisodeDetector: NewEpisodeDetector,
    private val notificationEngine: VodNotificationEngine,
    private val feedRanker: ForYouFeedRanker,
    private val continueWatchingDao: ContinueWatchingDao
) {
    suspend fun getContinueWatching(profileId: Long, limit: Int = 12): List<ContinueWatchingItem> =
        continueWatchingDao.getForProfile(profileId, limit).map { row ->
            ContinueWatchingItem(
                contentKey = row.contentKey,
                contentType = ContinueWatchingContentType.fromStored(row.contentType),
                title = row.title,
                posterUrl = row.posterUrl,
                streamUrl = row.streamUrl,
                positionMs = row.positionMs,
                durationMs = row.durationMs,
                lastWatchedAt = row.lastWatchedAt,
                playlistId = row.playlistId,
                streamId = row.streamId,
                seriesId = row.seriesId,
                seasonNumber = row.seasonNumber,
                episodeNumber = row.episodeNumber
            )
        }

    suspend fun getFollowedSeries(profileId: Long): List<SeriesFollow> =
        followManager.getFollowedSeries(profileId)

    suspend fun followSeries(
        profileId: Long,
        seriesId: Long,
        seriesTitle: String,
        playlistId: Long
    ) = followManager.followSeries(profileId, seriesId, seriesTitle, playlistId)

    suspend fun getNewEpisodes(profileId: Long, playlistId: Long): List<NewEpisodeDetection> {
        val followed = followManager.getFollowedSeries(profileId)
        val catalog = followed.flatMap { catalogMonitor.episodesForSeries(it.playlistId, it.seriesId) }
        return newEpisodeDetector.detectForProfile(profileId, catalog)
    }

    suspend fun processCatalogUpdate(
        profileId: Long,
        playlistId: Long,
        seriesId: Long,
        seriesTitle: String,
        syncResult: CatalogMonitor.CatalogSyncResult
    ): List<VodNotification> {
        val detections = newEpisodeDetector.detectForProfile(profileId, syncResult.addedEpisodes)
        return notificationEngine.createFromDetections(profileId, detections, syncResult)
    }

    suspend fun getForYouFeed(
        profileId: Long,
        playlistId: Long,
        trendingSeriesIds: List<Long> = emptyList()
    ): ForYouFeed = feedRanker.buildFeed(profileId, playlistId, trendingSeriesIds)

    suspend fun getUnreadNotifications(profileId: Long): List<VodNotification> =
        notificationEngine.getUnread(profileId)

    suspend fun unreadNotificationCount(profileId: Long): Int =
        notificationEngine.unreadCount(profileId)

    suspend fun recordEpisodeWatch(
        profileId: Long,
        seriesId: Long,
        seriesTitle: String,
        playlistId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        watchTracker.recordEpisode(
            profileId,
            playlistId,
            seriesId,
            seasonNumber,
            episodeNumber,
            positionMs,
            durationMs
        )
        followManager.evaluateAutoFollow(profileId, seriesId, seriesTitle, playlistId)
    }
}
