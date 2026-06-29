package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCategoryNameResolverPerformanceTest {

    @Test
    fun prepareSeriesCategoriesForSidebar_largeCatalog_completesQuickly() {
        val previousLogHandler = VodSidebarGenreNormalizer.mergeLogHandler
        VodSidebarGenreNormalizer.mergeLogHandler = {}
        try {
            val categories = (1..4_000).map { index ->
                VodCategory(
                    id = index.toString(),
                    name = "Genre ${index % 80}",
                    playlistId = 1L,
                )
            }
            val startNs = System.nanoTime()
            val sidebar = VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(categories)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            assertTrue("partition took ${elapsedMs}ms", elapsedMs < 250)
            assertTrue(sidebar.displayCategories.isNotEmpty())
            assertEquals(80, sidebar.displayCategories.size)
        } finally {
            VodSidebarGenreNormalizer.mergeLogHandler = previousLogHandler
        }
    }

    @Test
    fun prepareMovieCategoriesForSidebar_largeCatalog_completesQuickly() {
        val previousLogHandler = VodSidebarGenreNormalizer.mergeLogHandler
        VodSidebarGenreNormalizer.mergeLogHandler = {}
        try {
            val categories = (1..4_000).map { index ->
                VodCategory(
                    id = index.toString(),
                    name = "Movies ${index % 60}",
                    playlistId = if (index % 2 == 0) 1L else 2L,
                )
            }
            val startNs = System.nanoTime()
            val sidebar = VodCategoryNameResolver.prepareMovieCategoriesForSidebar(categories)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            assertTrue("partition took ${elapsedMs}ms", elapsedMs < 250)
            assertTrue(sidebar.displayCategories.size >= 60)
        } finally {
            VodSidebarGenreNormalizer.mergeLogHandler = previousLogHandler
        }
    }
}
