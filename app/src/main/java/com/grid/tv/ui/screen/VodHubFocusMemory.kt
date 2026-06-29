package com.grid.tv.ui.screen

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.grid.tv.domain.model.VodContentFilter

/** Focus breadcrumb for a browse grid (Movies / Series). */
data class VodGridFocusMemory(
    val itemIndex: Int = 0,
    val contentKey: String? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/** Focus breadcrumb for the All-tab content wall. */
data class VodWallFocusMemory(
    val rowIndex: Int = 0,
    val colIndex: Int = 0,
    val contentKey: String? = null,
)

/** Per-filter focus state — genre, grid scroll, and stable content identity. */
data class VodFilterFocusBreadcrumb(
    val genreIndex: Int = 0,
    val grid: VodGridFocusMemory = VodGridFocusMemory(),
    val wall: VodWallFocusMemory = VodWallFocusMemory(),
)

fun snapshotGridMemory(
    gridState: LazyGridState,
    itemIndex: Int,
    contentKey: String?,
): VodGridFocusMemory = VodGridFocusMemory(
    itemIndex = itemIndex,
    contentKey = contentKey,
    scrollIndex = gridState.firstVisibleItemIndex,
    scrollOffset = gridState.firstVisibleItemScrollOffset,
)

internal val vodBrowseFilters = setOf(
    VodContentFilter.MOVIES,
    VodContentFilter.SERIES,
)

internal fun VodContentFilter.storesBrowseGridMemory(): Boolean =
    this in vodBrowseFilters

internal fun VodContentFilter.storesWallMemory(): Boolean =
    this == VodContentFilter.ALL
