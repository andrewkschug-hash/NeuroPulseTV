package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.buildVodWallRows
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.NetflixContentWallRow
import com.grid.tv.ui.component.NetflixMovieRow
import com.grid.tv.ui.component.NetflixCategoryRow
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.VodContentFilterPanel
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.ui.component.VodHeroSection
import com.grid.tv.ui.component.VodSearchOverlay
import com.grid.tv.ui.component.movieMetaSubtitle
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.quitAppToHome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VodFocusZone {
    TOP_BAR,
    FILTER_PANEL,
    HERO,
    CONTENT,
    SEARCH_OVERLAY
}

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
    var vodSearchFocused by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val homeViewModel: HomeEpgViewModel = hiltViewModel()
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val continueWatchingItems by hubViewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val heroMovie by hubViewModel.heroMovie.collectAsStateWithLifecycle()
    val heroEnrichment by hubViewModel.heroEnrichment.collectAsStateWithLifecycle()
    val featuredCarousel by hubViewModel.featuredCarousel.collectAsStateWithLifecycle()
    val heroIndex by hubViewModel.heroIndex.collectAsStateWithLifecycle()
    val enrichmentMap by hubViewModel.enrichmentMap.collectAsStateWithLifecycle()
    val vodProgress by hubViewModel.vodProgress.collectAsStateWithLifecycle()
    val searchQuery by hubViewModel.searchQuery.collectAsStateWithLifecycle()
    val contentFilter by hubViewModel.contentFilter.collectAsStateWithLifecycle()
    val movieBrowseRows by moviesViewModel.browseRows.collectAsStateWithLifecycle()
    val seriesBrowseRows by seriesViewModel.browseRows.collectAsStateWithLifecycle()
    val catalogProgress by moviesViewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogLoading by moviesViewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogTotalCount by moviesViewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val recommendedForYou by hubViewModel.recommendedForYou.collectAsStateWithLifecycle()
    val trendingNow by hubViewModel.trendingNow.collectAsStateWithLifecycle()
    val moviePagingItems = moviesViewModel.pagedMovies.collectAsLazyPagingItems()
    val seriesPagingItems = seriesViewModel.pagedSeries.collectAsLazyPagingItems()
    val selectedShowId by seriesViewModel.selectedShowId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val columnListState = rememberLazyListState()

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
            VodContentFilter.SERIES -> seriesViewModel.setSearchQuery(searchQuery)
            else -> {
                moviesViewModel.setSearchQuery(searchQuery)
                if (contentFilter == VodContentFilter.MOVIES) {
                    seriesViewModel.setSearchQuery("")
                }
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
        homeViewModel.livePlayerManager.setMode(com.grid.tv.player.LivePlayerManager.Mode.IDLE)
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
        if (searchQuery.isBlank() && wallRows.isEmpty() && heroMovie == null &&
            focusZone == VodFocusZone.CONTENT
        ) {
            focusZone = VodFocusZone.FILTER_PANEL
        }
    }

    LaunchedEffect(focusZone, contentRowIndex) {
        if (focusZone == VodFocusZone.CONTENT && searchQuery.isBlank()) {
            val lazyIndex = if (heroMovie != null && searchQuery.isBlank()) contentRowIndex + 1 else contentRowIndex
            columnListState.animateScrollToItem(lazyIndex.coerceAtLeast(0))
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val filterPanelFocusRequester = remember { FocusRequester() }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val heroMoreInfoFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val searchOverlayFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusZone, showSearchOverlay, heroMovie) {
        when {
            showSearchOverlay -> {
                focusZone = VodFocusZone.SEARCH_OVERLAY
                searchOverlayFocusRequester.requestFocusSafelyAfterLayout()
            }
            focusZone == VodFocusZone.HERO && heroMovie != null ->
                heroPlayFocusRequester.requestFocusSafelyAfterLayout()
            else -> rootFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun resumeItem(item: ContinueWatchingItem) {
        VodPlaybackHelper.stageContinueWatching(item)
        onPlayUrl(item.title, item.streamUrl, true)
    }

    fun activateNavTab(tabItem: EpgNavTab) {
        when (tabItem) {
            EpgNavTab.Guide, EpgNavTab.Home, EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Vod, EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(2)
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Settings -> onNavigateSettings()
        }
    }

    fun applyContentFilter(index: Int) {
        val filter = VodContentFilter.entries.getOrNull(index) ?: VodContentFilter.ALL
        filterFocusIndex = index
        hubViewModel.setContentFilter(filter)
        contentRowIndex = 0
        contentColIndex = 0
        focusZone = VodFocusZone.CONTENT
        onNavigateVod(
            when (filter) {
                VodContentFilter.SERIES -> 1
                else -> 0
            }
        )
    }

    fun openSearchOverlay() {
        showSearchOverlay = true
        vodSearchFocused = false
    }

    fun closeSearchOverlay() {
        showSearchOverlay = false
        focusZone = VodFocusZone.TOP_BAR
    }

    fun ratingFor(movie: VodItem): String? =
        hubViewModel.displayRating(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun posterFor(movie: VodItem): String? {
        val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
        return enrichment?.posterUrl?.takeIf { it.isNotBlank() } ?: movie.posterUrl?.takeIf { it.isNotBlank() }
    }

    fun metaFor(movie: VodItem): String? = movieMetaSubtitle(movie, ratingFor(movie))

    fun overviewForMovie(movie: VodItem): String? {
        val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
        return enrichment?.overview?.takeIf { it.isNotBlank() } ?: movie.plot?.takeIf { it.isNotBlank() }
    }

    fun overviewForSeries(show: SeriesShow): String? = show.genre?.takeIf { it.isNotBlank() }

    fun playMovie(movie: VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        scope.launch {
            val resume = moviesViewModel.shouldResume(movie.toGridCardModel(), vodProgress)
            VodPlaybackHelper.stageMovie(movie)
            onPlayMovie(movie.title, movie.streamUrl, resume)
        }
    }

    fun activateWallItem(item: VodWallItem) {
        when (item) {
            is VodWallItem.ContinueItem -> resumeItem(item.item)
            is VodWallItem.MovieItem -> playMovie(item.movie)
            is VodWallItem.SeriesItem -> seriesViewModel.selectShow(item.show.id, null)
        }
    }

    fun handleFilterPanelKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionUp -> {
                if (filterFocusIndex == 0) {
                    focusZone = VodFocusZone.TOP_BAR
                } else {
                    filterFocusIndex -= 1
                }
                true
            }
            Key.DirectionDown -> {
                filterFocusIndex = (filterFocusIndex + 1).coerceAtMost(VodContentFilter.entries.lastIndex)
                true
            }
            Key.DirectionRight -> {
                focusZone = VodFocusZone.CONTENT
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                applyContentFilter(filterFocusIndex)
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
                true
            }
            Key.DirectionUp -> {
                focusZone = VodFocusZone.TOP_BAR
                true
            }
            else -> false
        }
    }

    fun handleContentKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
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
                    contentRowIndex -= 1
                    val maxCol = wallRows[contentRowIndex].items.lastIndex
                    contentColIndex = contentColIndex.coerceAtMost(maxCol)
                } else if (heroMovie != null && searchQuery.isBlank()) {
                    focusZone = VodFocusZone.HERO
                } else {
                    focusZone = VodFocusZone.TOP_BAR
                }
                true
            }
            Key.DirectionDown -> {
                if (contentRowIndex < wallRows.lastIndex) {
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
                if (vodSearchFocused) {
                    topBarFocusIndex = TopBarProfileIndex
                    vodSearchFocused = false
                } else {
                    topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                }
                true
            }
            Key.DirectionRight -> {
                if (topBarFocusIndex == TopBarProfileIndex) {
                    vodSearchFocused = true
                    topBarFocusIndex = -1
                } else if (vodSearchFocused) {
                    topBarFocusIndex = TopBarProfileIndex
                    vodSearchFocused = false
                } else {
                    topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                }
                true
            }
            Key.DirectionDown -> {
                when {
                    heroMovie != null && searchQuery.isBlank() -> focusZone = VodFocusZone.HERO
                    wallRows.isNotEmpty() -> {
                        focusZone = VodFocusZone.CONTENT
                        contentRowIndex = 0
                        contentColIndex = 0
                    }
                    else -> focusZone = VodFocusZone.FILTER_PANEL
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when {
                    vodSearchFocused -> {
                        openSearchOverlay()
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
        showSearchOverlay -> {
            closeSearchOverlay()
            true
        }
        profileMenuOpen -> {
            profileMenuOpen = false
            true
        }
        focusZone == VodFocusZone.CONTENT || focusZone == VodFocusZone.HERO || focusZone == VodFocusZone.FILTER_PANEL -> {
            focusZone = VodFocusZone.TOP_BAR
            vodSearchFocused = true
            topBarFocusIndex = -1
            true
        }
        else -> false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VodNetflixColors.Background)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> consumeVodLocalBack()
                    else -> when (focusZone) {
                        VodFocusZone.TOP_BAR -> handleTopBarKey(event)
                        VodFocusZone.FILTER_PANEL -> handleFilterPanelKey(event)
                        VodFocusZone.HERO -> handleHeroKey(event)
                        VodFocusZone.CONTENT -> handleContentKey(event)
                        VodFocusZone.SEARCH_OVERLAY -> false
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
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
                    focusZone = VodFocusZone.TOP_BAR
                    topBarFocusIndex = GridNavTabs.indexOf(tabItem).coerceAtLeast(0)
                    activateNavTab(tabItem)
                },
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                miniPlayer = {},
                vodSearchFocused = vodSearchFocused,
                onVodSearchClick = { openSearchOverlay() },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.weight(1f)) {
                if (searchQuery.isBlank()) {
                    VodContentFilterPanel(
                        selectedFilter = contentFilter,
                        focusedFilter = VodContentFilter.entries[filterFocusIndex.coerceIn(
                            VodContentFilter.entries.indices
                        )],
                        panelFocused = focusZone == VodFocusZone.FILTER_PANEL,
                        onFilterSelected = { filter ->
                            applyContentFilter(VodContentFilter.entries.indexOf(filter).coerceAtLeast(0))
                        },
                        focusRequester = filterPanelFocusRequester,
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                val vodLoading = catalogLoading || (catalogProgress.isLoading && catalogTotalCount == 0)
                LazyColumn(
                    state = columnListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (searchQuery.isBlank() && heroMovie != null) {
                        item(key = "hero") {
                            heroMovie?.let { hero ->
                                Box(
                                    modifier = Modifier.onPreviewKeyEvent { false }
                                ) {
                                    VodHeroSection(
                                        movie = hero,
                                        enrichment = heroEnrichment,
                                        carouselSize = featuredCarousel.size,
                                        carouselIndex = heroIndex,
                                        onPlay = { playMovie(hero) },
                                        onMoreInfo = { playMovie(hero) },
                                        playFocusRequester = heroPlayFocusRequester,
                                        moreInfoFocusRequester = heroMoreInfoFocusRequester
                                    )
                                }
                            }
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        item(key = "search_results") {
                            val movies = moviePagingItems.itemSnapshotList.items
                            val shows = seriesPagingItems.itemSnapshotList.items
                            if (movies.isNotEmpty()) {
                                NetflixCategoryRow(title = "Movies") {
                                    NetflixMovieRow(
                                        movies = movies,
                                        progressByStreamId = vodProgress,
                                        posterUrlFor = ::posterFor,
                                        metaSubtitleFor = ::metaFor,
                                        onPlayMovie = ::playMovie,
                                        firstItemFocusRequester = contentFocusRequester
                                    )
                                }
                            }
                            if (shows.isNotEmpty()) {
                                NetflixCategoryRow(title = "Series") {
                                    com.grid.tv.ui.component.NetflixSeriesRow(
                                        shows = shows,
                                        onOpenSeries = { show ->
                                            seriesViewModel.selectShow(show.id, null)
                                        }
                                    )
                                }
                            }
                        }
                    } else if (wallRows.isEmpty()) {
                        item(key = "vod_status") {
                            if (vodLoading) {
                                VodCatalogLoadingBanner(
                                    baseMessage = "Loading your movie and series catalog…",
                                    progress = catalogProgress,
                                    isMovies = true
                                )
                            } else {
                                VodEmptyState(
                                    title = "Nothing to watch yet",
                                    message = "Connect a playlist or wait for your catalog to finish syncing.",
                                    onRetry = { moviesViewModel.refreshCatalog() }
                                )
                            }
                        }
                    } else {
                        itemsIndexed(wallRows, key = { _, row -> row.id }) { index, row ->
                            NetflixContentWallRow(
                                row = row,
                                rowIndex = index,
                                focusedColumn = if (focusZone == VodFocusZone.CONTENT && contentRowIndex == index) {
                                    contentColIndex
                                } else {
                                    -1
                                },
                                rowFocused = focusZone == VodFocusZone.CONTENT && contentRowIndex == index,
                                listState = rememberLazyListState(),
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

        if (showSearchOverlay) {
            VodSearchOverlay(
                query = searchQuery,
                placeholder = "Search movies and series…",
                onQueryChange = hubViewModel::setSearchQuery,
                onDismiss = ::closeSearchOverlay,
                focusRequester = searchOverlayFocusRequester,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        closeSearchOverlay()
                        true
                    } else {
                        false
                    }
                }
            )
        }

        if (contentFilter == VodContentFilter.SERIES || selectedShowId != null) {
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
    }
}
