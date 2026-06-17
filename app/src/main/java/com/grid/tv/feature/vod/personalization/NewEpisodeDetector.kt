package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.repository.ContinueWatchingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewEpisodeDetector @Inject constructor(
    private val watchTracker: VodWatchTracker,
    private val followManager: SeriesFollowManager
) {
    suspend fun detectForProfile(
        profileId: Long,
        catalogEpisodes: List<CatalogEpisode>
    ): List<NewEpisodeDetection> {
        val followed = followManager.getFollowedSeries(profileId).map { it.seriesId }.toSet()
        val bySeries = catalogEpisodes.groupBy { it.seriesId }

        return bySeries.flatMap { (seriesId, episodes) ->
            if (seriesId !in followed) return@flatMap emptyList()
            val latestWatched = watchTracker.latestWatchedEpisode(profileId, seriesId)
            val lastEpisode = latestWatched?.episodeNumber
            val lastSeason = latestWatched?.seasonNumber ?: 1

            episodes
                .filter { ep ->
                    when {
                        lastEpisode == null -> true
                        ep.seasonNumber > lastSeason -> true
                        ep.seasonNumber == lastSeason && ep.episodeNumber > lastEpisode -> true
                        else -> false
                    }
                }
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                .map { ep ->
                    NewEpisodeDetection(
                        seriesId = ep.seriesId,
                        seriesTitle = ep.seriesTitle,
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        episodeTitle = ep.episodeTitle,
                        contentKey = ContinueWatchingRepository.seriesContentKey(
                            ep.seriesId,
                            ep.seasonNumber,
                            ep.episodeNumber
                        ),
                        previousWatchedEpisode = lastEpisode
                    )
                }
        }
    }

    /**
     * Classic example: user watched Episode 37, catalog adds Episode 38 → detected.
     */
    fun isSequentialNewEpisode(previousEpisode: Int?, newEpisode: CatalogEpisode): Boolean {
        if (previousEpisode == null) return false
        return newEpisode.episodeNumber == previousEpisode + 1
    }
}
