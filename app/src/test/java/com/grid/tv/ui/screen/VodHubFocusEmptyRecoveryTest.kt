package com.grid.tv.ui.screen

import com.grid.tv.domain.model.VodContentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodHubFocusEmptyRecoveryTest {

    @Test
    fun emptyMoviesGenre_fallsBackToAllGenre() {
        val action = resolveVodFocusEmptyRecovery(
            contentFilter = VodContentFilter.MOVIES,
            genreIndex = 2,
            genreCount = 4,
            moviesBrowseCount = 0,
            seriesBrowseCount = 10,
            wallRowCount = 0,
        )
        assertEquals(VodFocusEmptyRecoveryAction.ApplyGenre(0), action)
    }

    @Test
    fun emptyMoviesAllGenre_switchesToSeries() {
        val action = resolveVodFocusEmptyRecovery(
            contentFilter = VodContentFilter.MOVIES,
            genreIndex = 0,
            genreCount = 4,
            moviesBrowseCount = 0,
            seriesBrowseCount = 10,
            wallRowCount = 0,
        )
        assertEquals(VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.SERIES), action)
    }

    @Test
    fun emptyMoviesCatalogStillLoading_keepsFilter() {
        val action = resolveVodFocusEmptyRecovery(
            contentFilter = VodContentFilter.MOVIES,
            genreIndex = 0,
            genreCount = 4,
            moviesBrowseCount = 0,
            seriesBrowseCount = 0,
            wallRowCount = 16,
            moviesCatalogTotal = 120_000,
            isCatalogLoading = false,
        )
        assertEquals(VodFocusEmptyRecoveryAction.KeepFilter, action)
    }

    @Test
    fun emptyEverything_opensSidebar() {
        val action = resolveVodFocusEmptyRecovery(
            contentFilter = VodContentFilter.ALL,
            genreIndex = 0,
            genreCount = 0,
            moviesBrowseCount = 0,
            seriesBrowseCount = 0,
            wallRowCount = 0,
        )
        assertTrue(action is VodFocusEmptyRecoveryAction.OpenSidebar)
    }
}
