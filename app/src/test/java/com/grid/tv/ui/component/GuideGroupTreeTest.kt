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
        val child = rows.last() as GuideGroupVisibleRow.Group
        assertEquals("AFR|", child.fullName)
    }
}
