package com.grid.tv.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.grid.tv.domain.model.VodCatalogEmptyReason
import com.grid.tv.domain.model.SearchUiState
import com.grid.tv.domain.model.SearchSurfaceLogic
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.feature.vod.VodHubBrowseSurfaceInputs
import com.grid.tv.feature.vod.VodHubSurfaceState
import com.grid.tv.feature.vod.VodHubSurfaceStateResolver
import com.grid.tv.ui.component.VodCatalogSkeletonWall
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogProgressBar
import com.grid.tv.ui.component.VodCatalogRefreshWarningBanner
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.ui.component.VodGridCardModel
import com.grid.tv.ui.component.VodInlineSearchContent
import com.grid.tv.ui.component.VodMoviePagedGrid
import com.grid.tv.ui.component.VodPagedVerticalGrid
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.ui.component.VodUnifiedSearchGrid
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel

@Stable
class VodHubBrowseGridHandle {
    var itemCount by mutableIntStateOf(0)
    private var activateAtIndex: (Int) -> Unit = {}
    private var keyAtIndexFn: (Int) -> String? = { null }

    fun bind(
        itemCount: Int,
        activateAtIndex: (Int) -> Unit,
        keyAtIndex: (Int) -> String? = { null },
    ) {
        this.itemCount = itemCount
        this.activateAtIndex = activateAtIndex
        this.keyAtIndexFn = keyAtIndex
    }

    fun activateFocusedIndex(index: Int) {
        if (index < 0 || index >= itemCount) return
        activateAtIndex(index)
    }

    fun contentKeyAt(index: Int): String? {
        if (index < 0 || index >= itemCount) return null
        return keyAtIndexFn(index)
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
    emptyStateRetryFocusRequester: FocusRequester,
    onColumnCountChanged: (Int) -> Unit,
    onNavigateUpFromFirstRow: () -> Unit,
    onLeadingEdgeNavigateLeft: () -> Unit = {},
    restoreScrollIndex: Int = -1,
    restoreScrollOffset: Int = 0,
    gridRestoreRequest: VodGridFocusRestoreRequest? = null,
    onGridRestoreComplete: (Int) -> Unit = {},
    surfaceState: VodHubSurfaceState? = null,
    modifier: Modifier = Modifier
) {
    val moviePagingItems = moviesViewModel.pagedMovies.collectAsLazyPagingItems()
    val catalogProgress by moviesViewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogStatus by moviesViewModel.catalogStatus.collectAsStateWithLifecycle()
    val catalogTotalCount by moviesViewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val filteredTotalCount by moviesViewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val selectedCategoryId by moviesViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val catalogLoading by moviesViewModel.catalogLoading.collectAsStateWithLifecycle()
    val browseRows by moviesViewModel.browseRows.collectAsStateWithLifecycle()
    val categories by moviesViewModel.categories.collectAsStateWithLifecycle()

    val pagingRefreshing = moviePagingItems.loadState.refresh is LoadState.Loading
    val resolvedSurfaceState = surfaceState ?: VodHubSurfaceStateResolver.resolveBrowseTab(
        VodHubBrowseSurfaceInputs(
            tab = VodCatalogOnboardingTab.MOVIES,
            catalogLoading = catalogLoading,
            catalogProgress = catalogProgress,
            catalogStatus = catalogStatus,
            catalogTotalCount = catalogTotalCount,
            filteredTotalCount = filteredTotalCount,
            browseRowCount = browseRows.size,
            categoryCount = categories.size,
            pagedItemCount = moviePagingItems.itemCount,
            pagingRefreshing = pagingRefreshing,
            selectedCategoryId = selectedCategoryId,
        )
    )
    val refreshWarning = catalogStatus.moviesRefreshWarning(catalogTotalCount)

    SideEffect {
        browseGridHandle.bind(
            itemCount = moviePagingItems.itemCount,
            activateAtIndex = { index ->
                if (index in 0 until moviePagingItems.itemCount) {
                    moviePagingItems[index]?.let(onItemClick)
                }
            },
            keyAtIndex = { index ->
                if (index !in 0 until moviePagingItems.itemCount) {
                    null
                } else {
                    moviePagingItems[index]?.let { "${it.playlistId}_${it.streamId}" }
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        VodCatalogRefreshWarningBanner(message = refreshWarning)

        when (resolvedSurfaceState) {
            is VodHubSurfaceState.Loading -> {
                VodCatalogSkeletonWall(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            is VodHubSurfaceState.Empty -> {
                VodEmptyState(
                    title = resolvedSurfaceState.title,
                    message = resolvedSurfaceState.message,
                    onRetry = if (resolvedSurfaceState.canRetry) {
                        { moviesViewModel.refreshCatalog() }
                    } else {
                        null
                    },
                    retryFocusRequester = emptyStateRetryFocusRequester,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            is VodHubSurfaceState.Error -> {
                VodEmptyState(
                    title = resolvedSurfaceState.title,
                    message = resolvedSurfaceState.message,
                    onRetry = if (resolvedSurfaceState.canRetry) {
                        { moviesViewModel.refreshCatalog() }
                    } else {
                        null
                    },
                    retryFocusRequester = emptyStateRetryFocusRequester,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            is VodHubSurfaceState.Ready -> {
                VodMoviePagedGrid(
                    pagingItems = moviePagingItems,
                    progressByKey = progressByKey,
                    progressFraction = { movie, map -> moviesViewModel.progressFraction(movie, map) },
                    onItemClick = onItemClick,
                    gridState = gridState,
                    gridFocused = gridFocused,
                    focusedItemIndex = focusedItemIndex,
                    restoreScrollIndex = restoreScrollIndex,
                    restoreScrollOffset = restoreScrollOffset,
                    gridRestoreRequest = gridRestoreRequest,
                    onGridRestoreComplete = onGridRestoreComplete,
                    contentGridFocusRequester = contentGridFocusRequester,
                    onColumnCountChanged = onColumnCountChanged,
                    onNavigateUpFromFirstRow = onNavigateUpFromFirstRow,
                    onLeadingEdgeNavigateLeft = onLeadingEdgeNavigateLeft,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
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
    emptyStateRetryFocusRequester: FocusRequester,
    onColumnCountChanged: (Int) -> Unit,
    onNavigateUpFromFirstRow: () -> Unit,
    onLeadingEdgeNavigateLeft: () -> Unit = {},
    restoreScrollIndex: Int = -1,
    restoreScrollOffset: Int = 0,
    gridRestoreRequest: VodGridFocusRestoreRequest? = null,
    onGridRestoreComplete: (Int) -> Unit = {},
    surfaceState: VodHubSurfaceState? = null,
    isSeriesStillLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val seriesPagingItems = seriesViewModel.pagedSeries.collectAsLazyPagingItems()
    val catalogProgress by seriesViewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogStatus by seriesViewModel.catalogStatus.collectAsStateWithLifecycle()
    val catalogTotalCount by seriesViewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val filteredTotalCount by seriesViewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val selectedCategoryId by seriesViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val catalogLoading by seriesViewModel.catalogLoading.collectAsStateWithLifecycle()
    val browseRows by seriesViewModel.browseRows.collectAsStateWithLifecycle()
    val categories by seriesViewModel.categories.collectAsStateWithLifecycle()
    val preferredLanguages by seriesViewModel.preferredLanguages.collectAsStateWithLifecycle()

    val pagingRefreshing = seriesPagingItems.loadState.refresh is LoadState.Loading
    val resolvedSurfaceState = surfaceState ?: VodHubSurfaceStateResolver.resolveBrowseTab(
        VodHubBrowseSurfaceInputs(
            tab = VodCatalogOnboardingTab.SERIES,
            catalogLoading = catalogLoading,
            catalogProgress = catalogProgress,
            catalogStatus = catalogStatus,
            catalogTotalCount = catalogTotalCount,
            filteredTotalCount = filteredTotalCount,
            browseRowCount = browseRows.size,
            categoryCount = categories.size,
            pagedItemCount = seriesPagingItems.itemCount,
            pagingRefreshing = pagingRefreshing,
            selectedCategoryId = selectedCategoryId,
            languageFilterActive = preferredLanguages.isNotEmpty(),
            isSeriesStillLoading = isSeriesStillLoading,
        )
    )
    val refreshWarning = catalogStatus.seriesRefreshWarning(catalogTotalCount)

    LaunchedEffect(
        resolvedSurfaceState,
        seriesPagingItems.itemCount,
        filteredTotalCount,
        catalogTotalCount,
        selectedCategoryId,
        catalogProgress.seriesPhaseFinished
    ) {
        if (resolvedSurfaceState is VodHubSurfaceState.Empty) {
            android.util.Log.i(
                "VodCatalogPipeline",
                "VodHub Series empty-state: reason=${resolvedSurfaceState.reason} paged=${seriesPagingItems.itemCount} " +
                    "filtered=$filteredTotalCount catalog=$catalogTotalCount category=${selectedCategoryId ?: "All"} " +
                    "phaseFinished=${catalogProgress.seriesPhaseFinished}"
            )
        }
    }

    SideEffect {
        browseGridHandle.bind(
            itemCount = seriesPagingItems.itemCount,
            activateAtIndex = { index ->
                if (index in 0 until seriesPagingItems.itemCount) {
                    seriesPagingItems[index]?.let { show ->
                        onSeriesCardClick(show.toGridCardModel())
                    }
                }
            },
            keyAtIndex = { index ->
                if (index !in 0 until seriesPagingItems.itemCount) {
                    null
                } else {
                    seriesPagingItems[index]?.let { "${it.playlistId}_${it.id}" }
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        VodCatalogRefreshWarningBanner(message = refreshWarning)
        VodCatalogProgressBar(
            progress = catalogProgress.seriesProgressFraction(),
            visible = isSeriesStillLoading,
        )
        VodCatalogLoadingBanner(
            baseMessage = "Organizing series",
            progress = catalogProgress,
            isMovies = false,
        )

        when (resolvedSurfaceState) {
            is VodHubSurfaceState.Loading -> {
                VodCatalogSkeletonWall(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            is VodHubSurfaceState.Empty -> {
                VodEmptyState(
                    title = resolvedSurfaceState.title,
                    message = resolvedSurfaceState.message,
                    onRetry = if (resolvedSurfaceState.canRetry) {
                        { seriesViewModel.refreshCatalog() }
                    } else {
                        null
                    },
                    retryFocusRequester = emptyStateRetryFocusRequester,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            is VodHubSurfaceState.Error -> {
                VodEmptyState(
                    title = resolvedSurfaceState.title,
                    message = resolvedSurfaceState.message,
                    onRetry = if (resolvedSurfaceState.canRetry) {
                        { seriesViewModel.refreshCatalog() }
                    } else {
                        null
                    },
                    retryFocusRequester = emptyStateRetryFocusRequester,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            is VodHubSurfaceState.Ready -> {
                VodPagedVerticalGrid(
                    pagingItems = seriesPagingItems,
                    progressByKey = progressByKey,
                    progressFraction = { _, _ -> null },
                    onItemClick = onSeriesCardClick,
                    gridState = gridState,
                    gridFocused = gridFocused,
                    focusedItemIndex = focusedItemIndex,
                    restoreScrollIndex = restoreScrollIndex,
                    restoreScrollOffset = restoreScrollOffset,
                    gridRestoreRequest = gridRestoreRequest,
                    onGridRestoreComplete = onGridRestoreComplete,
                    contentGridFocusRequester = contentGridFocusRequester,
                    onColumnCountChanged = onColumnCountChanged,
                    onNavigateUpFromFirstRow = onNavigateUpFromFirstRow,
                    onLeadingEdgeNavigateLeft = onLeadingEdgeNavigateLeft,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun VodHubSearchSection(
    query: String,
    hubViewModel: VodHubViewModel,
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
            unifiedPagingItems = null,
            moviePagingItems = null,
            seriesPagingItems = null,
            progressByKey = progressByKey,
            movieProgressFraction = { movie, map -> hubViewModel.movieProgressFraction(movie, map) },
            onMovieClick = onMovieClick,
            onSeriesCardClick = onSeriesCardClick,
            onFocusSearchField = onFocusSearchField,
            onFocusResults = onFocusResults,
            onNavigateUpFromResults = onNavigateUpFromResults,
            modifier = modifier
        )
        return
    }

    val unifiedPagingItems = hubViewModel.pagedVodSearch.collectAsLazyPagingItems()
    val isRefreshLoading = unifiedPagingItems.loadState.refresh is LoadState.Loading
    val searchUiState = SearchSurfaceLogic.pagedSearchState(
        query = query,
        isRefreshLoading = isRefreshLoading,
        itemCount = unifiedPagingItems.itemCount,
        lastCompletedQuery = if (!isRefreshLoading) query else "",
    )
    val hasResults = searchUiState.hasAnyResults

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
        unifiedPagingItems = unifiedPagingItems,
        moviePagingItems = null,
        seriesPagingItems = null,
        searchUiState = searchUiState,
        progressByKey = progressByKey,
        movieProgressFraction = { movie, map -> hubViewModel.movieProgressFraction(movie, map) },
        onMovieClick = onMovieClick,
        onSeriesCardClick = onSeriesCardClick,
        onFocusSearchField = onFocusSearchField,
        onFocusResults = onFocusResults,
        onNavigateUpFromResults = onNavigateUpFromResults,
        modifier = modifier
    )
}
