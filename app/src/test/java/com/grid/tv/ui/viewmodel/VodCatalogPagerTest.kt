package com.grid.tv.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class VodCatalogPagerTest {

    @Test
    fun startsWithSinglePage() {
        val pager = VodCatalogPager<Int>()
        pager.reset((1..100).toList())
        assertEquals(20, pager.currentSlice().size)
        assertEquals(100, pager.totalCount())
    }

    @Test
    fun growsToMaxWindowThenSlides() {
        val pager = VodCatalogPager<Int>()
        pager.reset((1..200).toList())

        repeat(2) { assertEquals(true, pager.loadMore()) }
        assertEquals(60, pager.currentSlice().size)
        assertEquals(1, pager.currentSlice().first())

        assertEquals(true, pager.loadMore())
        assertEquals(60, pager.currentSlice().size)
        assertEquals(21, pager.currentSlice().first())
    }
}
