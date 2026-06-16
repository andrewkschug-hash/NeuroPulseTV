package com.neuropulse.tv.feature.enrichment

import com.neuropulse.tv.data.db.dao.TitleEnrichmentDao
import com.neuropulse.tv.data.db.entity.TitleEnrichmentEntity
import com.neuropulse.tv.data.network.tmdb.TmdbEnrichment
import com.neuropulse.tv.data.network.tmdb.TmdbService
import com.neuropulse.tv.domain.model.ContinueWatchingContentType
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.VodPlaybackMeta
import javax.inject.Inject
import javax.inject.Singleton

// TODO: replace with server-side equivalent when scaling
@Singleton
class TitleEnrichmentRepository @Inject constructor(
    private val dao: TitleEnrichmentDao,
    private val tmdbService: TmdbService
) {
    companion object {
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000

        fun xtreamVodKey(playlistId: Long, streamId: Long): String =
            "xtream:vod:$playlistId:$streamId"

        fun xtreamSeriesKey(playlistId: Long, seriesId: Long): String =
            "xtream:series:$playlistId:$seriesId"

        fun continueWatchingKey(item: ContinueWatchingItem): String =
            when (item.contentType) {
                ContinueWatchingContentType.MOVIE ->
                    "cw:movie:${item.streamId ?: item.contentKey}"
                ContinueWatchingContentType.SERIES ->
                    "cw:series:${item.seriesId ?: item.contentKey}"
            }
    }

    fun observe(providerKey: String) = dao.observe(providerKey)

    suspend fun getCached(providerKey: String): TitleEnrichmentEntity? {
        val cached = dao.get(providerKey) ?: return null
        return cached.takeIf { isFresh(cached) }
    }

    suspend fun getCachedBatch(providerKeys: List<String>): Map<String, TitleEnrichmentEntity> {
        if (providerKeys.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        return dao.getByProviderKeys(providerKeys)
            .filter { now - it.updatedAt < CACHE_TTL_MS }
            .associateBy { it.providerKey }
    }

    suspend fun enrichOnDemand(
        providerKey: String,
        title: String,
        releaseYear: Int? = null,
        isTv: Boolean = false,
        imdbId: String? = null
    ): TitleEnrichmentEntity? {
        val existing = dao.get(providerKey)
        if (existing != null && isFresh(existing) && existing.tmdbId != null) {
            return existing
        }

        val enrichment = runCatching {
            when {
                !imdbId.isNullOrBlank() -> tmdbService.enrichByImdb(imdbId)
                isTv -> tmdbService.enrichTvFromTitle(title, releaseYear)
                else -> tmdbService.enrichMovieFromTitle(title, releaseYear)
            }
        }.getOrNull() ?: return existing

        val entity = mapEnrichmentEntity(
            providerKey = providerKey,
            normalizedTitle = normalizeTitle(title),
            releaseYear = releaseYear ?: parseYear(title),
            enrichment = enrichment,
            prior = existing
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun enrichFromPlaybackMeta(meta: VodPlaybackMeta) {
        val title = meta.title?.trim().orEmpty()
        if (title.isBlank()) return
        val providerKey = meta.providerKey() ?: return
        enrichOnDemand(
            providerKey = providerKey,
            title = title,
            releaseYear = parseYear(title),
            isTv = meta.isTv
        )
    }

    suspend fun enrichContinueWatching(item: ContinueWatchingItem): TitleEnrichmentEntity? =
        enrichOnDemand(
            providerKey = continueWatchingKey(item),
            title = item.title,
            releaseYear = parseYear(item.title),
            isTv = item.contentType == ContinueWatchingContentType.SERIES
        )

    private fun isFresh(entity: TitleEnrichmentEntity): Boolean =
        System.currentTimeMillis() - entity.updatedAt < CACHE_TTL_MS

    private fun mapEnrichmentEntity(
        providerKey: String,
        normalizedTitle: String,
        releaseYear: Int?,
        enrichment: TmdbEnrichment,
        prior: TitleEnrichmentEntity?
    ): TitleEnrichmentEntity = TitleEnrichmentEntity(
        providerKey = providerKey,
        normalizedTitle = normalizedTitle,
        releaseYear = releaseYear,
        tmdbId = enrichment.tmdbId,
        imdbId = enrichment.imdbId,
        mediaType = enrichment.mediaType,
        title = enrichment.title,
        overview = enrichment.overview,
        tagline = enrichment.tagline,
        releaseDate = enrichment.releaseDate,
        runtimeMinutes = enrichment.runtimeMinutes,
        cast = enrichment.cast,
        directors = enrichment.directors,
        writers = enrichment.writers,
        rating = enrichment.voteAverage,
        voteCount = enrichment.voteCount,
        popularity = enrichment.popularity,
        posterUrl = enrichment.posterUrl,
        backdropUrl = enrichment.backdropUrl,
        genres = enrichment.genres,
        keywords = enrichment.keywords,
        spokenLanguages = enrichment.spokenLanguages,
        originCountry = enrichment.originCountry,
        status = enrichment.status,
        ageCertification = enrichment.ageCertification,
        numberOfSeasons = enrichment.numberOfSeasons,
        numberOfEpisodes = enrichment.numberOfEpisodes,
        episodeRunTime = enrichment.episodeRunTime,
        contentVector = prior?.contentVector,
        updatedAt = System.currentTimeMillis()
    )

    private fun VodPlaybackMeta.providerKey(): String? {
        val playlist = playlistId ?: return null
        return when {
            isSeries && seriesId != null -> xtreamSeriesKey(playlist, seriesId)
            streamId != null -> xtreamVodKey(playlist, streamId)
            else -> null
        }
    }

    private fun normalizeTitle(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ")

    private fun parseYear(value: String): Int? {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(value) ?: return null
        return match.value.toIntOrNull()
    }
}
