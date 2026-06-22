package com.grid.tv.domain.model

import com.grid.tv.data.repository.ContinueWatchingRepository
object VodPlaybackHelper {

    fun resumePositionFor(item: ContinueWatchingItem): Long {
        if (item.positionMs <= 5_000L) return 0L
        if (item.durationMs <= 0L) return item.positionMs
        val fraction = item.positionMs.toDouble() / item.durationMs.toDouble()
        return if (fraction >= ContinueWatchingRepository.COMPLETION_THRESHOLD) 0L else item.positionMs
    }

    fun stageContinueWatching(item: ContinueWatchingItem) {
        val resumePositionMs = resumePositionFor(item)
        when (item.contentType) {
            ContinueWatchingContentType.MOVIE -> {
                VodPlaybackContext.stageMovie(
                    posterUrl = item.posterUrl,
                    streamId = item.streamId,
                    title = item.title,
                    resumePositionMs = resumePositionMs
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
                    title = item.title,
                    resumePositionMs = resumePositionMs
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
