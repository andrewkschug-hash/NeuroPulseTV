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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
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
import com.grid.tv.ui.component.VodGenreSidePanel
import com.grid.tv.ui.component.runtimeLabelForMovie
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.VodAmbientBackdrop
import com.grid.tv.ui.component.VodContentFilterTabBar
import com.grid.tv.domain.model.VodSidebarGenreNormalizer
import com.grid.tv.ui.component.VodPosterFocusLayout
import com.grid.tv.ui.component.VodHubLanguageFilterFocusIndex
import com.grid.tv.ui.component.VodHubTabFilters
import com.grid.tv.ui.component.vodHubTabFilterIndex
import com.grid.tv.ui.component.vodHubTabFilterIndex
import com.grid.tv.ui.component.VodLanguagePreferenceDialog
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogOnboardingPanel
import com.grid.tv.ui.component.rememberVodCatalogOnboardingVisible
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.feature.vod.VodHubFoldMetrics
import com.grid.tv.feature.vod.VodHubHeroIsland
import com.grid.tv.feature.vod.VodHubHeroAmbientPoster
import com.grid.tv.feature.vod.handleVodHubHeroKeyEvent
import com.grid.tv.feature.vod.rememberVodHubFoldScroller
import com.grid.tv.feature.vod.resolveVodWallFocus
import com.grid.tv.feature.vod.vodWallItemKey
import com.grid.tv.ui.component.movieMetaSubtitle
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors
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

private val vodImmediateWallRowIds = setOf("continue_watching", "trending", "recommended")

private enum class VodFocusZone {
    NAV_DRAWER,
    FILTER_PANEL,
    GENRE_PANEL,
    HERO,
    CONTENT
}

private val vodManualFocusZones = setOf(
    VodFocusZone.NAV_DRAWER,
    VodFocusZone.FILTER_PANEL,
    VodFocusZone.GENRE_PANEL,
    VodFocusZone.HERO
)

private fun isVodDirectionalKey(event: KeyEvent): Boolean =
    event.key == Key.DirectionUp ||
        event.key == Key.DirectionDown ||
        event.key == Key.DirectionLeft ||
        event.key == Key.DirectionRight

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
    var focusZone by remember { mutableStateOf(VodFocusZone.FILTER_PANEL) }
    var navDrawerOpen by remember { mutableStateOf(false) }
    var navDrawerFocusIndex by remember {
        mutableIntStateOf(guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Vod))
    }
    var filterFocusIndex by remember { mutableIntStateOf(0) }
    var contentRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentColIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedContentKey by rememberSaveable { mutableStateOf<String?>(null) }
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

    /** Stable catalog/filter/wall snapshot — does not include hero carousel index. */
    val contentState by hubViewModel.contentState.collectAsStateWithLifecycle()
    val recommendationFeedbackRevision by hubViewModel.recommendationFeedbackRevision.collectAsStateWithLifecycle()

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

    val featuredCarousel = contentState.hero.featuredCarousel
    val hasHero = featuredCarousel.isNotEmpty()
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
    val immediateWallRows = remember(wallRows) {
        wallRows.filter { it.id in vodImmediateWallRowIds }
    }
    val deferredWallRows = remember(wallRows) {
        wallRows.filter { it.id !in vodImmediateWallRowIds }
            .sortedWith(
                compareBy({ VodSidebarGenreNormalizer.sidebarSortRank(it.title) }, { it.title.lowercase() })
            )
    }
    var loadedDeferredWallCount by rememberSaveable(wallRowsRevision) { mutableIntStateOf(0) }
    val displayWallRows = remember(contentFilter, wallRows, immediateWallRows, deferredWallRows, loadedDeferredWallCount) {
        if (contentFilter != VodContentFilter.ALL) {
            wallRows
        } else {
            immediateWallRows + deferredWallRows.take(loadedDeferredWallCount)
        }
    }
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
    val showHeroIsland = contentFilter == VodContentFilter.ALL && searchQuery.isBlank() && !showBrowseGrid
    val requestHeroPlayFocus = focusZone == VodFocusZone.HERO &&
        hasHero &&
        searchQuery.isBlank() &&
        !showInlineSearch
    val scope = rememberCoroutineScope()
    val columnListState = rememberLazyListState()
    val browseGridState = rememberLazyGridState()
    val density = LocalDensity.current
    val heroExpandedPx = with(density) { VodHubFoldMetrics.HeroExpandedHeight.toPx() }
    val vodFoldScroller = rememberVodHubFoldScroller(columnListState, heroExpandedPx)
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
    val vodWallRowHeightPx = remember(density) {
        with(density) { VodPosterFocusLayout.estimatedWallRowHeight.roundToPx() }
    }

    LaunchedEffect(initialTab) {
        hubViewModel.setContentFilter(
            when (initialTab) {
                1 -> VodContentFilter.SERIES
                else -> VodContentFilter.ALL
            }
        )
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

    val showVodOnboarding = contentFilter != VodContentFilter.SEARCH &&
        rememberVodCatalogOnboardingVisible(onboardingInputs)
    val showVodOnboardingFull = showVodOnboarding && wallRows.isEmpty()
    val showVodOnboardingStrip = showVodOnboarding && wallRows.isNotEmpty() && !showVodOnboardingFull

    fun syncFocusedWallItemKey() {
        val key = displayWallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex)?.key
        focusedContentKey = key
        hubViewModel.rememberFocusedContentKey(key)
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
        VodPerfLogger.logStage(
            "focusRestore",
            (System.nanoTime() - started) / 1_000_000,
            "before=$focusBefore after=$keyAfter row=$row col=$col"
        )
    }

    LaunchedEffect(wallRows.size) {
        val maxRow = displayWallRows.lastIndex.coerceAtLeast(0)
        if (displayWallRows.isEmpty()) return@LaunchedEffect
        contentRowIndex = contentRowIndex.coerceIn(0, maxRow)
        val maxCol = displayWallRows.getOrNull(contentRowIndex)?.items?.lastIndex ?: 0
        contentColIndex = contentColIndex.coerceIn(0, maxCol.coerceAtLeast(0))
    }

    LaunchedEffect(deferredWallRows.size, contentFilter) {
        if (contentFilter == VodContentFilter.ALL && deferredWallRows.isNotEmpty() && loadedDeferredWallCount == 0) {
            loadedDeferredWallCount = minOf(2, deferredWallRows.size)
        }
    }

    LaunchedEffect(contentRowIndex, focusZone, contentFilter, deferredWallRows.size, immediateWallRows.size) {
        if (contentFilter != VodContentFilter.ALL || showBrowseGrid || focusZone != VodFocusZone.CONTENT) return@LaunchedEffect
        val neededDeferred = (contentRowIndex - immediateWallRows.size + 2)
            .coerceIn(0, deferredWallRows.size)
        if (neededDeferred > loadedDeferredWallCount) {
            loadedDeferredWallCount = neededDeferred
        }
    }

    LaunchedEffect(wallRows.size, hasHero, searchQuery) {
        if (searchQuery.isBlank() && wallRows.isEmpty() && focusZone == VodFocusZone.CONTENT) {
            focusZone = if (hasHero) VodFocusZone.HERO else VodFocusZone.FILTER_PANEL
        }
    }

    LaunchedEffect(focusZone, contentRowIndex, showHeroIsland, showBrowseGrid, searchQuery, displayWallRows.size, showInlineSearch) {
        if (searchQuery.isNotBlank() || showBrowseGrid || showInlineSearch) return@LaunchedEffect
        when (focusZone) {
            VodFocusZone.HERO -> {
                columnListState.animateScrollVodWallRowIntoView(
                    index = 0,
                    direction = TvLazyFocusScrollDirection.UP,
                    safePaddingPx = vodWallScrollSafePaddingPx,
                    fallbackItemHeightPx = vodWallRowHeightPx
                )
            }
            VodFocusZone.CONTENT -> {
                val heroOffset = if (showHeroIsland) 1 else 0
                columnListState.animateScrollVodWallRowIntoView(
                    index = (contentRowIndex + heroOffset).coerceAtLeast(0),
                    direction = contentScrollDirection,
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

    val navDrawerFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val heroMoreInfoFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val browseGridFocusRequester = remember { FocusRequester() }

    LaunchedEffect(wallRowsRevision, preferredVodLanguages, includeUntaggedVodContent) {
        val focusBefore = focusedContentKey
            ?: vodWallItemKey(wallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex))
        restoreWallFocusAfterRebuild(focusBefore)
        if (focusZone == VodFocusZone.CONTENT && displayWallRows.isNotEmpty() && !showBrowseGrid && searchQuery.isBlank()) {
            contentFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    val filterPanelFocusRequester = remember { FocusRequester() }
    val genrePanelFocusRequester = remember { FocusRequester() }
    val inlineSearchFocusRequester = remember { FocusRequester() }
    val movieWatchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(genreLabels.size, selectedGenreIndex) {
        genreFocusIndex = selectedGenreIndex.coerceIn(0, (genreLabels.lastIndex).coerceAtLeast(0))
    }

    fun browseGridItemCount(): Int = when (contentFilter) {
        VodContentFilter.MOVIES -> moviesBrowseGridHandle.itemCount
        VodContentFilter.SERIES -> seriesBrowseGridHandle.itemCount
        VodContentFilter.ALL, VodContentFilter.SEARCH -> 0
    }

    LaunchedEffect(browseGridItemCount(), contentFilter) {
        val lastIndex = (browseGridItemCount() - 1).coerceAtLeast(0)
        browseGridFocusIndex = browseGridFocusIndex.coerceIn(0, lastIndex)
    }

    LaunchedEffect(
        focusZone,
        showInlineSearch,
        vodSearchFocused,
        requestHeroPlayFocus,
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
            requestHeroPlayFocus -> Unit
            focusZone == VodFocusZone.FILTER_PANEL ->
                filterPanelFocusRequester.requestFocusSafelyAfterLayout()
            focusZone == VodFocusZone.GENRE_PANEL ||
                focusZone == VodFocusZone.NAV_DRAWER ||
                (focusZone == VodFocusZone.CONTENT && !showInlineSearch && (showBrowseGrid || wallRows.isEmpty())) ->
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
        filterFocusIndex = vodHubTabFilterIndex(contentFilter)
        scope.launch {
            filterPanelFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusBrowseGridFromSidebar() {
        val count = browseGridItemCount()
        if (count <= 0) return
        focusZone = VodFocusZone.CONTENT
        browseGridFocusIndex = 0
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
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

    fun openGlobalSearch() {
        showGlobalSearchOverlay = true
        focusZone = VodFocusZone.FILTER_PANEL
    }

    fun openNavDrawer() {
        navDrawerOpen = true
        navDrawerFocusIndex = guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Vod)
        focusZone = VodFocusZone.NAV_DRAWER
    }

    fun closeNavDrawer(restoreFilter: Boolean = true) {
        navDrawerOpen = false
        if (restoreFilter) {
            focusZone = VodFocusZone.FILTER_PANEL
            scope.launch {
                filterPanelFocusRequester.requestFocusSafelyAfterLayout()
            }
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

    fun handleNavDrawerKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                else -> false
            }
        }
        val lastIndex = GuideNavDrawerItems.size
        return when (event.key) {
            Key.Back, Key.Escape -> {
                closeNavDrawer()
                true
            }
            Key.DirectionRight -> {
                if (navDrawerFocusIndex == GuideNavDrawerProfileFocusIndex) {
                    navDrawerFocusIndex = guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Vod)
                } else {
                    closeNavDrawer()
                }
                true
            }
            Key.DirectionDown -> {
                if (navDrawerFocusIndex < lastIndex) {
                    navDrawerFocusIndex += 1
                }
                true
            }
            Key.DirectionUp -> {
                if (navDrawerFocusIndex > GuideNavDrawerProfileFocusIndex) {
                    navDrawerFocusIndex -= 1
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (navDrawerFocusIndex == GuideNavDrawerProfileFocusIndex) {
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                    true
                } else {
                    GuideNavDrawerItems.getOrNull(navDrawerFocusIndex - 1)?.let { item ->
                        selectVodDrawerItem(item)
                        true
                    } ?: false
                }
            }
            else -> false
        }
    }

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
        vodSearchFocused = false
        if (contentFilter == VodContentFilter.SEARCH) {
            hubViewModel.setContentFilter(VodContentFilter.ALL)
            filterFocusIndex = vodHubTabFilterIndex(VodContentFilter.ALL)
        }
        focusZone = VodFocusZone.FILTER_PANEL
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

    fun applyContentFilter(index: Int) {
        if (index == VodHubLanguageFilterFocusIndex) {
            openLanguagePreferenceDialog()
            return
        }
        val filter = VodHubTabFilters.getOrNull(index) ?: VodContentFilter.ALL
        if (filter == contentFilter &&
            filter != VodContentFilter.MOVIES &&
            filter != VodContentFilter.SERIES
        ) {
            filterFocusIndex = index
            return
        }
        if (filter != VodContentFilter.SEARCH && contentFilter == VodContentFilter.SEARCH) {
            hubViewModel.setSearchQuery("")
            vodSearchFocused = false
        }
        filterFocusIndex = index
        hubViewModel.setContentFilter(filter)
        hubViewModel.setMovieCategory(null)
        hubViewModel.setSeriesCategory(null)
        moviesViewModel.setCategory(null)
        seriesViewModel.setCategory(null)
        genreFocusIndex = 0
        browseGridFocusIndex = 0
        contentRowIndex = 0
        contentColIndex = 0
        if (filter == VodContentFilter.SEARCH) {
            vodSearchFocused = true
            focusZone = VodFocusZone.CONTENT
            scope.launch {
                inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
            }
            return
        }
        focusZone = when (filter) {
            VodContentFilter.ALL -> VodFocusZone.CONTENT
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> VodFocusZone.GENRE_PANEL
            VodContentFilter.SEARCH -> VodFocusZone.CONTENT
        }
        onNavigateVod(
            when (filter) {
                VodContentFilter.SERIES -> 1
                else -> 0
            }
        )
        if (filter == VodContentFilter.MOVIES || filter == VodContentFilter.SERIES) {
            scope.launch {
                genrePanelFocusRequester.requestFocusSafelyAfterLayout()
            }
        }
    }

    fun moveFilterFocus(delta: Int) {
        val nextIndex = (filterFocusIndex + delta).coerceIn(0, VodHubLanguageFilterFocusIndex)
        filterFocusIndex = nextIndex
        if (nextIndex < VodHubLanguageFilterFocusIndex) {
            applyContentFilter(nextIndex)
        }
    }

    fun applyGenre(index: Int) {
        VodPerfLogger.markInput("genreSelect.ui", "index=$index filter=$contentFilter")
        genreFocusIndex = index.coerceIn(0, genreLabels.lastIndex.coerceAtLeast(0))
        when (contentFilter) {
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
                    Log.d("VodSeriesGenre", "applyGenre index=0 categoryId=null filterIds=null (All)")
                    hubViewModel.setSeriesCategory(null)
                    seriesViewModel.setCategory(null)
                } else {
                    val category = sidebarSeriesCategories.getOrNull(index - 1)
                    val filterIds = category?.let {
                        seriesCategoryFilterIdsByRepresentativeId[
                            categoryKey(it.playlistId, it.id)
                        ]
                    }
                    Log.d(
                        "VodSeriesGenre",
                        "applyGenre index=$index representativeId=${category?.id} " +
                            "playlistId=${category?.playlistId} filterIds=$filterIds name=${category?.name}"
                    )
                    hubViewModel.setSeriesCategory(category?.id, filterIds, category?.playlistId)
                    seriesViewModel.setCategory(category?.id, filterIds, category?.playlistId)
                }
            }
            else -> Unit
        }
        focusZone = VodFocusZone.CONTENT
        browseGridFocusIndex = 0
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
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
                        filterFocusIndex = vodHubTabFilterIndex(contentFilter)
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
                } else {
                    focusFilterPanelFromGenre()
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
                        moviesBrowseGridHandle.activateFocusedIndex(browseGridFocusIndex)
                    VodContentFilter.SERIES ->
                        seriesBrowseGridHandle.activateFocusedIndex(browseGridFocusIndex)
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

    fun handleFilterPanelKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (navDrawerOpen) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (filterFocusIndex > 0) {
                    moveFilterFocus(-1)
                } else {
                    openNavDrawer()
                }
                true
            }
            Key.DirectionRight -> {
                if (filterFocusIndex < VodHubLanguageFilterFocusIndex) {
                    moveFilterFocus(1)
                } else {
                    when {
                        showInlineSearch -> focusInlineSearchField()
                        showGenrePanel -> focusGenrePanelFromFilter()
                        showBrowseGrid -> focusBrowseGridFromSidebar()
                        hasHero && searchQuery.isBlank() -> {
                            focusZone = VodFocusZone.HERO
                            scope.launch {
                                heroPlayFocusRequester.requestFocusSafelyAfterLayout()
                            }
                        }
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
                    showInlineSearch -> focusInlineSearchField()
                    showGenrePanel -> focusGenrePanelFromFilter()
                    showBrowseGrid -> focusBrowseGridFromSidebar()
                    hasHero && searchQuery.isBlank() -> {
                        focusZone = VodFocusZone.HERO
                        scope.launch {
                            heroPlayFocusRequester.requestFocusSafelyAfterLayout()
                        }
                    }
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
                openNavDrawer()
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

    fun handleHeroKey(event: KeyEvent): Boolean =
        handleVodHubHeroKeyEvent(
            event = event,
            carouselSize = featuredCarousel.size,
            onStepCarousel = hubViewModel::stepHeroCarousel,
            onNavigateDown = {
                focusZone = VodFocusZone.CONTENT
                contentRowIndex = 0
                contentColIndex = 0
                contentScrollDirection = TvLazyFocusScrollDirection.DOWN
            },
            onNavigateUp = {
                focusZone = VodFocusZone.FILTER_PANEL
                scope.launch {
                    filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                }
            }
        )

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
                        else -> {
                            openNavDrawer()
                            VodFocusZone.NAV_DRAWER
                        }
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
                        !showBrowseGrid && hasHero && searchQuery.isBlank() -> VodFocusZone.HERO
                        searchQuery.isBlank() -> VodFocusZone.FILTER_PANEL
                        else -> {
                            openNavDrawer()
                            VodFocusZone.NAV_DRAWER
                        }
                    }
                    true
                }
                else -> false
            }
        }
        if (wallRows.isEmpty()) {
            return when (event.key) {
                Key.DirectionLeft -> {
                    openNavDrawer()
                    true
                }
                Key.DirectionUp -> {
                    openNavDrawer()
                    true
                }
                else -> false
            }
        }
        val row = displayWallRows.getOrNull(contentRowIndex) ?: return false
        val handled = when (event.key) {
            Key.DirectionLeft -> {
                if (contentColIndex > 0) {
                    contentColIndex -= 1
                } else {
                    openNavDrawer()
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
                    val maxCol = displayWallRows[contentRowIndex].items.lastIndex
                    contentColIndex = contentColIndex.coerceAtMost(maxCol)
                } else if (hasHero && searchQuery.isBlank()) {
                    focusZone = VodFocusZone.HERO
                    scope.launch {
                        heroPlayFocusRequester.requestFocusSafelyAfterLayout()
                    }
                } else {
                    focusZone = VodFocusZone.FILTER_PANEL
                }
                true
            }
            Key.DirectionDown -> {
                if (contentRowIndex < displayWallRows.lastIndex) {
                    contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                    contentRowIndex += 1
                    val maxCol = displayWallRows[contentRowIndex].items.lastIndex
                    contentColIndex = contentColIndex.coerceAtMost(maxCol)
                } else if (
                    contentFilter == VodContentFilter.ALL &&
                    !showBrowseGrid &&
                    loadedDeferredWallCount < deferredWallRows.size
                ) {
                    loadedDeferredWallCount += 1
                    contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                    contentRowIndex += 1
                    contentColIndex = 0
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                row.items.getOrNull(contentColIndex)?.let(::activateWallItem)
                true
            }
            else -> false
        }
        if (handled) syncFocusedWallItemKey()
        return handled
    }

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
        profileMenuOpen -> {
            profileMenuOpen = false
            true
        }
        focusZone == VodFocusZone.CONTENT || focusZone == VodFocusZone.HERO ||
            focusZone == VodFocusZone.FILTER_PANEL || focusZone == VodFocusZone.GENRE_PANEL -> {
            openNavDrawer()
            true
        }
        focusZone == VodFocusZone.NAV_DRAWER -> {
            closeNavDrawer()
            true
        }
        else -> false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    val showHeroBackdrop = focusZone == VodFocusZone.HERO &&
        hasHero &&
        searchQuery.isBlank() &&
        !showBrowseGrid
    val contentAmbientPosterUrl = when {
        searchQuery.isNotBlank() || showBrowseGrid || showHeroBackdrop -> null
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
                    !(showInlineSearch && vodSearchFocused) &&
                    !showGlobalSearchOverlay &&
                    focusZone != VodFocusZone.HERO &&
                    focusZone != VodFocusZone.FILTER_PANEL
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
                if (showInlineSearch && vodSearchFocused && !imeTypingActive) {
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        return@onPreviewKeyEvent consumeVodLocalBack()
                    }
                    return@onPreviewKeyEvent false
                }
                if (profileMenuOpen && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            profileMenuOpen = false
                            return@onPreviewKeyEvent true
                        }
                        else -> Unit
                    }
                }
                if (navDrawerOpen && focusZone == VodFocusZone.NAV_DRAWER) {
                    if (handleNavDrawerKey(event)) return@onPreviewKeyEvent true
                }
                val handled = when (event.key) {
                    Key.Back, Key.Escape -> consumeVodLocalBack()
                    else -> when (focusZone) {
                        VodFocusZone.NAV_DRAWER -> handleNavDrawerKey(event)
                        VodFocusZone.FILTER_PANEL -> handleFilterPanelKey(event)
                        VodFocusZone.GENRE_PANEL -> handleGenrePanelKey(event)
                        VodFocusZone.HERO -> handleHeroKey(event)
                        VodFocusZone.CONTENT -> handleContentKey(event)
                    }
                }
                if (handled) return@onPreviewKeyEvent true
                if (imeTypingActive) return@onPreviewKeyEvent false
                if (focusZone in vodManualFocusZones && isVodDirectionalKey(event)) {
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        if (showHeroBackdrop) {
            VodHubHeroAmbientPoster(
                hubViewModel = hubViewModel,
                featuredCarousel = featuredCarousel,
                posterUrlFor = ::posterFor,
                enrichmentMap = enrichmentMap
            )
        } else {
            VodAmbientBackdrop(posterUrl = contentAmbientPosterUrl)
        }

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
                focusedIndex = navDrawerFocusIndex,
                drawerActive = focusZone == VodFocusZone.NAV_DRAWER,
                drawerFocusRequester = navDrawerFocusRequester,
                profileInitials = profileInitials,
                profileAvatarColor = profileAvatarColor,
                profileFocused = profileMenuOpen,
                onProfileClick = {
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                },
                onItemFocused = { index ->
                    navDrawerFocusIndex = index
                    navDrawerOpen = true
                    focusZone = VodFocusZone.NAV_DRAWER
                },
                onItemSelected = ::selectVodDrawerItem,
                onPreviewKey = ::handleNavDrawerKey,
                selectedItem = GuideNavDrawerItem.Vod
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
            EpgGuideHeader(
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                onRecordingIndicatorClick = onNavigateRecordings,
                modifier = Modifier.fillMaxWidth()
            )

            VodContentFilterTabBar(
                selectedFilter = contentFilter,
                focusedFilter = VodHubTabFilters.getOrNull(filterFocusIndex) ?: contentFilter,
                barFocused = focusZone == VodFocusZone.FILTER_PANEL,
                languageFilterActive = languageFilterActive,
                languageFilterFocused = focusZone == VodFocusZone.FILTER_PANEL &&
                    filterFocusIndex == VodHubLanguageFilterFocusIndex,
                onLanguageFilterClick = ::openLanguagePreferenceDialog,
                onFilterSelected = { filter ->
                    applyContentFilter(vodHubTabFilterIndex(filter))
                },
                tabBarFocusRequester = filterPanelFocusRequester,
                heroPlayFocusRequester = heroPlayFocusRequester,
                sidebarFocusRequester = navDrawerFocusRequester,
                onBarFocused = { focusZone = VodFocusZone.FILTER_PANEL },
                onPreviewKey = ::handleFilterPanelKey
            )

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
                if (showGenrePanel && !showInlineSearch && !showVodOnboardingFull) {
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    when {
                        showVodOnboardingFull -> {
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
                                onFocusSearchField = { vodSearchFocused = true },
                                onFocusResults = ::focusSearchResults,
                                onNavigateUpFromResults = ::focusInlineSearchField,
                                onHasResultsChange = { searchHasResults = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        contentFilter == VodContentFilter.MOVIES -> {
                            VodHubMoviesBrowseSection(
                                moviesViewModel = moviesViewModel,
                                progressByKey = vodProgress,
                                onItemClick = ::openMovieDetail,
                                gridState = browseGridState,
                                gridFocused = focusZone == VodFocusZone.CONTENT && !showInlineSearch,
                                focusedItemIndex = browseGridFocusIndex,
                                browseGridHandle = moviesBrowseGridHandle,
                                contentGridFocusRequester = browseGridFocusRequester,
                                onColumnCountChanged = { browseGridColumnCount = it },
                                onNavigateUpFromFirstRow = ::focusNavDrawerFromBrowseGrid,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        contentFilter == VodContentFilter.SERIES -> {
                            VodHubSeriesBrowseSection(
                                seriesViewModel = seriesViewModel,
                                progressByKey = vodProgress,
                                onSeriesCardClick = { card ->
                                    seriesViewModel.selectShow(card.showId, card.playlistId)
                                },
                                gridState = browseGridState,
                                gridFocused = focusZone == VodFocusZone.CONTENT && !showInlineSearch,
                                focusedItemIndex = browseGridFocusIndex,
                                browseGridHandle = seriesBrowseGridHandle,
                                contentGridFocusRequester = browseGridFocusRequester,
                                onColumnCountChanged = { browseGridColumnCount = it },
                                onNavigateUpFromFirstRow = ::focusNavDrawerFromBrowseGrid,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
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
                                    showLanguageFilteredEmpty -> {
                                        VodEmptyState(
                                            title = "No titles match your language preferences.",
                                            message = "Try selecting additional languages, enable untagged content, or clear the language filter.",
                                            onRetry = ::openLanguagePreferenceDialog
                                        )
                                    }
                                    showCatalogEmptyState -> {
                                        VodEmptyState(
                                            title = "Nothing to watch yet",
                                            message = "Connect a playlist to add movies and series to your library.",
                                            onRetry = { moviesViewModel.refreshCatalog() }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val showHero = showHeroIsland
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (showVodOnboardingStrip) {
                                    VodCatalogOnboardingPanel(
                                        progress = catalogProgress,
                                        onboardingInputs = onboardingInputs,
                                        compact = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                LazyColumn(
                                state = columnListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 64.dp, top = 4.dp)
                            ) {
                                if (showHero) {
                                    item(key = "vod_hero") {
                                        VodHubHeroIsland(
                                            hubViewModel = hubViewModel,
                                            featuredCarousel = featuredCarousel,
                                            enrichmentMap = enrichmentMap,
                                            inputActive = focusZone == VodFocusZone.HERO,
                                            requestPlayFocus = requestHeroPlayFocus,
                                            onPlay = { hero ->
                                                hubViewModel.onHeroInteraction(hero)
                                                playMovie(hero)
                                            },
                                            onMoreInfo = { hero ->
                                                hubViewModel.onHeroInteraction(hero)
                                                openMovieDetail(hero)
                                            },
                                            onNavigateDown = {
                                                vodFoldScroller.snapDownFromHero()
                                                focusZone = VodFocusZone.CONTENT
                                                contentRowIndex = 0
                                                contentColIndex = 0
                                                contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                                            },
                                            onNavigateUp = {
                                                focusZone = VodFocusZone.FILTER_PANEL
                                                scope.launch {
                                                    filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                                                }
                                            },
                                            playFocusRequester = heroPlayFocusRequester,
                                            moreInfoFocusRequester = heroMoreInfoFocusRequester,
                                            topTabsFocusRequester = filterPanelFocusRequester,
                                            firstRowCardFocusRequester = contentFocusRequester,
                                            sidebarFocusRequester = navDrawerFocusRequester,
                                            onPlayFocused = { focusZone = VodFocusZone.HERO },
                                            onMoreInfoFocused = { focusZone = VodFocusZone.HERO }
                                        )
                                    }
                                }
                                itemsIndexed(displayWallRows, key = { _, row -> row.id }) { index, row ->
                                    val rowListState = remember(row.id) { LazyListState() }
                                    val rowFocused = focusZone == VodFocusZone.CONTENT && contentRowIndex == index
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
                                            heroPlayFocusRequester = heroPlayFocusRequester,
                                            sidebarFocusRequester = navDrawerFocusRequester
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
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
            expanded = profileMenuOpen,
            focusedIndex = profileMenuFocusIndex,
            profileDisplayName = profileDisplayName,
            onDismiss = { profileMenuOpen = false },
            onSwitchAccounts = {
                profileMenuOpen = false
                onNavigateProfile()
            },
            onOpenSettings = {
                profileMenuOpen = false
                onNavigateSettings()
            },
            onQuitApp = { context.quitAppToHome() },
            showSwitchAccounts = false,
            anchorFromSidebar = true
        )
    }
}
