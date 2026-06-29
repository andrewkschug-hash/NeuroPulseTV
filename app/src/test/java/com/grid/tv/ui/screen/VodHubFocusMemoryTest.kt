package com.grid.tv.ui.screen

import com.grid.tv.domain.model.VodContentFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class VodHubFocusMemoryTest {

    @Test
    fun resolveBrowseGridFocusIndex_findsItemByStableKey() {
        val keys = listOf("1_10", "1_20", "1_30", "1_42")
        val memory = VodGridFocusMemory(
            itemIndex = 0,
            contentKey = "1_42",
            scrollIndex = 30,
            scrollOffset = 120
        )
        val resolved = resolveBrowseGridFocusIndex(
            itemCount = keys.size,
            saved = memory,
            keyAtIndex = { index -> keys.getOrNull(index) }
        )
        assertEquals(3, resolved)
    }

    @Test
    fun resolveBrowseGridFocusIndex_fallsBackToSavedIndexWhenKeyMissing() {
        val memory = VodGridFocusMemory(
            itemIndex = 5,
            contentKey = "gone_item",
            scrollIndex = 2,
            scrollOffset = 0
        )
        val resolved = resolveBrowseGridFocusIndex(
            itemCount = 10,
            saved = memory,
            keyAtIndex = { index -> "item_$index" }
        )
        assertEquals(5, resolved)
    }

    @Test
    fun resolveBrowseGridFocusIndex_matchesByPlaylistAndItemId() {
        val keys = listOf("2_10", "2_20", "3_42")
        val memory = VodGridFocusMemory(
            itemIndex = 0,
            contentKey = "1_42",
            scrollIndex = 0,
            scrollOffset = 0,
        )
        val resolved = resolveBrowseGridFocusIndex(
            itemCount = keys.size,
            saved = memory,
            keyAtIndex = { index -> keys.getOrNull(index) },
        )
        assertEquals(2, resolved)
    }

    @Test
    fun resolveBrowseGridFocusIndex_fallsBackToFirstVisibleWhenIndexInvalid() {
        val memory = VodGridFocusMemory(
            itemIndex = 99,
            contentKey = "missing",
            scrollIndex = 0,
            scrollOffset = 0,
        )
        val resolved = resolveBrowseGridFocusIndex(
            itemCount = 5,
            saved = memory,
            keyAtIndex = { index -> if (index == 4) null else "k_$index" },
            firstVisibleIndex = 2,
        )
        assertEquals(2, resolved)
    }

    @Test
    fun perFilterBreadcrumbs_keepIndependentGenreAndGridState() {
        val ui = VodHubFocusUiState()
        ui.genreFocusIndex = 2
        ui.rememberGenreFor(VodContentFilter.MOVIES)
        ui.rememberGridFor(
            VodContentFilter.MOVIES,
            VodGridFocusMemory(itemIndex = 42, contentKey = "m_42")
        )
        ui.genreFocusIndex = 3
        ui.rememberGenreFor(VodContentFilter.SERIES)
        ui.rememberGridFor(
            VodContentFilter.SERIES,
            VodGridFocusMemory(itemIndex = 5, contentKey = "s_5")
        )

        assertEquals(2, ui.genreIndexFor(VodContentFilter.MOVIES))
        assertEquals(42, ui.gridMemoryFor(VodContentFilter.MOVIES).itemIndex)
        assertEquals(3, ui.genreIndexFor(VodContentFilter.SERIES))
        assertEquals(5, ui.gridMemoryFor(VodContentFilter.SERIES).itemIndex)
    }
}
