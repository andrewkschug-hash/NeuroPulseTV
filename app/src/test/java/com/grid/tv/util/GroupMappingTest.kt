package com.grid.tv.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMappingTest {

    @Test
    fun `adult groups map to dedicated parent at bottom tier`() {
        assertEquals(PARENT_GROUP_ADULT, resolveParentGroup("US ❖ XXX"))
        assertEquals(PARENT_GROUP_ADULT, resolveParentGroup("UK ❖ Adult"))
        assertEquals(PARENT_GROUP_ADULT, resolveParentGroup("For Adult Channels"))
        assertEquals(PARENT_GROUP_ADULT, resolveParentGroup("18+ ONLY"))
        assertEquals(PARENT_GROUP_ADULT, resolveParentGroup("Pink TV"))
        assertEquals(PARENT_GROUP_ADULT, resolveParentGroup("CA ❖ Porn"))
    }

    @Test
    fun `adult parent sorts after other categories`() {
        assert(parentGroupSortIndex(PARENT_GROUP_ADULT) > parentGroupSortIndex("Other"))
        assert(parentGroupSortIndex(PARENT_GROUP_ADULT) > parentGroupSortIndex("USA"))
    }

    @Test
    fun `non-adult groups still map to regions`() {
        assertEquals("Africa", resolveParentGroup("AFR|"))
        assertEquals("Europe", resolveParentGroup("AL|"))
        assertEquals("Americas", resolveParentGroup("AM|"))
        assertEquals("Europe", resolveParentGroup("EU ❖ Entertainment"))
        assertEquals("UK", resolveParentGroup("UK ❖ General"))
        assertEquals("USA", resolveParentGroup("US ❖ General"))
        assertEquals("Canada", resolveParentGroup("CA ❖ Sports"))
        assertEquals("Australia", resolveParentGroup("AU|"))
        assertEquals("New Zealand", resolveParentGroup("NZ|"))
        assertEquals("Middle East", resolveParentGroup("ME|"))
    }

    @Test
    fun `keyword groups map to semantic parents`() {
        assertEquals("Sports", resolveParentGroup("SPORT HD"))
        assertEquals("Sports", resolveParentGroup("FOOTBALL"))
        assertEquals("News", resolveParentGroup("WORLD NEWS"))
        assertEquals("Movies", resolveParentGroup("CINEMA"))
        assertEquals("Kids", resolveParentGroup("CARTOON NETWORK"))
    }

    @Test
    fun `special tags map to dedicated parents`() {
        assertEquals("24/7 Channels", resolveParentGroup("24/7"))
        assertEquals("4K Channels", resolveParentGroup("4K"))
    }

    @Test
    fun `unknown groups fall back to Other`() {
        assertEquals("Other", resolveParentGroup("MISC"))
        assertEquals("Other", resolveParentGroup(""))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals("Sports", resolveParentGroup("sport"))
        assertEquals("4K Channels", resolveParentGroup("4k"))
    }
}
