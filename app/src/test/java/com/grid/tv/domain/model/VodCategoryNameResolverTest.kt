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
}
