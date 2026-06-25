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

    @Test
    fun matchesVodLanguageFilter_includesUntaggedContentByDefault() {
        val englishOnly = setOf("EN")
        assertTrue(matchesVodLanguageFilter("EN - Inception", englishOnly))
        assertTrue(matchesVodLanguageFilter("Plain Title With No Tag", englishOnly))
        assertTrue(matchesVodLanguageFilter("Untitled Movie", englishOnly, categoryName = "Action"))
        assertTrue(
            matchesVodLanguageFilter(
                "Inception",
                englishOnly,
                categoryName = "Latest Movies"
            )
        )
    }

    @Test
    fun matchesVodLanguageFilter_excludesUntaggedContentWhenStrict() {
        val englishOnly = setOf("EN")
        assertTrue(!matchesVodLanguageFilter("Plain Title With No Tag", englishOnly, includeUntagged = false))
        assertTrue(!matchesVodLanguageFilter("Untitled Movie", englishOnly, categoryName = "Action", includeUntagged = false))
        assertTrue(
            !matchesVodLanguageFilter(
                "Inception",
                englishOnly,
                categoryName = "Latest Movies",
                includeUntagged = false
            )
        )
    }

    @Test
    fun matchesVodLanguageFilter_excludesMismatchedTaggedContent() {
        val englishOnly = setOf("EN")
        assertTrue(!matchesVodLanguageFilter("FR - Amélie", englishOnly))
        assertTrue(!matchesVodLanguageFilter("Plain Title", englishOnly, categoryName = "FR | Drama"))
        assertTrue(!matchesVodLanguageFilter("[DE] Dark", englishOnly))
        assertTrue(!matchesVodLanguageFilter("[DE] Dark", englishOnly, includeUntagged = false))
    }

    @Test
    fun matchesVodLanguageFilter_hybridExamples() {
        val options = VodLanguageFilterOptions(setOf("EN"), includeUntagged = true)
        assertTrue(matchesVodLanguageFilter("[EN] Top Gun", options))
        assertTrue(!matchesVodLanguageFilter("[FR] Amélie", options))
        assertTrue(!matchesVodLanguageFilter("[DE] Dark", options))
        assertTrue(matchesVodLanguageFilter("Top Gun Maverick", options))

        val strict = options.copy(includeUntagged = false)
        assertTrue(!matchesVodLanguageFilter("Top Gun Maverick", strict))
    }
}
