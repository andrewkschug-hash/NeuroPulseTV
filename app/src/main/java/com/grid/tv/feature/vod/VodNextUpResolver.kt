package com.grid.tv.feature.vod

import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.VodPlaybackMeta

data class VodNextUpItem(
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val streamUrl: String,
    val streamId: Long,
    val posterUrl: String?
)

object VodNextUpResolver {
    fun resolve(meta: VodPlaybackMeta, detail: SeriesDetail): VodNextUpItem? {
        if (!meta.isSeries || meta.seriesId == null) return null
        val season = meta.seasonNumber ?: return null
        val current = meta.episodeNumber ?: return null
        val seasonEpisodes = detail.seasons.firstOrNull { it.number == season }?.episodes.orEmpty()
        val next = seasonEpisodes.firstOrNull { (it.episodeNumber ?: 0) == current + 1 }
            ?: return null
        val url = next.streamUrl.takeIf { it.isNotBlank() } ?: return null
        return VodNextUpItem(
            title = next.title,
            seasonNumber = season,
            episodeNumber = next.episodeNumber ?: (current + 1),
            streamUrl = url,
            streamId = next.id,
            posterUrl = meta.posterUrl
        )
    }
}
