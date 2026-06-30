package com.grid.tv.ui.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.feature.vod.personalization.matchContinueWatchingSeries
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.domain.model.categoryKey
import com.grid.tv.domain.model.filterHomeWallRowsByType
import com.grid.tv.domain.model.homeLeadWallRowCount
import com.grid.tv.domain.model.lazyColumnIndexForWallRow
import com.grid.tv.domain.model.splitHomeWallRows
import com.grid.tv.ui.component.EpgGuideHeader
import com.grid.tv.ui.component.GuideNavDrawer
import com.grid.tv.ui.component.GuideNavDrawerItem
import com.grid.tv.ui.component.GuideNavDrawerItems
import com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex
import com.grid.tv.ui.component.ProfileMenuDropdown
import com.grid.tv.ui.component.guideNavDrawerItemFocusIndex
import com.grid.tv.ui.component.MovieDetailOverlay
import com.grid.tv.ui.component.NetflixContentWallRow
import com.grid.tv.ui.component.resolveMovieOverview
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.grid.tv.ui.component.VodLibraryNavPanel
import com.grid.tv.ui.component.VodLanguageSubmenuPanel
import com.grid.tv.ui.component.VodLibrarySubPanelOffsetExpanded
import com.grid.tv.ui.component.VodLibraryNavPanelExpandedWidth
import com.grid.tv.ui.component.VodGenreSidePanel
import com.grid.tv.ui.component.runtimeLabelForMovie
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.VodAmbientBackdrop
import com.grid.tv.ui.component.VodPosterFocusLayout
import com.grid.tv.ui.component.VodHubLanguageFilterFocusIndex
import com.grid.tv.ui.component.VodHubTabFilters
import com.grid.tv.ui.component.vodHubTabFilterIndex
import com.grid.tv.ui.component.VodLanguagePreferenceDialog
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingPanel
import com.grid.tv.ui.component.VodCatalogRefreshWarningBanner
import com.grid.tv.ui.component.isMoviesTabRenderable
import com.grid.tv.ui.component.isSeriesCatalogStillLoading
import com.grid.tv.ui.component.isVodHubColdStartLoad
import com.grid.tv.ui.component.rememberHubUnifiedLoadingVisible
import com.grid.tv.ui.component.rememberVodCatalogOnboardingVisible
import com.grid.tv.ui.component.VodEmptyState
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.grid.tv.feature.vod.VodHubBrowseGridFocusInputs
import com.grid.tv.feature.vod.VodHubBrowseSurfaceInputs
import com.grid.tv.feature.vod.VodHubLifecycleLogger
import com.grid.tv.feature.vod.VodHubSurfacePhase
import com.grid.tv.feature.vod.VodHubSurfaceState
import com.grid.tv.feature.vod.VodHubSurfaceStateResolver
import com.grid.tv.feature.vod.VodSeriesHydrationReason
import com.grid.tv.feature.vod.lifecyclePhase
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import com.grid.tv.feature.vod.resolveVodWallFocus
import com.grid.tv.feature.vod.vodWallItemKey
import com.grid.tv.ui.component.movieMetaSubtitle
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.grid.tv.ui.component.animateScrollGridItemIntoView
import com.grid.tv.ui.component.animateScrollToItemIfNeeded
import com.grid.tv.ui.component.animateScrollVodWallRowIntoView
import com.grid.tv.ui.component.scrollVodWallToTop
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import androidx.activity.compose.BackHandler
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.ui.component.SearchOverlay
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.quitAppToHome
import com.grid.tv.util.VodCatalogLogger
import com.grid.tv.util.VodPerfLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    initialPlaylistId: Long? = null,
    profileInitials: String = "?",
    profileAvatarColor: String = com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR,
    profileDisplayName: String? = null,
    onPlayMovie: (String, String, Boolean) -> Unit,
    onPlayUrl: (String, String, Boolean) -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onWatchChannel: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    hubViewModel: VodHubViewModel = hiltViewModel(),
    moviesViewModel: MoviesViewModel = hiltViewModel(),
    seriesViewModel: SeriesViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    var showGlobalSearchOverlay by remember { mutableStateOf(false) }
    val focusUi = remember { VodHubFocusUiState() }
    val focusController = remember(focusUi) { VodHubFocusController(focusUi) }
    var contentRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentColIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedContentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var browseGridFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    var homeWallTypeFilter by rememberSaveable { mutableStateOf(VodContentFilter.ALL) }
    var selectedMovie by remember { mutableStateOf<VodItem?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    DisposableEffect(hubViewModel) {
        hubViewModel.onHubEntered()
        onDispose { hubViewModel.onHubExited() }
    }

    val continueWatchingListState = remember { LazyListState() }

    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val context = LocalContext.current
    val livePlayerManager = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .livePlayerManager()
    }

    /** Stable catalog/filter/wall snapshot — does not include hero carousel index. */
    val contentState by hubViewModel.contentState.collectAsStateWithLifecycle()
    val tmdbWarning by hubViewModel.tmdbWarningMessage.collectAsStateWithLifecycle()
    val activePlaylistId by hubViewModel.activePlaylistId.collectAsStateWithLifecycle()
    val recommendationFeedbackRevision by hubViewModel.recommendationFeedbackRevision.collectAsStateWithLifecycle()
    var focusBootstrapComplete by remember { mutableStateOf(false) }

    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val imeTypingActive = TvTextInputSession.isActiveState.value
    val globalSearchQuery by searchViewModel.queryText.collectAsStateWithLifecycle()
    val globalSearchResults by searchViewModel.results.collectAsStateWithLifecycle()
    val globalUnifiedSearchResults by searchViewModel.unifiedResults.collectAsStateWithLifecycle()
    val globalSearchBarState by searchViewModel.searchBarState.collectAsStateWithLifecycle()
    val movieFilteredTotalCount by moviesViewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val seriesFilteredTotalCount by seriesViewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val moviesBrowseGridHandle = remember { VodHubBrowseGridHandle() }
    val seriesBrowseGridHandle = remember { VodHubBrowseGridHandle() }
    var searchHasResults by remember { mutableStateOf(false) }

    LaunchedEffect(movieFilteredTotalCount, seriesFilteredTotalCount) {
        hubViewModel.syncBrowseCounts(movieFilteredTotalCount, seriesFilteredTotalCount)
    }

    SideEffect {
        VodPerfLogger.logEmission(
            "VodHubScreen.contentState",
            "wallRows=${contentState.wallRows.size} filter=${contentState.contentFilter} rev=${contentState.wallRowsRevision}"
        )
    }

    val enrichmentMap = contentState.enrichmentMap
    val vodProgress = contentState.vodProgress
    val searchQuery = contentState.searchQuery
    val contentFilter = contentState.contentFilter
    val preferredVodLanguages = contentState.preferredLanguages
    val includeUntaggedVodContent = contentState.includeUntagged
    val availableVodLanguages = contentState.availableLanguages
    val languageFilterActive = contentState.languageFilterActive
    val movieBrowseRows = contentState.movieBrowseRows
    val seriesBrowseRows = contentState.seriesBrowseRows
    val catalogProgress = contentState.catalogUi.catalogProgress
    val catalogLoading = contentState.catalogUi.catalogLoading
    val catalogTotalCount = contentState.catalogUi.catalogTotalCount
    val seriesCatalogTotalCount = contentState.catalogUi.seriesCatalogTotalCount
    val combinedCatalogCount = contentState.catalogUi.combinedCatalogCount
    val catalogSampleCount = contentState.catalogUi.catalogSampleCount
    val filteredCatalogCount = contentState.catalogUi.filteredCatalogCount
    val recommendedForYou = contentState.recommendedForYou
    val trendingNow = contentState.trendingNow
    val wallRows = contentState.wallRows
    val wallRowsRevision = contentState.wallRowsRevision
    val displayWallRows = remember(
        contentFilter,
        wallRows,
        homeWallTypeFilter,
    ) {
        if (contentFilter == VodContentFilter.ALL) {
            filterHomeWallRowsByType(wallRows, homeWallTypeFilter)
        } else {
            wallRows
        }
    }
    val (homeLeadWallRows, homeTailWallRows) = remember(displayWallRows) {
        splitHomeWallRows(displayWallRows)
    }
    val homeLeadRowCount = homeLeadWallRows.size
    val homeCategoryStartIndex = homeLeadRowCount
    val onboardingInputs = contentState.onboardingInputs
    val showCatalogEmptyState = contentState.showCatalogEmptyState
    val showLanguageFilteredEmpty = contentState.showLanguageFilteredEmpty
    val sidebarMovieCategories = contentState.sidebar.movieCategories
    val sidebarSeriesCategories = contentState.sidebar.seriesCategories
    val movieCategoryFilterIdsByRepresentativeId = contentState.sidebar.movieFilterIdsByRepresentativeId
    val seriesCategoryFilterIdsByRepresentativeId = contentState.sidebar.seriesFilterIdsByRepresentativeId
    val genreLabels = contentState.sidebar.genreLabels
    val selectedGenreIndex = contentState.sidebar.selectedGenreIndex
    val selectedMovieCategoryId = contentState.sidebar.selectedMovieCategoryId
    val selectedMovieCategoryPlaylistId = contentState.sidebar.selectedMovieCategoryPlaylistId
    val selectedSeriesCategoryId = contentState.sidebar.selectedSeriesCategoryId
    val selectedSeriesCategoryPlaylistId = contentState.sidebar.selectedSeriesCategoryPlaylistId
    val continueWatchingItems = contentState.continueWatching

    LaunchedEffect(
        catalogTotalCount,
        movieFilteredTotalCount,
        seriesFilteredTotalCount,
        movieBrowseRows.size,
        seriesBrowseRows.size,
        preferredVodLanguages,
        contentFilter
    ) {
        if (contentFilter == VodContentFilter.SEARCH) return@LaunchedEffect
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubMoviesPaging", movieFilteredTotalCount)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubSeriesPaging", seriesFilteredTotalCount)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubMovieBrowseRows", movieBrowseRows.size)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubSeriesBrowseRows", seriesBrowseRows.size)
        if (catalogTotalCount > 0 &&
            movieFilteredTotalCount == 0 &&
            movieBrowseRows.isEmpty() &&
            contentFilter != VodContentFilter.SERIES
        ) {
            com.grid.tv.util.VodCatalogLogger.catalogStageFailure(
                stage = if (preferredVodLanguages.isNotEmpty()) "filter" else "ui",
                reason = if (preferredVodLanguages.isNotEmpty()) {
                    "language_filter=${preferredVodLanguages.joinToString(",")}"
                } else {
                    "paging_and_browse_empty"
                },
                dbMovies = catalogTotalCount,
                filtered = movieFilteredTotalCount
            )
        }
    }

    val hasBrowseResults = searchHasResults
    val showInlineSearch = contentFilter == VodContentFilter.SEARCH
    val selectedShowId by seriesViewModel.selectedShowId.collectAsStateWithLifecycle()
    val selectedShow by seriesViewModel.selectedShow.collectAsStateWithLifecycle()
    val seriesDetailOpen = selectedShowId != null
    val movieDetailOpen = selectedMovie != null
    val showGenrePanel = contentFilter == VodContentFilter.MOVIES ||
        contentFilter == VodContentFilter.SERIES
    val showBrowseGrid = searchQuery.isNotBlank() ||
        contentFilter == VodContentFilter.MOVIES ||
        contentFilter == VodContentFilter.SERIES
    val scope = rememberCoroutineScope()
    val columnListState = rememberLazyListState()
    val moviesBrowseGridState = rememberLazyGridState()
    val seriesBrowseGridState = rememberLazyGridState()
    val density = LocalDensity.current
    val vodWallScrollSafePaddingPx = remember(density) {
        with(density) {
            (
                VodPosterFocusLayout.categoryRowTopPadding +
                    24.dp +
                    VodPosterFocusLayout.categoryTitleBottomGap +
                    VodPosterFocusLayout.netflixEdgePaddingVertical
                ).roundToPx()
        }
    }
    val browseGridScrollSafePaddingPx = remember(density) {
        with(density) { 48.dp.roundToPx() }
    }
    val vodWallRowHeightPx = remember(density) {
        with(density) { VodPosterFocusLayout.estimatedWallRowHeight.roundToPx() }
    }

    LaunchedEffect(initialTab, focusBootstrapComplete) {
        if (!focusBootstrapComplete) return@LaunchedEffect
        if (initialTab != 1) return@LaunchedEffect
        if (contentFilter != VodContentFilter.SERIES) {
            hubViewModel.setContentFilter(VodContentFilter.SERIES)
        }
        hubViewModel.ensureSeriesTabHydrated(VodSeriesHydrationReason.DEEP_LINK)
    }

    LaunchedEffect(contentFilter, searchQuery) {
        moviesViewModel.setHubSearchMode(
            when (contentFilter) {
                VodContentFilter.MOVIES -> false
                VodContentFilter.SEARCH -> searchQuery.isBlank()
                else -> true
            }
        )
        seriesViewModel.setHubSearchMode(
            when (contentFilter) {
                VodContentFilter.SERIES -> false
                VodContentFilter.SEARCH -> searchQuery.isBlank()
                else -> true
            }
        )
        when (contentFilter) {
            VodContentFilter.SEARCH -> {
                moviesViewModel.setSearchQuery(searchQuery)
                seriesViewModel.setSearchQuery(searchQuery)
            }
            VodContentFilter.SERIES -> {
                seriesViewModel.setSearchQuery(searchQuery)
                moviesViewModel.setSearchQuery("")
            }
            VodContentFilter.MOVIES -> {
                moviesViewModel.setSearchQuery(searchQuery)
                seriesViewModel.setSearchQuery("")
            }
            VodContentFilter.ALL -> {
                moviesViewModel.setSearchQuery("")
                seriesViewModel.setSearchQuery("")
            }
        }
    }

    LaunchedEffect(initialSeriesId, initialPlaylistId, continueWatchingItems) {
        if (initialSeriesId == null) return@LaunchedEffect
        val playlistHint = initialPlaylistId ?: 0L
        val cw = matchContinueWatchingSeries(continueWatchingItems, initialSeriesId, playlistHint)
        seriesViewModel.selectShow(
            showId = initialSeriesId,
            playlistId = initialPlaylistId ?: cw?.playlistId ?: 0L,
            preferredSeason = cw?.seasonNumber
        )
    }

    LaunchedEffect(Unit) {
        com.grid.tv.util.PlaybackDiagnostics.logDeviceProfile(context)
        livePlayerManager.stopGuidePreview()
        livePlayerManager.setMode(com.grid.tv.player.LivePlayerManager.Mode.IDLE)
    }

    val showVodOnboarding = contentFilter != VodContentFilter.SEARCH &&
        rememberVodCatalogOnboardingVisible(onboardingInputs)
    val allSurfaceState = VodHubSurfaceStateResolver.resolveAllTab(
        contentFilter = contentFilter,
        onboardingInputs = onboardingInputs,
        showOnboarding = showVodOnboarding,
        wallRowCount = wallRows.size,
        catalogLoading = catalogLoading,
        catalogProgress = catalogProgress,
        combinedCatalogCount = combinedCatalogCount,
        hasContinueWatching = continueWatchingItems.isNotEmpty(),
        languageFilterActive = languageFilterActive,
        continueWatchingOnly = wallRows.isEmpty() &&
            continueWatchingItems.isNotEmpty() &&
            !showCatalogEmptyState &&
            !showLanguageFilteredEmpty,
    )

    val moviesCatalogStatus by moviesViewModel.catalogStatus.collectAsStateWithLifecycle()
    val moviesCatalogLoading by moviesViewModel.catalogLoading.collectAsStateWithLifecycle()
    val moviesBrowseRows by moviesViewModel.browseRows.collectAsStateWithLifecycle()
    val moviesCategories by moviesViewModel.categories.collectAsStateWithLifecycle()
    val moviesSelectedCategoryId by moviesViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val moviePagingItems = moviesViewModel.pagedMovies.collectAsLazyPagingItems()
    val moviesPagingRefreshing = moviePagingItems.loadState.refresh is LoadState.Loading
    val moviesBrowseSurface = VodHubSurfaceStateResolver.resolveBrowseTab(
        VodHubBrowseSurfaceInputs(
            tab = VodCatalogOnboardingTab.MOVIES,
            catalogLoading = moviesCatalogLoading,
            catalogProgress = catalogProgress,
            catalogStatus = moviesCatalogStatus,
            catalogTotalCount = catalogTotalCount,
            filteredTotalCount = movieFilteredTotalCount,
            browseRowCount = moviesBrowseRows.size,
            categoryCount = moviesCategories.size,
            pagedItemCount = moviePagingItems.itemCount,
            pagingRefreshing = moviesPagingRefreshing,
            selectedCategoryId = moviesSelectedCategoryId,
            languageFilterActive = languageFilterActive,
        )
    )

    val seriesCatalogStatus by seriesViewModel.catalogStatus.collectAsStateWithLifecycle()
    val seriesCatalogLoading by seriesViewModel.catalogLoading.collectAsStateWithLifecycle()
    val seriesBrowseRowsVm by seriesViewModel.browseRows.collectAsStateWithLifecycle()
    val seriesCategories by seriesViewModel.categories.collectAsStateWithLifecycle()
    val seriesSelectedCategoryId by seriesViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val seriesPagingItems = seriesViewModel.pagedSeries.collectAsLazyPagingItems()
    val seriesPagingRefreshing = seriesPagingItems.loadState.refresh is LoadState.Loading
    val isSeriesStillLoading = isSeriesCatalogStillLoading(catalogLoading, catalogProgress)
    val seriesBrowseSurface = VodHubSurfaceStateResolver.resolveBrowseTab(
        VodHubBrowseSurfaceInputs(
            tab = VodCatalogOnboardingTab.SERIES,
            catalogLoading = seriesCatalogLoading,
            catalogProgress = catalogProgress,
            catalogStatus = seriesCatalogStatus,
            catalogTotalCount = seriesCatalogTotalCount,
            filteredTotalCount = seriesFilteredTotalCount,
            browseRowCount = seriesBrowseRowsVm.size,
            categoryCount = seriesCategories.size,
            pagedItemCount = seriesPagingItems.itemCount,
            pagingRefreshing = seriesPagingRefreshing,
            selectedCategoryId = seriesSelectedCategoryId,
            languageFilterActive = languageFilterActive,
            isSeriesStillLoading = isSeriesStillLoading,
        )
    )

    val moviesOnboardingInputs = VodCatalogOnboardingInputs(
        catalogLoading = moviesCatalogLoading,
        progress = catalogProgress,
        tab = VodCatalogOnboardingTab.MOVIES,
        browseRowCount = moviesBrowseRows.size,
        categoryCount = moviesCategories.size,
        pagedItemCount = moviePagingItems.itemCount,
        catalogTotalCount = catalogTotalCount,
    )
    val seriesOnboardingInputs = VodCatalogOnboardingInputs(
        catalogLoading = seriesCatalogLoading,
        progress = catalogProgress,
        tab = VodCatalogOnboardingTab.SERIES,
        browseRowCount = seriesBrowseRowsVm.size,
        categoryCount = seriesCategories.size,
        pagedItemCount = seriesPagingItems.itemCount,
        catalogTotalCount = seriesCatalogTotalCount,
    )
    val isColdStart = isVodHubColdStartLoad(catalogTotalCount, seriesCatalogTotalCount)
    val showHubUnifiedLoading = contentFilter == VodContentFilter.ALL &&
        contentFilter != VodContentFilter.SEARCH &&
        isColdStart &&
        rememberHubUnifiedLoadingVisible(
            catalogLoading = catalogLoading,
            catalogProgress = catalogProgress,
            moviesInputs = moviesOnboardingInputs,
            seriesInputs = seriesOnboardingInputs,
            allInputs = onboardingInputs,
            isColdStart = isColdStart,
        )
    val hubCatalogReady = !isColdStart ||
        isMoviesTabRenderable(moviesOnboardingInputs, catalogProgress) ||
        !showHubUnifiedLoading
    val showLibraryNavPanel = hubCatalogReady && !showInlineSearch
    val showLibraryRail = showLibraryNavPanel && focusUi.libraryNavPanelVisible
    val librarySubPanelOpen = focusUi.focusZone == VodFocusZone.GENRE_PANEL ||
        focusUi.focusZone == VodFocusZone.LANGUAGE_SUBMENU
    val showLibraryOverlayScrim = librarySubPanelOpen
    val librarySubPanelOffset = if (showLibraryRail) VodLibrarySubPanelOffsetExpanded else 0.dp
    val libraryContentInsetTarget = if (showLibraryRail) VodLibraryNavPanelExpandedWidth else 0.dp
    val libraryContentInsetAnimated by animateDpAsState(
        targetValue = libraryContentInsetTarget,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f),
        label = "vodLibraryContentInset",
    )
    val libraryContentInset = libraryContentInsetAnimated.coerceAtLeast(0.dp)

    val activeSurfaceState = when (contentFilter) {
        VodContentFilter.MOVIES -> moviesBrowseSurface
        VodContentFilter.SERIES -> seriesBrowseSurface
        VodContentFilter.ALL -> allSurfaceState
        else -> VodHubSurfaceState.Ready()
    }

    fun browseGridKeyAtIndex(index: Int): String? = when (contentFilter) {
        VodContentFilter.MOVIES -> moviesBrowseGridHandle.contentKeyAt(index)
        VodContentFilter.SERIES -> seriesBrowseGridHandle.contentKeyAt(index)
        else -> null
    }

    fun activeBrowseGridState(): androidx.compose.foundation.lazy.grid.LazyGridState? =
        when (contentFilter) {
            VodContentFilter.MOVIES -> moviesBrowseGridState
            VodContentFilter.SERIES -> seriesBrowseGridState
            else -> null
        }

    fun browseGridItemCount(): Int = when (contentFilter) {
        VodContentFilter.MOVIES -> moviesBrowseGridHandle.itemCount
        VodContentFilter.SERIES -> seriesBrowseGridHandle.itemCount
        VodContentFilter.ALL, VodContentFilter.SEARCH -> 0
    }

    fun browseGridCatalogTotal(): Int = when (contentFilter) {
        VodContentFilter.MOVIES -> maxOf(movieFilteredTotalCount, catalogTotalCount)
        VodContentFilter.SERIES -> maxOf(seriesFilteredTotalCount, seriesCatalogTotalCount)
        else -> 0
    }

    fun browseGridFocusInputs() = VodHubBrowseGridFocusInputs(
        contentFilter = contentFilter,
        surfaceState = activeSurfaceState,
        gridItemCount = browseGridItemCount(),
        movieCatalogTotal = catalogTotalCount,
        seriesCatalogTotal = seriesCatalogTotalCount,
        catalogLoading = catalogLoading,
        catalogProgress = catalogProgress,
    )

    fun isBrowseGridLoading(): Boolean =
        VodHubSurfaceStateResolver.isBrowseGridLoading(browseGridFocusInputs())

    fun focusContentMode() = VodHubSurfaceStateResolver.focusContentMode(
        surfaceState = activeSurfaceState,
        gridItemCount = browseGridItemCount(),
        catalogTotal = browseGridCatalogTotal(),
    )

    fun lifecycleSnapshot() = VodHubLifecycleLogger.Snapshot(
        tab = contentFilter,
        focusZone = focusUi.focusZone.name,
        genreCount = genreLabels.size,
        browseRowCount = when (contentFilter) {
            VodContentFilter.MOVIES -> moviesBrowseRows.size
            VodContentFilter.SERIES -> seriesBrowseRowsVm.size
            VodContentFilter.ALL -> movieBrowseRows.size + seriesBrowseRows.size
            else -> 0
        },
        pagedItemCount = browseGridItemCount(),
        focusContentMode = focusContentMode(),
        catalogLoading = catalogLoading,
        blocksGridFocus = focusUi.blocksGridFocus,
    )

    var lastLoggedSurfacePhase by remember { mutableStateOf<VodHubSurfacePhase?>(null) }
    LaunchedEffect(
        activeSurfaceState,
        catalogLoading,
        contentFilter,
        focusUi.focusZone,
        genreLabels.size,
        browseGridItemCount(),
        focusUi.blocksGridFocus,
        focusContentMode(),
    ) {
        val phase = activeSurfaceState.lifecyclePhase(catalogLoading)
        val snapshot = lifecycleSnapshot()
        lastLoggedSurfacePhase?.let { previous ->
            VodHubLifecycleLogger.logTransition(previous, phase, snapshot)
        }
        lastLoggedSurfacePhase = phase
    }

    fun applyGenreSelection(index: Int, filter: VodContentFilter = contentFilter) {
        focusUi.genreFocusIndex = index.coerceIn(0, genreLabels.lastIndex.coerceAtLeast(0))
        focusUi.rememberGenreFor(filter)
        when (filter) {
            VodContentFilter.MOVIES -> {
                if (index == 0) {
                    hubViewModel.setMovieCategory(null)
                    moviesViewModel.setCategory(null)
                } else {
                    val category = sidebarMovieCategories.getOrNull(index - 1)
                    val filterIds = category?.let {
                        movieCategoryFilterIdsByRepresentativeId[
                            categoryKey(it.playlistId, it.id)
                        ]
                    }
                    hubViewModel.setMovieCategory(category?.id, filterIds, category?.playlistId)
                    moviesViewModel.setCategory(category?.id, filterIds, category?.playlistId)
                }
            }
            VodContentFilter.SERIES -> {
                if (index == 0) {
                    hubViewModel.setSeriesCategory(null)
                    seriesViewModel.setCategory(null)
                } else {
                    val category = sidebarSeriesCategories.getOrNull(index - 1)
                    val filterIds = category?.let {
                        seriesCategoryFilterIdsByRepresentativeId[
                            categoryKey(it.playlistId, it.id)
                        ]
                    }
                    hubViewModel.setSeriesCategory(category?.id, filterIds, category?.playlistId)
                    seriesViewModel.setCategory(category?.id, filterIds, category?.playlistId)
                }
            }
            else -> Unit
        }
    }

    fun syncFocusedWallItemKey() {
        val key = displayWallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex)?.key
        focusedContentKey = key
        hubViewModel.rememberFocusedContentKey(key)
        if (contentFilter == VodContentFilter.ALL) {
            focusUi.rememberWallFor(
                VodContentFilter.ALL,
                VodWallFocusMemory(contentRowIndex, contentColIndex, key)
            )
        }
        if (contentFilter.storesBrowseGridMemory()) {
            val count = browseGridItemCount()
            activeBrowseGridState()?.let { state ->
                val index = if (count > 0) browseGridFocusIndex.coerceIn(0, count - 1) else 0
                val existing = focusUi.gridMemoryFor(contentFilter)
                focusUi.rememberGridFor(
                    contentFilter,
                    snapshotGridMemory(
                        state,
                        index,
                        if (count > 0) browseGridKeyAtIndex(index) else existing.contentKey
                    )
                )
            }
        }
    }

    fun restoreWallFocusAfterRebuild(focusBefore: String?) {
        val started = System.nanoTime()
        val (row, col) = resolveVodWallFocus(
            wallRows = displayWallRows,
            savedContentKey = focusBefore,
            fallbackRow = contentRowIndex,
            fallbackCol = contentColIndex
        )
        if (row == contentRowIndex && col == contentColIndex) {
            VodPerfLogger.logStage("focusRestore.noop", (System.nanoTime() - started) / 1_000_000)
            return
        }
        contentRowIndex = row
        contentColIndex = col
        val keyAfter = displayWallRows.getOrNull(row)?.items?.getOrNull(col)?.key
        focusedContentKey = keyAfter
        hubViewModel.rememberFocusedContentKey(keyAfter)
        focusUi.rememberWallFor(
            VodContentFilter.ALL,
            VodWallFocusMemory(row, col, keyAfter)
        )
        VodPerfLogger.logStage(
            "focusRestore",
            (System.nanoTime() - started) / 1_000_000,
            "before=$focusBefore after=$keyAfter row=$row col=$col"
        )
    }

    LaunchedEffect(wallRows.size, searchQuery) {
        val maxRow = displayWallRows.lastIndex.coerceAtLeast(0)
        if (displayWallRows.isNotEmpty()) {
            contentRowIndex = contentRowIndex.coerceIn(0, maxRow)
            val maxCol = displayWallRows.getOrNull(contentRowIndex)?.items?.lastIndex ?: 0
            contentColIndex = contentColIndex.coerceIn(0, maxCol.coerceAtLeast(0))
        }
        if (searchQuery.isBlank() && wallRows.isEmpty() && focusUi.focusZone == VodFocusZone.CONTENT) {
            focusUi.libraryNavPanelVisible = true
            focusUi.focusZone = VodFocusZone.FILTER_PANEL
        }
    }

    LaunchedEffect(focusUi.focusZone, contentRowIndex, showBrowseGrid, searchQuery, displayWallRows.size, showInlineSearch) {
        if (searchQuery.isNotBlank() || showBrowseGrid || showInlineSearch) return@LaunchedEffect
        when (focusUi.focusZone) {
            VodFocusZone.CONTENT -> {
                columnListState.animateScrollVodWallRowIntoView(
                    index = lazyColumnIndexForWallRow(
                        wallRowIndex = contentRowIndex,
                        leadRowCount = homeLeadRowCount,
                        heroVisible = false,
                    ).coerceAtLeast(0),
                    direction = focusUi.contentScrollDirection,
                    safePaddingPx = vodWallScrollSafePaddingPx,
                    fallbackItemHeightPx = vodWallRowHeightPx
                )
            }
            VodFocusZone.FILTER_PANEL, VodFocusZone.NAV_DRAWER -> {
                columnListState.scrollVodWallToTop()
            }
            else -> Unit
        }
    }

    LaunchedEffect(
        focusUi.focusZone,
        browseGridFocusIndex,
        contentFilter,
        showBrowseGrid,
        focusUi.contentScrollDirection,
        focusUi.browseGridColumnCount,
        focusUi.gridFocusPending,
    ) {
        if (focusUi.focusZone != VodFocusZone.CONTENT || !showBrowseGrid || showInlineSearch) return@LaunchedEffect
        if (contentFilter != VodContentFilter.MOVIES && contentFilter != VodContentFilter.SERIES) return@LaunchedEffect
        if (focusUi.gridFocusPending) return@LaunchedEffect
        val state = activeBrowseGridState() ?: return@LaunchedEffect
        val count = browseGridItemCount()
        if (count <= 0) return@LaunchedEffect
        val index = browseGridFocusIndex.coerceIn(0, count - 1)
        val direction = focusUi.contentScrollDirection
        state.animateScrollGridItemIntoView(
            index = index,
            direction = direction,
            columnCount = focusUi.browseGridColumnCount,
            safePaddingPx = browseGridScrollSafePaddingPx,
        )
        if (direction != TvLazyFocusScrollDirection.NEUTRAL) {
            focusUi.contentScrollDirection = TvLazyFocusScrollDirection.NEUTRAL
        }
    }

    val navDrawerFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val browseGridCount = browseGridItemCount()
    val contentFocusRequester = remember { FocusRequester() }
    val browseGridFocusRequester = remember { FocusRequester() }
    val browseEmptyStateFocusRequester = remember { FocusRequester() }

    LaunchedEffect(wallRowsRevision, preferredVodLanguages, includeUntaggedVodContent) {
        val focusBefore = focusedContentKey
            ?: vodWallItemKey(wallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex))
        restoreWallFocusAfterRebuild(focusBefore)
        if (focusUi.focusZone == VodFocusZone.CONTENT && displayWallRows.isNotEmpty() && !showBrowseGrid && searchQuery.isBlank()) {
            rootFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    val filterPanelFocusRequester = remember { FocusRequester() }
    val genrePanelFocusRequester = remember { FocusRequester() }
    val languageSubmenuFocusRequester = remember { FocusRequester() }
    val inlineSearchFocusRequester = remember { FocusRequester() }
    val movieWatchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(contentFilter) {
        focusUi.filterFocusIndex = vodHubTabFilterIndex(contentFilter)
        focusUi.restoreGenreFrom(contentFilter)
    }

    fun saveCurrentFilterMemory() {
        when (contentFilter) {
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> {
                focusUi.rememberGenreFor(contentFilter)
                val count = browseGridItemCount()
                activeBrowseGridState()?.let { state ->
                    val index = if (count > 0) browseGridFocusIndex.coerceIn(0, count - 1) else 0
                    val existing = focusUi.gridMemoryFor(contentFilter)
                    focusUi.rememberGridFor(
                        contentFilter,
                        snapshotGridMemory(
                            state,
                            index,
                            if (count > 0) browseGridKeyAtIndex(index) else existing.contentKey
                        )
                    )
                }
            }
            VodContentFilter.ALL -> {
                focusUi.rememberWallFor(
                    VodContentFilter.ALL,
                    VodWallFocusMemory(contentRowIndex, contentColIndex, focusedContentKey)
                )
            }
            else -> Unit
        }
    }

    fun restoreFilterMemory(filter: VodContentFilter) {
        when (filter) {
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> {
                val genreIndex = focusUi.genreIndexFor(filter)
                focusUi.genreFocusIndex = genreIndex
                applyGenreSelection(genreIndex, filter)
                val memory = focusUi.gridMemoryFor(filter)
                val count = when (filter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridHandle.itemCount
                    VodContentFilter.SERIES -> seriesBrowseGridHandle.itemCount
                    else -> 0
                }
                val keyResolver: (Int) -> String? = when (filter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridHandle::contentKeyAt
                    VodContentFilter.SERIES -> seriesBrowseGridHandle::contentKeyAt
                    else -> { _ -> null }
                }
                val gridState = when (filter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridState
                    VodContentFilter.SERIES -> seriesBrowseGridState
                    else -> null
                }
                val resolved = resolveBrowseGridFocusIndex(
                    itemCount = count,
                    saved = memory,
                    keyAtIndex = keyResolver,
                    firstVisibleIndex = gridState?.firstVisibleItemIndex ?: 0,
                )
                browseGridFocusIndex = resolved
                focusUi.rememberGridFor(
                    filter,
                    memory.copy(
                        itemIndex = resolved,
                        contentKey = keyResolver(resolved)
                    )
                )
            }
            VodContentFilter.ALL -> {
                val memory = focusUi.wallMemoryFor(VodContentFilter.ALL)
                val (row, col) = resolveVodWallFocus(
                    wallRows = displayWallRows,
                    savedContentKey = memory.contentKey,
                    fallbackRow = memory.rowIndex,
                    fallbackCol = memory.colIndex
                )
                contentRowIndex = row
                contentColIndex = col
                syncFocusedWallItemKey()
            }
            else -> Unit
        }
    }

    LaunchedEffect(activePlaylistId, initialTab) {
        if (focusBootstrapComplete || activePlaylistId <= 0L) return@LaunchedEffect
        when (initialTab) {
            0 -> {
                hubViewModel.setContentFilter(VodContentFilter.ALL)
                focusUi.filterFocusIndex = vodHubTabFilterIndex(VodContentFilter.ALL)
                focusUi.libraryNavPanelVisible = false
                focusUi.focusZone = VodFocusZone.CONTENT
                VodHubFocusLogger.restore(activePlaylistId, VodContentFilter.ALL)
            }
            1 -> {
                hubViewModel.setContentFilter(VodContentFilter.SERIES)
                focusUi.filterFocusIndex = vodHubTabFilterIndex(VodContentFilter.SERIES)
                focusUi.libraryNavPanelVisible = false
                focusUi.focusZone = VodFocusZone.CONTENT
                VodHubFocusLogger.restore(activePlaylistId, VodContentFilter.SERIES)
            }
            else -> {
                val snapshot = hubViewModel.readPersistedFocus(activePlaylistId)
                if (snapshot != null) {
                    hydrateVodHubFocus(focusUi, snapshot)
                    val filter = runCatching { VodContentFilter.valueOf(snapshot.contentFilter) }
                        .getOrDefault(VodContentFilter.ALL)
                    hubViewModel.setContentFilter(filter)
                    focusUi.filterFocusIndex = snapshot.filterFocusIndex
                    restoreFilterMemory(filter)
                    val zone = runCatching { VodFocusZone.valueOf(snapshot.focusZone) }
                        .getOrDefault(VodFocusZone.FILTER_PANEL)
                    when {
                        zone == VodFocusZone.CONTENT &&
                            (filter == VodContentFilter.MOVIES || filter == VodContentFilter.SERIES) -> {
                            focusUi.libraryNavPanelVisible = false
                            focusUi.focusZone = VodFocusZone.CONTENT
                            focusController.focusBrowseGridRestored()
                        }
                        (zone == VodFocusZone.CONTENT || zone == VodFocusZone.HERO) &&
                            filter == VodContentFilter.ALL -> {
                            focusUi.libraryNavPanelVisible = false
                            focusUi.focusZone = VodFocusZone.CONTENT
                            focusController.focusWallContentRestored(resetToOrigin = false)
                        }
                        zone == VodFocusZone.GENRE_PANEL &&
                            (filter == VodContentFilter.MOVIES || filter == VodContentFilter.SERIES) -> {
                            focusUi.focusZone = VodFocusZone.CONTENT
                            focusUi.restoreGenreFrom(filter)
                            focusController.focusBrowseGridRestored()
                        }
                        else -> {
                            focusUi.libraryNavPanelVisible = true
                            focusUi.focusZone = VodFocusZone.FILTER_PANEL
                            filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                        }
                    }
                    VodHubFocusLogger.restore(activePlaylistId, filter)
                } else {
                    focusUi.libraryNavPanelVisible = false
                    focusUi.focusZone = VodFocusZone.CONTENT
                    focusUi.filterFocusIndex = vodHubTabFilterIndex(contentFilter)
                }
            }
        }
        focusBootstrapComplete = true
    }

    LaunchedEffect(
        activePlaylistId,
        contentFilter,
        focusUi.filterFocusIndex,
        focusUi.genreFocusIndex,
        browseGridFocusIndex,
        contentRowIndex,
        contentColIndex,
        focusedContentKey,
        focusUi.focusZone,
        focusBootstrapComplete,
    ) {
        if (!focusBootstrapComplete || activePlaylistId <= 0L) return@LaunchedEffect
        delay(400)
        if (showBrowseGrid && browseGridItemCount() <= 0) {
            hubViewModel.writePersistedFocus(
                activePlaylistId,
                snapshotVodHubFocus(focusUi, contentFilter, focusUi.focusZone),
            )
            return@LaunchedEffect
        }
        saveCurrentFilterMemory()
        hubViewModel.writePersistedFocus(
            activePlaylistId,
            snapshotVodHubFocus(focusUi, contentFilter, focusUi.focusZone),
        )
        VodHubFocusLogger.persist(activePlaylistId, contentFilter)
    }

    LaunchedEffect(
        focusUi.focusZone,
        showInlineSearch,
        focusUi.vodSearchFocused,
        movieDetailOpen,
        seriesDetailOpen,
        showBrowseGrid,
        hasBrowseResults,
        searchQuery,
        imeTypingActive,
        contentFilter,
        browseGridCount,
        focusUi.blocksGridFocus,
    ) {
        if (imeTypingActive) return@LaunchedEffect
        when {
            movieDetailOpen -> movieWatchFocusRequester.requestFocusSafelyAfterLayout()
            seriesDetailOpen -> Unit
            showInlineSearch && focusUi.vodSearchFocused ->
                inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                showInlineSearch &&
                hasBrowseResults &&
                !focusUi.vodSearchFocused &&
                !focusUi.blocksGridFocus ->
                browseGridFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.FILTER_PANEL && focusUi.libraryNavPanelVisible ->
                filterPanelFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.GENRE_PANEL ->
                genrePanelFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.LANGUAGE_SUBMENU ->
                languageSubmenuFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.NAV_DRAWER ->
                navDrawerFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                !showInlineSearch &&
                !showBrowseGrid &&
                displayWallRows.isNotEmpty() &&
                !focusUi.blocksGridFocus ->
                rootFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                !showInlineSearch &&
                (showBrowseGrid || wallRows.isEmpty()) &&
                !focusUi.blocksGridFocus &&
                (!showBrowseGrid || browseGridCount > 0) ->
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

    fun focusGenrePanelFromFilter() = focusController.focusGenrePanelFromFilter()

    fun focusFilterPanelFromGenre() = focusController.focusFilterPanelFromGenre()

    fun focusBrowseGridFromSidebar() = focusController.focusBrowseGridRestored()

    fun focusBrowseResultsFromTopBar() {
        focusUi.focusZone = VodFocusZone.CONTENT
        contentRowIndex = 0
        contentColIndex = 0
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusInlineSearchField() {
        focusUi.vodSearchFocused = true
        focusUi.focusZone = VodFocusZone.CONTENT
        scope.launch {
            inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusSearchResults() {
        if (!hasBrowseResults) return
        focusUi.vodSearchFocused = false
        focusUi.focusZone = VodFocusZone.CONTENT
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun openGlobalSearch() {
        showGlobalSearchOverlay = true
        focusUi.focusZone = VodFocusZone.FILTER_PANEL
    }

    fun openNavDrawer() {
        when (focusUi.focusZone) {
            VodFocusZone.FILTER_PANEL -> focusUi.rememberFilterFocus()
            VodFocusZone.GENRE_PANEL, VodFocusZone.CONTENT -> saveCurrentFilterMemory()
            else -> Unit
        }
        focusUi.rememberNavDrawerFocus()
        focusUi.navDrawerOpen = true
        focusUi.navDrawerFocusIndex = focusUi.lastNavDrawerFocusIndex
        focusUi.focusZone = VodFocusZone.NAV_DRAWER
        com.grid.tv.ui.screen.VodHubFocusLogger.sidebar("open")
        scope.launch {
            navDrawerFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun closeNavDrawer(restoreFilter: Boolean = true) {
        if (restoreFilter) {
            focusController.closeNavDrawerToContentZone()
        } else {
            focusUi.navDrawerOpen = false
        }
    }

    fun selectVodDrawerItem(item: GuideNavDrawerItem) {
        when (item) {
            GuideNavDrawerItem.Search -> {
                closeNavDrawer(restoreFilter = false)
                openGlobalSearch()
            }
            GuideNavDrawerItem.LiveView -> {
                closeNavDrawer(restoreFilter = false)
                onNavigateHome()
            }
            GuideNavDrawerItem.Vod -> closeNavDrawer()
            GuideNavDrawerItem.Favorites -> {
                closeNavDrawer(restoreFilter = false)
                onOpenFavorites()
            }
            GuideNavDrawerItem.Recordings -> {
                closeNavDrawer(restoreFilter = false)
                onNavigateRecordings()
            }
        }
    }

    fun handleNavDrawerKey(event: KeyEvent): Boolean = focusController.handleNavDrawerKey(event)

    fun dismissGlobalSearch() {
        showGlobalSearchOverlay = false
        searchViewModel.clearQuery()
    }

    fun handleGlobalSearchResult(result: SearchResultItem) {
        when (result.type) {
            SearchResultType.ACTOR -> {
                val actor = result.actorName ?: return
                searchViewModel.recordSelection(actor)
                searchViewModel.applyTrendingOrRecent(actor)
                return
            }
            SearchResultType.GENRE -> {
                val genre = result.genreName ?: return
                searchViewModel.recordSelection(genre)
                searchViewModel.applyTrendingOrRecent(genre)
                return
            }
            else -> Unit
        }
        searchViewModel.recordSelection(globalSearchQuery)
        dismissGlobalSearch()
        when (result.type) {
            SearchResultType.CHANNEL, SearchResultType.PROGRAM ->
                result.channelId?.let(onWatchChannel)
            SearchResultType.VOD -> result.vodItem?.let { item ->
                VodPlaybackHelper.stageMovie(item)
                val resume = (vodProgress[item.playlistId to item.streamId] ?: 0L) > 5_000L
                onPlayMovie(item.title, item.streamUrl, resume)
            }
            SearchResultType.SERIES -> result.seriesShow?.let { show ->
                seriesViewModel.selectShow(show.id, show.playlistId, preferredSeason = null, preview = show)
            }
            SearchResultType.EPISODE -> {
                val show = result.seriesShow
                val episode = result.seriesEpisode
                if (show != null && episode != null) {
                    VodPlaybackHelper.stageSeriesEpisode(
                        show = show,
                        seasonNumber = result.seriesSeasonNumber ?: 1,
                        episodeNumber = episode.episodeNumber ?: 1,
                        streamId = episode.id,
                        episodeTitle = episode.title.ifBlank { show.name }
                    )
                    onPlayUrl(
                        episode.title.ifBlank { show.name },
                        episode.streamUrl,
                        true
                    )
                }
            }
            else -> Unit
        }
    }

    fun clearInlineSearch() {
        hubViewModel.setSearchQuery("")
        focusUi.vodSearchFocused = false
        if (contentFilter == VodContentFilter.SEARCH) {
            hubViewModel.setContentFilter(VodContentFilter.ALL)
            focusUi.filterFocusIndex = vodHubTabFilterIndex(VodContentFilter.ALL)
        }
        focusUi.focusZone = VodFocusZone.FILTER_PANEL
    }

    fun focusNavDrawerFromBrowseGrid() {
        if (showInlineSearch) {
            focusInlineSearchField()
        } else {
            openNavDrawer()
        }
    }

    fun openLanguagePreferenceDialog() {
        hubViewModel.refreshAvailableVodLanguages()
        showLanguageDialog = true
    }

    fun applyPreferredLanguages(languages: Set<String>) {
        VodPerfLogger.markInput("languageFilter.ui", "languages=${languages.joinToString(",")}")
        syncFocusedWallItemKey()
        hubViewModel.setPreferredVodLanguages(languages)
    }

    fun togglePreferredLanguage(code: String?) {
        val newSet = if (code == null) {
            emptySet()
        } else {
            val upper = code.uppercase()
            if (preferredVodLanguages.any { it.equals(upper, ignoreCase = true) }) {
                preferredVodLanguages.filterNot { it.equals(code, ignoreCase = true) }.toSet()
            } else {
                preferredVodLanguages + upper
            }
        }
        applyPreferredLanguages(newSet)
    }

    fun commitFilterHighlight(index: Int) {
        if (index == VodHubLanguageFilterFocusIndex) {
            openLanguagePreferenceDialog()
            return
        }
        if (!hubCatalogReady && index < VodHubLanguageFilterFocusIndex) {
            val filter = VodHubTabFilters.getOrNull(index)
            if (filter != null && filter != contentFilter) return
        }
        val filter = VodHubTabFilters.getOrNull(index) ?: VodContentFilter.ALL
        focusUi.filterFocusIndex = index
        focusUi.rememberFilterFocus()
        if (filter == contentFilter) return

        saveCurrentFilterMemory()

        if (filter != VodContentFilter.SEARCH && contentFilter == VodContentFilter.SEARCH) {
            hubViewModel.setSearchQuery("")
            focusUi.vodSearchFocused = false
        }
        when (filter) {
            VodContentFilter.MOVIES -> {
                moviesViewModel.setHubSearchMode(false)
                seriesViewModel.setHubSearchMode(true)
            }
            VodContentFilter.SERIES -> {
                seriesViewModel.setHubSearchMode(false)
                moviesViewModel.setHubSearchMode(true)
            }
            VodContentFilter.SEARCH -> {
                moviesViewModel.setHubSearchMode(searchQuery.isBlank())
                seriesViewModel.setHubSearchMode(searchQuery.isBlank())
            }
            VodContentFilter.ALL -> {
                moviesViewModel.setHubSearchMode(true)
                seriesViewModel.setHubSearchMode(true)
            }
        }
        hubViewModel.setContentFilter(filter)
        if (filter == VodContentFilter.SERIES) {
            hubViewModel.ensureSeriesTabHydrated(VodSeriesHydrationReason.TAB_SELECT)
        }
        onNavigateVod(
            when (filter) {
                VodContentFilter.SERIES -> 1
                else -> 0
            }
        )
        if (filter == VodContentFilter.SEARCH) {
            focusUi.vodSearchFocused = true
            focusUi.focusZone = VodFocusZone.CONTENT
            scope.launch {
                inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
            }
            return
        }
        restoreFilterMemory(filter)
    }

    fun recoverFromEmptyCatalog(): Boolean {
        val action = resolveVodFocusEmptyRecovery(
            contentFilter = contentFilter,
            genreIndex = focusUi.genreFocusIndex,
            genreCount = genreLabels.size,
            moviesBrowseCount = moviesBrowseGridHandle.itemCount,
            seriesBrowseCount = seriesBrowseGridHandle.itemCount,
            wallRowCount = displayWallRows.size,
            moviesCatalogTotal = catalogTotalCount,
            seriesCatalogTotal = seriesCatalogTotalCount,
            isCatalogLoading = isBrowseGridLoading(),
        )
        VodHubFocusLogger.emptyRecovery(action.toString())
        return when (action) {
            is VodFocusEmptyRecoveryAction.ApplyGenre -> {
                applyGenreSelection(action.index)
                focusUi.focusZone = VodFocusZone.GENRE_PANEL
                scope.launch { genrePanelFocusRequester.requestFocusSafelyAfterLayout() }
                true
            }
            is VodFocusEmptyRecoveryAction.KeepFilter -> false
            is VodFocusEmptyRecoveryAction.SwitchFilter -> {
                commitFilterHighlight(vodHubTabFilterIndex(action.filter))
                true
            }
            is VodFocusEmptyRecoveryAction.OpenSidebar -> {
                openNavDrawer()
                true
            }
        }
    }

    fun ensureValidFocus() {
        if (seriesDetailOpen || movieDetailOpen || showGlobalSearchOverlay) return
        if (showInlineSearch && focusUi.vodSearchFocused) return
        if (showBrowseGrid && browseGridItemCount() <= 0) {
            if (focusUi.awaitingBrowseGridFocus || isBrowseGridLoading() || browseGridCatalogTotal() > 0) {
                return
            }
            if (focusUi.focusZone == VodFocusZone.FILTER_PANEL) {
                return
            }
            if (!isBrowseGridLoading() &&
                browseGridCatalogTotal() == 0 &&
                focusUi.focusZone == VodFocusZone.CONTENT
            ) {
                return
            }
            if (recoverFromEmptyCatalog()) return
        }
        when {
            focusUi.navDrawerOpen -> return
            focusUi.focusZone == VodFocusZone.CONTENT -> return
            focusUi.focusZone == VodFocusZone.FILTER_PANEL -> return
            focusUi.focusZone == VodFocusZone.GENRE_PANEL -> return
            showBrowseGrid && browseGridItemCount() > 0 -> {
                focusUi.focusZone = VodFocusZone.CONTENT
                focusController.focusBrowseGridRestored()
            }
            !showBrowseGrid && displayWallRows.isNotEmpty() -> {
                focusUi.focusZone = VodFocusZone.CONTENT
                restoreFilterMemory(VodContentFilter.ALL)
                scope.launch { rootFocusRequester.requestFocusSafelyAfterLayout() }
            }
            showGenrePanel -> {
                focusUi.focusZone = VodFocusZone.GENRE_PANEL
                focusUi.restoreGenreFrom(contentFilter)
                scope.launch { genrePanelFocusRequester.requestFocusSafelyAfterLayout() }
            }
            else -> {
                focusUi.focusZone = VodFocusZone.FILTER_PANEL
                scope.launch { filterPanelFocusRequester.requestFocusSafelyAfterLayout() }
            }
        }
    }

    LaunchedEffect(focusUi.awaitingBrowseGridFocus, browseGridItemCount()) {
        if (focusUi.awaitingBrowseGridFocus && browseGridItemCount() > 0) {
            focusUi.awaitingBrowseGridFocus = false
            focusController.focusBrowseGridRestored()
        }
    }

    LaunchedEffect(contentFilter) {
        if (contentFilter != VodContentFilter.MOVIES && contentFilter != VodContentFilter.SERIES) return@LaunchedEffect
        val count = browseGridItemCount()
        if (count <= 0) return@LaunchedEffect
        val memory = focusUi.gridMemoryFor(contentFilter)
        val keyResolver = when (contentFilter) {
            VodContentFilter.MOVIES -> moviesBrowseGridHandle::contentKeyAt
            VodContentFilter.SERIES -> seriesBrowseGridHandle::contentKeyAt
            else -> { _: Int -> null }
        }
        val resolved = resolveBrowseGridFocusIndex(
            itemCount = count,
            saved = memory,
            keyAtIndex = keyResolver,
            firstVisibleIndex = activeBrowseGridState()?.firstVisibleItemIndex ?: 0,
        )
        browseGridFocusIndex = resolved.coerceIn(0, count - 1)
    }

    LaunchedEffect(browseGridItemCount(), contentFilter, focusUi.focusZone) {
        if (focusUi.focusZone != VodFocusZone.CONTENT) return@LaunchedEffect
        when (contentFilter) {
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> {
                val count = browseGridItemCount()
                if (count <= 0) {
                    if (focusUi.awaitingBrowseGridFocus || isBrowseGridLoading() || browseGridCatalogTotal() > 0) {
                        return@LaunchedEffect
                    }
                    ensureValidFocus()
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(wallRowsRevision, contentFilter, focusedContentKey, focusUi.focusZone) {
        if (focusUi.focusZone != VodFocusZone.CONTENT) return@LaunchedEffect
        if (contentFilter != VodContentFilter.ALL || showBrowseGrid) return@LaunchedEffect
        restoreWallFocusAfterRebuild(focusedContentKey)
    }

    LaunchedEffect(focusUi.awaitingBrowseGridFocus, contentFilter) {
        if (!focusUi.awaitingBrowseGridFocus) return@LaunchedEffect
        delay(1_500L)
        if (!focusUi.awaitingBrowseGridFocus) return@LaunchedEffect
        if (browseGridItemCount() > 0) return@LaunchedEffect
        focusController.escapeAwaitingBrowseGridFocus()
    }

    fun applyGenre(index: Int) {
        VodPerfLogger.markInput("genreSelect.ui", "index=$index filter=$contentFilter")
        applyGenreSelection(index)
        focusUi.focusZone = VodFocusZone.CONTENT
        focusController.focusBrowseGridRestored()
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

    fun openContinueWatchingDetail(item: ContinueWatchingItem) {
        when (item.contentType) {
            ContinueWatchingContentType.MOVIE -> {
                scope.launch {
                    val movie = item.streamId?.let { streamId ->
                        moviesViewModel.resolveMovie(item.playlistId, streamId)
                    } ?: VodItem(
                        id = 0L,
                        title = item.title,
                        streamId = item.streamId ?: 0L,
                        streamUrl = item.streamUrl,
                        posterUrl = item.posterUrl,
                        plot = null,
                        cast = null,
                        director = null,
                        genre = null,
                        rating = null,
                        duration = null,
                        playlistId = item.playlistId
                    )
                    openMovieDetail(movie)
                }
            }
            ContinueWatchingContentType.SERIES -> {
                item.seriesId?.let { seriesId ->
                    seriesViewModel.selectShow(
                        showId = seriesId,
                        playlistId = item.playlistId,
                        preferredSeason = item.seasonNumber,
                        preview = SeriesShow(
                            id = seriesId,
                            name = item.title,
                            coverUrl = item.posterUrl,
                            playlistId = item.playlistId
                        )
                    )
                }
            }
        }
    }

    fun activateWallItem(item: VodWallItem) {
        when (item) {
            is VodWallItem.ContinueItem -> openContinueWatchingDetail(item.item)
            is VodWallItem.MovieItem -> openMovieDetail(item.movie)
            is VodWallItem.SeriesItem -> seriesViewModel.selectShow(
                item.show.id,
                item.show.playlistId,
                preferredSeason = null,
                preview = item.show
            )
        }
    }

    SideEffect {
        focusController.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = contentFilter,
                searchQuery = searchQuery,
                showGenrePanel = showGenrePanel,
                showLibraryNavPanel = showLibraryNavPanel,
                showBrowseGrid = showBrowseGrid,
                showInlineSearch = showInlineSearch,
                hasHero = false,
                hasBrowseResults = hasBrowseResults,
                genreLabels = genreLabels,
                wallRows = wallRows,
                displayWallRows = displayWallRows,
                navDrawerOpen = focusUi.navDrawerOpen,
                filterPanelFocusRequester = filterPanelFocusRequester,
                genrePanelFocusRequester = genrePanelFocusRequester,
                languageSubmenuFocusRequester = languageSubmenuFocusRequester,
                browseGridFocusRequester = browseGridFocusRequester,
                browseEmptyStateFocusRequester = browseEmptyStateFocusRequester,
                rootFocusRequester = rootFocusRequester,
                heroPlayFocusRequester = rootFocusRequester,
                inlineSearchFocusRequester = inlineSearchFocusRequester,
                navDrawerFocusRequester = navDrawerFocusRequester,
                browseGridFocusIndex = browseGridFocusIndex,
                setBrowseGridFocusIndex = { browseGridFocusIndex = it },
                contentRowIndex = contentRowIndex,
                setContentRowIndex = { contentRowIndex = it },
                contentColIndex = contentColIndex,
                setContentColIndex = { contentColIndex = it },
                browseGridItemCount = ::browseGridItemCount,
                browseGridCatalogTotal = ::browseGridCatalogTotal,
                focusContentMode = ::focusContentMode,
                isBrowseGridLoading = ::isBrowseGridLoading,
                browseGridKeyAtIndex = ::browseGridKeyAtIndex,
                activeBrowseGridState = ::activeBrowseGridState,
                syncFocusedWallItemKey = ::syncFocusedWallItemKey,
                commitFilterHighlight = ::commitFilterHighlight,
                applyGenre = ::applyGenre,
                activateWallItem = ::activateWallItem,
                openLanguagePreferenceDialog = ::openLanguagePreferenceDialog,
                refreshAvailableLanguages = hubViewModel::refreshAvailableVodLanguages,
                togglePreferredLanguage = ::togglePreferredLanguage,
                languageFilterActive = languageFilterActive,
                availableLanguages = availableVodLanguages,
                openNavDrawer = ::openNavDrawer,
                closeNavDrawer = ::closeNavDrawer,
                selectVodDrawerItem = ::selectVodDrawerItem,
                focusInlineSearchField = ::focusInlineSearchField,
                focusSearchResults = ::focusSearchResults,
                moviesBrowseGridActivate = moviesBrowseGridHandle::activateFocusedIndex,
                seriesBrowseGridActivate = seriesBrowseGridHandle::activateFocusedIndex,
                ensureValidFocus = ::ensureValidFocus,
                hubTabsNavigable = { hubCatalogReady },
            )
        )
    }

    fun handleLibraryNavPanelKey(event: KeyEvent): Boolean =
        focusController.handleLibraryNavPanelKey(event)

    fun handleFilterPanelKey(event: KeyEvent): Boolean =
        handleLibraryNavPanelKey(event)

    fun handleGenrePanelKey(event: KeyEvent): Boolean =
        focusController.handleGenrePanelKey(event)

    fun handleLanguageSubmenuKey(event: KeyEvent): Boolean =
        focusController.handleLanguageSubmenuKey(event)

    fun handleContentKey(event: KeyEvent): Boolean =
        focusController.handleContentKey(event)

    fun consumeVodLocalBack(): Boolean = when {
        showGlobalSearchOverlay -> {
            dismissGlobalSearch()
            true
        }
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
        focusUi.profileMenuOpen -> {
            focusUi.profileMenuOpen = false
            true
        }
        focusUi.focusZone == VodFocusZone.CONTENT ||
            focusUi.focusZone == VodFocusZone.FILTER_PANEL || focusUi.focusZone == VodFocusZone.GENRE_PANEL -> {
            openNavDrawer()
            true
        }
        focusUi.focusZone == VodFocusZone.LANGUAGE_SUBMENU -> {
            focusController.focusLibraryNavFromLanguageSubmenu()
            true
        }
        focusUi.focusZone == VodFocusZone.NAV_DRAWER -> {
            closeNavDrawer()
            true
        }
        else -> false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    val contentAmbientPosterUrl = when {
        searchQuery.isNotBlank() || showBrowseGrid -> null
        focusUi.focusZone == VodFocusZone.CONTENT && wallRows.isNotEmpty() -> {
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
                    !(showInlineSearch && focusUi.vodSearchFocused) &&
                    !showGlobalSearchOverlay &&
                    focusUi.focusZone != VodFocusZone.FILTER_PANEL &&
                    focusUi.focusZone != VodFocusZone.GENRE_PANEL &&
                    focusUi.focusZone != VodFocusZone.LANGUAGE_SUBMENU
            )
            .focusProperties {
                if (seriesDetailOpen || movieDetailOpen) {
                    canFocus = false
                }
            }
            .onPreviewKeyEvent { event ->
                if (showGlobalSearchOverlay) return@onPreviewKeyEvent false
                if (seriesDetailOpen || movieDetailOpen) {
                    return@onPreviewKeyEvent false
                }
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) {
                    return@onPreviewKeyEvent false
                }
                if (showInlineSearch && focusUi.vodSearchFocused && !imeTypingActive) {
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        return@onPreviewKeyEvent consumeVodLocalBack()
                    }
                    return@onPreviewKeyEvent false
                }
                if (focusUi.profileMenuOpen && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            focusUi.profileMenuOpen = false
                            return@onPreviewKeyEvent true
                        }
                        else -> Unit
                    }
                }
                if (focusUi.navDrawerOpen && focusUi.focusZone == VodFocusZone.NAV_DRAWER) {
                    if (handleNavDrawerKey(event)) return@onPreviewKeyEvent true
                }
                val handled = when (event.key) {
                    Key.Back, Key.Escape -> consumeVodLocalBack()
                    else -> when (focusUi.focusZone) {
                        VodFocusZone.NAV_DRAWER -> handleNavDrawerKey(event)
                        VodFocusZone.FILTER_PANEL -> handleLibraryNavPanelKey(event)
                        VodFocusZone.GENRE_PANEL -> handleGenrePanelKey(event)
                        VodFocusZone.LANGUAGE_SUBMENU -> handleLanguageSubmenuKey(event)
                        VodFocusZone.HERO -> false
                        VodFocusZone.CONTENT -> {
                            if (
                                focusUi.libraryNavPanelVisible &&
                                event.type == KeyEventType.KeyDown &&
                                isVodDirectionalKey(event)
                            ) {
                                focusUi.focusZone = VodFocusZone.FILTER_PANEL
                                handleLibraryNavPanelKey(event)
                            } else {
                                handleContentKey(event)
                            }
                        }
                    }
                }
                if (handled) return@onPreviewKeyEvent true
                if (imeTypingActive) return@onPreviewKeyEvent false
                if (focusUi.focusZone in vodManualFocusZones && isVodDirectionalKey(event)) {
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        VodAmbientBackdrop(posterUrl = contentAmbientPosterUrl)

        Row(
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
            GuideNavDrawer(
                focusedIndex = focusUi.navDrawerFocusIndex,
                drawerActive = focusUi.focusZone == VodFocusZone.NAV_DRAWER,
                drawerFocusRequester = navDrawerFocusRequester,
                profileInitials = profileInitials,
                profileAvatarColor = profileAvatarColor,
                profileFocused = focusUi.profileMenuOpen,
                onProfileClick = {
                    focusUi.profileMenuOpen = true
                    focusUi.profileMenuFocusIndex = 0
                },
                onItemFocused = { index ->
                    focusUi.navDrawerFocusIndex = index
                    focusUi.navDrawerOpen = true
                    focusUi.focusZone = VodFocusZone.NAV_DRAWER
                },
                onItemSelected = ::selectVodDrawerItem,
                onPreviewKey = ::handleNavDrawerKey,
                selectedItem = GuideNavDrawerItem.Vod
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
            EpgGuideHeader(
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                onRecordingIndicatorClick = onNavigateRecordings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = libraryContentInset)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (imeTypingActive) {
                            Modifier.focusProperties { canFocus = false }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = libraryContentInset)
                ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    VodCatalogRefreshWarningBanner(message = tmdbWarning)
                    when {
                        showHubUnifiedLoading -> {
                            VodCatalogOnboardingPanel(
                                progress = catalogProgress,
                                onboardingInputs = onboardingInputs,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        showInlineSearch -> {
                            VodHubSearchSection(
                                query = searchQuery,
                                moviesViewModel = moviesViewModel,
                                seriesViewModel = seriesViewModel,
                                progressByKey = vodProgress,
                                onQueryChange = hubViewModel::setSearchQuery,
                                onMovieClick = ::openMovieDetail,
                                onSeriesCardClick = { card ->
                                    seriesViewModel.selectShow(card.showId, card.playlistId)
                                },
                                searchFocusRequester = inlineSearchFocusRequester,
                                resultsFocusRequester = browseGridFocusRequester,
                                onFocusSearchField = { focusUi.vodSearchFocused = true },
                                onFocusResults = ::focusSearchResults,
                                onNavigateUpFromResults = ::focusInlineSearchField,
                                onHasResultsChange = { searchHasResults = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        contentFilter == VodContentFilter.MOVIES -> {
                            val moviesGridMemory = focusUi.gridMemoryFor(VodContentFilter.MOVIES)
                            val gridFocused = focusUi.focusZone == VodFocusZone.CONTENT && !showInlineSearch
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                VodHubMoviesBrowseSection(
                                    moviesViewModel = moviesViewModel,
                                    progressByKey = vodProgress,
                                    onItemClick = ::openMovieDetail,
                                    gridState = moviesBrowseGridState,
                                    gridFocused = gridFocused,
                                    focusedItemIndex = if (gridFocused && browseGridItemCount() > 0) {
                                        browseGridFocusIndex.coerceIn(0, browseGridItemCount() - 1)
                                    } else {
                                        -1
                                    },
                                    browseGridHandle = moviesBrowseGridHandle,
                                    contentGridFocusRequester = browseGridFocusRequester,
                                    emptyStateRetryFocusRequester = browseEmptyStateFocusRequester,
                                    onColumnCountChanged = { focusUi.browseGridColumnCount = it },
                                    onNavigateUpFromFirstRow = ::focusFilterPanelFromGenre,
                                    onLeadingEdgeNavigateLeft = focusController::handleBrowseGridLeadingEdgeLeft,
                                    restoreScrollIndex = -1,
                                    restoreScrollOffset = moviesGridMemory.scrollOffset,
                                    gridRestoreRequest = focusUi.gridRestoreRequest,
                                    onGridRestoreComplete = focusController::onGridRestoreComplete,
                                    surfaceState = moviesBrowseSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            }
                        }
                        contentFilter == VodContentFilter.SERIES -> {
                            val seriesGridMemory = focusUi.gridMemoryFor(VodContentFilter.SERIES)
                            val gridFocused = focusUi.focusZone == VodFocusZone.CONTENT && !showInlineSearch
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                VodHubSeriesBrowseSection(
                                    seriesViewModel = seriesViewModel,
                                    progressByKey = vodProgress,
                                    onSeriesCardClick = { card ->
                                        seriesViewModel.selectShow(card.showId, card.playlistId)
                                    },
                                    gridState = seriesBrowseGridState,
                                    gridFocused = gridFocused,
                                    focusedItemIndex = if (gridFocused && browseGridItemCount() > 0) {
                                        browseGridFocusIndex.coerceIn(0, browseGridItemCount() - 1)
                                    } else {
                                        -1
                                    },
                                    browseGridHandle = seriesBrowseGridHandle,
                                    contentGridFocusRequester = browseGridFocusRequester,
                                    emptyStateRetryFocusRequester = browseEmptyStateFocusRequester,
                                    onColumnCountChanged = { focusUi.browseGridColumnCount = it },
                                    onNavigateUpFromFirstRow = ::focusFilterPanelFromGenre,
                                    onLeadingEdgeNavigateLeft = focusController::handleBrowseGridLeadingEdgeLeft,
                                    restoreScrollIndex = -1,
                                    restoreScrollOffset = seriesGridMemory.scrollOffset,
                                    gridRestoreRequest = focusUi.gridRestoreRequest,
                                    onGridRestoreComplete = focusController::onGridRestoreComplete,
                                    surfaceState = seriesBrowseSurface,
                                    isSeriesStillLoading = isSeriesStillLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            }
                        }
                        wallRows.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
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
                                            progressByKey = vodProgress,
                                            posterUrlForMovie = ::posterFor,
                                            ratingForMovie = ::ratingFor,
                                            metaForMovie = ::metaFor,
                                            overviewForMovie = ::overviewForMovie,
                                            overviewForSeries = ::overviewForSeries,
                                            onActivateItem = ::activateWallItem
                                        )
                                    }
                                    allSurfaceState is VodHubSurfaceState.Empty &&
                                        allSurfaceState.variant ==
                                        VodHubSurfaceState.Empty.EmptyVariant.ALL_LANGUAGE_FILTER -> {
                                        VodEmptyState(
                                            title = allSurfaceState.title,
                                            message = allSurfaceState.message,
                                            onRetry = ::openLanguagePreferenceDialog
                                        )
                                    }
                                    allSurfaceState is VodHubSurfaceState.Empty &&
                                        allSurfaceState.variant ==
                                        VodHubSurfaceState.Empty.EmptyVariant.ALL_CATALOG -> {
                                        VodEmptyState(
                                            title = allSurfaceState.title,
                                            message = allSurfaceState.message,
                                            onRetry = { moviesViewModel.refreshCatalog() }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (allSurfaceState is VodHubSurfaceState.Ready && allSurfaceState.showOnboardingStrip) {
                                    VodCatalogOnboardingPanel(
                                        progress = catalogProgress,
                                        onboardingInputs = allSurfaceState.onboardingInputs,
                                        compact = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                LazyColumn(
                                state = columnListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .focusGroup(),
                                contentPadding = PaddingValues(bottom = 64.dp)
                            ) {
                                itemsIndexed(homeLeadWallRows, key = { _, row -> row.id }) { index, row ->
                                    val rowListState = remember(row.id) { LazyListState() }
                                    val rowFocused = focusUi.focusZone == VodFocusZone.CONTENT && contentRowIndex == index
                                    val focusedColumn = if (rowFocused) contentColIndex else -1
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = VodPosterFocusLayout.estimatedWallRowHeight)
                                            .graphicsLayer { clip = false }
                                    ) {
                                        NetflixContentWallRow(
                                            row = row,
                                            rowIndex = index,
                                            focusedColumn = focusedColumn,
                                            rowFocused = rowFocused,
                                            listState = rowListState,
                                            progressByKey = vodProgress,
                                            posterUrlForMovie = ::posterFor,
                                            ratingForMovie = ::ratingFor,
                                            metaForMovie = ::metaFor,
                                            overviewForMovie = ::overviewForMovie,
                                            overviewForSeries = ::overviewForSeries,
                                            onActivateItem = ::activateWallItem,
                                            firstItemFocusRequester = if (index == 0) contentFocusRequester else null,
                                            sidebarFocusRequester = filterPanelFocusRequester,
                                        )
                                    }
                                }
                                itemsIndexed(homeTailWallRows, key = { _, row -> row.id }) { tailIndex, row ->
                                    val index = homeCategoryStartIndex + tailIndex
                                    val rowListState = remember(row.id) { LazyListState() }
                                    val rowFocused = focusUi.focusZone == VodFocusZone.CONTENT && contentRowIndex == index
                                    val focusedColumn = if (rowFocused) contentColIndex else -1
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = VodPosterFocusLayout.estimatedWallRowHeight)
                                            .graphicsLayer { clip = false }
                                    ) {
                                        NetflixContentWallRow(
                                            row = row,
                                            rowIndex = index,
                                            focusedColumn = focusedColumn,
                                            rowFocused = rowFocused,
                                            listState = rowListState,
                                            progressByKey = vodProgress,
                                            posterUrlForMovie = ::posterFor,
                                            ratingForMovie = ::ratingFor,
                                            metaForMovie = ::metaFor,
                                            overviewForMovie = ::overviewForMovie,
                                            overviewForSeries = ::overviewForSeries,
                                            onActivateItem = ::activateWallItem,
                                            firstItemFocusRequester = if (index == homeCategoryStartIndex && homeLeadWallRows.isEmpty()) {
                                                contentFocusRequester
                                            } else {
                                                null
                                            },
                                            sidebarFocusRequester = filterPanelFocusRequester,
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
                }
                if (showLibraryOverlayScrim) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = libraryContentInset)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .zIndex(1f),
                    )
                }
                if (showGenrePanel && hubCatalogReady && focusUi.focusZone == VodFocusZone.GENRE_PANEL) {
                    VodGenreSidePanel(
                        genres = genreLabels,
                        selectedIndex = selectedGenreIndex,
                        focusedIndex = focusUi.genreFocusIndex,
                        panelFocused = true,
                        contentGridFocusRequester = browseGridFocusRequester,
                        libraryNavFocusRequester = filterPanelFocusRequester,
                        entryFocusRequester = genrePanelFocusRequester,
                        onFocusedIndexChange = { focusUi.genreFocusIndex = it },
                        onGenreSelected = ::applyGenre,
                        onPreviewKey = ::handleGenrePanelKey,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = librarySubPanelOffset)
                            .fillMaxHeight(),
                    )
                }
                if (focusUi.focusZone == VodFocusZone.LANGUAGE_SUBMENU) {
                    VodLanguageSubmenuPanel(
                        availableLanguages = availableVodLanguages,
                        selectedLanguages = preferredVodLanguages,
                        focusedIndex = focusUi.languageSubmenuFocusIndex,
                        panelFocused = true,
                        onFocusedIndexChange = { focusUi.languageSubmenuFocusIndex = it },
                        onLanguageToggle = ::togglePreferredLanguage,
                        onPreviewKey = ::handleLanguageSubmenuKey,
                        entryFocusRequester = languageSubmenuFocusRequester,
                        libraryNavFocusRequester = filterPanelFocusRequester,
                        contentFocusRequester = rootFocusRequester,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = librarySubPanelOffset)
                            .fillMaxHeight(),
                    )
                }
            }
                }
                if (showLibraryNavPanel) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLibraryRail,
                        enter = slideInHorizontally { -it } + fadeIn(),
                        exit = slideOutHorizontally { -it } + fadeOut(),
                    ) {
                        VodLibraryNavPanel(
                            selectedFilter = contentFilter,
                            focusedIndex = focusUi.filterFocusIndex,
                            panelFocused = focusUi.focusZone == VodFocusZone.FILTER_PANEL,
                            languageFilterActive = languageFilterActive,
                            tabsNavigable = hubCatalogReady,
                            panelFocusRequester = filterPanelFocusRequester,
                            contentFocusRequester = rootFocusRequester,
                            navDrawerFocusRequester = navDrawerFocusRequester,
                            genrePanelFocusRequester = if (showGenrePanel) genrePanelFocusRequester else null,
                            onPanelFocused = { focusUi.focusZone = VodFocusZone.FILTER_PANEL },
                            onFocusedIndexChange = { index ->
                                focusUi.filterFocusIndex = index
                            },
                            onItemSelected = { index ->
                                if (hubCatalogReady) {
                                    commitFilterHighlight(index)
                                }
                            },
                            onPreviewKey = ::handleLibraryNavPanelKey,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxHeight()
                                .zIndex(2f),
                        )
                    }
                }
        }
        }

        selectedMovie?.let { movie ->
            val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
            recommendationFeedbackRevision
            val movieVote = hubViewModel.recommendationVoteFor(movie)
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
                watchFocusRequester = movieWatchFocusRequester,
                recommendationVote = movieVote,
                onRecommendationVote = { vote -> hubViewModel.voteRecommendation(movie, vote) }
            )
        }

        if (selectedShowId != null) {
            SeriesBrowserScreen(
                initialSeriesId = selectedShowId,
                initialPlaylistId = selectedShow?.playlistId ?: initialPlaylistId,
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

        if (showGlobalSearchOverlay) {
            BackHandler {
                dismissGlobalSearch()
            }
            SearchOverlay(
                query = globalSearchQuery,
                unifiedResults = globalUnifiedSearchResults,
                flatResults = globalSearchResults,
                searchBarState = globalSearchBarState,
                onQueryChange = searchViewModel::updateQuery,
                onClear = searchViewModel::clearQuery,
                onDismiss = ::dismissGlobalSearch,
                onMicClick = {
                    if (globalSearchBarState == SearchBarState.LISTENING) {
                        searchViewModel.stopVoiceSearch()
                    } else {
                        searchViewModel.beginVoiceSearch()
                    }
                },
                onResultSelected = ::handleGlobalSearchResult,
                onSuggestionSelected = searchViewModel::applyTrendingOrRecent,
                onClearHistory = searchViewModel::clearRecentHistory
            )
        }

        ProfileMenuDropdown(
            expanded = focusUi.profileMenuOpen,
            focusedIndex = focusUi.profileMenuFocusIndex,
            profileDisplayName = profileDisplayName,
            onDismiss = { focusUi.profileMenuOpen = false },
            onSwitchAccounts = {
                focusUi.profileMenuOpen = false
                onNavigateProfile()
            },
            onOpenSettings = {
                focusUi.profileMenuOpen = false
                onNavigateSettings()
            },
            onQuitApp = { context.quitAppToHome() },
            showSwitchAccounts = false,
            anchorFromSidebar = true
        )
    }
}
