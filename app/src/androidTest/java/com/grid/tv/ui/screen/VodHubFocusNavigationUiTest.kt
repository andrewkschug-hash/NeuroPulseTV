package com.grid.tv.ui.screen

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grid.tv.feature.vod.VodHubFocusContentMode
import com.grid.tv.domain.model.VodContentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodHubFocusNavigationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

  private val scope = CoroutineScope(Dispatchers.Main)

    @Test
    fun filterToGenreToGrid_roundTripPreservesPerFilterMemory() {
        val ui = VodHubFocusUiState()
        val controller = VodHubFocusController(ui)
        var gridIndex = 7

        controller.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = VodContentFilter.MOVIES,
                searchQuery = "",
                showGenrePanel = true,
                showBrowseGrid = true,
                showInlineSearch = false,
                hasHero = false,
                hasBrowseResults = true,
                genreLabels = listOf("All", "Comedy", "Action"),
                wallRows = emptyList(),
                displayWallRows = emptyList(),
                loadedDeferredWallCount = 0,
                deferredWallRowsSize = 0,
                navDrawerOpen = false,
                filterPanelFocusRequester = FocusRequester(),
                genrePanelFocusRequester = FocusRequester(),
                browseGridFocusRequester = FocusRequester(),
                browseEmptyStateFocusRequester = FocusRequester(),
                rootFocusRequester = FocusRequester(),
                heroPlayFocusRequester = FocusRequester(),
                inlineSearchFocusRequester = FocusRequester(),
                navDrawerFocusRequester = FocusRequester(),
                browseGridFocusIndex = gridIndex,
                setBrowseGridFocusIndex = { gridIndex = it },
                contentRowIndex = 0,
                setContentRowIndex = {},
                contentColIndex = 0,
                setContentColIndex = {},
                browseGridItemCount = { 20 },
                browseGridCatalogTotal = { 20 },
                focusContentMode = { VodHubFocusContentMode.Ready },
                isBrowseGridLoading = { false },
                browseGridKeyAtIndex = { index -> "1_$index" },
                activeBrowseGridState = { LazyGridState() },
                syncFocusedWallItemKey = {},
                commitFilterHighlight = {},
                applyGenre = {},
                activateWallItem = {},
                openLanguagePreferenceDialog = {},
                openNavDrawer = {
                    ui.navDrawerOpen = true
                    ui.focusZone = VodFocusZone.NAV_DRAWER
                },
                closeNavDrawer = { ui.navDrawerOpen = false },
                selectVodDrawerItem = {},
                focusInlineSearchField = {},
                focusSearchResults = {},
                moviesBrowseGridActivate = {},
                seriesBrowseGridActivate = {},
                ensureValidFocus = {},
            )
        )

        composeRule.runOnUiThread {
            ui.genreFocusIndex = 1
            ui.rememberGenreFor(VodContentFilter.MOVIES)
            ui.rememberGridFor(
                VodContentFilter.MOVIES,
                VodGridFocusMemory(itemIndex = 7, contentKey = "1_7", scrollIndex = 4, scrollOffset = 12)
            )

            controller.focusGenrePanelFromGrid()
            assertEquals(VodFocusZone.GENRE_PANEL, ui.focusZone)

            controller.focusBrowseGridRestored()
            assertEquals(VodFocusZone.CONTENT, ui.focusZone)
            assertEquals(7, ui.gridRestoreRequest?.targetIndex)
            assertEquals(4, ui.gridRestoreRequest?.scrollIndex)

            controller.moveFilterHighlight(1)
            ui.rememberGenreFor(VodContentFilter.SERIES)
            ui.rememberGridFor(
                VodContentFilter.SERIES,
                VodGridFocusMemory(itemIndex = 3, contentKey = "1_3")
            )

            controller.moveFilterHighlight(-1)
            controller.focusBrowseGridRestored()
            assertEquals(7, ui.gridMemoryFor(VodContentFilter.MOVIES).itemIndex)
            assertEquals("1_7", ui.gridMemoryFor(VodContentFilter.MOVIES).contentKey)
        }
    }
}
