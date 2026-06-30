package com.grid.tv.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTitleNormalizerTest {

    @Test
    fun normalize_stripsLanguageAndQualityTags() {
        assertEquals(
            "inception",
            SearchTitleNormalizer.normalize("EN - Inception (2024) (MULTI SUB)")
        )
        assertEquals(
            "dune",
            SearchTitleNormalizer.normalize("4K - Dune (2021) HEVC")
        )
    }

    @Test
    fun toSqlPrefixPattern_returnsEmptyForBlank() {
        assertEquals("", SearchTitleNormalizer.toSqlPrefixPattern("   "))
    }

    @Test
    fun toSqlPrefixPattern_returnsIndexedPrefix() {
        assertEquals("espn%", SearchTitleNormalizer.toSqlPrefixPattern("EN - ESPN HD"))
    }
}
