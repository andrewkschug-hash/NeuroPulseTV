package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.VodCatalogEpisodeDao
import com.grid.tv.data.db.dao.VodWatchEventDao
import com.grid.tv.domain.model.SeriesSeason
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background catalog monitoring: syncs catalog snapshots and triggers new-episode processing.
 */
@Singleton
class CatalogUpdateWorker @Inject constructor(
    private val catalogMonitor: CatalogMonitor,
    private val personalizationService: VodPersonalizationService
) {
    suspend fun onSeriesCatalogRefreshed(
        profileId: Long,
        playlistId: Long,
        seriesId: Long,
        seriesTitle: String,
        seasons: List<SeriesSeason>
    ): List<VodNotification> {
        val syncResult = catalogMonitor.syncSeriesCatalog(playlistId, seriesId, seriesTitle, seasons)
        if (syncResult.addedEpisodes.isEmpty() && syncResult.addedSeasons.isEmpty()) {
            return emptyList()
        }
        return personalizationService.processCatalogUpdate(profileId, playlistId, seriesId, seriesTitle, syncResult)
    }

    suspend fun onEpisodeWatched(
        profileId: Long,
        seriesId: Long,
        seriesTitle: String,
        playlistId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        personalizationService.recordEpisodeWatch(
            profileId, seriesId, seriesTitle, playlistId,
            seasonNumber, episodeNumber, positionMs, durationMs
        )
    }
}
