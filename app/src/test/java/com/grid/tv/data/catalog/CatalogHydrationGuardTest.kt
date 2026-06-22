package com.grid.tv.data.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogHydrationGuardTest {

    @Test
    fun viewportEpgSuspended_defaultsFalse() {
        val guard = CatalogHydrationGuard()
        assertFalse(guard.viewportEpgSuspended)
    }

    @Test
    fun setViewportEpgSuspended_togglesFlag() {
        val guard = CatalogHydrationGuard()
        guard.setViewportEpgSuspended(true)
        assertTrue(guard.viewportEpgSuspended)
        guard.setViewportEpgSuspended(false)
        assertFalse(guard.viewportEpgSuspended)
    }
}
