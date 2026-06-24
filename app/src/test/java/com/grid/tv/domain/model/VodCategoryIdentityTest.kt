package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodCategoryIdentityTest {

    @Test
    fun categoryKey_scopesByPlaylist() {
        assertEquals("1_1006", categoryKey(1L, "1006"))
        assertEquals("2_1006", categoryKey(2L, "1006"))
    }

    @Test
    fun categoryBrowseRowId_roundTripsWithUnderscoresInCategoryId() {
        val rowId = categoryBrowseRowId(3L, "action_adventure")
        assertEquals("cat_3_action_adventure", rowId)
        assertEquals(3L to "action_adventure", parseCategoryBrowseRowId(rowId))
    }

    @Test
    fun parseCategoryBrowseRowId_rejectsLegacyBareIdFormat() {
        assertNull(parseCategoryBrowseRowId("cat_1006"))
    }
}
