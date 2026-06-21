package com.grid.tv.feature.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodContentLanguageTest {

    @Test
    fun parseVodContentLanguageCode_readsPrefixSuffixAndCategoryPatterns() {
        assertEquals("EN", parseVodContentLanguageCode("EN - Inception"))
        assertEquals("FR", parseVodContentLanguageCode("FR - Amélie"))
        assertEquals("DE", parseVodContentLanguageCode("4K - 1899 (2022) (DE)"))
        assertEquals("FR", parseVodContentLanguageCode("56 jours (US) - FR"))
        assertEquals("AR", parseVodContentLanguageCode("|AR| Breaking Bad"))
        assertEquals("EN", parseVodContentLanguageCode("EN | Latest Movies"))
        assertEquals("FR", parseVodContentLanguageCode("[FR] Series Name"))
    }

    @Test
    fun parseVodContentLanguageCode_ignoresResolutionCodes() {
        assertNull(parseVodContentLanguageCode("4K - Dune"))
        assertNull(parseVodContentLanguageCode("HD - Movie"))
    }

    @Test
    fun discoverLanguageCodesFromLabels_collectsDistinctSortedCodes() {
        val codes = discoverLanguageCodesFromLabels(
            sequenceOf(
                "EN - Movie A",
                "FR - Movie B",
                "EN - Movie C",
                "FR | Series",
                "Plain Title"
            )
        )
        assertEquals(listOf("EN", "FR"), codes)
    }

    @Test
    fun vodContentLanguageCode_fallsBackToCategoryName() {
        assertEquals(
            "FR",
            vodContentLanguageCode("Inception", categoryName = "FR | Action")
        )
    }
}
