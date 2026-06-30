package com.grid.tv.feature.enrichment

import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbNegativeCacheTest {

    @Test
    fun isNegative_detectsSentinelEntity() {
        val entity = TitleEnrichmentEntity(
            providerKey = "xtream:vod:1:2",
            normalizedTitle = "test",
            tmdbId = TmdbNegativeCache.NEGATIVE_TMDB_ID,
            mediaType = TmdbNegativeCache.NEGATIVE_MEDIA_TYPE,
        )
        assertTrue(TmdbNegativeCache.isNegative(entity))
    }

    @Test
    fun sessionMiss_expiresAfterTtl() {
        val key = "xtream:vod:9:9"
        TmdbNegativeCache.markSessionMiss(key)
        assertTrue(TmdbNegativeCache.isSessionMiss(key))
    }

    @Test
    fun isNegativeFresh_respectsUpdatedAt() {
        val entity = TitleEnrichmentEntity(
            providerKey = "k",
            normalizedTitle = "title",
            tmdbId = TmdbNegativeCache.NEGATIVE_TMDB_ID,
            mediaType = TmdbNegativeCache.NEGATIVE_MEDIA_TYPE,
            updatedAt = System.currentTimeMillis(),
        )
        assertTrue(TmdbNegativeCache.isNegativeFresh(entity))
    }
}
