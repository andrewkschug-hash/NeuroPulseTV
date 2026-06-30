package com.grid.tv.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.ui.viewmodel.VodCatalogPager
import com.grid.tv.ui.screen.VodGridFocusRestoreRequest
import com.grid.tv.ui.screen.restoreGridFocusAnimated
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
private fun VodGridPrefetchEffect(
    gridState: LazyGridState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(gridState, itemCount) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (itemCount <= 0) return@collect
                if (lastVisibleIndex >= itemCount - VodCatalogPager.PREFETCH_THRESHOLD) {
                    onLoadMore()
                }
            }
    }
}

@Composable
private fun VodGridAnimatedRestoreEffect(
    gridState: LazyGridState,
    itemCount: Int,
    gridRestoreRequest: VodGridFocusRestoreRequest?,
    onGridRestoreComplete: (Int) -> Unit,
) {
    LaunchedEffect(gridRestoreRequest?.token, itemCount) {
        val request = gridRestoreRequest ?: return@LaunchedEffect
        if (itemCount <= 0) return@LaunchedEffect
        val resolved = restoreGridFocusAnimated(gridState, request, itemCount)
        onGridRestoreComplete(resolved)
    }
}

@Composable
private fun VodGridColumnCountEffect(
    gridState: LazyGridState,
    itemCount: Int,
    onColumnCountChanged: ((Int) -> Unit)?,
) {
    LaunchedEffect(gridState, itemCount) {
        snapshotFlow { gridState.visibleColumnCount() }
            .distinctUntilChanged()
            .collect { columns -> onColumnCountChanged?.invoke(columns) }
    }
}

private fun Modifier.interceptLeadingEdgeLeft(
    focusedIndex: Int,
    columnCount: Int,
    onLeadingEdgeNavigateLeft: (() -> Unit)?,
): Modifier {
    val callback = onLeadingEdgeNavigateLeft ?: return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionLeft &&
            SidebarContentFocus.isLeadingGridColumn(focusedIndex, columnCount.coerceAtLeast(1))
        ) {
            callback()
            true
        } else {
            false
        }
    }
}

@Composable
fun VodPagedVerticalGrid(
  items: List<VodGridCardModel>,
  progressByKey: Map<Pair<Long, Long>, Long>,
  progressFraction: (VodGridCardModel, Map<Pair<Long, Long>, Long>) -> Float?,
  onItemClick: (VodGridCardModel) -> Unit,
  onLoadMore: () -> Unit,
  modifier: Modifier = Modifier,
  gridState: LazyGridState = rememberLazyGridState(),
  minCellSize: androidx.compose.ui.unit.Dp = 112.dp,
) {
  LaunchedEffect(gridState, items.size) {
    snapshotFlow {
      gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    }
      .distinctUntilChanged()
      .collect { lastVisibleIndex ->
        if (items.isEmpty()) return@collect
        if (lastVisibleIndex >= items.size - VodCatalogPager.PREFETCH_THRESHOLD) {
          onLoadMore()
        }
      }
  }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = minCellSize),
    state = gridState,
    contentPadding = VodLayout.gridContentPadding(),
    horizontalArrangement = Arrangement.spacedBy(VodLayout.PosterSpacing + VodPosterFocusLayout.gridEdgePadding),
    verticalArrangement = Arrangement.spacedBy(VodLayout.RowSpacing + VodPosterFocusLayout.gridEdgePadding),
    modifier = modifier
  ) {
    items(
      items = items,
      key = { card -> card.key }
    ) { card ->
      VodPosterCard(
        title = card.title,
        posterUrl = card.posterUrl,
        progressFraction = progressFraction(card, progressByKey),
        showHdBadge = card.showHdBadge,
        onClick = { onItemClick(card) }
      )
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VodPagedVerticalGrid(
    pagingItems: LazyPagingItems<SeriesShow>,
    progressByKey: Map<Pair<Long, Long>, Long>,
    progressFraction: (VodGridCardModel, Map<Pair<Long, Long>, Long>) -> Float?,
    onItemClick: (VodGridCardModel) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    minCellSize: androidx.compose.ui.unit.Dp = 112.dp,
    firstItemFocusRequester: FocusRequester? = null,
    contentGridFocusRequester: FocusRequester? = null,
    gridFocused: Boolean = false,
    focusedItemIndex: Int = -1,
    restoreScrollIndex: Int = -1,
    restoreScrollOffset: Int = 0,
    gridRestoreRequest: VodGridFocusRestoreRequest? = null,
    onGridRestoreComplete: (Int) -> Unit = {},
    onColumnCountChanged: ((Int) -> Unit)? = null,
    onNavigateUpFromFirstRow: (() -> Unit)? = null,
    onLeadingEdgeNavigateLeft: (() -> Unit)? = null,
) {
    val gridFocusRequester = contentGridFocusRequester ?: firstItemFocusRequester
    val useNativeFocus = gridFocusRequester != null && !gridFocused
    val effectiveFocusedIndex = if (gridFocused && focusedItemIndex >= 0) focusedItemIndex else -1
    var columnCount by remember { mutableIntStateOf(1) }
    val leadingEdgeIndex = if (effectiveFocusedIndex >= 0) effectiveFocusedIndex else focusedItemIndex

    VodGridAnimatedRestoreEffect(
        gridState = gridState,
        itemCount = pagingItems.itemCount,
        gridRestoreRequest = gridRestoreRequest,
        onGridRestoreComplete = onGridRestoreComplete,
    )

    VodGridPrefetchEffect(
        gridState = gridState,
        itemCount = pagingItems.itemCount,
        onLoadMore = { pagingItems[pagingItems.itemCount - 1] },
    )

    VodGridColumnCountEffect(
        gridState = gridState,
        itemCount = pagingItems.itemCount,
        onColumnCountChanged = { columns ->
            columnCount = columns
            onColumnCountChanged?.invoke(columns)
        },
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        state = gridState,
        contentPadding = VodLayout.gridContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(VodLayout.PosterSpacing + VodPosterFocusLayout.gridEdgePadding),
        verticalArrangement = Arrangement.spacedBy(VodLayout.RowSpacing + VodPosterFocusLayout.gridEdgePadding),
        modifier = modifier
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { show -> "${show.playlistId}_${show.id}" }
        ) { index ->
            val show = pagingItems[index] ?: return@items
            val card = show.toGridCardModel()
            val externallyFocused = gridFocused && index == effectiveFocusedIndex
            val itemModifier = when {
                useNativeFocus && index == 0 && gridFocusRequester != null -> Modifier
                    .focusRequester(gridFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (index == 0 &&
                            onNavigateUpFromFirstRow != null &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp
                        ) {
                            onNavigateUpFromFirstRow()
                            true
                        } else {
                            false
                        }
                    }
                gridFocused -> Modifier.focusProperties { canFocus = false }
                else -> Modifier
            }
            VodPosterCard(
                title = card.title,
                posterUrl = card.posterUrl,
                progressFraction = progressFraction(card, progressByKey),
                showHdBadge = card.showHdBadge,
                onClick = { onItemClick(card) },
                externallyFocused = externallyFocused,
                modifier = itemModifier
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VodMoviePagedGrid(
    pagingItems: LazyPagingItems<VodItem>,
    progressByKey: Map<Pair<Long, Long>, Long>,
    progressFraction: (VodItem, Map<Pair<Long, Long>, Long>) -> Float?,
    onItemClick: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    minCellSize: androidx.compose.ui.unit.Dp = 112.dp,
    firstItemFocusRequester: FocusRequester? = null,
    contentGridFocusRequester: FocusRequester? = null,
    gridFocused: Boolean = false,
    focusedItemIndex: Int = -1,
    restoreScrollIndex: Int = -1,
    restoreScrollOffset: Int = 0,
    gridRestoreRequest: VodGridFocusRestoreRequest? = null,
    onGridRestoreComplete: (Int) -> Unit = {},
    onColumnCountChanged: ((Int) -> Unit)? = null,
    onNavigateUpFromFirstRow: (() -> Unit)? = null,
    onLeadingEdgeNavigateLeft: (() -> Unit)? = null,
) {
    val gridFocusRequester = contentGridFocusRequester ?: firstItemFocusRequester
    val useNativeFocus = gridFocusRequester != null && !gridFocused
    val effectiveFocusedIndex = if (gridFocused && focusedItemIndex >= 0) focusedItemIndex else -1
    var columnCount by remember { mutableIntStateOf(1) }
    val leadingEdgeIndex = if (effectiveFocusedIndex >= 0) effectiveFocusedIndex else focusedItemIndex

    VodGridAnimatedRestoreEffect(
        gridState = gridState,
        itemCount = pagingItems.itemCount,
        gridRestoreRequest = gridRestoreRequest,
        onGridRestoreComplete = onGridRestoreComplete,
    )

    VodGridPrefetchEffect(
        gridState = gridState,
        itemCount = pagingItems.itemCount,
        onLoadMore = { pagingItems[pagingItems.itemCount - 1] },
    )

    VodGridColumnCountEffect(
        gridState = gridState,
        itemCount = pagingItems.itemCount,
        onColumnCountChanged = { columns ->
            columnCount = columns
            onColumnCountChanged?.invoke(columns)
        },
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        state = gridState,
        contentPadding = VodLayout.gridContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(VodLayout.PosterSpacing),
        verticalArrangement = Arrangement.spacedBy(VodLayout.RowSpacing),
        modifier = modifier
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { movie -> "${movie.playlistId}_${movie.streamId}" }
        ) { index ->
            val movie = pagingItems[index] ?: return@items
            val card = movie.toGridCardModel()
            val externallyFocused = gridFocused && index == effectiveFocusedIndex
            val itemModifier = when {
                useNativeFocus && index == 0 && gridFocusRequester != null -> Modifier
                    .focusRequester(gridFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (index == 0 &&
                            onNavigateUpFromFirstRow != null &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp
                        ) {
                            onNavigateUpFromFirstRow()
                            true
                        } else {
                            false
                        }
                    }
                gridFocused -> Modifier.focusProperties { canFocus = false }
                else -> Modifier
            }
            VodPosterCard(
                title = card.title,
                posterUrl = card.posterUrl,
                progressFraction = progressFraction(movie, progressByKey),
                showHdBadge = card.showHdBadge,
                onClick = { onItemClick(movie) },
                externallyFocused = externallyFocused,
                modifier = itemModifier
            )
        }
    }
}
