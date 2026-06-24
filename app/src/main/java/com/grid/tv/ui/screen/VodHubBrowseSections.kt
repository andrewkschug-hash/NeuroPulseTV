package com.grid.tv.ui.screen

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.component.VodGridCardModel
import com.grid.tv.ui.component.VodInlineSearchContent
import com.grid.tv.ui.component.VodMoviePagedGrid
import com.grid.tv.ui.component.VodPagedVerticalGrid
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel

@Stable
class VodHubBrowseGridHandle {
    var itemCount by mutableIntStateOf(0)
    private var activateAtIndex: (Int) -> Unit = {}

    fun bind(itemCount: Int, activateAtIndex: (Int) -> Unit) {
        this.itemCount = itemCount
        this.activateAtIndex = activateAtIndex
    }

    fun activateFocusedIndex(index: Int) {
        activateAtIndex(index)
    }
}

@Composable
fun VodHubMoviesBrowseSection(
    moviesViewModel: MoviesViewModel,
    progressByKey: Map<Pair<Long, Long>, Long>,
    onItemClick: (VodItem) -> Unit,
    gridState: LazyGridState,
    gridFocused: Boolean,
    focusedItemIndex: Int,
    browseGridHandle: VodHubBrowseGridHandle,
    contentGridFocusRequester: FocusRequester,
    onColumnCountChanged: (Int) -> Unit,
    onNavigateUpFromFirstRow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moviePagingItems = moviesViewModel.pagedMovies.collectAsLazyPagingItems()

    SideEffect {
        browseGridHandle.bind(moviePagingItems.itemCount) { index ->
            moviePagingItems[index]?.let(onItemClick)
        }
    }

    VodMoviePagedGrid(
        pagingItems = moviePagingItems,
        progressByKey = progressByKey,
        progressFraction = { movie, map -> moviesViewModel.progressFraction(movie, map) },
        onItemClick = onItemClick,
        gridState = gridState,
        gridFocused = gridFocused,
        focusedItemIndex = focusedItemIndex,
        contentGridFocusRequester = contentGridFocusRequester,
        onColumnCountChanged = onColumnCountChanged,
        onNavigateUpFromFirstRow = onNavigateUpFromFirstRow,
        modifier = modifier
    )
}

@Composable
fun VodHubSeriesBrowseSection(
    seriesViewModel: SeriesViewModel,
    progressByKey: Map<Pair<Long, Long>, Long>,
    onSeriesCardClick: (VodGridCardModel) -> Unit,
    gridState: LazyGridState,
    gridFocused: Boolean,
    focusedItemIndex: Int,
    browseGridHandle: VodHubBrowseGridHandle,
    contentGridFocusRequester: FocusRequester,
    onColumnCountChanged: (Int) -> Unit,
    onNavigateUpFromFirstRow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seriesPagingItems = seriesViewModel.pagedSeries.collectAsLazyPagingItems()

    SideEffect {
        browseGridHandle.bind(seriesPagingItems.itemCount) { index ->
            seriesPagingItems[index]?.let { show ->
                onSeriesCardClick(show.toGridCardModel())
            }
        }
    }

    VodPagedVerticalGrid(
        pagingItems = seriesPagingItems,
        progressByKey = progressByKey,
        progressFraction = { _, _ -> null },
        onItemClick = onSeriesCardClick,
        gridState = gridState,
        gridFocused = gridFocused,
        focusedItemIndex = focusedItemIndex,
        contentGridFocusRequester = contentGridFocusRequester,
        onColumnCountChanged = onColumnCountChanged,
        onNavigateUpFromFirstRow = onNavigateUpFromFirstRow,
        modifier = modifier
    )
}

@Composable
fun VodHubSearchSection(
    query: String,
    moviesViewModel: MoviesViewModel,
    seriesViewModel: SeriesViewModel,
    progressByKey: Map<Pair<Long, Long>, Long>,
    onQueryChange: (String) -> Unit,
    onMovieClick: (VodItem) -> Unit,
    onSeriesCardClick: (VodGridCardModel) -> Unit,
    searchFocusRequester: FocusRequester,
    resultsFocusRequester: FocusRequester,
    onFocusSearchField: () -> Unit,
    onFocusResults: () -> Unit,
    onNavigateUpFromResults: () -> Unit,
    onHasResultsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (query.isBlank()) {
        LaunchedEffect(Unit) { onHasResultsChange(false) }
        VodInlineSearchContent(
            query = query,
            placeholder = "Search movies and series…",
            onQueryChange = onQueryChange,
            searchFocusRequester = searchFocusRequester,
            resultsFocusRequester = resultsFocusRequester,
            contentFilter = VodContentFilter.ALL,
            moviePagingItems = null,
            seriesPagingItems = null,
            progressByKey = progressByKey,
            movieProgressFraction = { _, _ -> null },
            onMovieClick = onMovieClick,
            onSeriesCardClick = onSeriesCardClick,
            onFocusSearchField = onFocusSearchField,
            onFocusResults = onFocusResults,
            onNavigateUpFromResults = onNavigateUpFromResults,
            modifier = modifier
        )
        return
    }

    val moviePagingItems = moviesViewModel.pagedMovies.collectAsLazyPagingItems()
    val seriesPagingItems = seriesViewModel.pagedSeries.collectAsLazyPagingItems()
    val hasResults = moviePagingItems.itemCount > 0 || seriesPagingItems.itemCount > 0

    LaunchedEffect(hasResults) {
        onHasResultsChange(hasResults)
    }

    VodInlineSearchContent(
        query = query,
        placeholder = "Search movies and series…",
        onQueryChange = onQueryChange,
        searchFocusRequester = searchFocusRequester,
        resultsFocusRequester = resultsFocusRequester,
        contentFilter = VodContentFilter.ALL,
        moviePagingItems = moviePagingItems,
        seriesPagingItems = seriesPagingItems,
        progressByKey = progressByKey,
        movieProgressFraction = { movie, map -> moviesViewModel.progressFraction(movie, map) },
        onMovieClick = onMovieClick,
        onSeriesCardClick = onSeriesCardClick,
        onFocusSearchField = onFocusSearchField,
        onFocusResults = onFocusResults,
        onNavigateUpFromResults = onNavigateUpFromResults,
        modifier = modifier
    )
}
