package com.grid.tv.domain.model

data class VodPlaybackMeta(
    val posterUrl: String? = null,
    val streamId: Long? = null,
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val title: String? = null,
    val playlistId: Long? = null,
    val isTv: Boolean = false,
    /** Staged resume position from Continue Watching or caller intent (RESUME_POSITION). */
    val resumePositionMs: Long = 0L
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
        playlistId: Long? = null,
        resumePositionMs: Long = 0L
    ) {
        pending = VodPlaybackMeta(
            posterUrl = posterUrl,
            streamId = streamId,
            title = title,
            playlistId = playlistId,
            isTv = false,
            resumePositionMs = resumePositionMs.coerceAtLeast(0L)
        )
    }

    fun stageSeriesEpisode(
        posterUrl: String?,
        streamId: Long?,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String? = null,
        playlistId: Long? = null,
        resumePositionMs: Long = 0L
    ) {
        pending = VodPlaybackMeta(
            posterUrl = posterUrl,
            streamId = streamId,
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = title,
            playlistId = playlistId,
            isTv = true,
            resumePositionMs = resumePositionMs.coerceAtLeast(0L)
        )
    }

    fun consume(): VodPlaybackMeta {
        val snapshot = pending
        pending = VodPlaybackMeta()
        return snapshot
    }
}
