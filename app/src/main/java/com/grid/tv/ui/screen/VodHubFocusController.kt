package com.grid.tv.ui.screen

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.feature.vod.VodHubFocusContentMode
import com.grid.tv.feature.vod.VodHubSurfaceStateResolver
import com.grid.tv.feature.vod.resolveVodWallFocus
import com.grid.tv.ui.component.GuideNavDrawerItem
import com.grid.tv.ui.component.SidebarContentFocus
import com.grid.tv.ui.component.SidebarContentFocus.isLeadingGridColumn
import com.grid.tv.ui.component.SidebarContentFocus.shouldEnterSidebarFromContent
import com.grid.tv.ui.component.SidebarContentFocus.sidebarHorizontalResult
import com.grid.tv.ui.component.GuideNavDrawerItems
import com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import com.grid.tv.ui.component.VodHubLanguageFilterFocusIndex
import com.grid.tv.ui.component.VodHubTabFilters
import com.grid.tv.ui.component.guideNavDrawerItemFocusIndex
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.vodHubTabFilterIndex
import com.grid.tv.ui.screen.VodHubFocusLogger
import com.grid.tv.util.TvTextInputSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun isVodDirectionalKey(event: KeyEvent): Boolean =
    event.key == Key.DirectionUp ||
        event.key == Key.DirectionDown ||
        event.key == Key.DirectionLeft ||
        event.key == Key.DirectionRight

internal data class VodHubFocusDeps(
    val scope: CoroutineScope,
    val contentFilter: VodContentFilter,
    val searchQuery: String,
    val showGenrePanel: Boolean,
    val showLibraryNavPanel: Boolean,
    val showBrowseGrid: Boolean,
    val showInlineSearch: Boolean,
    val hasHero: Boolean,
    val hasBrowseResults: Boolean,
    val genreLabels: List<String>,
    val wallRows: List<VodWallRow>,
    val displayWallRows: List<VodWallRow>,
    val loadedDeferredWallCount: Int,
    val deferredWallRowsSize: Int,
    val navDrawerOpen: Boolean,
    val filterPanelFocusRequester: FocusRequester,
    val genrePanelFocusRequester: FocusRequester,
    val languageSubmenuFocusRequester: FocusRequester,
    val browseGridFocusRequester: FocusRequester,
    val browseEmptyStateFocusRequester: FocusRequester,
    val rootFocusRequester: FocusRequester,
    val heroPlayFocusRequester: FocusRequester,
    val inlineSearchFocusRequester: FocusRequester,
    val navDrawerFocusRequester: FocusRequester,
    var browseGridFocusIndex: Int,
    val setBrowseGridFocusIndex: (Int) -> Unit,
    var contentRowIndex: Int,
    val setContentRowIndex: (Int) -> Unit,
    var contentColIndex: Int,
    val setContentColIndex: (Int) -> Unit,
    val browseGridItemCount: () -> Int,
    val browseGridCatalogTotal: () -> Int,
    val focusContentMode: () -> VodHubFocusContentMode,
    val isBrowseGridLoading: () -> Boolean,
    val browseGridKeyAtIndex: (Int) -> String?,
    val activeBrowseGridState: () -> LazyGridState?,
    val syncFocusedWallItemKey: () -> Unit,
    /** Apply tab filter when highlight moves (LEFT/RIGHT) or Enter — preserves per-filter memory. */
    val commitFilterHighlight: (Int) -> Unit,
    val applyGenre: (Int) -> Unit,
    val activateWallItem: (com.grid.tv.domain.model.VodWallItem) -> Unit,
    val openLanguagePreferenceDialog: () -> Unit,
    val refreshAvailableLanguages: () -> Unit,
    val togglePreferredLanguage: (String?) -> Unit,
    val languageFilterActive: Boolean,
    val availableLanguages: List<String>,
    val openNavDrawer: () -> Unit,
    val closeNavDrawer: (restoreFilter: Boolean) -> Unit,
    val selectVodDrawerItem: (GuideNavDrawerItem) -> Unit,
    val focusInlineSearchField: () -> Unit,
    val focusSearchResults: () -> Unit,
    val moviesBrowseGridActivate: (Int) -> Unit,
    val seriesBrowseGridActivate: (Int) -> Unit,
    val ensureValidFocus: () -> Unit,
    val hubTabsNavigable: () -> Boolean = { true },
)

internal class VodHubFocusController(
    private val ui: VodHubFocusUiState,
) {
    private var deps: VodHubFocusDeps? = null

    fun bind(deps: VodHubFocusDeps) {
        this.deps = deps
    }

    private val d: VodHubFocusDeps
        get() = deps ?: error("VodHubFocusController.bind() must run before interaction")

    private fun activeFilter(): VodContentFilter = d.contentFilter

    private fun persistGridFocus(filter: VodContentFilter = activeFilter()) {
        if (!filter.storesBrowseGridMemory()) return
        val gridState = d.activeBrowseGridState() ?: return
        val key = d.browseGridKeyAtIndex(d.browseGridFocusIndex)
        ui.rememberGridFor(
            filter,
            snapshotGridMemory(gridState, d.browseGridFocusIndex, key)
        )
    }

    private fun persistGenreFocus(filter: VodContentFilter = activeFilter()) {
        if (filter == VodContentFilter.MOVIES || filter == VodContentFilter.SERIES) {
            ui.rememberGenreFor(filter)
        }
    }

    private fun transitionToZone(zone: VodFocusZone, detail: String = "") {
        val from = ui.focusZone
        if (from != zone) {
            VodHubFocusLogger.zoneTransition(from, zone, detail)
            ui.focusZone = zone
        }
    }

    fun moveLibraryNavHighlight(delta: Int) {
        val nextIndex = (ui.filterFocusIndex + delta).coerceIn(0, VodHubLanguageFilterFocusIndex)
        if (nextIndex == ui.filterFocusIndex) return
        if (!d.hubTabsNavigable() && nextIndex < VodHubLanguageFilterFocusIndex) {
            return
        }
        ui.filterFocusIndex = nextIndex
        if (nextIndex < VodHubLanguageFilterFocusIndex) {
            VodHubTabFilters.getOrNull(nextIndex)?.let { filter ->
                VodHubFocusLogger.filterHighlight(filter, nextIndex)
            }
            d.commitFilterHighlight(nextIndex)
        }
    }

    fun moveFilterHighlight(delta: Int) {
        moveLibraryNavHighlight(delta)
    }

    fun collapseLibraryNavPanel() {
        ui.libraryNavPanelVisible = false
    }

    fun openLibraryNavPanel() {
        if (!d.showLibraryNavPanel) return
        ui.navDrawerOpen = false
        ui.libraryNavPanelVisible = true
        val appliedIndex = vodHubTabFilterIndex(d.contentFilter)
            .coerceIn(0, VodHubLanguageFilterFocusIndex)
        ui.filterFocusIndex = if (ui.lastFilterFocusIndex != 0) {
            ui.lastFilterFocusIndex.coerceIn(0, VodHubLanguageFilterFocusIndex)
        } else {
            appliedIndex
        }
        transitionToZone(VodFocusZone.FILTER_PANEL, "openLibraryNav")
        VodHubFocusLogger.filterHighlight(d.contentFilter, ui.filterFocusIndex)
        d.scope.launch {
            d.filterPanelFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun returnToContentFromLibraryNav(resetOrigin: Boolean = true) {
        collapseLibraryNavPanel()
        ui.rememberFilterFocus()
        when {
            d.showBrowseGrid -> focusBrowseGridRestored(resetToOrigin = resetOrigin)
            d.showInlineSearch -> d.focusInlineSearchField()
            d.hasHero && d.searchQuery.isBlank() && d.contentFilter == VodContentFilter.ALL -> {
                transitionToZone(VodFocusZone.HERO)
                d.scope.launch {
                    d.heroPlayFocusRequester.requestFocusSafelyAfterLayout()
                }
            }
            d.displayWallRows.isNotEmpty() -> focusWallContentRestored(resetToOrigin = resetOrigin)
            else -> {
                transitionToZone(VodFocusZone.CONTENT, "libraryNavRight")
                d.ensureValidFocus()
            }
        }
    }

    fun focusWallContentRestored(resetToOrigin: Boolean = false) {
        if (d.displayWallRows.isEmpty()) {
            d.ensureValidFocus()
            return
        }
        collapseLibraryNavPanel()
        transitionToZone(VodFocusZone.CONTENT, if (resetToOrigin) "wallOrigin" else "wallRestore")
        if (resetToOrigin) {
            d.setContentRowIndex(0)
            d.setContentColIndex(0)
            d.syncFocusedWallItemKey()
        } else {
            restoreWallFocus()
        }
        requestWallRootFocus()
    }

    private fun requestWallRootFocus() {
        d.scope.launch {
            d.rootFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun commitFocusedFilterHighlight() {
        if (ui.filterFocusIndex == VodHubLanguageFilterFocusIndex) {
            d.openLanguagePreferenceDialog()
            return
        }
        if (!d.hubTabsNavigable() && ui.filterFocusIndex < VodHubLanguageFilterFocusIndex) {
            val filter = VodHubTabFilters.getOrNull(ui.filterFocusIndex)
            if (filter != null && filter != d.contentFilter) return
        }
        d.commitFilterHighlight(ui.filterFocusIndex)
    }

    fun focusFilterPanelFromGenre() {
        openLibraryNavPanel()
    }

    fun focusLibraryNavFromGenre() = focusFilterPanelFromGenre()

    fun focusGenrePanelFromFilter() {
        ui.rememberFilterFocus()
        transitionToZone(VodFocusZone.GENRE_PANEL, "fromFilter")
        ui.restoreGenreFrom(d.contentFilter)
        ui.genreFocusIndex = ui.genreFocusIndex.coerceIn(0, (d.genreLabels.size - 1).coerceAtLeast(0))
        val label = d.genreLabels.getOrNull(ui.genreFocusIndex) ?: "?"
        VodHubFocusLogger.genreFocus(label, ui.genreFocusIndex)
        d.scope.launch {
            d.genrePanelFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusGenrePanelFromGrid() {
        persistGridFocus()
        transitionToZone(VodFocusZone.GENRE_PANEL, "fromGrid")
        ui.restoreGenreFrom(d.contentFilter)
        val label = d.genreLabels.getOrNull(ui.genreFocusIndex) ?: "?"
        VodHubFocusLogger.genreFocus(label, ui.genreFocusIndex)
        d.scope.launch {
            d.genrePanelFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusBrowseGridEmpty() {
        collapseLibraryNavPanel()
        persistGenreFocus()
        transitionToZone(VodFocusZone.CONTENT, "gridEmpty")
        ui.awaitingBrowseGridFocus = false
        d.setBrowseGridFocusIndex(0)
        d.scope.launch {
            d.browseEmptyStateFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun escapeAwaitingBrowseGridFocus() {
        ui.awaitingBrowseGridFocus = false
        val catalogTotal = d.browseGridCatalogTotal()
        when (d.focusContentMode()) {
            VodHubFocusContentMode.Empty,
            VodHubFocusContentMode.Error,
            -> focusBrowseGridEmpty()
            VodHubFocusContentMode.Loading -> {
                if (catalogTotal == 0) {
                    focusBrowseGridEmpty()
                } else {
                    transitionToZone(VodFocusZone.FILTER_PANEL, "awaitGridTimeout")
                    ui.libraryNavPanelVisible = true
                    d.scope.launch {
                        d.filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                    }
                }
            }
            VodHubFocusContentMode.Ready -> {
                transitionToZone(VodFocusZone.FILTER_PANEL, "awaitGridTimeout")
                ui.libraryNavPanelVisible = true
                d.scope.launch {
                    d.filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                }
            }
        }
    }

    fun focusBrowseGridRestored(resetToOrigin: Boolean = false) {
        collapseLibraryNavPanel()
        persistGenreFocus()
        val count = d.browseGridItemCount()
        if (count <= 0) {
            val catalogTotal = d.browseGridCatalogTotal()
            when (d.focusContentMode()) {
                VodHubFocusContentMode.Loading -> {
                    val awaiting = VodHubSurfaceStateResolver.shouldAwaitBrowseGrid(
                        VodHubFocusContentMode.Loading,
                        catalogTotal,
                    )
                    transitionToZone(
                        VodFocusZone.CONTENT,
                        if (d.isBrowseGridLoading()) "gridLoading" else "gridPagingWarm",
                    )
                    if (awaiting || d.isBrowseGridLoading()) {
                        ui.awaitingBrowseGridFocus = true
                        d.setBrowseGridFocusIndex(0)
                        return
                    }
                }
                VodHubFocusContentMode.Empty,
                VodHubFocusContentMode.Error,
                -> {
                    focusBrowseGridEmpty()
                    return
                }
                VodHubFocusContentMode.Ready -> Unit
            }
            focusBrowseGridEmpty()
            return
        }
        ui.awaitingBrowseGridFocus = false
        val gridState = d.activeBrowseGridState()
        val firstVisible = gridState?.firstVisibleItemIndex ?: 0
        val memory = ui.gridMemoryFor(d.contentFilter)
        val resolved = if (resetToOrigin) {
            0
        } else {
            resolveBrowseGridFocusIndex(
                itemCount = count,
                saved = memory,
                keyAtIndex = d.browseGridKeyAtIndex,
                firstVisibleIndex = firstVisible,
            )
        }
        val key = d.browseGridKeyAtIndex(resolved)
        ui.rememberGridFor(
            d.contentFilter,
            memory.copy(itemIndex = resolved, contentKey = key)
        )
        transitionToZone(VodFocusZone.CONTENT, "gridRestore")
        d.setBrowseGridFocusIndex(resolved)
        if (d.activeBrowseGridState() == null) {
            ui.gridFocusPending = false
            ui.gridRestoreRequest = null
            VodHubFocusLogger.gridFocus(d.contentFilter, resolved, key)
        } else {
            ui.gridFocusPending = true
            ui.gridRestoreRequest = VodGridFocusRestoreRequest(
                targetIndex = resolved,
                scrollIndex = memory.scrollIndex,
                scrollOffset = memory.scrollOffset,
                contentKey = key,
            )
            VodHubFocusLogger.gridRestore(d.contentFilter, resolved, memory.scrollIndex, key)
        }
    }

    fun onGridRestoreComplete(resolvedIndex: Int) {
        ui.gridFocusPending = false
        ui.gridRestoreRequest = null
        d.setBrowseGridFocusIndex(resolvedIndex)
        val key = d.browseGridKeyAtIndex(resolvedIndex)
        ui.rememberGridFor(
            d.contentFilter,
            ui.gridMemoryFor(d.contentFilter).copy(itemIndex = resolvedIndex, contentKey = key)
        )
        VodHubFocusLogger.gridFocus(d.contentFilter, resolvedIndex, key)
        d.scope.launch {
            d.rootFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusContentFromFilters() {
        ui.rememberFilterFocus()
        ui.contentScrollDirection = TvLazyFocusScrollDirection.NEUTRAL
        when {
            d.showInlineSearch -> d.focusInlineSearchField()
            d.showBrowseGrid -> focusBrowseGridRestored(resetToOrigin = true)
            d.hasHero && d.searchQuery.isBlank() -> {
                transitionToZone(VodFocusZone.HERO)
                d.scope.launch {
                    d.heroPlayFocusRequester.requestFocusSafelyAfterLayout()
                }
            }
            d.displayWallRows.isNotEmpty() -> focusWallContentRestored(resetToOrigin = false)
            else -> d.ensureValidFocus()
        }
    }

    private fun restoreWallFocus() {
        val memory = ui.wallMemoryFor(VodContentFilter.ALL)
        val (row, col) = resolveVodWallFocus(
            wallRows = d.displayWallRows,
            savedContentKey = memory.contentKey,
            fallbackRow = memory.rowIndex,
            fallbackCol = memory.colIndex
        )
        d.setContentRowIndex(row)
        d.setContentColIndex(col)
        d.syncFocusedWallItemKey()
    }

    fun closeNavDrawerToContentZone(restoreFilter: Boolean = true) {
        ui.navDrawerOpen = false
        if (!restoreFilter) return
        ui.navDrawerFocusIndex = ui.lastNavDrawerFocusIndex
        val targetZone = when {
            d.showGenrePanel -> VodFocusZone.GENRE_PANEL
            else -> VodFocusZone.FILTER_PANEL
        }
        transitionToZone(targetZone, "closeDrawer")
        d.scope.launch {
            when (targetZone) {
                VodFocusZone.GENRE_PANEL -> {
                    ui.restoreGenreFrom(d.contentFilter)
                    d.genrePanelFocusRequester.requestFocusSafelyAfterLayout()
                }
                VodFocusZone.FILTER_PANEL -> {
                    ui.libraryNavPanelVisible = true
                    ui.filterFocusIndex = ui.lastFilterFocusIndex.coerceAtLeast(0)
                    d.filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                }
                else -> Unit
            }
        }
    }

    fun handleNavDrawerKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    ui.profileMenuOpen = false
                    true
                }
                else -> false
            }
        }
        val lastIndex = GuideNavDrawerItems.size
        return when (event.key) {
            Key.Back, Key.Escape -> {
                closeNavDrawerToContentZone()
                true
            }
            Key.DirectionRight -> {
                if (ui.navDrawerFocusIndex == GuideNavDrawerProfileFocusIndex) {
                    ui.navDrawerFocusIndex = guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Vod)
                } else if (d.showLibraryNavPanel) {
                    ui.rememberNavDrawerFocus()
                    ui.navDrawerOpen = false
                    openLibraryNavPanel()
                } else if (
                    d.contentFilter == VodContentFilter.ALL &&
                    d.displayWallRows.isNotEmpty() &&
                    !d.showBrowseGrid
                ) {
                    ui.rememberNavDrawerFocus()
                    ui.navDrawerOpen = false
                    focusWallContentRestored(resetToOrigin = true)
                } else {
                    ui.rememberNavDrawerFocus()
                    closeNavDrawerToContentZone()
                }
                true
            }
            Key.DirectionDown -> {
                if (ui.navDrawerFocusIndex < lastIndex) {
                    ui.navDrawerFocusIndex += 1
                }
                true
            }
            Key.DirectionUp -> {
                if (ui.navDrawerFocusIndex > GuideNavDrawerProfileFocusIndex) {
                    ui.navDrawerFocusIndex -= 1
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (ui.navDrawerFocusIndex == GuideNavDrawerProfileFocusIndex) {
                    ui.profileMenuOpen = true
                    ui.profileMenuFocusIndex = 0
                    true
                } else {
                    GuideNavDrawerItems.getOrNull(ui.navDrawerFocusIndex - 1)?.let { item ->
                        d.selectVodDrawerItem(item)
                        true
                    } ?: false
                }
            }
            else -> false
        }
    }

    fun routeLibraryNavRight() {
        if (ui.filterFocusIndex == VodHubLanguageFilterFocusIndex) {
            if (d.languageFilterActive) {
                returnToContentFromLibraryNav(resetOrigin = true)
            } else {
                openLanguageSubmenu()
            }
            return
        }
        if (!d.hubTabsNavigable()) return
        if (d.showGenrePanel && d.genreLabels.isNotEmpty()) {
            focusGenrePanelFromFilter()
        } else {
            returnToContentFromLibraryNav(resetOrigin = true)
        }
    }

    fun openLanguageSubmenu() {
        ui.rememberFilterFocus()
        ui.filterFocusIndex = VodHubLanguageFilterFocusIndex
        d.refreshAvailableLanguages()
        transitionToZone(VodFocusZone.LANGUAGE_SUBMENU, "openLanguageSub")
        ui.languageSubmenuFocusIndex = ui.languageSubmenuFocusIndex.coerceIn(
            0,
            d.availableLanguages.size.coerceAtLeast(0),
        )
        d.scope.launch {
            d.languageSubmenuFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusLibraryNavFromLanguageSubmenu() {
        transitionToZone(VodFocusZone.FILTER_PANEL, "langSubLeft")
        ui.filterFocusIndex = VodHubLanguageFilterFocusIndex
        d.scope.launch {
            d.filterPanelFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun handleLanguageSubmenuKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        val itemCount = d.availableLanguages.size + 1
        if (itemCount <= 0) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                focusLibraryNavFromLanguageSubmenu()
                true
            }
            Key.DirectionRight -> {
                returnToContentFromLibraryNav(resetOrigin = false)
                true
            }
            Key.DirectionDown -> {
                if (ui.languageSubmenuFocusIndex < itemCount - 1) {
                    ui.languageSubmenuFocusIndex += 1
                }
                true
            }
            Key.DirectionUp -> {
                if (ui.languageSubmenuFocusIndex > 0) {
                    ui.languageSubmenuFocusIndex -= 1
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (ui.languageSubmenuFocusIndex == 0) {
                    d.togglePreferredLanguage(null)
                } else {
                    d.availableLanguages.getOrNull(ui.languageSubmenuFocusIndex - 1)?.let {
                        d.togglePreferredLanguage(it)
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleLibraryNavPanelKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.focusZone == VodFocusZone.NAV_DRAWER) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                ui.rememberFilterFocus()
                d.openNavDrawer()
                true
            }
            Key.DirectionRight -> {
                routeLibraryNavRight()
                true
            }
            Key.DirectionDown -> {
                if (ui.filterFocusIndex < VodHubLanguageFilterFocusIndex) {
                    moveLibraryNavHighlight(1)
                }
                true
            }
            Key.DirectionUp -> {
                if (ui.filterFocusIndex > 0) {
                    moveLibraryNavHighlight(-1)
                } else {
                    ui.rememberFilterFocus()
                    d.openNavDrawer()
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (ui.filterFocusIndex == VodHubLanguageFilterFocusIndex) {
                    if (d.languageFilterActive) {
                        returnToContentFromLibraryNav(resetOrigin = true)
                    } else {
                        openLanguageSubmenu()
                    }
                } else if (d.hubTabsNavigable()) {
                    commitFocusedFilterHighlight()
                    if (d.showGenrePanel && d.genreLabels.isNotEmpty()) {
                        focusGenrePanelFromFilter()
                    } else {
                        returnToContentFromLibraryNav(resetOrigin = true)
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleFilterPanelKey(event: KeyEvent): Boolean = handleLibraryNavPanelKey(event)

    fun handleGenrePanelKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (d.genreLabels.isEmpty()) return false
        val handled = when (event.key) {
            Key.DirectionUp -> {
                if (ui.genreFocusIndex > 0) {
                    ui.genreFocusIndex -= 1
                    persistGenreFocus()
                } else {
                    focusFilterPanelFromGenre()
                }
                true
            }
            Key.DirectionDown -> {
                ui.genreFocusIndex = (ui.genreFocusIndex + 1).coerceAtMost(d.genreLabels.lastIndex)
                persistGenreFocus()
                true
            }
            Key.DirectionLeft -> {
                persistGenreFocus()
                openLibraryNavPanel()
                true
            }
            Key.DirectionRight -> {
                when (sidebarHorizontalResult(event.key, allowLeftToRail = false)) {
                    SidebarContentFocus.SidebarHorizontalResult.ReturnToContent ->
                        focusBrowseGridRestored()
                    else -> Unit
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                d.applyGenre(ui.genreFocusIndex)
                true
            }
            else -> false
        }
        return handled
    }

    fun handleBrowseGridLeadingEdgeLeft() {
        if (shouldEnterSidebarFromContent(
                Key.DirectionLeft,
                atLeadingEdge = true,
                hasSidebar = d.showGenrePanel,
            )
        ) {
            focusGenrePanelFromGrid()
        } else if (d.showLibraryNavPanel) {
            persistGridFocus()
            openLibraryNavPanel()
        }
    }

    fun handleBrowseGridKey(event: KeyEvent): Boolean {
        val itemCount = d.browseGridItemCount()
        if (itemCount <= 0) return false
        val columns = ui.browseGridColumnCount.coerceAtLeast(1)
        val lastIndex = itemCount - 1
        val handled = when (event.key) {
            Key.DirectionLeft -> {
                ui.contentScrollDirection = TvLazyFocusScrollDirection.NEUTRAL
                if (isLeadingGridColumn(d.browseGridFocusIndex, columns)) {
                    handleBrowseGridLeadingEdgeLeft()
                } else {
                    d.setBrowseGridFocusIndex(d.browseGridFocusIndex - 1)
                    persistGridFocus()
                }
                true
            }
            Key.DirectionRight -> {
                ui.contentScrollDirection = TvLazyFocusScrollDirection.NEUTRAL
                val column = d.browseGridFocusIndex % columns
                if (column < columns - 1 && d.browseGridFocusIndex < lastIndex) {
                    d.setBrowseGridFocusIndex(d.browseGridFocusIndex + 1)
                    persistGridFocus()
                }
                true
            }
            Key.DirectionUp -> {
                ui.contentScrollDirection = TvLazyFocusScrollDirection.UP
                if (d.browseGridFocusIndex >= columns) {
                    d.setBrowseGridFocusIndex(d.browseGridFocusIndex - columns)
                    persistGridFocus()
                } else {
                    focusFilterPanelFromGenre()
                }
                true
            }
            Key.DirectionDown -> {
                ui.contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                val next = d.browseGridFocusIndex + columns
                if (next <= lastIndex) {
                    d.setBrowseGridFocusIndex(next)
                    persistGridFocus()
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (d.contentFilter) {
                    VodContentFilter.MOVIES -> d.moviesBrowseGridActivate(d.browseGridFocusIndex)
                    VodContentFilter.SERIES -> d.seriesBrowseGridActivate(d.browseGridFocusIndex)
                    else -> Unit
                }
                true
            }
            else -> false
        }
        return handled
    }

    fun handleWallContentKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (d.displayWallRows.isEmpty()) {
            return when (event.key) {
                Key.DirectionLeft, Key.DirectionUp -> {
                    if (d.showLibraryNavPanel) {
                        openLibraryNavPanel()
                    } else {
                        d.openNavDrawer()
                    }
                    true
                }
                else -> false
            }
        }
        val row = d.displayWallRows.getOrNull(d.contentRowIndex) ?: return false
        val handled = when (event.key) {
            Key.DirectionLeft -> {
                if (d.contentColIndex > 0) {
                    d.setContentColIndex(d.contentColIndex - 1)
                } else if (d.showLibraryNavPanel) {
                    openLibraryNavPanel()
                } else {
                    d.openNavDrawer()
                }
                true
            }
            Key.DirectionRight -> {
                d.setContentColIndex((d.contentColIndex + 1).coerceAtMost(row.items.lastIndex))
                true
            }
            Key.DirectionUp -> {
                if (d.contentRowIndex > 0) {
                    ui.contentScrollDirection = TvLazyFocusScrollDirection.UP
                    d.setContentRowIndex(d.contentRowIndex - 1)
                    val maxCol = d.displayWallRows[d.contentRowIndex].items.lastIndex
                    d.setContentColIndex(d.contentColIndex.coerceAtMost(maxCol))
                } else if (d.hasHero && d.searchQuery.isBlank()) {
                    transitionToZone(VodFocusZone.HERO)
                    d.scope.launch {
                        d.heroPlayFocusRequester.requestFocusSafelyAfterLayout()
                    }
                } else {
                    focusFilterPanelFromGenre()
                }
                true
            }
            Key.DirectionDown -> {
                if (d.contentRowIndex < d.displayWallRows.lastIndex) {
                    ui.contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                    d.setContentRowIndex(d.contentRowIndex + 1)
                    val maxCol = d.displayWallRows[d.contentRowIndex].items.lastIndex
                    d.setContentColIndex(d.contentColIndex.coerceAtMost(maxCol))
                } else if (
                    d.contentFilter == VodContentFilter.ALL &&
                    !d.showBrowseGrid &&
                    d.loadedDeferredWallCount < d.deferredWallRowsSize
                ) {
                    ui.contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                    d.setContentRowIndex(d.contentRowIndex + 1)
                    d.setContentColIndex(0)
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                row.items.getOrNull(d.contentColIndex)?.let(d.activateWallItem)
                true
            }
            else -> false
        }
        if (handled) d.syncFocusedWallItemKey()
        return handled
    }

    fun handleContentKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (d.showInlineSearch) {
            if (ui.vodSearchFocused) return false
            if (!d.showBrowseGrid) {
                return when (event.key) {
                    Key.DirectionUp -> {
                        d.focusInlineSearchField()
                        d.scope.launch {
                            d.inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        if (d.showBrowseGrid) {
            if (!d.showInlineSearch) {
                return handleBrowseGridKey(event)
            }
            return when (event.key) {
                Key.DirectionLeft -> {
                    ui.focusZone = when {
                        d.showGenrePanel -> VodFocusZone.GENRE_PANEL
                        d.searchQuery.isBlank() -> VodFocusZone.FILTER_PANEL
                        else -> {
                            d.openNavDrawer()
                            VodFocusZone.NAV_DRAWER
                        }
                    }
                    d.scope.launch {
                        when (ui.focusZone) {
                            VodFocusZone.GENRE_PANEL ->
                                d.genrePanelFocusRequester.requestFocusSafelyAfterLayout()
                            VodFocusZone.FILTER_PANEL ->
                                d.filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                            else -> Unit
                        }
                    }
                    true
                }
                Key.DirectionUp -> {
                    ui.focusZone = when {
                        !d.showBrowseGrid && d.hasHero && d.searchQuery.isBlank() -> VodFocusZone.HERO
                        d.searchQuery.isBlank() -> VodFocusZone.FILTER_PANEL
                        else -> {
                            d.openNavDrawer()
                            VodFocusZone.NAV_DRAWER
                        }
                    }
                    true
                }
                else -> false
            }
        }
        return handleWallContentKey(event)
    }
}
