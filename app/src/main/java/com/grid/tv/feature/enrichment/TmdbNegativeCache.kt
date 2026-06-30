package com.grid.tv.feature.enrichment

import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import java.util.concurrent.ConcurrentHashMap

internal object TmdbNegativeCache {
    const val NEGATIVE_TMDB_ID = -1L
    const val NEGATIVE_MEDIA_TYPE = "not_found"
    const val NEGATIVE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    const val SESSION_NEGATIVE_TTL_MS = 30L * 60 * 1000

    private val sessionMissUntil = ConcurrentHashMap<String, Long>()

    fun isNegative(entity: TitleEnrichmentEntity?): Boolean =
        entity?.tmdbId == NEGATIVE_TMDB_ID || entity?.mediaType == NEGATIVE_MEDIA_TYPE

    fun isNegativeFresh(entity: TitleEnrichmentEntity): Boolean =
        System.currentTimeMillis() - entity.updatedAt < NEGATIVE_CACHE_TTL_MS

    fun markSessionMiss(providerKey: String) {
        sessionMissUntil[providerKey] = System.currentTimeMillis() + SESSION_NEGATIVE_TTL_MS
    }

    fun isSessionMiss(providerKey: String): Boolean {
        val until = sessionMissUntil[providerKey] ?: return false
        if (System.currentTimeMillis() > until) {
            sessionMissUntil.remove(providerKey)
            return false
        }
        return true
    }

    fun buildNegativeEntity(
        providerKey: String,
        normalizedTitle: String,
        releaseYear: Int?,
        prior: TitleEnrichmentEntity?,
    ): TitleEnrichmentEntity = TitleEnrichmentEntity(
        providerKey = providerKey,
        normalizedTitle = normalizedTitle,
        releaseYear = releaseYear,
        tmdbId = NEGATIVE_TMDB_ID,
        mediaType = NEGATIVE_MEDIA_TYPE,
        title = prior?.title,
        updatedAt = System.currentTimeMillis(),
    )
}
