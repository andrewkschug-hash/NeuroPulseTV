package com.grid.tv.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grid.tv.domain.model.VodContentFilter
import kotlinx.serialization.Serializable
import com.grid.tv.ui.component.GuideNavDrawerItem
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import com.grid.tv.ui.component.guideNavDrawerItemFocusIndex

/** Four-zone VOD hub focus model: sidebar, top filters, genre list, content grid (+ hero on All). */
internal enum class VodFocusZone {
    NAV_DRAWER,
    FILTER_PANEL,
    GENRE_PANEL,
    HERO,
    CONTENT
}

internal val vodManualFocusZones = setOf(
    VodFocusZone.NAV_DRAWER,
    VodFocusZone.FILTER_PANEL,
    VodFocusZone.GENRE_PANEL,
    VodFocusZone.HERO
)

@Stable
internal class VodHubFocusUiState {
    var focusZone by mutableStateOf(VodFocusZone.FILTER_PANEL)
    var navDrawerOpen by mutableStateOf(false)
    var navDrawerFocusIndex by mutableIntStateOf(
        guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Vod)
    )
    /** Keyboard highlight on the top filter bar (may differ from applied [contentFilter]). */
    var filterFocusIndex by mutableIntStateOf(0)
    var genreFocusIndex by mutableIntStateOf(0)
    var browseGridColumnCount by mutableIntStateOf(4)
    var contentScrollDirection by mutableStateOf(TvLazyFocusScrollDirection.NEUTRAL)
    var vodSearchFocused by mutableStateOf(false)
    var profileMenuOpen by mutableStateOf(false)
    var profileMenuFocusIndex by mutableIntStateOf(0)

    var lastNavDrawerFocusIndex by mutableIntStateOf(
        guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Vod)
    )
    var lastFilterFocusIndex by mutableIntStateOf(0)

    /** Per-filter breadcrumbs: genre, grid index/key/scroll, wall row/col/key. */
    private val filterBreadcrumbs = mutableStateMapOf<VodContentFilter, VodFilterFocusBreadcrumb>()

    fun breadcrumbFor(filter: VodContentFilter): VodFilterFocusBreadcrumb =
        filterBreadcrumbs[filter] ?: VodFilterFocusBreadcrumb()

    fun genreIndexFor(filter: VodContentFilter): Int =
        breadcrumbFor(filter).genreIndex

    fun gridMemoryFor(filter: VodContentFilter): VodGridFocusMemory =
        breadcrumbFor(filter).grid

    fun wallMemoryFor(filter: VodContentFilter): VodWallFocusMemory =
        breadcrumbFor(filter).wall

    fun rememberNavDrawerFocus() {
        lastNavDrawerFocusIndex = navDrawerFocusIndex
    }

    fun rememberFilterFocus() {
        lastFilterFocusIndex = filterFocusIndex
    }

    fun rememberGenreFor(filter: VodContentFilter) {
        val current = breadcrumbFor(filter)
        filterBreadcrumbs[filter] = current.copy(genreIndex = genreFocusIndex)
    }

    fun rememberGridFor(
        filter: VodContentFilter,
        memory: VodGridFocusMemory,
    ) {
        val current = breadcrumbFor(filter)
        filterBreadcrumbs[filter] = current.copy(grid = memory)
    }

    fun rememberWallFor(
        filter: VodContentFilter,
        memory: VodWallFocusMemory,
    ) {
        val current = breadcrumbFor(filter)
        filterBreadcrumbs[filter] = current.copy(wall = memory)
    }

    fun restoreGenreFrom(filter: VodContentFilter) {
        genreFocusIndex = genreIndexFor(filter)
    }

    var gridRestoreRequest by mutableStateOf<VodGridFocusRestoreRequest?>(null)

    /** When true, grid highlight is suppressed until scroll restoration completes. */
    var gridFocusPending by mutableStateOf(false)

    /** Tab-bar Down was pressed before paging exposed any grid items — restore when [itemCount] > 0. */
    var awaitingBrowseGridFocus by mutableStateOf(false)

    /** Focus orchestration only — not catalog readiness (see [VodHubSurfaceState]). */
    val blocksGridFocus: Boolean
        get() = gridFocusPending || awaitingBrowseGridFocus

    fun importPersistedBreadcrumb(filter: VodContentFilter, persisted: VodFilterFocusBreadcrumbPersisted) {
        rememberGridFor(
            filter,
            VodGridFocusMemory(
                itemIndex = persisted.grid.itemIndex,
                contentKey = persisted.grid.contentKey,
                scrollIndex = persisted.grid.scrollIndex,
                scrollOffset = persisted.grid.scrollOffset,
            )
        )
        rememberWallFor(
            filter,
            VodWallFocusMemory(
                rowIndex = persisted.wall.rowIndex,
                colIndex = persisted.wall.colIndex,
                contentKey = persisted.wall.contentKey,
            )
        )
        val current = breadcrumbFor(filter)
        filterBreadcrumbs[filter] = current.copy(genreIndex = persisted.genreIndex)
    }

    fun exportPersistedBreadcrumb(filter: VodContentFilter): VodFilterFocusBreadcrumbPersisted {
        val crumb = breadcrumbFor(filter)
        return VodFilterFocusBreadcrumbPersisted(
            genreIndex = crumb.genreIndex,
            grid = VodGridFocusMemoryPersisted(
                itemIndex = crumb.grid.itemIndex,
                contentKey = crumb.grid.contentKey,
                scrollIndex = crumb.grid.scrollIndex,
                scrollOffset = crumb.grid.scrollOffset,
            ),
            wall = VodWallFocusMemoryPersisted(
                rowIndex = crumb.wall.rowIndex,
                colIndex = crumb.wall.colIndex,
                contentKey = crumb.wall.contentKey,
            ),
        )
    }
}

@Serializable
data class VodGridFocusMemoryPersisted(
    val itemIndex: Int = 0,
    val contentKey: String? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

@Serializable
data class VodWallFocusMemoryPersisted(
    val rowIndex: Int = 0,
    val colIndex: Int = 0,
    val contentKey: String? = null,
)

@Serializable
data class VodFilterFocusBreadcrumbPersisted(
    val genreIndex: Int = 0,
    val grid: VodGridFocusMemoryPersisted = VodGridFocusMemoryPersisted(),
    val wall: VodWallFocusMemoryPersisted = VodWallFocusMemoryPersisted(),
)

@Serializable
data class VodHubPersistedFocusSnapshot(
    val contentFilter: String = VodContentFilter.ALL.name,
    val filterFocusIndex: Int = 0,
    val focusZone: String = VodFocusZone.FILTER_PANEL.name,
    val movies: VodFilterFocusBreadcrumbPersisted = VodFilterFocusBreadcrumbPersisted(),
    val series: VodFilterFocusBreadcrumbPersisted = VodFilterFocusBreadcrumbPersisted(),
    val all: VodFilterFocusBreadcrumbPersisted = VodFilterFocusBreadcrumbPersisted(),
)
