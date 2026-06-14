package com.neuropulse.tv.domain.model

data class VodPlaybackMeta(
    val posterUrl: String? = null,
    val streamId: Long? = null,
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
) {
    val isSeries: Boolean
        get() = seriesId != null && seasonNumber != null && episodeNumber != null
}

object VodPlaybackContext {
    @Volatile
    private var pending: VodPlaybackMeta = VodPlaybackMeta()

    fun stageMovie(posterUrl: String?, streamId: Long?) {
        pending = VodPlaybackMeta(posterUrl = posterUrl, streamId = streamId)
    }

    fun stageSeriesEpisode(
        posterUrl: String?,
        streamId: Long?,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        pending = VodPlaybackMeta(
            posterUrl = posterUrl,
            streamId = streamId,
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    fun consume(): VodPlaybackMeta {
        val snapshot = pending
        pending = VodPlaybackMeta()
        return snapshot
    }
}
