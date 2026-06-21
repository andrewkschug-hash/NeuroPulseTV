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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
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
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun VodPagedVerticalGrid(
  items: List<VodGridCardModel>,
  progressByStreamId: Map<Long, Long>,
  progressFraction: (VodGridCardModel, Map<Long, Long>) -> Float?,
  onItemClick: (VodGridCardModel) -> Unit,
  onLoadMore: () -> Unit,
  modifier: Modifier = Modifier,
  gridState: LazyGridState = rememberLazyGridState(),
  minCellSize: androidx.compose.ui.unit.Dp = 112.dp
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
    contentPadding = PaddingValues(vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = modifier
  ) {
    items(
      items = items,
      key = { card -> card.key }
    ) { card ->
      VodPosterCard(
        title = card.title,
        posterUrl = card.posterUrl,
        progressFraction = progressFraction(card, progressByStreamId),
        showHdBadge = card.showHdBadge,
        onClick = { onItemClick(card) }
      )
    }
  }
}

@Composable
fun VodPagedVerticalGrid(
    pagingItems: LazyPagingItems<SeriesShow>,
    progressByStreamId: Map<Long, Long>,
    progressFraction: (VodGridCardModel, Map<Long, Long>) -> Float?,
    onItemClick: (VodGridCardModel) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    minCellSize: androidx.compose.ui.unit.Dp = 112.dp,
    firstItemFocusRequester: FocusRequester? = null,
    contentGridFocusRequester: FocusRequester? = null,
    onNavigateUpFromFirstRow: (() -> Unit)? = null
) {
    val gridFocusRequester = contentGridFocusRequester ?: firstItemFocusRequester

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        state = gridState,
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { show -> "${show.playlistId}_${show.id}" }
        ) { index ->
            val show = pagingItems[index] ?: return@items
            val card = show.toGridCardModel()
            val itemModifier = if (index == 0 && gridFocusRequester != null) {
                Modifier
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
            } else {
                Modifier
            }
            VodPosterCard(
                title = card.title,
                posterUrl = card.posterUrl,
                progressFraction = progressFraction(card, progressByStreamId),
                showHdBadge = card.showHdBadge,
                onClick = { onItemClick(card) },
                modifier = itemModifier
            )
        }
    }
}

@Composable
fun VodMoviePagedGrid(
    pagingItems: LazyPagingItems<VodItem>,
    progressByStreamId: Map<Long, Long>,
    progressFraction: (VodGridCardModel, Map<Long, Long>) -> Float?,
    onItemClick: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    minCellSize: androidx.compose.ui.unit.Dp = 112.dp,
    firstItemFocusRequester: FocusRequester? = null,
    contentGridFocusRequester: FocusRequester? = null,
    onNavigateUpFromFirstRow: (() -> Unit)? = null
) {
    val gridFocusRequester = contentGridFocusRequester ?: firstItemFocusRequester

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { movie -> "${movie.playlistId}_${movie.streamId}" }
        ) { index ->
            val movie = pagingItems[index] ?: return@items
            val card = movie.toGridCardModel()
            val itemModifier = if (index == 0 && gridFocusRequester != null) {
                Modifier
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
            } else {
                Modifier
            }
            VodPosterCard(
                title = card.title,
                posterUrl = card.posterUrl,
                progressFraction = progressFraction(card, progressByStreamId),
                showHdBadge = card.showHdBadge,
                onClick = { onItemClick(movie) },
                modifier = itemModifier
            )
        }
    }
}
