package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.VodWatchEventDao
import com.grid.tv.data.db.entity.VodWatchEventEntity
import com.grid.tv.data.repository.ContinueWatchingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodWatchTracker @Inject constructor(
    private val watchEventDao: VodWatchEventDao
) {
    suspend fun recordMovie(
        profileId: Long,
        playlistId: Long,
        streamId: Long,
        positionMs: Long,
        durationMs: Long
    ) {
        val progress = progressPercent(positionMs, durationMs)
        watchEventDao.insert(
            VodWatchEventEntity(
                profileId = profileId,
                contentId = ContinueWatchingRepository.movieContentKey(playlistId, streamId),
                contentType = VodContentType.MOVIE.name,
                playlistId = playlistId.coerceAtLeast(0L),
                seriesId = null,
                seasonNumber = null,
                episodeNumber = null,
                progressPercent = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatched = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordEpisode(
        profileId: Long,
        playlistId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        val contentKey = ContinueWatchingRepository.seriesContentKey(
            playlistId,
            seriesId,
            seasonNumber,
            episodeNumber
        )
        val progress = progressPercent(positionMs, durationMs)
        watchEventDao.insert(
            VodWatchEventEntity(
                profileId = profileId,
                contentId = contentKey,
                contentType = VodContentType.EPISODE.name,
                playlistId = playlistId.coerceAtLeast(0L),
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                progressPercent = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatched = System.currentTimeMillis()
            )
        )
    }

    suspend fun latestWatchedEpisode(
        profileId: Long,
        playlistId: Long,
        seriesId: Long
    ): VodWatchEvent? =
        watchEventDao.latestForSeries(profileId, playlistId, seriesId)?.toDomain()

    suspend fun recentEpisodes(profileId: Long, limit: Int = 20): List<VodWatchEvent> =
        watchEventDao.recentEpisodes(profileId, limit).map { it.toDomain() }

    private fun progressPercent(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private fun VodWatchEventEntity.toDomain() = VodWatchEvent(
        profileId = profileId,
        contentId = contentId,
        contentType = VodContentType.fromStored(contentType),
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        progressPercent = progressPercent,
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatched = lastWatched
    )
}
