package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.ContinueWatchingDao
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForYouFeedRanker @Inject constructor(
    private val continueWatchingDao: ContinueWatchingDao,
    private val followManager: SeriesFollowManager,
    private val watchTracker: VodWatchTracker,
    private val newEpisodeDetector: NewEpisodeDetector,
    private val catalogMonitor: CatalogMonitor
) {
    companion object {
        private const val WEIGHT_NEW_EPISODE_FOLLOWED = 100.0
        private const val WEIGHT_CONTINUE_WATCHING = 90.0
        private const val WEIGHT_RECENT_FRANCHISE = 70.0
        private const val WEIGHT_RECENT_RELEASE = 50.0
        private const val WEIGHT_TRENDING = 20.0
    }

    suspend fun buildFeed(
        profileId: Long,
        playlistId: Long,
        trendingSeriesIds: List<Long> = emptyList()
    ): ForYouFeed {
        val items = mutableListOf<ForYouFeedItem>()
        val followed = followManager.getFollowedSeries(profileId)

        val cwRows = continueWatchingDao.getForProfile(profileId, 12)

        cwRows.forEach { row ->
            val item = ContinueWatchingItem(
                contentKey = row.contentKey,
                contentType = ContinueWatchingContentType.fromStored(row.contentType),
                title = row.title,
                posterUrl = row.posterUrl,
                streamUrl = row.streamUrl,
                positionMs = row.positionMs,
                durationMs = row.durationMs,
                lastWatchedAt = row.lastWatchedAt,
                streamId = row.streamId,
                seriesId = row.seriesId,
                seasonNumber = row.seasonNumber,
                episodeNumber = row.episodeNumber
            )
            val subtitle = when (item.contentType) {
                ContinueWatchingContentType.SERIES -> {
                    val ep = item.episodeNumber ?: 0
                    "Resume Episode $ep"
                }
                ContinueWatchingContentType.MOVIE -> "Resume"
            }
            items += ForYouFeedItem(
                section = ForYouFeedSection.CONTINUE_WATCHING,
                title = item.title,
                subtitle = subtitle,
                seriesId = item.seriesId,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                contentKey = item.contentKey,
                rankScore = WEIGHT_CONTINUE_WATCHING + recencyBoost(item.lastWatchedAt),
                posterUrl = item.posterUrl
            )
        }

        followed.forEach { follow ->
            val catalog = catalogMonitor.episodesForSeries(follow.playlistId, follow.seriesId)
            val detections = newEpisodeDetector.detectForProfile(profileId, catalog)
            detections.forEach { detection ->
                items += ForYouFeedItem(
                    section = ForYouFeedSection.NEW_FOR_YOU,
                    title = detection.seriesTitle,
                    subtitle = "Episode ${detection.episodeNumber} Added",
                    seriesId = detection.seriesId,
                    seasonNumber = detection.seasonNumber,
                    episodeNumber = detection.episodeNumber,
                    contentKey = detection.contentKey,
                    rankScore = WEIGHT_NEW_EPISODE_FOLLOWED + detection.episodeNumber
                )
            }

            catalog.sortedByDescending { it.addedAt }.take(5).forEach { ep ->
                items += ForYouFeedItem(
                    section = ForYouFeedSection.RECENTLY_ADDED_FOLLOWED,
                    title = ep.seriesTitle,
                    subtitle = "S${ep.seasonNumber}E${ep.episodeNumber} · ${ep.episodeTitle}",
                    seriesId = ep.seriesId,
                    seasonNumber = ep.seasonNumber,
                    episodeNumber = ep.episodeNumber,
                    contentKey = ContinueWatchingRepository.seriesContentKey(
                        ep.playlistId,
                        ep.seriesId,
                        ep.seasonNumber,
                        ep.episodeNumber
                    ),
                    rankScore = WEIGHT_RECENT_RELEASE + (ep.addedAt / 1_000_000.0)
                )
            }
        }

        val recentFranchises = watchTracker.recentEpisodes(profileId, 30)
            .mapNotNull { it.seriesId }
            .distinct()
            .filter { id -> followed.none { it.seriesId == id } }

        recentFranchises.take(5).forEachIndexed { index, seriesId ->
            items += ForYouFeedItem(
                section = ForYouFeedSection.RECENTLY_ADDED_FOLLOWED,
                title = "Series $seriesId",
                subtitle = "Because you watched recently",
                seriesId = seriesId,
                seasonNumber = null,
                episodeNumber = null,
                contentKey = null,
                rankScore = WEIGHT_RECENT_FRANCHISE - index
            )
        }

        trendingSeriesIds.forEachIndexed { index, seriesId ->
            if (followed.any { it.seriesId == seriesId }) return@forEachIndexed
            items += ForYouFeedItem(
                section = ForYouFeedSection.TRENDING,
                title = "Trending Series $seriesId",
                subtitle = "Popular now",
                seriesId = seriesId,
                seasonNumber = null,
                episodeNumber = null,
                contentKey = null,
                rankScore = WEIGHT_TRENDING - index
            )
        }

        val ranked = items
            .distinctBy { it.contentKey ?: "${it.section}:${it.title}:${it.subtitle}" }
            .sortedByDescending { it.rankScore }

        return ForYouFeed(profileId = profileId, items = ranked)
    }

    private fun recencyBoost(lastWatchedAt: Long): Double {
        val ageHours = (System.currentTimeMillis() - lastWatchedAt) / 3_600_000.0
        return (10.0 - ageHours.coerceAtMost(10.0)).coerceAtLeast(0.0)
    }
}
