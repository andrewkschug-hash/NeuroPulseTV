package com.grid.tv.domain.model

object VodSearchIdentity {
    fun vodResultId(playlistId: Long, streamId: Long): String = "vod-$playlistId-$streamId"

    fun seriesResultId(playlistId: Long, seriesId: Long): String = "series-$playlistId-$seriesId"

    fun episodeResultId(playlistId: Long, seriesId: Long, season: Int, episode: Int): String =
        "episode-$playlistId-$seriesId-$season-$episode"

    fun vodDedupKey(playlistId: Long, streamId: Long, seriesId: Long? = null): String =
        "$playlistId:$streamId:${seriesId ?: 0L}"
}
