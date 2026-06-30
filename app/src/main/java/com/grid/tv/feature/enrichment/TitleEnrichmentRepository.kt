package com.grid.tv.feature.enrichment

import android.util.Log
import com.grid.tv.data.auth.SupabaseClientProvider
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.mapper.MovieDetailsEnrichmentMapper
import com.grid.tv.data.network.tmdb.TmdbEnrichment
import com.grid.tv.data.network.tmdb.TmdbService
import com.grid.tv.data.network.tmdb.TmdbTitleNormalizer
import com.grid.tv.data.network.tmdb.TmdbYearParser
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodPlaybackMeta
import com.grid.tv.domain.repository.MovieRepository
import com.grid.tv.util.cache.AppCacheRegistry
import com.grid.tv.util.cache.BoundedMemoryCache
import com.grid.tv.util.cache.CacheSizeEstimators
import com.grid.tv.util.PerformanceTelemetryTracker
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TitleEnrichmentRepository @Inject constructor(
    private val dao: TitleEnrichmentDao,
    private val tmdbService: TmdbService,
    private val movieRepository: MovieRepository,
    private val supabaseClientProvider: SupabaseClientProvider,
    appCacheRegistry: AppCacheRegistry
) {
    private val sessionCache = BoundedMemoryCache<String, TitleEnrichmentEntity>(
        name = "title_enrichment_session",
        maxEntries = MAX_SESSION_ENTRIES,
        maxBytes = MAX_SESSION_BYTES,
        valueSizeEstimator = CacheSizeEstimators::titleEnrichment,
        registry = appCacheRegistry
    )
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<TitleEnrichmentEntity?>>()

    companion object {
        private const val TAG = "TitleEnrichment"
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_SESSION_ENTRIES = 400
        const val MAX_SESSION_BYTES = 2L * 1024L * 1024L
        const val MAX_IN_FLIGHT = 64

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
        if (TmdbNegativeCache.isSessionMiss(providerKey)) return null
        sessionCache.get(providerKey)?.takeIf { entity ->
            when {
                TmdbNegativeCache.isNegative(entity) -> TmdbNegativeCache.isNegativeFresh(entity)
                else -> isFresh(entity) && entity.tmdbId != null
            }
        }?.let { return it }
        val cached = dao.get(providerKey) ?: return null
        return when {
            TmdbNegativeCache.isNegative(cached) && TmdbNegativeCache.isNegativeFresh(cached) -> cached
            isFresh(cached) && cached.tmdbId != null -> cached.also { sessionCache.put(providerKey, it) }
            else -> null
        }
    }

    suspend fun getCachedBatch(providerKeys: List<String>): Map<String, TitleEnrichmentEntity> {
        if (providerKeys.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        return providerKeys.mapNotNull { key ->
            if (TmdbNegativeCache.isSessionMiss(key)) return@mapNotNull null
            sessionCache.get(key)?.takeIf { entity ->
                when {
                    TmdbNegativeCache.isNegative(entity) -> TmdbNegativeCache.isNegativeFresh(entity)
                    else -> isFresh(entity) && entity.tmdbId != null
                }
            } ?: dao.get(key)?.takeIf { entity ->
                when {
                    TmdbNegativeCache.isNegative(entity) -> now - entity.updatedAt < TmdbNegativeCache.NEGATIVE_CACHE_TTL_MS
                    else -> now - entity.updatedAt < CACHE_TTL_MS && entity.tmdbId != null
                }
            }
        }.associateBy { it.providerKey }
            .also { batch -> batch.forEach { (key, entity) -> sessionCache.put(key, entity) } }
    }

    suspend fun enrichOnDemand(
        providerKey: String,
        title: String,
        releaseYear: Int? = null,
        isTv: Boolean = false,
        imdbId: String? = null
    ): TitleEnrichmentEntity? {
        if (TmdbNegativeCache.isSessionMiss(providerKey)) return null

        sessionCache.get(providerKey)?.let { cached ->
            if (TmdbNegativeCache.isNegative(cached) && TmdbNegativeCache.isNegativeFresh(cached)) return null
            if (isFresh(cached) && cached.tmdbId != null) return cached
        }

        val existing = dao.get(providerKey)
        if (existing != null) {
            if (TmdbNegativeCache.isNegative(existing) && TmdbNegativeCache.isNegativeFresh(existing)) {
                sessionCache.put(providerKey, existing)
                TmdbNegativeCache.markSessionMiss(providerKey)
                return null
            }
            if (isFresh(existing) && existing.tmdbId != null) {
                sessionCache.put(providerKey, existing)
                return existing
            }
        }

        inFlight[providerKey]?.let { return it.await() }

        val waiter = CompletableDeferred<TitleEnrichmentEntity?>()
        trimInFlightIfNeeded()
        val existingWaiter = inFlight.putIfAbsent(providerKey, waiter)
        if (existingWaiter != null) return existingWaiter.await()

        return try {
            val started = System.currentTimeMillis()
            val searchTitle = TmdbTitleNormalizer.normalizeForSearch(title)
            if (searchTitle.isBlank()) {
                waiter.complete(existing)
                return existing
            }
            val searchYear = TmdbYearParser.parse(title) ?: releaseYear
            val entity = fetchAndPersist(
                providerKey = providerKey,
                title = title,
                searchTitle = searchTitle,
                releaseYear = searchYear,
                isTv = isTv,
                imdbId = imdbId,
                prior = existing
            )
            PerformanceTelemetryTracker.tmdbEnrichment(
                providerKey = providerKey,
                elapsedMs = System.currentTimeMillis() - started,
                hit = entity != null && !TmdbNegativeCache.isNegative(entity)
            )
            waiter.complete(entity)
            entity?.let { sessionCache.put(providerKey, it) }
            entity
        } catch (error: Throwable) {
            Log.w(TAG, "Enrichment failed for $providerKey: ${error.message}")
            waiter.complete(existing)
            existing
        } finally {
            inFlight.remove(providerKey, waiter)
        }
    }

    suspend fun enrichFromPlaybackMeta(meta: VodPlaybackMeta) {
        val title = meta.title?.trim().orEmpty()
        if (title.isBlank()) return
        val providerKey = meta.providerKey() ?: return
        enrichOnDemand(
            providerKey = providerKey,
            title = title,
            releaseYear = TmdbYearParser.parse(title),
            isTv = meta.isTv
        )
    }

    suspend fun enrichContinueWatching(item: ContinueWatchingItem): TitleEnrichmentEntity? =
        enrichOnDemand(
            providerKey = continueWatchingKey(item),
            title = item.title,
            releaseYear = TmdbYearParser.parse(item.title),
            isTv = item.contentType == ContinueWatchingContentType.SERIES
        )

    private fun trimInFlightIfNeeded() {
        if (inFlight.size < MAX_IN_FLIGHT) return
        inFlight.entries.removeIf { it.value.isCompleted }
        if (inFlight.size >= MAX_IN_FLIGHT) {
            inFlight.keys.firstOrNull()?.let { inFlight.remove(it) }
        }
    }

    private suspend fun fetchAndPersist(
        providerKey: String,
        title: String,
        searchTitle: String,
        releaseYear: Int?,
        isTv: Boolean,
        imdbId: String?,
        prior: TitleEnrichmentEntity?
    ): TitleEnrichmentEntity? {
        val enrichment = runCatching {
            fetchEnrichment(searchTitle, title, releaseYear, isTv, imdbId)
        }.getOrNull()
        if (enrichment == null) {
            val negative = TmdbNegativeCache.buildNegativeEntity(
                providerKey = providerKey,
                normalizedTitle = normalizeTitle(searchTitle),
                releaseYear = releaseYear,
                prior = prior,
            )
            dao.upsert(negative)
            sessionCache.put(providerKey, negative)
            TmdbNegativeCache.markSessionMiss(providerKey)
            return null
        }

        val entity = mapEnrichmentEntity(
            providerKey = providerKey,
            normalizedTitle = normalizeTitle(searchTitle),
            releaseYear = releaseYear,
            enrichment = enrichment,
            prior = prior
        )
        dao.upsert(entity)
        return entity
    }

    private suspend fun fetchEnrichment(
        searchTitle: String,
        rawTitle: String,
        releaseYear: Int?,
        isTv: Boolean,
        imdbId: String?
    ): TmdbEnrichment? = withContext(Dispatchers.IO) {
        when {
            !imdbId.isNullOrBlank() -> tmdbService.enrichByImdb(imdbId)
            isTv -> tmdbService.enrichTvFromTitle(rawTitle, releaseYear)
            supabaseClientProvider.isConfigured ->
                enrichMovieViaEdgeFunction(searchTitle, rawTitle, releaseYear, imdbId = null)
            else -> tmdbService.enrichMovieFromTitle(rawTitle, releaseYear)
        }
    }

    private suspend fun enrichMovieViaEdgeFunction(
        searchTitle: String,
        rawTitle: String,
        releaseYear: Int?,
        imdbId: String?
    ): TmdbEnrichment? {
        val movieId = resolveMovieTmdbId(searchTitle, rawTitle, releaseYear, imdbId) ?: return null
        val details = runCatching {
            movieRepository.getMovieDetails(movieId, forceRefresh = false)
        }.getOrNull() ?: return null
        return MovieDetailsEnrichmentMapper.toTmdbEnrichment(details)
    }

    private suspend fun resolveMovieTmdbId(
        searchTitle: String,
        rawTitle: String,
        releaseYear: Int?,
        imdbId: String?
    ): Long? {
        if (!imdbId.isNullOrBlank()) {
            val match = tmdbService.resolveImdbMatch(imdbId) ?: return null
            return match.first.takeIf { match.second == "movie" }
        }
        return tmdbService.resolveMovieId(rawTitle, releaseYear)
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
}
