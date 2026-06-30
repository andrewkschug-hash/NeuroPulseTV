package com.grid.tv.data.network.tmdb

import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbTitleNormalizerTest {

    @Test
    fun normalizeForSearch_stripsIptvNoise() {
        assertEquals(
            "Inception",
            TmdbTitleNormalizer.normalizeForSearch("EN - Inception (2010) 4K")
        )
    }

    @Test
    fun stripForRetry_removesParentheticalTags() {
        assertEquals(
            "Dune",
            TmdbTitleNormalizer.stripForRetry("Dune (MULTI SUB)")
        )
    }
}
