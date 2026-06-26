package com.grid.tv.data.repository

import com.grid.tv.data.db.dao.ContinueWatchingDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.entity.ContinueWatchingEntity
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.ContinueWatchingKeys
import com.grid.tv.domain.model.PlaylistIdentityGuards
import com.grid.tv.domain.model.VodProgressKeys
import com.grid.tv.domain.model.VodProgressPolicy
import com.grid.tv.feature.vod.StoredResumeProgress
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
        /** Remove from continue-watching when this fraction of runtime is reached. */
        val COMPLETION_THRESHOLD = VodProgressPolicy.WATCHED_FRACTION
        private const val DEFAULT_LIMIT = 12

        fun movieContentKey(playlistId: Long, streamId: Long): String =
            ContinueWatchingKeys.movieContentKey(playlistId, streamId)

        fun seriesContentKey(playlistId: Long, seriesId: Long, season: Int, episode: Int): String =
            ContinueWatchingKeys.seriesContentKey(playlistId, seriesId, season, episode)

        @Deprecated("Use movieContentKey(playlistId, streamId)")
        fun movieContentKey(streamId: Long): String = ContinueWatchingKeys.legacyMovieContentKey(streamId)

        @Deprecated("Use seriesContentKey(playlistId, seriesId, season, episode)")
        fun seriesContentKey(seriesId: Long, season: Int, episode: Int): String =
            ContinueWatchingKeys.legacySeriesContentKey(seriesId, season, episode)
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

    suspend fun resumePositionForStream(
        profileId: Long,
        playlistId: Long,
        streamId: Long
    ): Long? = storedResumeForStream(profileId, playlistId, streamId)?.positionMs?.takeIf { it > 0L }

    suspend fun resumePositionForSeriesEpisode(
        profileId: Long,
        playlistId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ): Long? = storedResumeForSeriesEpisode(
        profileId,
        playlistId,
        seriesId,
        seasonNumber,
        episodeNumber
    )?.positionMs?.takeIf { it > 0L }

    suspend fun storedResumeForStream(
        profileId: Long,
        playlistId: Long,
        streamId: Long
    ): StoredResumeProgress? = entityWithLegacyFallback(
        profileId = profileId,
        playlistId = playlistId,
        scopedKey = movieContentKey(playlistId, streamId),
        legacyKeys = ContinueWatchingKeys.legacyMovieKeys(streamId)
    )?.toStoredResume()

    suspend fun storedResumeForSeriesEpisode(
        profileId: Long,
        playlistId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ): StoredResumeProgress? = entityWithLegacyFallback(
        profileId = profileId,
        playlistId = playlistId,
        scopedKey = seriesContentKey(playlistId, seriesId, seasonNumber, episodeNumber),
        legacyKeys = listOf(
            ContinueWatchingKeys.legacySeriesContentKey(seriesId, seasonNumber, episodeNumber)
        )
    )?.toStoredResume()

    suspend fun latestForSeries(
        profileId: Long,
        seriesId: Long,
        playlistId: Long = 0L
    ): ContinueWatchingItem? =
        dao.latestForSeries(profileId, seriesId, playlistId)?.let(::toDomain)

    suspend fun hasResumeProgress(
        profileId: Long,
        playlistId: Long,
        streamId: Long
    ): Boolean = resumePositionForStream(profileId, playlistId, streamId)?.let { it > 5_000L } == true

    suspend fun hasEpisodeResumeProgress(
        profileId: Long,
        playlistId: Long,
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int
    ): Boolean = resumePositionForSeriesEpisode(
        profileId,
        playlistId,
        seriesId,
        seasonNumber,
        episodeNumber
    )?.let { it > 5_000L } == true

    suspend fun saveMovie(
        profileId: Long,
        playlistId: Long,
        streamId: Long,
        title: String,
        posterUrl: String?,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val contentKey = movieContentKey(playlistId, streamId)
        if (shouldRemove(positionMs, durationMs)) {
            purgeMovieKeys(profileId, playlistId, streamId)
            return
        }
        purgeLegacyMovieKeys(profileId, streamId, excludeKey = contentKey)
        dao.upsert(
            ContinueWatchingEntity(
                profileId = profileId,
                contentKey = contentKey,
                contentType = ContinueWatchingContentType.MOVIE.name,
                playlistId = playlistId.coerceAtLeast(0L),
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
        playlistId: Long,
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
        val contentKey = seriesContentKey(playlistId, seriesId, seasonNumber, episodeNumber)
        if (shouldRemove(positionMs, durationMs)) {
            dao.delete(profileId, contentKey)
            if (playlistId > 0L) {
                dao.delete(
                    profileId,
                    ContinueWatchingKeys.legacySeriesContentKey(seriesId, seasonNumber, episodeNumber)
                )
            }
            return
        }
        if (playlistId > 0L) {
            dao.delete(
                profileId,
                ContinueWatchingKeys.legacySeriesContentKey(seriesId, seasonNumber, episodeNumber)
            )
        }
        dao.upsert(
            ContinueWatchingEntity(
                profileId = profileId,
                contentKey = contentKey,
                contentType = ContinueWatchingContentType.SERIES.name,
                playlistId = playlistId.coerceAtLeast(0L),
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

    private suspend fun entityWithLegacyFallback(
        profileId: Long,
        playlistId: Long,
        scopedKey: String,
        legacyKeys: List<String>
    ): ContinueWatchingEntity? {
        dao.get(profileId, scopedKey)?.let { return it }
        for (legacyKey in legacyKeys) {
            if (legacyKey == scopedKey) continue
            val row = dao.get(profileId, legacyKey) ?: continue
            if (row.positionMs <= 0L) continue
            if (playlistId > 0L) {
                migrateLegacyRow(profileId, row, scopedKey, playlistId)
            }
            return dao.get(profileId, scopedKey) ?: row.copy(contentKey = scopedKey, playlistId = playlistId.coerceAtLeast(0L))
        }
        return null
    }

    private fun ContinueWatchingEntity.toStoredResume(): StoredResumeProgress =
        StoredResumeProgress(positionMs = positionMs, durationMs = durationMs)

    private suspend fun migrateLegacyRow(
        profileId: Long,
        row: ContinueWatchingEntity,
        newKey: String,
        playlistId: Long
    ) {
        dao.delete(profileId, row.contentKey)
        dao.upsert(
            row.copy(
                contentKey = newKey,
                playlistId = playlistId.coerceAtLeast(0L)
            )
        )
    }

    private suspend fun purgeMovieKeys(profileId: Long, playlistId: Long, streamId: Long) {
        dao.delete(profileId, movieContentKey(playlistId, streamId))
        purgeLegacyMovieKeys(profileId, streamId)
    }

    private suspend fun purgeLegacyMovieKeys(
        profileId: Long,
        streamId: Long,
        excludeKey: String? = null
    ) {
        ContinueWatchingKeys.legacyMovieKeys(streamId).forEach { key ->
            if (key != excludeKey) dao.delete(profileId, key)
        }
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
            playlistId = entity.playlistId,
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
        val progressKey = VodProgressKeys.decode(row.channelId)
        if (progressKey.streamId <= 0L || row.lastPosition <= 5_000L) return null
        val durationMs = row.genreHint?.toLongOrNull() ?: 0L
        if (shouldRemove(row.lastPosition, durationMs)) return null
        val vod = if (progressKey.playlistId > 0L) {
            vodStreamDao.findByStreamId(progressKey.playlistId, progressKey.streamId)
        } else {
            PlaylistIdentityGuards.warnGlobalStreamLookup(
                "ContinueWatchingRepository.historyToContinueWatching",
                progressKey.streamId
            )
            null
        } ?: return null
        val playlistId = progressKey.playlistId.takeIf { it > 0L } ?: vod.playlistId
        val resolvedDuration = durationMs.takeIf { it > 0L } ?: parseStoredDurationMs(vod.duration) ?: 0L
        if (shouldRemove(row.lastPosition, resolvedDuration)) return null
        return ContinueWatchingItem(
            contentKey = movieContentKey(playlistId, progressKey.streamId),
            contentType = ContinueWatchingContentType.MOVIE,
            title = row.lastProgramTitle?.takeIf { it.isNotBlank() } ?: vod.title,
            posterUrl = vod.posterUrl,
            streamUrl = vod.streamUrl,
            positionMs = row.lastPosition,
            durationMs = resolvedDuration,
            lastWatchedAt = row.lastWatched,
            playlistId = playlistId,
            streamId = progressKey.streamId
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
