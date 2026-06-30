package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.VodCatalogEpisodeDao
import com.grid.tv.data.db.entity.VodCatalogEpisodeEntity
import com.grid.tv.domain.model.SeriesSeason
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogMonitor @Inject constructor(
    private val catalogDao: VodCatalogEpisodeDao
) {
    data class CatalogSyncResult(
        val playlistId: Long,
        val seriesId: Long,
        val seriesTitle: String,
        val addedEpisodes: List<CatalogEpisode>,
        val addedSeasons: List<Int>
    )

    suspend fun syncSeriesCatalog(
        playlistId: Long,
        seriesId: Long,
        seriesTitle: String,
        seasons: List<SeriesSeason>,
        detectedAt: Long = System.currentTimeMillis()
    ): CatalogSyncResult {
        val existing = catalogDao.episodesForSeries(playlistId, seriesId)
            .associateBy { it.seasonNumber to it.episodeNumber }
        val incoming = mutableListOf<VodCatalogEpisodeEntity>()
        val added = mutableListOf<CatalogEpisode>()

        seasons.forEach { season ->
            season.episodes.forEach { ep ->
                val epNum = ep.episodeNumber ?: season.episodes.indexOf(ep) + 1
                val key = season.number to epNum
                if (!existing.containsKey(key)) {
                    val entity = VodCatalogEpisodeEntity(
                        playlistId = playlistId,
                        seriesId = seriesId,
                        seriesTitle = seriesTitle,
                        seasonNumber = season.number,
                        episodeNumber = epNum,
                        episodeId = ep.id,
                        episodeTitle = ep.title,
                        addedAt = detectedAt,
                        extension = ep.extension,
                        streamUrl = ep.streamUrl,
                        plot = ep.plot,
                        duration = ep.duration,
                        seriesPlot = null,
                        fetchedAt = detectedAt
                    )
                    incoming.add(entity)
                    added.add(entity.toDomain())
                }
            }
        }

        if (incoming.isNotEmpty()) {
            catalogDao.upsertAll(incoming)
        }

        val previousSeasons = existing.keys.map { it.first }.toSet()
        val newSeasons = added.map { it.seasonNumber }.filter { it !in previousSeasons }.distinct()

        return CatalogSyncResult(
            playlistId = playlistId,
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            addedEpisodes = added,
            addedSeasons = newSeasons
        )
    }

    suspend fun syncFromCatalogEpisodes(episodes: List<CatalogEpisode>): List<CatalogSyncResult> {
        if (episodes.isEmpty()) return emptyList()
        catalogDao.upsertAll(episodes.map { it.toEntity() })
        return episodes.groupBy { it.playlistId to it.seriesId }.map { (key, rows) ->
            CatalogSyncResult(
                playlistId = key.first,
                seriesId = key.second,
                seriesTitle = rows.first().seriesTitle,
                addedEpisodes = rows,
                addedSeasons = rows.map { it.seasonNumber }.distinct()
            )
        }
    }

    suspend fun episodesForSeries(playlistId: Long, seriesId: Long): List<CatalogEpisode> =
        catalogDao.episodesForSeries(playlistId, seriesId).map { it.toDomain() }

    private fun VodCatalogEpisodeEntity.toDomain() = CatalogEpisode(
        playlistId = playlistId,
        seriesId = seriesId,
        seriesTitle = seriesTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeId = episodeId,
        episodeTitle = episodeTitle,
        addedAt = addedAt
    )

    private fun CatalogEpisode.toEntity() = VodCatalogEpisodeEntity(
        playlistId = playlistId,
        seriesId = seriesId,
        seriesTitle = seriesTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeId = episodeId,
        episodeTitle = episodeTitle,
        addedAt = addedAt,
        extension = "mp4",
        streamUrl = "",
        plot = null,
        duration = null,
        seriesPlot = null,
        fetchedAt = addedAt
    )
}
