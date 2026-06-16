package com.grid.tv.domain.model

object VodPlaybackHelper {

    fun stageContinueWatching(item: ContinueWatchingItem) {
        when (item.contentType) {
            ContinueWatchingContentType.MOVIE -> {
                VodPlaybackContext.stageMovie(
                    posterUrl = item.posterUrl,
                    streamId = item.streamId,
                    title = item.title
                )
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
                    episodeNumber = episode,
                    title = item.title
                )
            }
        }
    }

    fun stageMovie(item: VodItem) {
        VodPlaybackContext.stageMovie(
            posterUrl = item.posterUrl,
            streamId = item.streamId,
            title = item.title,
            playlistId = item.playlistId.takeIf { it > 0L }
        )
    }

    fun stageSeriesEpisode(
        show: SeriesShow,
        seasonNumber: Int,
        episodeNumber: Int,
        streamId: Long,
        episodeTitle: String? = null
    ) {
        VodPlaybackContext.stageSeriesEpisode(
            posterUrl = show.coverUrl,
            streamId = streamId,
            seriesId = show.id,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = episodeTitle ?: show.name,
            playlistId = show.playlistId.takeIf { it > 0L }
        )
    }
}
