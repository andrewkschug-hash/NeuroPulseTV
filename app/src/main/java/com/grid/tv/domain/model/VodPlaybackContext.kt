package com.grid.tv.domain.model

data class VodPlaybackMeta(
    val posterUrl: String? = null,
    val streamId: Long? = null,
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val title: String? = null,
    val playlistId: Long? = null,
    val isTv: Boolean = false
) {
    val isSeries: Boolean
        get() = seriesId != null && seasonNumber != null && episodeNumber != null
}

object VodPlaybackContext {
    @Volatile
    private var pending: VodPlaybackMeta = VodPlaybackMeta()

    fun stageMovie(
        posterUrl: String?,
        streamId: Long?,
        title: String? = null,
        playlistId: Long? = null
    ) {
        pending = VodPlaybackMeta(
            posterUrl = posterUrl,
            streamId = streamId,
            title = title,
            playlistId = playlistId,
            isTv = false
        )
    }

    fun stageSeriesEpisode(
        posterUrl: String?,
        streamId: Long?,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String? = null,
        playlistId: Long? = null
    ) {
        pending = VodPlaybackMeta(
            posterUrl = posterUrl,
            streamId = streamId,
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = title,
            playlistId = playlistId,
            isTv = true
        )
    }

    fun consume(): VodPlaybackMeta {
        val snapshot = pending
        pending = VodPlaybackMeta()
        return snapshot
    }
}
