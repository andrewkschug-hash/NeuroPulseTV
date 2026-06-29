package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCategoryNameResolverTest {

    @Test
    fun isUnresolvedName_detectsNumericAndIdEchoes() {
        assertTrue(VodCategoryNameResolver.isUnresolvedName("1006", "1006"))
        assertTrue(VodCategoryNameResolver.isUnresolvedName("1006", "1012"))
        assertTrue(VodCategoryNameResolver.isUnresolvedName("1006", "   "))
        assertFalse(VodCategoryNameResolver.isUnresolvedName("1006", "NETFLIX ASIA"))
    }

    @Test
    fun resolveDisplayName_usesLookupTable() {
        val lookup = mapOf(
            "1_1006" to "NETFLIX ASIA",
            "1012" to "Action"
        )
        assertEquals(
            "NETFLIX ASIA",
            VodCategoryNameResolver.resolveDisplayName(
                categoryId = "1006",
                storedName = "1006",
                playlistId = 1L,
                lookupById = lookup
            )
        )
        assertEquals(
            "Action",
            VodCategoryNameResolver.resolveDisplayName(
                categoryId = "1012",
                storedName = "1012",
                playlistId = 2L,
                lookupById = lookup
            )
        )
    }

    @Test
    fun normalizeList_preservesHumanReadableNames() {
        val categories = listOf(
            VodCategory(id = "1006", name = "NETFLIX ASIA", playlistId = 1L),
            VodCategory(id = "1012", name = "1012", playlistId = 1L)
        )
        val normalized = VodCategoryNameResolver.normalizeList(categories)
        assertEquals("NETFLIX ASIA", normalized[0].name)
        assertEquals("1012", normalized[1].name)
    }

    @Test
    fun prepareSeriesCategoriesForSidebar_collapsesSameDisplayNameWithinPlaylist() {
        val categories = listOf(
            VodCategory(id = "1006", name = "Action & Adventure / Sci-Fi", playlistId = 1L),
            VodCategory(id = "1040", name = "Action & Adventure / Sci-Fi", playlistId = 1L),
            VodCategory(id = "1006", name = "Action", playlistId = 2L),
            VodCategory(id = "2001", name = "Comedy", playlistId = 1L)
        )
        val (streamBacked, _) = VodCategoryGuards.partitionStreamBacked(categories)
        val sidebar = VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(streamBacked)
        assertEquals(3, sidebar.displayCategories.size)
        assertEquals(
            setOf("1006", "1040"),
            sidebar.filterIdsByRepresentativeId[VodCategoryNameResolver.categoryKey(1L, "1006")]
                ?: sidebar.filterIdsByRepresentativeId[VodCategoryNameResolver.categoryKey(1L, "1040")]
        )
        assertEquals(
            setOf("1006"),
            sidebar.filterIdsByRepresentativeId[VodCategoryNameResolver.categoryKey(2L, "1006")]
        )
    }

    @Test
    fun mergeSeriesCategorySources_collapsesDuplicatePlaylistCategoryPairs() {
        val stored = VodCategory(id = "1006", name = "1006", playlistId = 1L)
        val streamFallback = VodCategory(id = "1006", name = "1006", playlistId = 1L)
        val genreHint = VodCategory(id = "1006", name = "Action & Adventure", playlistId = 1L)
        val merged = VodCategoryNameResolver.mergeSeriesCategorySources(
            listOf(stored),
            listOf(streamFallback),
            listOf(genreHint)
        )
        assertEquals(1, merged.size)
        assertEquals("1006", merged.single().id)
        assertEquals(1L, merged.single().playlistId)
    }

    @Test
    fun prepareSeriesCategoriesForSidebar_dedupesSameKeyFromMultipleSources() {
        val categories = listOf(
            VodCategory(id = "1006", name = "Action & Adventure", playlistId = 1L),
            VodCategory(id = "1006", name = "1006", playlistId = 1L),
            VodCategory(id = "1006", name = "Action & Adventure", playlistId = 1L)
        )
        val sidebar = VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(categories)
        assertEquals(1, sidebar.displayCategories.size)
        assertEquals("Action & Adventure", sidebar.displayCategories.single().name)
        assertEquals(
            setOf("1006"),
            sidebar.filterIdsByRepresentativeId[VodCategoryNameResolver.categoryKey(1L, "1006")]
        )
    }

    @Test
    fun prepareSeriesCategoriesForSidebar_keepsSeparateEntriesAcrossPlaylistsWithSameLabel() {
        val categories = listOf(
            VodCategory(id = "1006", name = "Action & Adventure", playlistId = 1L),
            VodCategory(id = "1006", name = "Action & Adventure", playlistId = 2L)
        )
        val sidebar = VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(categories)
        assertEquals(2, sidebar.displayCategories.size)
        assertEquals(
            setOf("Action & Adventure"),
            sidebar.displayCategories.map { it.name }.toSet()
        )
    }

    @Test
    fun buildLookupTable_prefersPlaylistScopedKeys() {
        val lookup = VodCategoryNameResolver.buildLookupTable(
            listOf(
                VodCategory(id = "1006", name = "Provider A Action", playlistId = 1L),
                VodCategory(id = "1006", name = "Provider B Action", playlistId = 2L)
            )
        )
        assertEquals("Provider A Action", lookup["1_1006"])
        assertEquals("Provider B Action", lookup["2_1006"])
    }

    @Test
    fun prepareMovieCategoriesForSidebar_collapsesDuplicateLabels() {
        val categories = listOf(
            VodCategory(id = "1006", name = "Action & Adventure", playlistId = 1L),
            VodCategory(id = "1040", name = "Action & Adventure", playlistId = 1L),
            VodCategory(id = "2001", name = "Comedy", playlistId = 1L)
        )
        val sidebar = VodCategoryNameResolver.prepareMovieCategoriesForSidebar(categories)
        assertEquals(2, sidebar.displayCategories.size)
        assertEquals(
            setOf("Action & Adventure", "Comedy"),
            sidebar.displayCategories.map { it.name }.toSet()
        )
        val actionKey = sidebar.displayCategories
            .first { it.name == "Action & Adventure" }
            .let { VodCategoryNameResolver.categoryKey(it.playlistId, it.id) }
        assertEquals(setOf("1006", "1040"), sidebar.filterIdsByRepresentativeId[actionKey])
    }

    @Test
    fun prepareMovieCategoriesForSidebar_prefersCategoryWithHighestItemCount() {
        val categories = listOf(
            VodCategory(id = "713", name = "Action & Adventure", playlistId = 1L),
            VodCategory(id = "1403", name = "Action & Adventure", playlistId = 1L),
        )
        val itemCounts = mapOf(
            VodCategoryNameResolver.categoryKey(1L, "713") to 12,
            VodCategoryNameResolver.categoryKey(1L, "1403") to 480,
        )
        val sidebar = VodCategoryNameResolver.prepareMovieCategoriesForSidebar(categories, itemCounts)
        assertEquals(1, sidebar.displayCategories.size)
        assertEquals("1403", sidebar.displayCategories.single().id)
        assertEquals(
            setOf("713", "1403"),
            sidebar.filterIdsByRepresentativeId[
                VodCategoryNameResolver.categoryKey(1L, "1403")
            ],
        )
    }

    @Test
    fun findDisplayNameCollisions_usesPrimaryGenreToken() {
        val collisions = VodCategoryGuards.findDisplayNameCollisions(
            listOf(
                VodCategory(id = "713", name = "Action & Adventure", playlistId = 1L),
                VodCategory(id = "1403", name = "Action & Adventure / Sci-Fi", playlistId = 1L),
            )
        )
        assertEquals(1, collisions.size)
        assertEquals(setOf("713", "1403"), collisions.single().categoryIds.toSet())
    }

    @Test
    fun prepareMovieCategoriesForSidebar_mergesNormalizedGenreNameVariants() {
        val categories = listOf(
            VodCategory(id = "1006", name = "ACTION & ADVENTURE", playlistId = 1L),
            VodCategory(id = "1040", name = "Action and Adventure", playlistId = 1L),
            VodCategory(id = "1055", name = "  Action   &   Adventure  ", playlistId = 1L),
            VodCategory(id = "2001", name = "Comedy", playlistId = 1L)
        )
        val sidebar = VodCategoryNameResolver.prepareMovieCategoriesForSidebar(categories)
        assertEquals(2, sidebar.displayCategories.size)
        assertEquals("Action & Adventure", sidebar.displayCategories.first { it.name != "Comedy" }.name)
        val actionKey = sidebar.displayCategories
            .first { it.name == "Action & Adventure" }
            .let { VodCategoryNameResolver.categoryKey(it.playlistId, it.id) }
        assertEquals(setOf("1006", "1040", "1055"), sidebar.filterIdsByRepresentativeId[actionKey])
    }

    @Test
    fun prepareMovieCategoriesForSidebar_keepsQualitySuffixCategoriesSeparate() {
        val categories = listOf(
            VodCategory(id = "1001", name = "Movies", playlistId = 1L),
            VodCategory(id = "1002", name = "Movies HD", playlistId = 1L),
            VodCategory(id = "1003", name = "Movies FHD", playlistId = 1L),
            VodCategory(id = "1004", name = "Movies 4K", playlistId = 1L)
        )
        val sidebar = VodCategoryNameResolver.prepareMovieCategoriesForSidebar(categories)
        assertEquals(4, sidebar.displayCategories.size)
        assertEquals(
            setOf("Movies", "Movies HD", "Movies FHD", "Movies 4K"),
            sidebar.displayCategories.map { it.name }.toSet()
        )
    }
}
