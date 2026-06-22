package com.grid.tv.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GuideViewportFetchKeyTest {

    @Test
    fun viewportEpgFetchKey_isOrderIndependent() {
        val a = HomeEpgViewModel.viewportEpgFetchKey(listOf(3L, 1L, 2L))
        val b = HomeEpgViewModel.viewportEpgFetchKey(listOf(2L, 3L, 1L))
        assertEquals(a, b)
    }

    @Test
    fun viewportEpgFetchKey_changesWhenVisibleSetChanges() {
        val a = HomeEpgViewModel.viewportEpgFetchKey(listOf(1L, 2L, 3L))
        val b = HomeEpgViewModel.viewportEpgFetchKey(listOf(4L, 5L, 6L))
        assertNotEquals(a, b)
    }

    @Test
    fun viewportEpgFetchKey_dedupesDuplicates() {
        val a = HomeEpgViewModel.viewportEpgFetchKey(listOf(1L, 1L, 2L))
        val b = HomeEpgViewModel.viewportEpgFetchKey(listOf(1L, 2L))
        assertEquals(a, b)
    }
}
