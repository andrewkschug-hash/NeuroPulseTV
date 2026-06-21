package com.grid.tv.feature.enrichment

import com.grid.tv.data.auth.SupabaseClientProvider
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.mapper.MovieDetailsEnrichmentMapper
import com.grid.tv.data.network.tmdb.TmdbEnrichment
import com.grid.tv.data.network.tmdb.TmdbService
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodPlaybackMeta
import com.grid.tv.domain.repository.MovieRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

@Singleton
class TitleEnrichmentRepository @Inject constructor(
    private val dao: TitleEnrichmentDao,
    private val tmdbService: TmdbService,
    private val movieRepository: MovieRepository,
    private val supabaseClientProvider: SupabaseClientProvider
) {
    private val sessionCache = ConcurrentHashMap<String, TitleEnrichmentEntity>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<TitleEnrichmentEntity?>>()

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
        sessionCache[providerKey]?.takeIf { isFresh(it) }?.let { return it }
        val cached = dao.get(providerKey) ?: return null
        return cached.takeIf { isFresh(cached) }?.also { sessionCache[providerKey] = it }
    }

    suspend fun getCachedBatch(providerKeys: List<String>): Map<String, TitleEnrichmentEntity> {
        if (providerKeys.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        return providerKeys.mapNotNull { key ->
            sessionCache[key]?.takeIf { isFresh(it) }
                ?: dao.get(key)?.takeIf { now - it.updatedAt < CACHE_TTL_MS }
        }.associateBy { it.providerKey }
            .also { batch -> sessionCache.putAll(batch) }
    }

    suspend fun enrichOnDemand(
        providerKey: String,
        title: String,
        releaseYear: Int? = null,
        isTv: Boolean = false,
        imdbId: String? = null
    ): TitleEnrichmentEntity? {
        sessionCache[providerKey]?.takeIf { isFresh(it) && it.tmdbId != null }?.let { return it }

        val existing = dao.get(providerKey)
        if (existing != null && isFresh(existing) && existing.tmdbId != null) {
            sessionCache[providerKey] = existing
            return existing
        }

        inFlight[providerKey]?.let { return it.await() }

        val deferred = CompletableDeferred<TitleEnrichmentEntity?>()
        inFlight[providerKey] = deferred
        return try {
            val entity = fetchAndPersist(
                providerKey = providerKey,
                title = title,
                releaseYear = releaseYear ?: parseYear(title),
                isTv = isTv,
                imdbId = imdbId,
                prior = existing
            )
            deferred.complete(entity)
            entity?.let { sessionCache[providerKey] = it }
            entity
        } catch (_: Throwable) {
            deferred.complete(existing)
            existing
        } finally {
            inFlight.remove(providerKey)
        }
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

    private suspend fun fetchAndPersist(
        providerKey: String,
        title: String,
        releaseYear: Int?,
        isTv: Boolean,
        imdbId: String?,
        prior: TitleEnrichmentEntity?
    ): TitleEnrichmentEntity? {
        val enrichment = runCatching {
            fetchEnrichment(title, releaseYear, isTv, imdbId)
        }.getOrNull() ?: return prior

        val entity = mapEnrichmentEntity(
            providerKey = providerKey,
            normalizedTitle = normalizeTitle(title),
            releaseYear = releaseYear,
            enrichment = enrichment,
            prior = prior
        )
        dao.upsert(entity)
        return entity
    }

    private suspend fun fetchEnrichment(
        title: String,
        releaseYear: Int?,
        isTv: Boolean,
        imdbId: String?
    ): TmdbEnrichment? {
        if (!isTv && supabaseClientProvider.isConfigured) {
            enrichMovieViaEdgeFunction(title, releaseYear, imdbId)?.let { return it }
        }
        return when {
            !imdbId.isNullOrBlank() -> tmdbService.enrichByImdb(imdbId)
            isTv -> tmdbService.enrichTvFromTitle(title, releaseYear)
            else -> tmdbService.enrichMovieFromTitle(title, releaseYear)
        }
    }

    /**
     * Resolves a TMDB movie id from title/year (or IMDb), then loads full metadata via the
     * Supabase `get-movie-details` edge function.
     */
    private suspend fun enrichMovieViaEdgeFunction(
        title: String,
        releaseYear: Int?,
        imdbId: String?
    ): TmdbEnrichment? {
        val movieId = resolveMovieTmdbId(title, releaseYear, imdbId) ?: return null
        val details = runCatching {
            movieRepository.getMovieDetails(movieId, forceRefresh = false)
        }.getOrNull() ?: return null
        return MovieDetailsEnrichmentMapper.toTmdbEnrichment(details)
    }

    private suspend fun resolveMovieTmdbId(
        title: String,
        releaseYear: Int?,
        imdbId: String?
    ): Long? {
        if (!imdbId.isNullOrBlank()) {
            val match = tmdbService.resolveImdbMatch(imdbId) ?: return null
            return match.first.takeIf { match.second == "movie" }
        }
        return tmdbService.resolveMovieId(title, releaseYear)
    }

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
