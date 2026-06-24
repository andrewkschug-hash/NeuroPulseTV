package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCategoryGuardsTest {

    @Test
    fun isStreamBackedCategoryId_acceptsXtreamTokens() {
        assertTrue(VodCategoryGuards.isStreamBackedCategoryId("1006"))
        assertTrue(VodCategoryGuards.isStreamBackedCategoryId("vod-action"))
    }

    @Test
    fun isStreamBackedCategoryId_rejectsGenreLabels() {
        assertFalse(VodCategoryGuards.isStreamBackedCategoryId("Action & Adventure"))
        assertFalse(VodCategoryGuards.isStreamBackedCategoryId("Sci-Fi / Drama"))
        assertFalse(VodCategoryGuards.isStreamBackedCategoryId(""))
        assertFalse(VodCategoryGuards.isStreamBackedCategoryId(null))
    }

    @Test
    fun filterStreamBacked_dropsInvalidIds() {
        val (valid, dropped) = VodCategoryGuards.partitionStreamBacked(
            listOf(
                VodCategory(id = "1006", name = "Action", playlistId = 1L),
                VodCategory(id = "Action & Adventure", name = "Action", playlistId = 1L)
            )
        )
        assertEquals(1, valid.size)
        assertEquals("1006", valid.single().id)
        assertEquals(1, dropped.size)
    }

    @Test
    fun findDisplayNameCollisions_detectsSameLabelDifferentIds() {
        val collisions = VodCategoryGuards.findDisplayNameCollisions(
            listOf(
                VodCategory(id = "1006", name = "Action", playlistId = 1L),
                VodCategory(id = "1040", name = "Action", playlistId = 1L)
            )
        )
        assertEquals(1, collisions.size)
        assertEquals(setOf("1006", "1040"), collisions.single().categoryIds.toSet())
    }
}
