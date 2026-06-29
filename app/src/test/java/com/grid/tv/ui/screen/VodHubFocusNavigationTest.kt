package com.grid.tv.ui.screen

import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.domain.model.VodContentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class VodHubFocusNavigationTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun setupController(
        contentFilter: VodContentFilter = VodContentFilter.MOVIES,
        browseCount: Int = 12,
        browseIndex: Int = 0,
        filterIndex: Int = 1,
        genreIndex: Int = 2,
        browseCatalogTotal: Int = browseCount,
        browseLoading: Boolean = false,
    ): Pair<VodHubFocusUiState, VodHubFocusController> {
        val ui = VodHubFocusUiState()
        ui.filterFocusIndex = filterIndex
        ui.genreFocusIndex = genreIndex
        ui.rememberGenreFor(contentFilter)
        ui.rememberGridFor(
            contentFilter,
            VodGridFocusMemory(itemIndex = browseIndex, contentKey = "key_$browseIndex")
        )
        ui.focusZone = VodFocusZone.FILTER_PANEL
        var gridIndex = browseIndex
        val controller = VodHubFocusController(ui)
        controller.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = contentFilter,
                searchQuery = "",
                showGenrePanel = true,
                showBrowseGrid = true,
                showInlineSearch = false,
                hasHero = false,
                hasBrowseResults = browseCount > 0,
                genreLabels = listOf("All", "Action", "Comedy", "Drama"),
                wallRows = emptyList(),
                displayWallRows = emptyList(),
                loadedDeferredWallCount = 0,
                deferredWallRowsSize = 0,
                navDrawerOpen = false,
                filterPanelFocusRequester = FocusRequester(),
                genrePanelFocusRequester = FocusRequester(),
                browseGridFocusRequester = FocusRequester(),
                browseEmptyStateFocusRequester = FocusRequester(),
                heroPlayFocusRequester = FocusRequester(),
                inlineSearchFocusRequester = FocusRequester(),
                navDrawerFocusRequester = FocusRequester(),
                browseGridFocusIndex = gridIndex,
                setBrowseGridFocusIndex = { gridIndex = it },
                contentRowIndex = 0,
                setContentRowIndex = {},
                contentColIndex = 0,
                setContentColIndex = {},
                browseGridItemCount = { browseCount },
                browseGridCatalogTotal = { browseCatalogTotal },
                isBrowseGridLoading = { browseLoading },
                browseGridKeyAtIndex = { index -> "key_$index" },
                activeBrowseGridState = { null },
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
        return ui to controller
    }

    @Test
    fun moveFilterHighlight_stepsAcrossTabsWithoutZoneChange() {
        val (ui, controller) = setupController(filterIndex = 1)
        controller.moveFilterHighlight(1)
        assertEquals(2, ui.filterFocusIndex)
        assertEquals(VodFocusZone.FILTER_PANEL, ui.focusZone)
    }

    @Test
    fun focusGenrePanelFromGrid_restoresPerFilterGenreIndex() {
        val (ui, controller) = setupController(genreIndex = 2)
        controller.focusGenrePanelFromGrid()
        assertEquals(VodFocusZone.GENRE_PANEL, ui.focusZone)
        assertEquals(2, ui.genreFocusIndex)
    }

    @Test
    fun focusBrowseGridRestored_withoutGridState_entersContentWithImmediateHighlight() {
        val (ui, controller) = setupController(browseIndex = 5)
        ui.focusZone = VodFocusZone.GENRE_PANEL
        controller.focusBrowseGridRestored()
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
        assertEquals(false, ui.gridFocusPending)
        assertEquals(null, ui.gridRestoreRequest)
        assertEquals(5, ui.gridMemoryFor(VodContentFilter.MOVIES).itemIndex)
    }

    @Test
    fun closeNavDrawerToContentZone_returnsToGenreOnMoviesTab() {
        val (ui, controller) = setupController(genreIndex = 2)
        ui.focusZone = VodFocusZone.NAV_DRAWER
        ui.navDrawerOpen = true
        controller.closeNavDrawerToContentZone()
        assertEquals(VodFocusZone.GENRE_PANEL, ui.focusZone)
        assertEquals(2, ui.genreFocusIndex)
    }

    @Test
    fun focusFilterPanelFromGenre_syncsHighlightToAppliedFilter() {
        val (ui, controller) = setupController(filterIndex = 1)
        ui.focusZone = VodFocusZone.GENRE_PANEL
        controller.focusFilterPanelFromGenre()
        assertEquals(VodFocusZone.FILTER_PANEL, ui.focusZone)
        assertEquals(1, ui.filterFocusIndex)
    }

    @Test
    fun focusBrowseGridRestored_withPagingWarm_awaitsItemsWithoutSwitchingTab() {
        val (ui, controller) = setupController(browseCount = 0, browseCatalogTotal = 120)
        controller.focusBrowseGridRestored()
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
        assertEquals(true, ui.awaitingBrowseGridFocus)
        assertEquals(1, ui.filterFocusIndex)
    }

    @Test
    fun focusBrowseGridEmpty_entersContentWithoutAwaiting() {
        val (ui, controller) = setupController(browseCount = 0, browseCatalogTotal = 0)
        controller.focusBrowseGridEmpty()
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
        assertEquals(false, ui.awaitingBrowseGridFocus)
    }

    @Test
    fun escapeAwaitingBrowseGridFocus_confirmedEmpty_entersGridEmpty() {
        val (ui, controller) = setupController(browseCount = 0, browseCatalogTotal = 0)
        ui.awaitingBrowseGridFocus = true
        controller.escapeAwaitingBrowseGridFocus()
        assertEquals(false, ui.awaitingBrowseGridFocus)
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
    }
}
