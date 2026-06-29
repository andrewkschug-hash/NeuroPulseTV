package com.grid.tv.ui.screen

import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.feature.vod.VodHubFocusContentMode
import com.grid.tv.ui.component.VodHubLanguageFilterFocusIndex
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
        showGenrePanel: Boolean = true,
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
                showGenrePanel = showGenrePanel,
                showLibraryNavPanel = true,
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
                languageSubmenuFocusRequester = FocusRequester(),
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
                browseGridItemCount = { browseCount },
                browseGridCatalogTotal = { browseCatalogTotal },
                focusContentMode = {
                    when {
                        browseCount > 0 -> VodHubFocusContentMode.Ready
                        browseCatalogTotal > 0 || browseLoading -> VodHubFocusContentMode.Loading
                        else -> VodHubFocusContentMode.Empty
                    }
                },
                isBrowseGridLoading = { browseLoading },
                browseGridKeyAtIndex = { index -> "key_$index" },
                activeBrowseGridState = { null },
                syncFocusedWallItemKey = {},
                commitFilterHighlight = {},
                applyGenre = {},
                activateWallItem = {},
                openLanguagePreferenceDialog = {},
                refreshAvailableLanguages = {},
                togglePreferredLanguage = {},
                languageFilterActive = false,
                availableLanguages = emptyList(),
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
    fun moveLibraryNavHighlight_stepsVerticallyWithoutZoneChange() {
        val (ui, controller) = setupController(filterIndex = 1)
        controller.moveLibraryNavHighlight(1)
        assertEquals(2, ui.filterFocusIndex)
        assertEquals(VodFocusZone.FILTER_PANEL, ui.focusZone)
    }

    @Test
    fun moveFilterHighlight_stepsAcrossTabsWithoutZoneChange() {
        val (ui, controller) = setupController(filterIndex = 1)
        controller.moveFilterHighlight(1)
        assertEquals(2, ui.filterFocusIndex)
        assertEquals(VodFocusZone.FILTER_PANEL, ui.focusZone)
    }

    @Test
    fun routeLibraryNavRight_onMoviesOrSeriesTab_entersGenrePanel() {
        val (ui, controller) = setupController(
            contentFilter = VodContentFilter.SERIES,
            filterIndex = 2,
            genreIndex = 1,
        )
        controller.routeLibraryNavRight()
        assertEquals(VodFocusZone.GENRE_PANEL, ui.focusZone)
        assertEquals(1, ui.genreFocusIndex)
    }

    @Test
    fun routeLibraryNavRight_onHomeTab_skipsGenrePanel() {
        val ui = VodHubFocusUiState()
        ui.filterFocusIndex = 0
        ui.focusZone = VodFocusZone.FILTER_PANEL
        var gridIndex = 0
        val controller = VodHubFocusController(ui)
        controller.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = VodContentFilter.ALL,
                searchQuery = "",
                showGenrePanel = false,
                showLibraryNavPanel = true,
                showBrowseGrid = false,
                showInlineSearch = false,
                hasHero = false,
                hasBrowseResults = false,
                genreLabels = emptyList(),
                wallRows = emptyList(),
                displayWallRows = emptyList(),
                loadedDeferredWallCount = 0,
                deferredWallRowsSize = 0,
                navDrawerOpen = false,
                filterPanelFocusRequester = FocusRequester(),
                genrePanelFocusRequester = FocusRequester(),
                languageSubmenuFocusRequester = FocusRequester(),
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
                browseGridItemCount = { 0 },
                browseGridCatalogTotal = { 0 },
                focusContentMode = { VodHubFocusContentMode.Empty },
                isBrowseGridLoading = { false },
                browseGridKeyAtIndex = { null },
                activeBrowseGridState = { null },
                syncFocusedWallItemKey = {},
                commitFilterHighlight = {},
                applyGenre = {},
                activateWallItem = {},
                openLanguagePreferenceDialog = {},
                refreshAvailableLanguages = {},
                togglePreferredLanguage = {},
                languageFilterActive = false,
                availableLanguages = emptyList(),
                openNavDrawer = {},
                closeNavDrawer = {},
                selectVodDrawerItem = {},
                focusInlineSearchField = {},
                focusSearchResults = {},
                moviesBrowseGridActivate = {},
                seriesBrowseGridActivate = {},
                ensureValidFocus = {},
            )
        )
        controller.routeLibraryNavRight()
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
    }

    @Test
    fun focusGenrePanelFromGrid_restoresPerFilterGenreIndex() {
        val (ui, controller) = setupController(genreIndex = 2)
        controller.focusGenrePanelFromGrid()
        assertEquals(VodFocusZone.GENRE_PANEL, ui.focusZone)
        assertEquals(2, ui.genreFocusIndex)
    }

    @Test
    fun focusContentFromFilters_entersGridAtOrigin() {
        val (ui, controller) = setupController(browseIndex = 5)
        ui.focusZone = VodFocusZone.FILTER_PANEL
        controller.focusContentFromFilters()
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
        assertEquals(0, ui.gridMemoryFor(VodContentFilter.MOVIES).itemIndex)
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
    fun handleBrowseGridLeadingEdgeLeft_withoutGenrePanel_opensLibraryNav() {
        val (ui, controller) = setupController(
            contentFilter = VodContentFilter.MOVIES,
            browseIndex = 0,
            filterIndex = 1,
            showGenrePanel = false,
        )
        ui.focusZone = VodFocusZone.CONTENT
        ui.browseGridColumnCount = 4
        controller.handleBrowseGridLeadingEdgeLeft()
        assertEquals(VodFocusZone.FILTER_PANEL, ui.focusZone)
        assertEquals(1, ui.filterFocusIndex)
    }

    @Test
    fun handleBrowseGridLeadingEdgeLeft_withGenrePanel_entersGenrePanel() {
        val (ui, controller) = setupController(
            contentFilter = VodContentFilter.MOVIES,
            browseIndex = 4,
            genreIndex = 2,
        )
        ui.focusZone = VodFocusZone.CONTENT
        ui.browseGridColumnCount = 4
        controller.handleBrowseGridLeadingEdgeLeft()
        assertEquals(VodFocusZone.GENRE_PANEL, ui.focusZone)
        assertEquals(2, ui.genreFocusIndex)
    }

    @Test
    fun routeLibraryNavRight_onLanguagesWithoutFilter_opensLanguageSubmenu() {
        val (ui, controller) = setupController(
            filterIndex = VodHubLanguageFilterFocusIndex,
            showGenrePanel = false,
        )
        ui.focusZone = VodFocusZone.FILTER_PANEL
        controller.routeLibraryNavRight()
        assertEquals(VodFocusZone.LANGUAGE_SUBMENU, ui.focusZone)
        assertEquals(VodHubLanguageFilterFocusIndex, ui.filterFocusIndex)
    }

    @Test
    fun routeLibraryNavRight_onLanguagesWithFilter_skipsToContent() {
        val (ui, controller) = setupController(
            filterIndex = VodHubLanguageFilterFocusIndex,
            showGenrePanel = false,
        )
        val deps = VodHubFocusDeps(
            scope = scope,
            contentFilter = VodContentFilter.MOVIES,
            searchQuery = "",
            showGenrePanel = false,
            showLibraryNavPanel = true,
            showBrowseGrid = true,
            showInlineSearch = false,
            hasHero = false,
            hasBrowseResults = true,
            genreLabels = emptyList(),
            wallRows = emptyList(),
            displayWallRows = emptyList(),
            loadedDeferredWallCount = 0,
            deferredWallRowsSize = 0,
            navDrawerOpen = false,
            filterPanelFocusRequester = FocusRequester(),
            genrePanelFocusRequester = FocusRequester(),
            languageSubmenuFocusRequester = FocusRequester(),
            browseGridFocusRequester = FocusRequester(),
            browseEmptyStateFocusRequester = FocusRequester(),
            rootFocusRequester = FocusRequester(),
            heroPlayFocusRequester = FocusRequester(),
            inlineSearchFocusRequester = FocusRequester(),
            navDrawerFocusRequester = FocusRequester(),
            browseGridFocusIndex = 0,
            setBrowseGridFocusIndex = {},
            contentRowIndex = 0,
            setContentRowIndex = {},
            contentColIndex = 0,
            setContentColIndex = {},
            browseGridItemCount = { 12 },
            browseGridCatalogTotal = { 12 },
            focusContentMode = { VodHubFocusContentMode.Ready },
            isBrowseGridLoading = { false },
            browseGridKeyAtIndex = { index -> "key_$index" },
            activeBrowseGridState = { null },
            syncFocusedWallItemKey = {},
            commitFilterHighlight = {},
            applyGenre = {},
            activateWallItem = {},
            openLanguagePreferenceDialog = {},
            refreshAvailableLanguages = {},
            togglePreferredLanguage = {},
            languageFilterActive = true,
            availableLanguages = listOf("EN"),
            openNavDrawer = {},
            closeNavDrawer = {},
            selectVodDrawerItem = {},
            focusInlineSearchField = {},
            focusSearchResults = {},
            moviesBrowseGridActivate = {},
            seriesBrowseGridActivate = {},
            ensureValidFocus = {},
        )
        controller.bind(deps)
        ui.focusZone = VodFocusZone.FILTER_PANEL
        ui.filterFocusIndex = VodHubLanguageFilterFocusIndex
        controller.routeLibraryNavRight()
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
    }

    private fun sampleWallRows(): List<VodWallRow> {
        val movie = VodItem(
            id = 1L,
            streamId = 1L,
            playlistId = 1L,
            title = "Movie",
            streamUrl = "http://test",
            posterUrl = null,
            plot = null,
            cast = null,
            director = null,
            categoryId = null,
            rating = null,
            duration = null,
            genre = null,
        )
        return listOf(
            VodWallRow(
                id = "continue_watching",
                title = "Continue Watching",
                items = listOf(VodWallItem.MovieItem(movie)),
            ),
            VodWallRow(
                id = "movie_recent",
                title = "Recently Added",
                items = listOf(VodWallItem.MovieItem(movie.copy(streamId = 2L))),
            ),
        )
    }

    @Test
    fun focusWallContentRestored_entersContentAtSavedWallPosition() {
        val ui = VodHubFocusUiState()
        ui.rememberWallFor(
            VodContentFilter.ALL,
            VodWallFocusMemory(rowIndex = 1, colIndex = 0, contentKey = "movie_1_2"),
        )
        var rowIndex = 0
        var colIndex = 0
        val wallRows = sampleWallRows()
        val controller = VodHubFocusController(ui)
        controller.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = VodContentFilter.ALL,
                searchQuery = "",
                showGenrePanel = false,
                showLibraryNavPanel = true,
                showBrowseGrid = false,
                showInlineSearch = false,
                hasHero = false,
                hasBrowseResults = false,
                genreLabels = emptyList(),
                wallRows = wallRows,
                displayWallRows = wallRows,
                loadedDeferredWallCount = 0,
                deferredWallRowsSize = 0,
                navDrawerOpen = false,
                filterPanelFocusRequester = FocusRequester(),
                genrePanelFocusRequester = FocusRequester(),
                languageSubmenuFocusRequester = FocusRequester(),
                browseGridFocusRequester = FocusRequester(),
                browseEmptyStateFocusRequester = FocusRequester(),
                rootFocusRequester = FocusRequester(),
                heroPlayFocusRequester = FocusRequester(),
                inlineSearchFocusRequester = FocusRequester(),
                navDrawerFocusRequester = FocusRequester(),
                browseGridFocusIndex = 0,
                setBrowseGridFocusIndex = {},
                contentRowIndex = rowIndex,
                setContentRowIndex = { rowIndex = it },
                contentColIndex = colIndex,
                setContentColIndex = { colIndex = it },
                browseGridItemCount = { 0 },
                browseGridCatalogTotal = { 0 },
                focusContentMode = { VodHubFocusContentMode.Ready },
                isBrowseGridLoading = { false },
                browseGridKeyAtIndex = { null },
                activeBrowseGridState = { null },
                syncFocusedWallItemKey = {},
                commitFilterHighlight = {},
                applyGenre = {},
                activateWallItem = {},
                openLanguagePreferenceDialog = {},
                refreshAvailableLanguages = {},
                togglePreferredLanguage = {},
                languageFilterActive = false,
                availableLanguages = emptyList(),
                openNavDrawer = {},
                closeNavDrawer = {},
                selectVodDrawerItem = {},
                focusInlineSearchField = {},
                focusSearchResults = {},
                moviesBrowseGridActivate = {},
                seriesBrowseGridActivate = {},
                ensureValidFocus = {},
            )
        )
        controller.focusWallContentRestored(resetToOrigin = false)
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
        assertEquals(1, rowIndex)
        assertEquals(0, colIndex)
    }

    @Test
    fun returnToContentFromLibraryNav_onHomeWall_resetsToFirstItem() {
        val ui = VodHubFocusUiState()
        ui.focusZone = VodFocusZone.FILTER_PANEL
        var rowIndex = 1
        var colIndex = 2
        val wallRows = sampleWallRows()
        val controller = VodHubFocusController(ui)
        controller.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = VodContentFilter.ALL,
                searchQuery = "",
                showGenrePanel = false,
                showLibraryNavPanel = true,
                showBrowseGrid = false,
                showInlineSearch = false,
                hasHero = false,
                hasBrowseResults = false,
                genreLabels = emptyList(),
                wallRows = wallRows,
                displayWallRows = wallRows,
                loadedDeferredWallCount = 0,
                deferredWallRowsSize = 0,
                navDrawerOpen = false,
                filterPanelFocusRequester = FocusRequester(),
                genrePanelFocusRequester = FocusRequester(),
                languageSubmenuFocusRequester = FocusRequester(),
                browseGridFocusRequester = FocusRequester(),
                browseEmptyStateFocusRequester = FocusRequester(),
                rootFocusRequester = FocusRequester(),
                heroPlayFocusRequester = FocusRequester(),
                inlineSearchFocusRequester = FocusRequester(),
                navDrawerFocusRequester = FocusRequester(),
                browseGridFocusIndex = 0,
                setBrowseGridFocusIndex = {},
                contentRowIndex = rowIndex,
                setContentRowIndex = { rowIndex = it },
                contentColIndex = colIndex,
                setContentColIndex = { colIndex = it },
                browseGridItemCount = { 0 },
                browseGridCatalogTotal = { 0 },
                focusContentMode = { VodHubFocusContentMode.Ready },
                isBrowseGridLoading = { false },
                browseGridKeyAtIndex = { null },
                activeBrowseGridState = { null },
                syncFocusedWallItemKey = {},
                commitFilterHighlight = {},
                applyGenre = {},
                activateWallItem = {},
                openLanguagePreferenceDialog = {},
                refreshAvailableLanguages = {},
                togglePreferredLanguage = {},
                languageFilterActive = false,
                availableLanguages = emptyList(),
                openNavDrawer = {},
                closeNavDrawer = {},
                selectVodDrawerItem = {},
                focusInlineSearchField = {},
                focusSearchResults = {},
                moviesBrowseGridActivate = {},
                seriesBrowseGridActivate = {},
                ensureValidFocus = {},
            )
        )
        controller.returnToContentFromLibraryNav(resetOrigin = true)
        assertEquals(VodFocusZone.CONTENT, ui.focusZone)
        assertEquals(0, rowIndex)
        assertEquals(0, colIndex)
    }
}
