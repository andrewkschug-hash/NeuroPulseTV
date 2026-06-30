package com.grid.tv.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideGroupTreeTest {

    @Test
    fun `buildGuideGroupCategories groups raw tags under parent labels`() {
        val categories = buildGuideGroupCategories(
            channelGroups = listOf("AFR|", "AL|", "SPORT HD", "24/7", "4K"),
            groupChannelCounts = mapOf(
                "AFR|" to 10,
                "AL|" to 5,
                "SPORT HD" to 20
            )
        )

        assertEquals("Africa", categories.first { it.displayName == "Africa" }.displayName)
        assertEquals(listOf("AFR|"), categories.first { it.displayName == "Africa" }.groups)
        assertEquals(10, categories.first { it.displayName == "Africa" }.channelCount)
        assertEquals("Sports", categories.first { it.displayName == "Sports" }.displayName)
        assertEquals(20, categories.first { it.displayName == "Sports" }.channelCount)
    }

    @Test
    fun `toggleCategoryExpansion keeps only one parent open`() {
        assertEquals(setOf(1), toggleCategoryExpansion(setOf(0), 1))
        assertEquals(emptySet<Int>(), toggleCategoryExpansion(setOf(1), 1))
    }

    @Test
    fun `buildVisibleGuideGroupRows nests raw groups under expanded parent`() {
        val categories = buildGuideGroupCategories(listOf("AFR|", "AL|"))
        val rows = buildVisibleGuideGroupRows(categories, expandedCategories = setOf(0))

        assertEquals(GuideGroupVisibleRow.AllChannels, rows.first())
        val category = rows[1] as GuideGroupVisibleRow.Category
        assertEquals("Africa", category.displayName)
        val child = rows.filterIsInstance<GuideGroupVisibleRow.Group>().first()
        assertEquals("AFR|", child.fullName)
    }

    @Test
    fun `buildFlatProviderVisibleRows lists provider groups in display order`() {
        val rows = buildFlatProviderVisibleRows(listOf("UK| SPORT", "CA| NEWS", "AFR|"))

        assertEquals(GuideGroupVisibleRow.FavoriteSectionHeader, rows[0])
        assertEquals(GuideGroupVisibleRow.FavoriteSectionEmpty, rows[1])
        assertEquals(GuideGroupVisibleRow.AllChannels, rows[2])
        val groups = rows.filterIsInstance<GuideGroupVisibleRow.Group>()
            .filter { it.listSection == GuideGroupVisibleRow.ListSection.Catalog }
            .map { it.fullName }
        assertEquals(listOf("UK| SPORT", "CA| NEWS", "AFR|"), groups)
    }

    @Test
    fun `buildFlatProviderVisibleRows surfaces favourites before all channels`() {
        val rows = buildFlatProviderVisibleRows(
            channelGroups = listOf("Sports", "Movies", "Kids"),
            favoriteGroups = listOf("Sports", "Kids")
        )

        assertEquals(GuideGroupVisibleRow.FavoriteSectionHeader, rows[0])
        assertEquals("Sports", (rows[1] as GuideGroupVisibleRow.Group).fullName)
        assertEquals(GuideGroupVisibleRow.ListSection.Favorites, (rows[1] as GuideGroupVisibleRow.Group).listSection)
        assertEquals("Kids", (rows[2] as GuideGroupVisibleRow.Group).fullName)
        assertEquals(GuideGroupVisibleRow.AllChannels, rows[3])
        val catalogGroups = rows.filterIsInstance<GuideGroupVisibleRow.Group>()
            .filter { it.listSection == GuideGroupVisibleRow.ListSection.Catalog }
            .map { it.fullName }
        assertEquals(listOf("Sports", "Movies", "Kids"), catalogGroups)
    }

    @Test
    fun `firstFocusableFlatGroupRowIndex skips non-focusable favourites header`() {
        assertEquals(2, firstFocusableFlatGroupRowIndex(listOf("Sports"), favoriteGroups = emptyList()))
        assertEquals(1, firstFocusableFlatGroupRowIndex(listOf("Sports"), favoriteGroups = listOf("Sports")))
    }

    @Test
    fun `resolveChannelGroupsFocusIndex keeps current row when it matches committed filter`() {
        val groups = listOf("UK| SPORT", "AFR| ETHIOPIA")
        val focusIndex = resolveChannelGroupsFocusIndex(
            channelGroups = groups,
            favoriteGroups = emptyList(),
            committedFilter = com.grid.tv.feature.epg.GuideChannelFilter(setOf("AFR| ETHIOPIA")),
            currentIndex = 4,
        )
        assertEquals(4, focusIndex)
    }

    @Test
    fun `resolveChannelGroupsFocusIndex restores committed group when current row is stale`() {
        val groups = listOf("UK| SPORT", "AFR| ETHIOPIA")
        val focusIndex = resolveChannelGroupsFocusIndex(
            channelGroups = groups,
            favoriteGroups = emptyList(),
            committedFilter = com.grid.tv.feature.epg.GuideChannelFilter(setOf("AFR| ETHIOPIA")),
            currentIndex = 3,
        )
        assertEquals(4, focusIndex)
    }

    @Test
    fun `visibleRowIndexForSelection expands category containing focus group not first match`() {
        val categories = listOf(
            GuideGroupCategory(displayName = "Africa", groups = listOf("ZAF| SPORT")),
            GuideGroupCategory(displayName = "Europe", groups = listOf("UK| BBC")),
        )
        val selected = setOf("UK| BBC", "ZAF| SPORT")
        val expanded = expandedCategoriesForSelection(categories, selected)
        val index = visibleRowIndexForSelection(categories, expanded, selected)
        val rows = buildVisibleGuideGroupRows(categories, expanded)
        val focusedRow = rows[index]

        assertEquals(setOf(1), expanded)
        assertEquals("UK| BBC", (focusedRow as GuideGroupVisibleRow.Group).fullName)
    }

    @Test
    fun `primarySelectedGroupForFocus uses stable sorted order from encoded filter`() {
        val categories = listOf(
            GuideGroupCategory(displayName = "Africa", groups = listOf("ZAF|")),
            GuideGroupCategory(displayName = "Europe", groups = listOf("UK|")),
        )
        val decoded = com.grid.tv.feature.epg.GuideChannelFilter.decode(
            com.grid.tv.feature.epg.GuideChannelFilter.encode(setOf("ZAF|", "UK|"))
        )
        assertEquals("UK|", primarySelectedGroupForFocus(categories, decoded))
    }

    @Test
    fun `resolveChannelGroupsFocusIndex prefers committed filter over stale last row key`() {
        val groups = listOf("UK| SPORT", "AFR| ETHIOPIA")
        val focusIndex = resolveChannelGroupsFocusIndex(
            channelGroups = groups,
            favoriteGroups = emptyList(),
            committedFilter = com.grid.tv.feature.epg.GuideChannelFilter(setOf("AFR| ETHIOPIA")),
            currentIndex = 2,
            lastRowKey = "all",
        )
        assertEquals(4, focusIndex)
    }
}
