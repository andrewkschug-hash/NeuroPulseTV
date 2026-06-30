package com.grid.tv.data.db.mapper

import com.grid.tv.data.db.entity.VodCatalogEpisodeEntity
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason

fun List<VodCatalogEpisodeEntity>.toSeriesDetail(): SeriesDetail {
    if (isEmpty()) return SeriesDetail()
    val plot = firstOrNull()?.seriesPlot
    val seasons = groupBy { it.seasonNumber }
        .toSortedMap()
        .map { (seasonNum, episodes) ->
            SeriesSeason(
                number = seasonNum,
                episodes = episodes
                    .sortedBy { it.episodeNumber }
                    .map { it.toEpisode() }
            )
        }
    return SeriesDetail(seasons = seasons, plot = plot)
}

fun VodCatalogEpisodeEntity.toEpisode(): SeriesEpisode = SeriesEpisode(
    id = episodeId,
    title = episodeTitle,
    extension = extension,
    streamUrl = streamUrl,
    plot = plot,
    duration = duration,
    episodeNumber = episodeNumber
)

fun SeriesDetail.toEpisodeEntities(
    playlistId: Long,
    seriesId: Long,
    seriesTitle: String,
    fetchedAt: Long = System.currentTimeMillis()
): List<VodCatalogEpisodeEntity> = seasons.flatMap { season ->
    season.episodes.map { episode ->
        val episodeNumber = episode.episodeNumber
            ?: (season.episodes.indexOf(episode) + 1)
        VodCatalogEpisodeEntity(
            playlistId = playlistId,
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            seasonNumber = season.number,
            episodeNumber = episodeNumber,
            episodeId = episode.id,
            episodeTitle = episode.title,
            addedAt = fetchedAt,
            extension = episode.extension,
            streamUrl = episode.streamUrl,
            plot = episode.plot,
            duration = episode.duration,
            seriesPlot = plot,
            fetchedAt = fetchedAt
        )
    }
}
