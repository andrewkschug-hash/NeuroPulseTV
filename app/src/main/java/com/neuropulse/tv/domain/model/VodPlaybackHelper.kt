package com.neuropulse.tv.domain.model

object VodPlaybackHelper {

    fun stageContinueWatching(item: ContinueWatchingItem) {
        when (item.contentType) {
            ContinueWatchingContentType.MOVIE -> {
                VodPlaybackContext.stageMovie(item.posterUrl, item.streamId)
            }
            ContinueWatchingContentType.SERIES -> {
                val seriesId = item.seriesId ?: return
                val season = item.seasonNumber ?: return
                val episode = item.episodeNumber ?: return
                VodPlaybackContext.stageSeriesEpisode(
                    posterUrl = item.posterUrl,
                    streamId = item.streamId,
                    seriesId = seriesId,
                    seasonNumber = season,
                    episodeNumber = episode
                )
            }
        }
    }

    fun stageMovie(item: VodItem) {
        VodPlaybackContext.stageMovie(item.posterUrl, item.streamId)
    }

    fun stageSeriesEpisode(
        show: SeriesShow,
        seasonNumber: Int,
        episodeNumber: Int,
        streamId: Long
    ) {
        VodPlaybackContext.stageSeriesEpisode(
            posterUrl = show.coverUrl,
            streamId = streamId,
            seriesId = show.id,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }
}
