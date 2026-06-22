package com.grid.tv.data.repository

import com.grid.tv.data.db.dao.ContinueWatchingDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.entity.ContinueWatchingEntity
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class ContinueWatchingRepository @Inject constructor(
    private val dao: ContinueWatchingDao,
    private val profileDao: ProfileDao
) {
    companion object {
        const val COMPLETION_THRESHOLD = 0.95
        private const val DEFAULT_LIMIT = 12

        fun movieContentKey(streamId: Long): String = "movie:$streamId"

        fun seriesContentKey(seriesId: Long, season: Int, episode: Int): String =
            "series:$seriesId:$season:$episode"
    }

    fun observeItems(limit: Int = DEFAULT_LIMIT): Flow<List<ContinueWatchingItem>> =
        profileDao.observeActiveProfileId().flatMapLatest { profileId ->
            if (profileId == null) flowOf(emptyList())
            else dao.observeForProfile(profileId, limit).map { rows -> rows.map(::toDomain) }
        }

    suspend fun resumePosition(profileId: Long, contentKey: String): Long? =
        dao.get(profileId, contentKey)?.positionMs?.takeIf { it > 0L }

    suspend fun resumePositionForStream(profileId: Long, streamId: Long): Long? =
        resumePosition(profileId, movieContentKey(streamId))
            ?: dao.get(profileId, "stream:$streamId")?.positionMs?.takeIf { it > 0L }

    suspend fun resumePositionForSeriesEpisode(
        profileId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ): Long? = resumePosition(profileId, seriesContentKey(seriesId, seasonNumber, episodeNumber))

    suspend fun latestForSeries(profileId: Long, seriesId: Long): ContinueWatchingItem? =
        dao.latestForSeries(profileId, seriesId)?.let(::toDomain)

    suspend fun hasResumeProgress(profileId: Long, streamId: Long): Boolean =
        resumePositionForStream(profileId, streamId)?.let { it > 5_000L } == true

    suspend fun hasEpisodeResumeProgress(
        profileId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ): Boolean = resumePositionForSeriesEpisode(profileId, seriesId, seasonNumber, episodeNumber)
        ?.let { it > 5_000L } == true

    suspend fun saveMovie(
        profileId: Long,
        streamId: Long,
        title: String,
        posterUrl: String?,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        if (shouldRemove(positionMs, durationMs)) {
            dao.delete(profileId, movieContentKey(streamId))
            return
        }
        dao.upsert(
            ContinueWatchingEntity(
                profileId = profileId,
                contentKey = movieContentKey(streamId),
                contentType = ContinueWatchingContentType.MOVIE.name,
                streamId = streamId,
                seriesId = null,
                seasonNumber = null,
                episodeNumber = null,
                title = title,
                posterUrl = posterUrl,
                streamUrl = streamUrl,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                lastWatchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveSeriesEpisode(
        profileId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        streamId: Long,
        title: String,
        posterUrl: String?,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val contentKey = seriesContentKey(seriesId, seasonNumber, episodeNumber)
        if (shouldRemove(positionMs, durationMs)) {
            dao.delete(profileId, contentKey)
            return
        }
        dao.upsert(
            ContinueWatchingEntity(
                profileId = profileId,
                contentKey = contentKey,
                contentType = ContinueWatchingContentType.SERIES.name,
                streamId = streamId,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                title = title,
                posterUrl = posterUrl,
                streamUrl = streamUrl,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                lastWatchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(profileId: Long, contentKey: String) {
        dao.delete(profileId, contentKey)
    }

    private fun shouldRemove(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs <= 0L) return true
        if (durationMs <= 0L) return false
        return positionMs.toDouble() / durationMs >= COMPLETION_THRESHOLD
    }

    private fun toDomain(entity: ContinueWatchingEntity): ContinueWatchingItem =
        ContinueWatchingItem(
            contentKey = entity.contentKey,
            contentType = ContinueWatchingContentType.fromStored(entity.contentType),
            title = entity.title,
            posterUrl = entity.posterUrl,
            streamUrl = entity.streamUrl,
            positionMs = entity.positionMs,
            durationMs = entity.durationMs,
            lastWatchedAt = entity.lastWatchedAt,
            streamId = entity.streamId,
            seriesId = entity.seriesId,
            seasonNumber = entity.seasonNumber,
            episodeNumber = entity.episodeNumber
        )
}
