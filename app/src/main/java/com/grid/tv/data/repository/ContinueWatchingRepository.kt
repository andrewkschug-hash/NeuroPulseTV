package com.grid.tv.data.repository

import com.grid.tv.data.db.dao.ContinueWatchingDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.entity.ContinueWatchingEntity
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

@Singleton
class ContinueWatchingRepository @Inject constructor(
    private val dao: ContinueWatchingDao,
    private val profileDao: ProfileDao,
    private val profileWatchHistoryDao: ProfileWatchHistoryDao,
    private val vodStreamDao: VodStreamDao
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
            else combine(
                dao.observeForProfile(profileId, limit),
                profileWatchHistoryDao.observeVodPositions(profileId)
            ) { rows, historyRows -> rows to historyRows }
                .flatMapLatest { (rows, historyRows) ->
                    flow {
                        emit(
                            withContext(Dispatchers.IO) {
                                mergeResumeItems(rows, historyRows, limit)
                            }
                        )
                    }
                }
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

    private suspend fun mergeResumeItems(
        primaryRows: List<ContinueWatchingEntity>,
        historyRows: List<ProfileWatchHistoryEntity>,
        limit: Int
    ): List<ContinueWatchingItem> {
        val merged = primaryRows.map(::toDomain).toMutableList()
        val seenKeys = merged.map { it.contentKey }.toMutableSet()
        for (row in historyRows.sortedByDescending { it.lastWatched }) {
            if (merged.size >= limit) break
            val fallback = historyToContinueWatching(row) ?: continue
            if (fallback.contentKey in seenKeys) continue
            merged += fallback
            seenKeys += fallback.contentKey
        }
        return merged.take(limit)
    }

    private suspend fun historyToContinueWatching(
        row: ProfileWatchHistoryEntity
    ): ContinueWatchingItem? {
        val streamId = -row.channelId
        if (streamId <= 0L || row.lastPosition <= 5_000L) return null
        val durationMs = row.genreHint?.toLongOrNull() ?: 0L
        if (shouldRemove(row.lastPosition, durationMs)) return null
        val vod = vodStreamDao.findAnyByStreamId(streamId) ?: return null
        val resolvedDuration = durationMs.takeIf { it > 0L } ?: parseStoredDurationMs(vod.duration) ?: 0L
        if (shouldRemove(row.lastPosition, resolvedDuration)) return null
        return ContinueWatchingItem(
            contentKey = movieContentKey(streamId),
            contentType = ContinueWatchingContentType.MOVIE,
            title = row.lastProgramTitle?.takeIf { it.isNotBlank() } ?: vod.title,
            posterUrl = vod.posterUrl,
            streamUrl = vod.streamUrl,
            positionMs = row.lastPosition,
            durationMs = resolvedDuration,
            lastWatchedAt = row.lastWatched,
            streamId = streamId
        )
    }

    private fun parseStoredDurationMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.trim().toLongOrNull()?.let { return it * 1_000L }
        val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3_600 + parts[1] * 60 + parts[2]) * 1_000L
            2 -> (parts[0] * 60 + parts[1]) * 1_000L
            else -> null
        }
    }
}
