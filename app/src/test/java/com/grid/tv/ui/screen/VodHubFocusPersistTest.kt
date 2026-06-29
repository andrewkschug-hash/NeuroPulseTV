package com.grid.tv.ui.screen

import com.grid.tv.domain.model.VodContentFilter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class VodHubFocusPersistTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun snapshotRoundTrip_preservesPerFilterBreadcrumbs() {
        val ui = VodHubFocusUiState()
        ui.genreFocusIndex = 2
        ui.rememberGenreFor(VodContentFilter.MOVIES)
        ui.rememberGridFor(
            VodContentFilter.MOVIES,
            VodGridFocusMemory(
                itemIndex = 42,
                contentKey = "1_99",
                scrollIndex = 30,
                scrollOffset = 80,
            )
        )
        ui.rememberWallFor(
            VodContentFilter.ALL,
            VodWallFocusMemory(rowIndex = 1, colIndex = 3, contentKey = "wall_key")
        )

        val snapshot = VodHubPersistedFocusSnapshot(
            contentFilter = VodContentFilter.MOVIES.name,
            filterFocusIndex = 1,
            focusZone = VodFocusZone.CONTENT.name,
            movies = ui.exportPersistedBreadcrumb(VodContentFilter.MOVIES),
            all = ui.exportPersistedBreadcrumb(VodContentFilter.ALL),
        )
        val encoded = json.encodeToString(snapshot)
        val decoded = json.decodeFromString<VodHubPersistedFocusSnapshot>(encoded)

        val hydrated = VodHubFocusUiState()
        hydrated.importPersistedBreadcrumb(VodContentFilter.MOVIES, decoded.movies)
        hydrated.importPersistedBreadcrumb(VodContentFilter.ALL, decoded.all)

        assertEquals(2, hydrated.genreIndexFor(VodContentFilter.MOVIES))
        assertEquals(42, hydrated.gridMemoryFor(VodContentFilter.MOVIES).itemIndex)
        assertEquals("1_99", hydrated.gridMemoryFor(VodContentFilter.MOVIES).contentKey)
        assertEquals(30, hydrated.gridMemoryFor(VodContentFilter.MOVIES).scrollIndex)
        assertEquals(1, hydrated.wallMemoryFor(VodContentFilter.ALL).rowIndex)
        assertEquals("wall_key", hydrated.wallMemoryFor(VodContentFilter.ALL).contentKey)
    }
}
