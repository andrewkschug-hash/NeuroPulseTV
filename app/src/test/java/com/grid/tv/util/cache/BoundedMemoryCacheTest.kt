package com.grid.tv.util.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedMemoryCacheTest {

    @Test
    fun evictsLeastRecentlyUsedWhenMaxEntriesExceeded() {
        val cache = BoundedMemoryCache<String, String>(
            name = "test",
            maxEntries = 2
        )
        cache.put("a", "1")
        cache.put("b", "2")
        cache.get("a")
        cache.put("c", "3")
        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))
        assertTrue(cache.statistics().evictions >= 1)
    }

    @Test
    fun evictsWhenByteBudgetExceeded() {
        val cache = BoundedMemoryCache<String, String>(
            name = "bytes",
            maxEntries = 100,
            maxBytes = 50L,
            valueSizeEstimator = { it.length * 10 }
        )
        cache.put("a", "12345")
        cache.put("b", "12345")
        assertTrue(cache.size() <= 1)
        assertTrue(cache.statistics().evictions >= 1)
    }

    @Test
    fun tracksHitsAndMisses() {
        val cache = BoundedMemoryCache<String, Int>(
            name = "stats",
            maxEntries = 4
        )
        cache.put("x", 1)
        cache.get("x")
        cache.get("missing")
        val stats = cache.statistics()
        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
    }
}
