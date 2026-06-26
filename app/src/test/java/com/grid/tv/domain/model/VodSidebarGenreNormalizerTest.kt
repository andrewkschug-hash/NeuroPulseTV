package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VodSidebarGenreNormalizerTest {

    @Test
    fun comparisonKey_treatsCaseWhitespaceAndAndVariantsAsEqual() {
        val variants = listOf(
            "ACTION & ADVENTURE",
            "Action & Adventure",
            "Action and Adventure",
            "  Action   and   Adventure  "
        )
        val keys = variants.map { VodSidebarGenreNormalizer.comparisonKey(it) }.toSet()
        assertEquals(1, keys.size)
        assertEquals("action & adventure", keys.single())
    }

    @Test
    fun formatDisplayName_normalizesWhitespaceAndAndSeparator() {
        assertEquals("Action & Adventure", VodSidebarGenreNormalizer.formatDisplayName("Action and Adventure"))
        assertEquals("ACTION & ADVENTURE", VodSidebarGenreNormalizer.formatDisplayName("ACTION & ADVENTURE"))
        assertEquals("Action & Adventure", VodSidebarGenreNormalizer.formatDisplayName("  Action   and   Adventure  "))
    }

    @Test
    fun pickCanonicalDisplayName_prefersMixedCaseOverAllCaps() {
        assertEquals(
            "Action & Adventure",
            VodSidebarGenreNormalizer.pickCanonicalDisplayName(
                listOf("ACTION & ADVENTURE", "Action and Adventure", "Action & Adventure")
            )
        )
    }

    @Test
    fun comparisonKey_doesNotMergeQualitySuffixVariants() {
        val movies = listOf("Movies", "Movies HD", "Movies FHD", "Movies 4K", "MOVIES HD")
        val keys = movies.map { VodSidebarGenreNormalizer.comparisonKey(it) }.toSet()
        assertEquals(4, keys.size)
        assertEquals("movies", VodSidebarGenreNormalizer.comparisonKey("Movies"))
        assertEquals("movies hd", VodSidebarGenreNormalizer.comparisonKey("MOVIES HD"))
        assertEquals("movies fhd", VodSidebarGenreNormalizer.comparisonKey("Movies FHD"))
        assertEquals("movies 4k", VodSidebarGenreNormalizer.comparisonKey("Movies 4K"))
    }

    @Test
    fun primaryComparisonKey_collapsesCompoundGenreLists() {
        val variants = listOf(
            "Action & Adventure / Comedy / Drama",
            "Action & Adventure / Sci-Fi",
            "Action and Adventure, Comedy"
        )
        val keys = variants.map { VodSidebarGenreNormalizer.primaryComparisonKey(it) }.toSet()
        assertEquals(1, keys.size)
    }

    @Test
    fun sidebarSortRank_deprioritizesSubtitleBuckets() {
        assertEquals(0, VodSidebarGenreNormalizer.sidebarSortRank("Action & Adventure"))
        assertEquals(3, VodSidebarGenreNormalizer.sidebarSortRank("HINDI subs"))
    }
}
