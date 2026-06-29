package com.grid.tv.ui.screen

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
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.vodEmptyMessage
import com.grid.tv.domain.model.vodEmptyTitle
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import com.grid.tv.ui.component.VodCatalogProgressBar
import com.grid.tv.ui.component.VodCatalogRefreshWarningBanner
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.ui.component.VodGridCardModel
import com.grid.tv.ui.component.VodInlineSearchContent
import com.grid.tv.ui.component.VodMoviePagedGrid
import com.grid.tv.ui.component.VodPagedVerticalGrid
import com.grid.tv.ui.component.rememberVodCatalogOnboardingVisible
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel

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
    restoreScrollIndex: Int = -1,
    restoreScrollOffset: Int = 0,
    gridRestoreRequest: VodGridFocusRestoreRequest? = null,
    onGridRestoreComplete: (Int) -> Unit = {},
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

    val moviesLoading = catalogProgress.isLoading && !catalogProgress.isMoviesPhaseComplete
    val pagingRefreshing = moviePagingItems.loadState.refresh is LoadState.Loading
    val showVodOnboarding = rememberVodCatalogOnboardingVisible(
        VodCatalogOnboardingInputs(
            catalogLoading = catalogLoading,
            progress = catalogProgress,
            tab = VodCatalogOnboardingTab.MOVIES,
            browseRowCount = browseRows.size,
            categoryCount = categories.size,
            pagedItemCount = moviePagingItems.itemCount
        )
    )
    val emptyReason = catalogStatus.moviesEmptyReason(
        filteredCount = filteredTotalCount,
        catalogTotal = catalogTotalCount,
        categoryId = selectedCategoryId,
        searchQuery = ""
    )
    val refreshWarning = catalogStatus.moviesRefreshWarning(catalogTotalCount)
    val showEmptyGrid = moviePagingItems.itemCount == 0 &&
        !moviesLoading &&
        !pagingRefreshing &&
        catalogProgress.moviesPhaseFinished

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
        VodCatalogProgressBar(
            progress = catalogProgress.moviesProgressFraction(),
            visible = moviesLoading && !showVodOnboarding
        )
        VodCatalogLoadingBanner(
            baseMessage = "Fetching your provider's movie catalog. Large libraries can take a minute.",
            progress = catalogProgress,
            isMovies = true
        )
        VodCatalogRefreshWarningBanner(message = refreshWarning)

        when {
            showVodOnboarding -> {
                com.grid.tv.ui.component.VodCatalogOnboardingPanel(
                    progress = catalogProgress,
                    onboardingInputs = VodCatalogOnboardingInputs(
                        catalogLoading = catalogLoading,
                        progress = catalogProgress,
                        tab = VodCatalogOnboardingTab.MOVIES,
                        browseRowCount = browseRows.size,
                        categoryCount = categories.size,
                        pagedItemCount = moviePagingItems.itemCount
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            showEmptyGrid -> {
                VodEmptyState(
                    title = emptyReason.vodEmptyTitle(isMovies = true),
                    message = emptyReason.vodEmptyMessage(catalogStatus, isMovies = true),
                    onRetry = if (emptyReason != VodCatalogEmptyReason.FILTERED_EMPTY) {
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
            else -> {
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
    restoreScrollIndex: Int = -1,
    restoreScrollOffset: Int = 0,
    gridRestoreRequest: VodGridFocusRestoreRequest? = null,
    onGridRestoreComplete: (Int) -> Unit = {},
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

    val seriesLoading = catalogProgress.isLoading &&
        catalogProgress.isMoviesPhaseComplete &&
        !catalogProgress.isSeriesPhaseComplete
    val pagingRefreshing = seriesPagingItems.loadState.refresh is LoadState.Loading
    val showVodOnboarding = rememberVodCatalogOnboardingVisible(
        VodCatalogOnboardingInputs(
            catalogLoading = catalogLoading,
            progress = catalogProgress,
            tab = VodCatalogOnboardingTab.SERIES,
            browseRowCount = browseRows.size,
            categoryCount = categories.size,
            pagedItemCount = seriesPagingItems.itemCount
        )
    )
    val emptyReason = catalogStatus.seriesEmptyReason(
        filteredCount = filteredTotalCount,
        catalogTotal = catalogTotalCount,
        category = selectedCategoryId ?: "All",
        searchQuery = ""
    )
    val refreshWarning = catalogStatus.seriesRefreshWarning(catalogTotalCount)
    val showEmptyGrid = seriesPagingItems.itemCount == 0 &&
        !seriesLoading &&
        !pagingRefreshing &&
        (catalogProgress.seriesPhaseFinished || emptyReason != VodCatalogEmptyReason.NOT_LOADED)

    LaunchedEffect(
        emptyReason,
        seriesPagingItems.itemCount,
        filteredTotalCount,
        catalogTotalCount,
        selectedCategoryId,
        seriesLoading,
        catalogProgress.seriesPhaseFinished
    ) {
        android.util.Log.i(
            "VodCatalogPipeline",
            "VodHub Series empty-state: reason=$emptyReason paged=${seriesPagingItems.itemCount} " +
                "filtered=$filteredTotalCount catalog=$catalogTotalCount category=${selectedCategoryId ?: "All"} " +
                "loading=$seriesLoading phaseFinished=${catalogProgress.seriesPhaseFinished}"
        )
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
        VodCatalogProgressBar(
            progress = catalogProgress.seriesProgressFraction(),
            visible = seriesLoading && !showVodOnboarding
        )
        VodCatalogLoadingBanner(
            baseMessage = "Fetching your provider's series catalog. Large libraries can take a minute.",
            progress = catalogProgress,
            isMovies = false
        )
        VodCatalogRefreshWarningBanner(message = refreshWarning)

        when {
            showVodOnboarding -> {
                com.grid.tv.ui.component.VodCatalogOnboardingPanel(
                    progress = catalogProgress,
                    onboardingInputs = VodCatalogOnboardingInputs(
                        catalogLoading = catalogLoading,
                        progress = catalogProgress,
                        tab = VodCatalogOnboardingTab.SERIES,
                        browseRowCount = browseRows.size,
                        categoryCount = categories.size,
                        pagedItemCount = seriesPagingItems.itemCount
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            showEmptyGrid -> {
                VodEmptyState(
                    title = emptyReason.vodEmptyTitle(isMovies = false),
                    message = emptyReason.vodEmptyMessage(catalogStatus, isMovies = false),
                    onRetry = if (emptyReason != VodCatalogEmptyReason.FILTERED_EMPTY) {
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
            else -> {
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
