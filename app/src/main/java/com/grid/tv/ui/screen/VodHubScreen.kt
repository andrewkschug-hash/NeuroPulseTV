package com.grid.tv.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.unit.dp
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.domain.model.buildVodWallRows
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.MovieDetailOverlay
import com.grid.tv.ui.component.NetflixContentWallRow
import com.grid.tv.ui.component.resolveMovieOverview
import com.grid.tv.ui.component.VodGenreSidePanel
import com.grid.tv.ui.component.VodMoviePagedGrid
import com.grid.tv.ui.component.VodPagedVerticalGrid
import com.grid.tv.ui.component.runtimeLabelForMovie
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.VodAmbientBackdrop
import com.grid.tv.ui.component.VodContentFilterTabBar
import com.grid.tv.ui.component.VodLanguagePreferenceDialog
import com.grid.tv.ui.component.VodCatalogSkeletonWall
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.ui.component.VodHeroSection
import com.grid.tv.ui.component.VodInlineSearchContent
import com.grid.tv.ui.component.movieMetaSubtitle
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.grid.tv.ui.component.animateScrollToItemIfNeeded
import com.grid.tv.ui.component.animateScrollVodWallRowIntoView
import com.grid.tv.ui.component.scrollVodWallToTop
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.quitAppToHome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VodFocusZone {
    TOP_BAR,
    FILTER_PANEL,
    GENRE_PANEL,
    HERO,
    CONTENT
}

private const val LanguageFilterFocusIndex = 3

private val vodManualFocusZones = setOf(
    VodFocusZone.TOP_BAR,
    VodFocusZone.FILTER_PANEL,
    VodFocusZone.GENRE_PANEL,
    VodFocusZone.HERO
)

private fun isVodTraversalKey(event: KeyEvent): Boolean =
    TvImeKeyDispatcher.isImeNavigationKey(event.key)

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    profileInitials: String = "?",
    profileAvatarColor: String = com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR,
    onPlayMovie: (String, String, Boolean) -> Unit,
    onPlayUrl: (String, String, Boolean) -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onBack: () -> Unit = {},
    hubViewModel: VodHubViewModel = hiltViewModel(),
    moviesViewModel: MoviesViewModel = hiltViewModel(),
    seriesViewModel: SeriesViewModel = hiltViewModel()
) {
    var focusZone by remember { mutableStateOf(VodFocusZone.FILTER_PANEL) }
    var topBarFocusIndex by remember {
        mutableIntStateOf(GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0))
    }
    var filterFocusIndex by remember { mutableIntStateOf(0) }
    var contentRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentColIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentScrollDirection by remember { mutableStateOf(TvLazyFocusScrollDirection.NEUTRAL) }
    var vodSearchFocused by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<VodItem?>(null) }
    var genreFocusIndex by remember { mutableIntStateOf(0) }
    var browseGridFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    var browseGridColumnCount by remember { mutableIntStateOf(4) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val continueWatchingListState = remember { LazyListState() }

    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val context = LocalContext.current
    val livePlayerManager = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .livePlayerManager()
    }
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val continueWatchingItems by hubViewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val imeTypingActive = TvTextInputSession.isActiveState.value
    val heroMovie by hubViewModel.heroMovie.collectAsStateWithLifecycle()
    val heroEnrichment by hubViewModel.heroEnrichment.collectAsStateWithLifecycle()
    val featuredCarousel by hubViewModel.featuredCarousel.collectAsStateWithLifecycle()
    val heroIndex by hubViewModel.heroIndex.collectAsStateWithLifecycle()
    val enrichmentMap by hubViewModel.enrichmentMap.collectAsStateWithLifecycle()
    val vodProgress by hubViewModel.vodProgress.collectAsStateWithLifecycle()
    val searchQuery by hubViewModel.searchQuery.collectAsStateWithLifecycle()
    val contentFilter by hubViewModel.contentFilter.collectAsStateWithLifecycle()
    val preferredVodLanguages by hubViewModel.preferredVodLanguages.collectAsStateWithLifecycle()
    val availableVodLanguages by hubViewModel.availableVodLanguages.collectAsStateWithLifecycle()
    val languageFilterActive = preferredVodLanguages.isNotEmpty()
    val movieBrowseRows by moviesViewModel.browseRows.collectAsStateWithLifecycle()
    val seriesBrowseRows by seriesViewModel.browseRows.collectAsStateWithLifecycle()
    val movieCategories by moviesViewModel.categories.collectAsStateWithLifecycle()
    val seriesCategories by seriesViewModel.categories.collectAsStateWithLifecycle()
    val selectedMovieCategoryId by moviesViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val selectedSeriesCategoryId by seriesViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val catalogProgress by moviesViewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogLoading by moviesViewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogTotalCount by moviesViewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val recommendedForYou by hubViewModel.recommendedForYou.collectAsStateWithLifecycle()
    val trendingNow by hubViewModel.trendingNow.collectAsStateWithLifecycle()
    val moviePagingItems = moviesViewModel.pagedMovies.collectAsLazyPagingItems()
    val seriesPagingItems = seriesViewModel.pagedSeries.collectAsLazyPagingItems()

    LaunchedEffect(
        catalogTotalCount,
        moviePagingItems.itemCount,
        seriesPagingItems.itemCount,
        movieBrowseRows.size,
        seriesBrowseRows.size,
        preferredVodLanguages
    ) {
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubMoviesPaging", moviePagingItems.itemCount)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubSeriesPaging", seriesPagingItems.itemCount)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubMovieBrowseRows", movieBrowseRows.size)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubSeriesBrowseRows", seriesBrowseRows.size)
        if (catalogTotalCount > 0 &&
            moviePagingItems.itemCount == 0 &&
            movieBrowseRows.isEmpty()
        ) {
            com.grid.tv.util.VodCatalogLogger.catalogStageFailure(
                stage = if (preferredVodLanguages.isNotEmpty()) "filter" else "ui",
                reason = if (preferredVodLanguages.isNotEmpty()) {
                    "language_filter=${preferredVodLanguages.joinToString(",")}"
                } else {
                    "paging_and_browse_empty"
                },
                dbMovies = catalogTotalCount,
                filtered = moviePagingItems.itemCount
            )
        }
    }

    val hasBrowseResults = when (contentFilter) {
        VodContentFilter.MOVIES -> moviePagingItems.itemCount > 0
        VodContentFilter.SERIES -> seriesPagingItems.itemCount > 0
        VodContentFilter.ALL -> moviePagingItems.itemCount > 0 || seriesPagingItems.itemCount > 0
    }
    val showInlineSearch = searchQuery.isNotBlank() || vodSearchFocused
    val selectedShowId by seriesViewModel.selectedShowId.collectAsStateWithLifecycle()
    val seriesDetailOpen = selectedShowId != null
    val movieDetailOpen = selectedMovie != null
    val showGenrePanel = searchQuery.isBlank() &&
        (contentFilter == VodContentFilter.MOVIES || contentFilter == VodContentFilter.SERIES)
    val showBrowseGrid = searchQuery.isNotBlank() ||
        contentFilter == VodContentFilter.MOVIES ||
        contentFilter == VodContentFilter.SERIES
    val sidebarMovieCategories = remember(movieCategories, movieBrowseRows) {
        categoriesForSidebar(
            primary = movieCategories,
            browseRows = movieBrowseRows
        )
    }
    val sidebarSeriesCategoriesBundle = remember(seriesCategories, seriesBrowseRows) {
        seriesCategoriesForSidebar(
            primary = seriesCategories,
            browseRows = seriesBrowseRows
        )
    }
    val sidebarSeriesCategories = sidebarSeriesCategoriesBundle.displayCategories
    val seriesCategoryFilterIdsByRepresentativeId =
        sidebarSeriesCategoriesBundle.filterIdsByRepresentativeId
    val genreLabels = remember(contentFilter, sidebarMovieCategories, sidebarSeriesCategories) {
        when (contentFilter) {
            VodContentFilter.MOVIES -> listOf("All") + sidebarMovieCategories.map { it.name }
            VodContentFilter.SERIES -> listOf("All") + sidebarSeriesCategories.map { it.name }
            else -> emptyList()
        }
    }
    val selectedGenreIndex = when (contentFilter) {
        VodContentFilter.MOVIES -> {
            if (selectedMovieCategoryId == null) {
                0
            } else {
                sidebarMovieCategories.indexOfFirst { it.id == selectedMovieCategoryId }
                    .takeIf { it >= 0 }?.plus(1) ?: 0
            }
        }
        VodContentFilter.SERIES -> {
            if (selectedSeriesCategoryId == null) {
                0
            } else {
                sidebarSeriesCategories.indexOfFirst { it.id == selectedSeriesCategoryId }
                    .takeIf { it >= 0 }?.plus(1) ?: 0
            }
        }
        else -> 0
    }
    val scope = rememberCoroutineScope()
    val columnListState = rememberLazyListState()
    val browseGridState = rememberLazyGridState()

    LaunchedEffect(initialTab) {
        hubViewModel.setContentFilter(
            when (initialTab) {
                1 -> VodContentFilter.SERIES
                else -> VodContentFilter.ALL
            }
        )
    }

    LaunchedEffect(searchQuery, contentFilter) {
        when (contentFilter) {
            VodContentFilter.SERIES -> {
                seriesViewModel.setSearchQuery(searchQuery)
                moviesViewModel.setSearchQuery("")
            }
            VodContentFilter.MOVIES -> {
                moviesViewModel.setSearchQuery(searchQuery)
                seriesViewModel.setSearchQuery("")
            }
            VodContentFilter.ALL -> {
                moviesViewModel.setSearchQuery(searchQuery)
                seriesViewModel.setSearchQuery(searchQuery)
            }
        }
    }

    LaunchedEffect(initialSeriesId, continueWatchingItems) {
        if (initialSeriesId == null) return@LaunchedEffect
        val cw = continueWatchingItems.firstOrNull {
            it.contentType == ContinueWatchingContentType.SERIES && it.seriesId == initialSeriesId
        }
        seriesViewModel.selectShow(initialSeriesId, cw?.seasonNumber)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            if (featuredCarousel.size > 1 && focusZone != VodFocusZone.HERO) {
                hubViewModel.advanceHeroCarousel()
            }
        }
    }

    LaunchedEffect(Unit) {
        com.grid.tv.util.PlaybackDiagnostics.logDeviceProfile(context)
        livePlayerManager.stopGuidePreview()
        livePlayerManager.setMode(com.grid.tv.player.LivePlayerManager.Mode.IDLE)
    }

    val wallRows = remember(
        contentFilter,
        continueWatchingItems,
        trendingNow,
        recommendedForYou,
        movieBrowseRows,
        seriesBrowseRows,
        searchQuery
    ) {
        if (searchQuery.isNotBlank()) emptyList()
        else buildVodWallRows(
            filter = contentFilter,
            continueWatching = continueWatchingItems,
            trendingMovies = trendingNow,
            recommendedMovies = recommendedForYou,
            movieBrowseRows = movieBrowseRows,
            seriesBrowseRows = seriesBrowseRows
        )
    }

    LaunchedEffect(wallRows.size) {
        contentRowIndex = contentRowIndex.coerceIn(0, (wallRows.lastIndex).coerceAtLeast(0))
        val maxCol = wallRows.getOrNull(contentRowIndex)?.items?.lastIndex ?: 0
        contentColIndex = contentColIndex.coerceIn(0, maxCol.coerceAtLeast(0))
    }

    LaunchedEffect(wallRows.size, heroMovie, searchQuery) {
        if (searchQuery.isBlank() && wallRows.isEmpty() && focusZone == VodFocusZone.CONTENT) {
            focusZone = if (heroMovie != null) VodFocusZone.HERO else VodFocusZone.FILTER_PANEL
        }
    }

    LaunchedEffect(focusZone, contentRowIndex, heroMovie, showBrowseGrid, searchQuery, wallRows.size, showInlineSearch) {
        if (searchQuery.isNotBlank() || showBrowseGrid || showInlineSearch) return@LaunchedEffect
        when (focusZone) {
            VodFocusZone.HERO -> {
                columnListState.animateScrollVodWallRowIntoView(0, TvLazyFocusScrollDirection.UP)
            }
            VodFocusZone.CONTENT -> {
                val heroOffset = if (heroMovie != null) 1 else 0
                columnListState.animateScrollVodWallRowIntoView(
                    index = (contentRowIndex + heroOffset).coerceAtLeast(0),
                    direction = contentScrollDirection
                )
            }
            VodFocusZone.FILTER_PANEL, VodFocusZone.TOP_BAR -> {
                columnListState.scrollVodWallToTop()
            }
            else -> Unit
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val heroMoreInfoFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val browseGridFocusRequester = remember { FocusRequester() }
    val filterPanelFocusRequester = remember { FocusRequester() }
    val genrePanelFocusRequester = remember { FocusRequester() }
    val inlineSearchFocusRequester = remember { FocusRequester() }
    val movieWatchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(genreLabels.size, selectedGenreIndex) {
        genreFocusIndex = selectedGenreIndex.coerceIn(0, (genreLabels.lastIndex).coerceAtLeast(0))
    }

    fun browseGridItemCount(): Int = when (contentFilter) {
        VodContentFilter.MOVIES -> moviePagingItems.itemCount
        VodContentFilter.SERIES -> seriesPagingItems.itemCount
        VodContentFilter.ALL -> 0
    }

    LaunchedEffect(browseGridItemCount(), contentFilter) {
        val lastIndex = (browseGridItemCount() - 1).coerceAtLeast(0)
        browseGridFocusIndex = browseGridFocusIndex.coerceIn(0, lastIndex)
    }

    fun tabBarVisible(): Boolean = searchQuery.isBlank() && !vodSearchFocused

    LaunchedEffect(
        focusZone,
        showInlineSearch,
        vodSearchFocused,
        heroMovie,
        movieDetailOpen,
        seriesDetailOpen,
        showBrowseGrid,
        hasBrowseResults,
        searchQuery,
        imeTypingActive
    ) {
        if (imeTypingActive) return@LaunchedEffect
        when {
            movieDetailOpen -> movieWatchFocusRequester.requestFocusSafelyAfterLayout()
            seriesDetailOpen -> Unit
            showInlineSearch && vodSearchFocused ->
                inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
            focusZone == VodFocusZone.CONTENT &&
                showInlineSearch &&
                hasBrowseResults &&
                !vodSearchFocused ->
                browseGridFocusRequester.requestFocusSafelyAfterLayout()
            focusZone == VodFocusZone.HERO &&
                heroMovie != null &&
                searchQuery.isBlank() &&
                !showInlineSearch ->
                heroPlayFocusRequester.requestFocusSafelyAfterLayout()
            focusZone == VodFocusZone.FILTER_PANEL ||
                focusZone == VodFocusZone.GENRE_PANEL ||
                focusZone == VodFocusZone.TOP_BAR ||
                focusZone == VodFocusZone.HERO ||
                (focusZone == VodFocusZone.CONTENT && !showInlineSearch) ->
                rootFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun resumeItem(item: ContinueWatchingItem) {
        VodPlaybackHelper.stageContinueWatching(item)
        val resumeMs = VodPlaybackHelper.resumePositionFor(item)
        onPlayUrl(item.title, item.streamUrl, resumeMs > 0L)
    }

    fun openMovieDetail(movie: VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        selectedMovie = movie
    }

    fun closeMovieDetail() {
        selectedMovie = null
    }

    fun playMovie(movie: VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        scope.launch {
            val resume = moviesViewModel.shouldResume(movie.toGridCardModel(), vodProgress)
            VodPlaybackHelper.stageMovie(movie)
            onPlayMovie(movie.title, movie.streamUrl, resume)
        }
    }

    fun watchSelectedMovie() {
        val movie = selectedMovie ?: return
        closeMovieDetail()
        playMovie(movie)
    }

    fun focusGenrePanelFromFilter() {
        focusZone = VodFocusZone.GENRE_PANEL
        scope.launch {
            genrePanelFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusFilterPanelFromGenre() {
        focusZone = VodFocusZone.FILTER_PANEL
        filterFocusIndex = VodContentFilter.entries.indexOf(contentFilter).coerceAtLeast(0)
    }

    fun focusBrowseGridFromSidebar() {
        val count = browseGridItemCount()
        if (count <= 0) return
        focusZone = VodFocusZone.CONTENT
        browseGridFocusIndex = 0
    }

    fun focusBrowseResultsFromTopBar() {
        focusZone = VodFocusZone.CONTENT
        contentRowIndex = 0
        contentColIndex = 0
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusInlineSearchField() {
        vodSearchFocused = true
        focusZone = VodFocusZone.CONTENT
        scope.launch {
            inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusSearchResults() {
        if (!hasBrowseResults) return
        vodSearchFocused = false
        focusZone = VodFocusZone.CONTENT
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun activateInlineSearch() {
        vodSearchFocused = true
        topBarFocusIndex = GridNavTabs.indexOf(EpgNavTab.Search).coerceAtLeast(0)
        focusZone = VodFocusZone.CONTENT
        scope.launch {
            inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun clearInlineSearch() {
        if (searchQuery.isNotBlank()) {
            hubViewModel.setSearchQuery("")
        }
        vodSearchFocused = false
        focusZone = VodFocusZone.TOP_BAR
        topBarFocusIndex = GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0)
    }

    fun focusTopBarFromBrowseGrid() {
        if (showInlineSearch) {
            focusInlineSearchField()
        } else {
            focusZone = VodFocusZone.TOP_BAR
            topBarFocusIndex = GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0)
        }
    }

    fun activateNavTab(tabItem: EpgNavTab) {
        when (tabItem) {
            EpgNavTab.Guide, EpgNavTab.Home -> onNavigateHome()
            EpgNavTab.Search -> activateInlineSearch()
            EpgNavTab.Vod, EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(1)
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Settings -> onNavigateSettings()
        }
    }

    fun openLanguagePreferenceDialog() {
        hubViewModel.refreshAvailableVodLanguages()
        showLanguageDialog = true
    }

    fun applyPreferredLanguages(languages: Set<String>) {
        hubViewModel.setPreferredVodLanguages(languages)
    }

    fun applyContentFilter(index: Int) {
        if (index == LanguageFilterFocusIndex) {
            openLanguagePreferenceDialog()
            return
        }
        val filter = VodContentFilter.entries.getOrNull(index) ?: VodContentFilter.ALL
        filterFocusIndex = index
        hubViewModel.setContentFilter(filter)
        moviesViewModel.setCategory(null)
        seriesViewModel.setCategory(null)
        genreFocusIndex = 0
        browseGridFocusIndex = 0
        contentRowIndex = 0
        contentColIndex = 0
        focusZone = if (filter == VodContentFilter.ALL) VodFocusZone.CONTENT else VodFocusZone.GENRE_PANEL
        onNavigateVod(
            when (filter) {
                VodContentFilter.SERIES -> 1
                else -> 0
            }
        )
    }

    fun applyGenre(index: Int) {
        genreFocusIndex = index.coerceIn(0, genreLabels.lastIndex.coerceAtLeast(0))
        when (contentFilter) {
            VodContentFilter.MOVIES -> {
                val categoryId = if (index == 0) null else sidebarMovieCategories.getOrNull(index - 1)?.id
                moviesViewModel.setCategory(categoryId)
            }
            VodContentFilter.SERIES -> {
                if (index == 0) {
                    Log.d("VodSeriesGenre", "applyGenre index=0 categoryId=null filterIds=null (All)")
                    seriesViewModel.setCategory(null)
                } else {
                    val category = sidebarSeriesCategories.getOrNull(index - 1)
                    val filterIds = category?.id?.let { seriesCategoryFilterIdsByRepresentativeId[it] }
                    Log.d(
                        "VodSeriesGenre",
                        "applyGenre index=$index representativeId=${category?.id} " +
                            "filterIds=$filterIds name=${category?.name}"
                    )
                    seriesViewModel.setCategory(category?.id, filterIds)
                }
            }
            else -> Unit
        }
        focusZone = VodFocusZone.CONTENT
        browseGridFocusIndex = 0
    }

    fun handleBrowseGridKey(event: KeyEvent): Boolean {
        val itemCount = browseGridItemCount()
        if (itemCount <= 0) return false
        val columns = browseGridColumnCount.coerceAtLeast(1)
        val lastIndex = itemCount - 1
        return when (event.key) {
            Key.DirectionLeft -> {
                if (browseGridFocusIndex % columns == 0) {
                    focusZone = if (showGenrePanel) {
                        VodFocusZone.GENRE_PANEL
                    } else {
                        VodFocusZone.FILTER_PANEL
                    }
                    if (!showGenrePanel) {
                        filterFocusIndex = VodContentFilter.entries.indexOf(contentFilter).coerceAtLeast(0)
                    }
                } else {
                    browseGridFocusIndex -= 1
                }
                true
            }
            Key.DirectionRight -> {
                val column = browseGridFocusIndex % columns
                if (column < columns - 1 && browseGridFocusIndex < lastIndex) {
                    browseGridFocusIndex += 1
                }
                true
            }
            Key.DirectionUp -> {
                if (browseGridFocusIndex >= columns) {
                    browseGridFocusIndex -= columns
                } else if (tabBarVisible()) {
                    focusFilterPanelFromGenre()
                } else {
                    focusZone = VodFocusZone.TOP_BAR
                }
                true
            }
            Key.DirectionDown -> {
                val next = browseGridFocusIndex + columns
                if (next <= lastIndex) {
                    browseGridFocusIndex = next
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (contentFilter) {
                    VodContentFilter.MOVIES ->
                        moviePagingItems[browseGridFocusIndex]?.let(::openMovieDetail)
                    VodContentFilter.SERIES ->
                        seriesPagingItems[browseGridFocusIndex]?.let { show ->
                            seriesViewModel.selectShow(show.id, null)
                        }
                    else -> Unit
                }
                true
            }
            else -> false
        }
    }

    fun ratingFor(movie: VodItem): String? =
        hubViewModel.displayRating(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun posterFor(movie: VodItem): String? {
        val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
        return enrichment?.posterUrl?.takeIf { it.isNotBlank() } ?: movie.posterUrl?.takeIf { it.isNotBlank() }
    }

    fun metaFor(movie: VodItem): String? = movieMetaSubtitle(movie, ratingFor(movie))

    fun overviewForMovie(movie: VodItem): String? =
        resolveMovieOverview(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun overviewForSeries(show: SeriesShow): String? {
        val enrichment = if (show.playlistId > 0L) {
            val key = com.grid.tv.feature.enrichment.TitleEnrichmentRepository.xtreamSeriesKey(
                show.playlistId,
                show.id
            )
            enrichmentMap[key]
        } else {
            null
        }
        return enrichment?.overview?.takeIf { it.isNotBlank() }
            ?: show.plot?.takeIf { it.isNotBlank() }
            ?: show.genre?.takeIf { it.isNotBlank() }
    }

    fun activateWallItem(item: VodWallItem) {
        when (item) {
            is VodWallItem.ContinueItem -> resumeItem(item.item)
            is VodWallItem.MovieItem -> openMovieDetail(item.movie)
            is VodWallItem.SeriesItem -> seriesViewModel.selectShow(item.show.id, null, preview = item.show)
        }
    }

    fun handleFilterPanelKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (filterFocusIndex > 0) {
                    filterFocusIndex -= 1
                } else {
                    focusZone = VodFocusZone.TOP_BAR
                }
                true
            }
            Key.DirectionRight -> {
                if (filterFocusIndex < LanguageFilterFocusIndex) {
                    filterFocusIndex += 1
                } else {
                    when {
                        showGenrePanel -> focusGenrePanelFromFilter()
                        showBrowseGrid -> focusBrowseGridFromSidebar()
                        heroMovie != null && searchQuery.isBlank() -> focusZone = VodFocusZone.HERO
                        wallRows.isNotEmpty() -> {
                            focusZone = VodFocusZone.CONTENT
                            contentRowIndex = 0
                            contentColIndex = 0
                            contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                        }
                    }
                }
                true
            }
            Key.DirectionDown -> {
                when {
                    showGenrePanel -> focusGenrePanelFromFilter()
                    showBrowseGrid -> focusBrowseGridFromSidebar()
                    heroMovie != null && searchQuery.isBlank() -> focusZone = VodFocusZone.HERO
                    wallRows.isNotEmpty() -> {
                        focusZone = VodFocusZone.CONTENT
                        contentRowIndex = 0
                        contentColIndex = 0
                        contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                    }
                }
                true
            }
            Key.DirectionUp -> {
                focusZone = VodFocusZone.TOP_BAR
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                applyContentFilter(filterFocusIndex)
                true
            }
            else -> false
        }
    }

    fun handleGenrePanelKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (genreLabels.isEmpty()) return false
        return when (event.key) {
            Key.DirectionUp -> {
                if (genreFocusIndex > 0) {
                    genreFocusIndex -= 1
                } else {
                    focusFilterPanelFromGenre()
                }
                true
            }
            Key.DirectionDown -> {
                genreFocusIndex = (genreFocusIndex + 1).coerceAtMost(genreLabels.lastIndex)
                true
            }
            Key.DirectionLeft -> {
                focusZone = VodFocusZone.FILTER_PANEL
                true
            }
            Key.DirectionRight -> {
                focusBrowseGridFromSidebar()
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                applyGenre(genreFocusIndex)
                true
            }
            else -> false
        }
    }

    fun handleHeroKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (featuredCarousel.size > 1) {
                    val next = (heroIndex - 1 + featuredCarousel.size) % featuredCarousel.size
                    hubViewModel.setHeroIndex(next)
                }
                true
            }
            Key.DirectionRight -> {
                if (featuredCarousel.size > 1) {
                    val next = (heroIndex + 1) % featuredCarousel.size
                    hubViewModel.setHeroIndex(next)
                }
                true
            }
            Key.DirectionDown -> {
                focusZone = VodFocusZone.CONTENT
                contentRowIndex = 0
                contentColIndex = 0
                contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                true
            }
            Key.DirectionUp -> {
                focusZone = if (tabBarVisible()) {
                    VodFocusZone.FILTER_PANEL
                } else {
                    VodFocusZone.TOP_BAR
                }
                true
            }
            else -> false
        }
    }

    fun handleContentKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (showInlineSearch) {
            if (vodSearchFocused) return false
            if (!showBrowseGrid) {
                return when (event.key) {
                    Key.DirectionUp -> {
                        focusInlineSearchField()
                        scope.launch {
                            inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        if (showBrowseGrid) {
            if (!showInlineSearch) {
                return handleBrowseGridKey(event)
            }
            return when (event.key) {
                Key.DirectionLeft -> {
                    focusZone = when {
                        showGenrePanel -> VodFocusZone.GENRE_PANEL
                        searchQuery.isBlank() -> VodFocusZone.FILTER_PANEL
                        else -> VodFocusZone.TOP_BAR
                    }
                    scope.launch {
                        when (focusZone) {
                            VodFocusZone.GENRE_PANEL ->
                                genrePanelFocusRequester.requestFocusSafelyAfterLayout()
                            VodFocusZone.FILTER_PANEL ->
                                filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                            else -> Unit
                        }
                    }
                    true
                }
                Key.DirectionUp -> {
                    focusZone = when {
                        !showBrowseGrid && heroMovie != null && searchQuery.isBlank() -> VodFocusZone.HERO
                        searchQuery.isBlank() -> VodFocusZone.FILTER_PANEL
                        else -> VodFocusZone.TOP_BAR
                    }
                    true
                }
                else -> false
            }
        }
        if (wallRows.isEmpty()) {
            return when (event.key) {
                Key.DirectionLeft -> {
                    focusZone = VodFocusZone.FILTER_PANEL
                    true
                }
                Key.DirectionUp -> {
                    focusZone = VodFocusZone.TOP_BAR
                    true
                }
                else -> false
            }
        }
        val row = wallRows.getOrNull(contentRowIndex) ?: return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (contentColIndex > 0) {
                    contentColIndex -= 1
                } else {
                    focusZone = VodFocusZone.FILTER_PANEL
                    filterFocusIndex = VodContentFilter.entries.indexOf(contentFilter).coerceAtLeast(0)
                }
                true
            }
            Key.DirectionRight -> {
                contentColIndex = (contentColIndex + 1).coerceAtMost(row.items.lastIndex)
                true
            }
            Key.DirectionUp -> {
                if (contentRowIndex > 0) {
                    contentScrollDirection = TvLazyFocusScrollDirection.UP
                    contentRowIndex -= 1
                    val maxCol = wallRows[contentRowIndex].items.lastIndex
                    contentColIndex = contentColIndex.coerceAtMost(maxCol)
                } else if (heroMovie != null && searchQuery.isBlank()) {
                    focusZone = VodFocusZone.HERO
                } else if (tabBarVisible()) {
                    focusZone = VodFocusZone.FILTER_PANEL
                } else {
                    focusZone = VodFocusZone.TOP_BAR
                }
                true
            }
            Key.DirectionDown -> {
                if (contentRowIndex < wallRows.lastIndex) {
                    contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                    contentRowIndex += 1
                    val maxCol = wallRows[contentRowIndex].items.lastIndex
                    contentColIndex = contentColIndex.coerceAtMost(maxCol)
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                row.items.getOrNull(contentColIndex)?.let(::activateWallItem)
                true
            }
            else -> false
        }
    }

    fun handleTopBarKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                else -> false
            }
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                true
            }
            Key.DirectionDown -> {
                when {
                    topBarFocusIndex == GridNavTabs.indexOf(EpgNavTab.Search) -> activateInlineSearch()
                    showInlineSearch && hasBrowseResults -> focusSearchResults()
                    tabBarVisible() -> focusZone = VodFocusZone.FILTER_PANEL
                    showBrowseGrid && hasBrowseResults -> focusBrowseResultsFromTopBar()
                    heroMovie != null && searchQuery.isBlank() -> focusZone = VodFocusZone.HERO
                    wallRows.isNotEmpty() -> {
                        focusZone = VodFocusZone.CONTENT
                        contentRowIndex = 0
                        contentColIndex = 0
                    }
                    searchQuery.isBlank() -> {
                        focusZone = when {
                            showBrowseGrid && showGenrePanel -> VodFocusZone.GENRE_PANEL
                            else -> VodFocusZone.FILTER_PANEL
                        }
                    }
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when {
                    topBarFocusIndex == GridNavTabs.indexOf(EpgNavTab.Search) -> {
                        activateInlineSearch()
                        true
                    }
                    topBarFocusIndex in GridNavTabs.indices -> {
                        activateNavTab(GridNavTabs[topBarFocusIndex])
                        true
                    }
                    topBarFocusIndex == TopBarProfileIndex -> {
                        profileMenuOpen = true
                        profileMenuFocusIndex = 0
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    fun consumeVodLocalBack(): Boolean = when {
        movieDetailOpen -> {
            closeMovieDetail()
            true
        }
        seriesDetailOpen -> {
            seriesViewModel.clearShowSelection()
            true
        }
        showInlineSearch -> {
            clearInlineSearch()
            true
        }
        profileMenuOpen -> {
            profileMenuOpen = false
            true
        }
        focusZone == VodFocusZone.CONTENT || focusZone == VodFocusZone.HERO ||
            focusZone == VodFocusZone.FILTER_PANEL || focusZone == VodFocusZone.GENRE_PANEL -> {
            focusZone = VodFocusZone.TOP_BAR
            topBarFocusIndex = if (showInlineSearch) {
                GridNavTabs.indexOf(EpgNavTab.Search).coerceAtLeast(0)
            } else {
                GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0)
            }
            true
        }
        else -> false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    val ambientPosterUrl = when {
        searchQuery.isNotBlank() || showBrowseGrid -> null
        focusZone == VodFocusZone.HERO -> heroMovie?.let { posterFor(it) }
        focusZone == VodFocusZone.CONTENT && wallRows.isNotEmpty() -> {
            when (val item = wallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex)) {
                is VodWallItem.ContinueItem -> item.item.posterUrl
                is VodWallItem.MovieItem -> posterFor(item.movie)
                is VodWallItem.SeriesItem -> item.show.coverUrl
                null -> null
            }
        }
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable(
                enabled = !seriesDetailOpen &&
                    !movieDetailOpen &&
                    !imeTypingActive &&
                    !(showInlineSearch && vodSearchFocused)
            )
            .focusProperties {
                if (seriesDetailOpen || movieDetailOpen) {
                    canFocus = false
                }
            }
            .onPreviewKeyEvent { event ->
                if (seriesDetailOpen || movieDetailOpen) {
                    return@onPreviewKeyEvent false
                }
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) {
                    return@onPreviewKeyEvent false
                }
                if (showInlineSearch && vodSearchFocused && !imeTypingActive) {
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        return@onPreviewKeyEvent consumeVodLocalBack()
                    }
                    return@onPreviewKeyEvent false
                }
                val handled = when (event.key) {
                    Key.Back, Key.Escape -> consumeVodLocalBack()
                    else -> when (focusZone) {
                        VodFocusZone.TOP_BAR -> handleTopBarKey(event)
                        VodFocusZone.FILTER_PANEL -> handleFilterPanelKey(event)
                        VodFocusZone.GENRE_PANEL -> handleGenrePanelKey(event)
                        VodFocusZone.HERO -> handleHeroKey(event)
                        VodFocusZone.CONTENT -> handleContentKey(event)
                    }
                }
                if (handled) return@onPreviewKeyEvent true
                if (imeTypingActive) return@onPreviewKeyEvent false
                if (focusZone in vodManualFocusZones && isVodTraversalKey(event)) {
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        VodAmbientBackdrop(posterUrl = ambientPosterUrl)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (seriesDetailOpen || movieDetailOpen) {
                        Modifier.focusProperties { canFocus = false }
                    } else {
                        Modifier
                    }
                )
        ) {
            EpgTopBar(
                selectedTab = EpgNavTab.Vod,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == VodFocusZone.TOP_BAR &&
                    topBarFocusIndex in GridNavTabs.indices,
                profileFocused = focusZone == VodFocusZone.TOP_BAR &&
                    topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                profileAvatarColor = profileAvatarColor,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                onProfileClick = {
                    focusZone = VodFocusZone.TOP_BAR
                    topBarFocusIndex = TopBarProfileIndex
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                },
                onSwitchAccounts = {
                    profileMenuOpen = false
                    onNavigateProfile()
                },
                onOpenSettings = {
                    profileMenuOpen = false
                    onNavigateSettings()
                },
                onQuitApp = { context.quitAppToHome() },
                onProfileMenuDismiss = { profileMenuOpen = false },
                onTabSelected = { tabItem ->
                    if (tabItem == EpgNavTab.Search) {
                        activateInlineSearch()
                    } else {
                        focusZone = VodFocusZone.TOP_BAR
                        topBarFocusIndex = GridNavTabs.indexOf(tabItem).coerceAtLeast(0)
                        activateNavTab(tabItem)
                    }
                },
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                miniPlayer = {},
                modifier = Modifier.fillMaxWidth()
            )

            if (searchQuery.isBlank() && !vodSearchFocused) {
                VodContentFilterTabBar(
                    selectedFilter = contentFilter,
                    focusedFilter = VodContentFilter.entries.getOrNull(filterFocusIndex) ?: contentFilter,
                    barFocused = focusZone == VodFocusZone.FILTER_PANEL,
                    languageFilterActive = languageFilterActive,
                    languageFilterFocused = focusZone == VodFocusZone.FILTER_PANEL &&
                        filterFocusIndex == LanguageFilterFocusIndex,
                    onLanguageFilterClick = ::openLanguagePreferenceDialog,
                    onFilterSelected = { filter ->
                        applyContentFilter(VodContentFilter.entries.indexOf(filter).coerceAtLeast(0))
                    }
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (imeTypingActive) {
                            Modifier.focusProperties { canFocus = false }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (showGenrePanel && !showInlineSearch) {
                    VodGenreSidePanel(
                        genres = genreLabels,
                        selectedIndex = selectedGenreIndex,
                        focusedIndex = genreFocusIndex,
                        panelFocused = focusZone == VodFocusZone.GENRE_PANEL,
                        contentGridFocusRequester = browseGridFocusRequester,
                        entryFocusRequester = genrePanelFocusRequester,
                        onFocusedIndexChange = { genreFocusIndex = it },
                        onGenreSelected = ::applyGenre,
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                val catalogSyncActive = catalogLoading || catalogProgress.isLoading
                val movieProgressFraction = { card: com.grid.tv.ui.component.VodGridCardModel, map: Map<Long, Long> ->
                    moviePagingItems.itemSnapshotList.items
                        .firstOrNull { it.streamId == card.streamId }
                        ?.let { moviesViewModel.progressFraction(it, map) }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    when {
                        showInlineSearch -> {
                            VodInlineSearchContent(
                                query = searchQuery,
                                placeholder = "Search movies and series…",
                                onQueryChange = hubViewModel::setSearchQuery,
                                searchFocusRequester = inlineSearchFocusRequester,
                                resultsFocusRequester = browseGridFocusRequester,
                                contentFilter = contentFilter,
                                moviePagingItems = moviePagingItems,
                                seriesPagingItems = seriesPagingItems,
                                progressByStreamId = vodProgress,
                                movieProgressFraction = movieProgressFraction,
                                onMovieClick = ::openMovieDetail,
                                onSeriesCardClick = { card ->
                                    seriesViewModel.selectShow(card.showId, null)
                                },
                                onFocusSearchField = { vodSearchFocused = true },
                                onFocusResults = ::focusSearchResults,
                                onNavigateUpFromResults = ::focusInlineSearchField,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        contentFilter == VodContentFilter.MOVIES -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (catalogSyncActive && moviePagingItems.itemCount == 0) {
                                    VodCatalogSkeletonWall(modifier = Modifier.fillMaxSize())
                                }
                                VodMoviePagedGrid(
                                    pagingItems = moviePagingItems,
                                    progressByStreamId = vodProgress,
                                    progressFraction = movieProgressFraction,
                                    onItemClick = ::openMovieDetail,
                                    gridState = browseGridState,
                                    gridFocused = focusZone == VodFocusZone.CONTENT && !showInlineSearch,
                                    focusedItemIndex = browseGridFocusIndex,
                                    onColumnCountChanged = { browseGridColumnCount = it },
                                    onNavigateUpFromFirstRow = ::focusTopBarFromBrowseGrid,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        contentFilter == VodContentFilter.SERIES -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (catalogSyncActive && seriesPagingItems.itemCount == 0) {
                                    VodCatalogSkeletonWall(modifier = Modifier.fillMaxSize())
                                }
                                VodPagedVerticalGrid(
                                    pagingItems = seriesPagingItems,
                                    progressByStreamId = vodProgress,
                                    progressFraction = { _, _ -> null },
                                    onItemClick = { card ->
                                        seriesViewModel.selectShow(card.showId, null)
                                    },
                                    gridState = browseGridState,
                                    gridFocused = focusZone == VodFocusZone.CONTENT && !showInlineSearch,
                                    focusedItemIndex = browseGridFocusIndex,
                                    onColumnCountChanged = { browseGridColumnCount = it },
                                    onNavigateUpFromFirstRow = ::focusTopBarFromBrowseGrid,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        wallRows.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (catalogSyncActive) {
                                    VodCatalogLoadingBanner(
                                        baseMessage = "Syncing your catalog…",
                                        progress = catalogProgress,
                                        isMovies = !catalogProgress.isMoviesPhaseComplete
                                    )
                                }
                                when {
                                    continueWatchingItems.isNotEmpty() -> {
                                        NetflixContentWallRow(
                                            row = VodWallRow(
                                                id = "continue_watching",
                                                title = "Continue Watching",
                                                items = continueWatchingItems.map { VodWallItem.ContinueItem(it) }
                                            ),
                                            rowIndex = 0,
                                            focusedColumn = -1,
                                            rowFocused = false,
                                            listState = continueWatchingListState,
                                            progressByStreamId = vodProgress,
                                            posterUrlForMovie = ::posterFor,
                                            ratingForMovie = ::ratingFor,
                                            metaForMovie = ::metaFor,
                                            overviewForMovie = ::overviewForMovie,
                                            overviewForSeries = ::overviewForSeries,
                                            onActivateItem = ::activateWallItem
                                        )
                                        if (catalogSyncActive || catalogTotalCount == 0) {
                                            VodCatalogSkeletonWall(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    catalogSyncActive || (catalogTotalCount == 0 &&
                                        !catalogProgress.isMoviesPhaseComplete) -> {
                                        VodCatalogSkeletonWall(modifier = Modifier.weight(1f))
                                    }
                                    else -> {
                                        VodEmptyState(
                                            title = "Nothing to watch yet",
                                            message = "Connect a playlist or wait for your catalog to finish syncing.",
                                            onRetry = { moviesViewModel.refreshCatalog() }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val showHero = !showBrowseGrid && searchQuery.isBlank() && heroMovie != null
                            LazyColumn(
                                state = columnListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp)
                            ) {
                                if (showHero) {
                                    item(key = "vod_hero") {
                                        heroMovie?.let { hero ->
                                            Box(
                                                modifier = Modifier.onPreviewKeyEvent { event ->
                                                    focusZone == VodFocusZone.HERO && handleHeroKey(event)
                                                }
                                            ) {
                                                VodHeroSection(
                                                    movie = hero,
                                                    enrichment = heroEnrichment,
                                                    carouselSize = featuredCarousel.size,
                                                    carouselIndex = heroIndex,
                                                    onPlay = {
                                                        hubViewModel.onHeroInteraction(hero)
                                                        playMovie(hero)
                                                    },
                                                    onMoreInfo = {
                                                        hubViewModel.onHeroInteraction(hero)
                                                        openMovieDetail(hero)
                                                    },
                                                    playFocusRequester = heroPlayFocusRequester,
                                                    moreInfoFocusRequester = heroMoreInfoFocusRequester
                                                )
                                            }
                                        }
                                    }
                                }
                                itemsIndexed(wallRows, key = { _, row -> row.id }) { index, row ->
                                    val rowListState = remember(row.id) { LazyListState() }
                                    NetflixContentWallRow(
                                        row = row,
                                        rowIndex = index,
                                        focusedColumn = if (focusZone == VodFocusZone.CONTENT && contentRowIndex == index) {
                                            contentColIndex
                                        } else {
                                            -1
                                        },
                                        rowFocused = focusZone == VodFocusZone.CONTENT && contentRowIndex == index,
                                        listState = rowListState,
                                        progressByStreamId = vodProgress,
                                        posterUrlForMovie = ::posterFor,
                                        ratingForMovie = ::ratingFor,
                                        metaForMovie = ::metaFor,
                                        overviewForMovie = ::overviewForMovie,
                                        overviewForSeries = ::overviewForSeries,
                                        onActivateItem = ::activateWallItem,
                                        firstItemFocusRequester = if (index == 0) contentFocusRequester else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedMovie?.let { movie ->
            val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
            LaunchedEffect(movie.streamId) {
                hubViewModel.enrichOnBrowse(movie)
                hubViewModel.awaitEnrichment(movie)
                moviesViewModel.resolveMovie(movie.playlistId, movie.streamId)?.let { refreshed ->
                    if (selectedMovie?.streamId == refreshed.streamId) {
                        selectedMovie = refreshed
                    }
                }
            }
            MovieDetailOverlay(
                movie = selectedMovie ?: movie,
                enrichment = hubViewModel.enrichmentFor(selectedMovie ?: movie, enrichmentMap),
                overview = overviewForMovie(selectedMovie ?: movie),
                runtimeLabel = runtimeLabelForMovie(
                    selectedMovie ?: movie,
                    hubViewModel.enrichmentFor(selectedMovie ?: movie, enrichmentMap)
                ),
                onWatchNow = ::watchSelectedMovie,
                onBack = ::closeMovieDetail,
                watchFocusRequester = movieWatchFocusRequester
            )
        }

        if (selectedShowId != null) {
            SeriesBrowserScreen(
                initialSeriesId = initialSeriesId,
                onPlayUrl = onPlayUrl,
                embedded = true,
                hubSearchQuery = searchQuery,
                overlayDetail = true,
                viewModel = seriesViewModel,
                hubViewModel = hubViewModel
            )
        }

        if (showLanguageDialog) {
            VodLanguagePreferenceDialog(
                availableLanguages = availableVodLanguages,
                selectedLanguages = preferredVodLanguages,
                onApply = ::applyPreferredLanguages,
                onDismiss = { showLanguageDialog = false }
            )
        }
    }
}

private fun seriesCategoriesForSidebar(
    primary: List<VodCategory>,
    browseRows: List<VodBrowseRow>
): VodCategoryNameResolver.SeriesSidebarCategories {
    val raw = if (primary.isNotEmpty()) {
        primary.distinctBy { it.id }
    } else {
        browseRows.mapNotNull { row ->
            val id = when {
                row.id.startsWith("cat_") -> row.id.removePrefix("cat_")
                row.id.startsWith("genre_") -> row.id.removePrefix("genre_")
                else -> return@mapNotNull null
            }
            if (id.isBlank() || row.title.isBlank()) return@mapNotNull null
            VodCategory(id = id, name = row.title, playlistId = 0L)
        }.distinctBy { it.id }
    }
    return VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(raw)
}

private fun categoriesForSidebar(
    primary: List<VodCategory>,
    browseRows: List<VodBrowseRow>
): List<VodCategory> {
    if (primary.isNotEmpty()) {
        return primary.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }
    return browseRows.mapNotNull { row ->
        val id = when {
            row.id.startsWith("cat_") -> row.id.removePrefix("cat_")
            row.id.startsWith("genre_") -> row.id.removePrefix("genre_")
            else -> return@mapNotNull null
        }
        if (id.isBlank() || row.title.isBlank()) return@mapNotNull null
        VodCategory(id = id, name = row.title, playlistId = 0L)
    }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
}
