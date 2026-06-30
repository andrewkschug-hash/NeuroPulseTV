package com.grid.tv.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupNameNormalizerGuideTest {

    @Test
    fun normalize_mapsCountryAliases() {
        assertEquals("United States", GroupNameNormalizer.normalize("USA"))
        assertEquals("United Kingdom", GroupNameNormalizer.normalize("UK"))
        assertEquals("United Kingdom", GroupNameNormalizer.normalize("England"))
    }

    @Test
    fun normalize_mapsCategoryAliases() {
        assertEquals("Movies", GroupNameNormalizer.normalize("Cinema"))
        assertEquals("Sports", GroupNameNormalizer.normalize("Sports"))
    }
}
