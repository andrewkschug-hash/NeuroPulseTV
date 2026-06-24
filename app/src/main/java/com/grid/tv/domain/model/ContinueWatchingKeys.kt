package com.grid.tv.domain.model

object ContinueWatchingKeys {
    fun movieContentKey(playlistId: Long, streamId: Long): String =
        if (playlistId > 0L) "movie:$playlistId:$streamId" else legacyMovieContentKey(streamId)

    fun legacyMovieContentKey(streamId: Long): String = "movie:$streamId"

    fun seriesContentKey(playlistId: Long, seriesId: Long, season: Int, episode: Int): String =
        if (playlistId > 0L) {
            "series:$playlistId:$seriesId:$season:$episode"
        } else {
            legacySeriesContentKey(seriesId, season, episode)
        }

    fun legacySeriesContentKey(seriesId: Long, season: Int, episode: Int): String =
        "series:$seriesId:$season:$episode"

    fun legacyStreamKey(streamId: Long): String = "stream:$streamId"

    fun legacyMovieKeys(streamId: Long): List<String> = listOf(
        legacyMovieContentKey(streamId),
        legacyStreamKey(streamId)
    )
}
