package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.domain.epg.ProgrammeIndex
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.feature.epg.EpgPlaceholderData
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.buildGuideGroupCategories
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.expandedCategoriesForSelection
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.EpgGuidePosition
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.util.PerformanceAudit
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

internal enum class EpgFocusZone { TOP_BAR, CONTINUE_WATCHING, PREVIEW, GRID_FILTER, GRID }

internal fun epgZoneAbove(
    from: EpgFocusZone,
    showPreview: Boolean,
    hasContinueWatching: Boolean,
    showGroupFilter: Boolean = true
): EpgFocusZone? = when (from) {
    EpgFocusZone.GRID -> if (showGroupFilter) {
        EpgFocusZone.GRID_FILTER
    } else {
        when {
            showPreview -> EpgFocusZone.PREVIEW
            hasContinueWatching -> EpgFocusZone.CONTINUE_WATCHING
            else -> EpgFocusZone.TOP_BAR
        }
    }
    EpgFocusZone.GRID_FILTER -> when {
        showPreview -> EpgFocusZone.PREVIEW
        hasContinueWatching -> EpgFocusZone.CONTINUE_WATCHING
        else -> EpgFocusZone.TOP_BAR
    }
    EpgFocusZone.PREVIEW -> when {
        hasContinueWatching -> EpgFocusZone.CONTINUE_WATCHING
        else -> EpgFocusZone.TOP_BAR
    }
    EpgFocusZone.CONTINUE_WATCHING -> EpgFocusZone.TOP_BAR
    EpgFocusZone.TOP_BAR -> null
}

internal fun epgZoneBelow(
    from: EpgFocusZone,
    showPreview: Boolean,
    hasContinueWatching: Boolean,
    showGroupFilter: Boolean = true
): EpgFocusZone? = when (from) {
    EpgFocusZone.TOP_BAR -> when {
        hasContinueWatching -> EpgFocusZone.CONTINUE_WATCHING
        showPreview -> EpgFocusZone.PREVIEW
        showGroupFilter -> EpgFocusZone.GRID_FILTER
        else -> EpgFocusZone.GRID
    }
    EpgFocusZone.CONTINUE_WATCHING -> when {
        showPreview -> EpgFocusZone.PREVIEW
        showGroupFilter -> EpgFocusZone.GRID_FILTER
        else -> EpgFocusZone.GRID
    }
    EpgFocusZone.PREVIEW -> if (showGroupFilter) EpgFocusZone.GRID_FILTER else EpgFocusZone.GRID
    EpgFocusZone.GRID_FILTER -> EpgFocusZone.GRID
    EpgFocusZone.GRID -> null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeEpgScreen(
    onWatchChannel: (Long) -> Unit,
    onPlayCatchup: (String, String) -> Unit = { _, _ -> },
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onNavigateSeries: (playlistId: Long, seriesId: Long) -> Unit = { _, _ -> },
    onPlayVod: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onResumeContinueWatching: (ContinueWatchingItem) -> Unit = {},
    profileInitials: String = "?",
    profileAvatarColor: String = com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR,
    openFavoritesInitially: Boolean = false,
    viewModel: HomeEpgViewModel,
    recordingViewModel: RecordingViewModel,
    searchViewModel: SearchViewModel
) {
    val ui = remember { HomeEpgUiState() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val screen by viewModel.homeEpgScreenState.collectAsStateWithLifecycle()
    val epg = screen.epg
    val chrome = screen.chrome
    val channels = epg.channels
    val programmeIndex = epg.programmeIndex
    val windowStart = epg.windowStart
    val windowDurationMs = epg.windowDurationMs
    val isInitializing = chrome.isInitializing
    val hasConnection = chrome.hasConnection
    val guideFilter = chrome.guideFilter
    val favoriteGroupFilter = chrome.favoriteGroupFilter
    val guideFiltersConfigured = chrome.guideFiltersConfigured
    val guideSettingsLoaded = chrome.guideSettingsLoaded
    val isReloadingChannels = chrome.isReloadingChannels
    val channelGroups = chrome.channelGroups
    val groupChannelCounts = chrome.groupChannelCounts
    val hasCatalogChannels = chrome.hasCatalogChannels
    val demoFavoriteIds = chrome.demoFavoriteIds
    val favoriteGroups = chrome.favoriteGroups
    val favoriteSavedMessage = chrome.favoriteSavedMessage
    val guidePreviewEnabled = chrome.guidePreviewEnabled
    val previewChannelId = chrome.guidePreviewChannelId
    val guidePosition = chrome.guidePosition
    val vodProgress = chrome.vodProgress
    val profileAccessMessage = viewModel.profileAccessMessage()
    val livePlayerManager = viewModel.livePlayerManager
    val fullscreenActive by livePlayerManager.fullscreenActive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.reloadPlaybackSettings(context)
    }

    val guidePreviewEnabledState = rememberUpdatedState(guidePreviewEnabled)

    val lifecycleOwner = LocalLifecycleOwner.current
    var previewSurfaceAttached by remember { androidx.compose.runtime.mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        previewSurfaceAttached = lifecycleOwner.lifecycle.currentState
            .isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    previewSurfaceAttached = true
                    viewModel.reloadPlaybackSettings(context)
                    viewModel.reloadGuideSettings()
                    viewModel.setScannerForeground(true)
                    if (guidePreviewEnabledState.value && !livePlayerManager.isFullscreenActive()) {
                        viewModel.resumeGuidePreviewIfEnabled(context)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    previewSurfaceAttached = false
                    viewModel.setScannerForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelPreviewTune()
        }
    }

    val showEmptyState = !isInitializing && !hasConnection
    val usePlaceholder = !isInitializing && hasConnection && !hasCatalogChannels
    val displayChannels = remember(
        channels,
        usePlaceholder,
        showEmptyState,
        favoriteGroupFilter,
        demoFavoriteIds,
        guideFilter
    ) {
        when {
            showEmptyState -> emptyList()
            usePlaceholder -> {
                val all = EpgPlaceholderData.channels().filter { guideFilter.appliesTo(it) }
                when (favoriteGroupFilter) {
                    null -> all
                    HomeEpgViewModel.FAVORITES_FILTER -> all.filter { it.id in demoFavoriteIds }
                    else -> emptyList()
                }
            }
            else -> channels.filter { guideFilter.appliesTo(it) }
        }
    }
    val showFilteredEmptyState = !usePlaceholder && !showEmptyState &&
        displayChannels.isEmpty() && (guideFilter.isActive || favoriteGroupFilter != null)

    if (PerformanceAudit.ENABLED) {
        SideEffect {
            PerformanceAudit.logEpgRecomposition("HomeEpgScreen")
        }
    }

    LaunchedEffect(favoriteSavedMessage) {
        if (favoriteSavedMessage != null) {
            delay(2500)
            viewModel.clearFavoriteSavedMessage()
        }
    }

    HomeEpgLivePlaybackSideEffects(
        livePlayerManager = livePlayerManager,
        viewModel = viewModel,
        context = context,
        channels = channels,
        guidePreviewEnabled = guidePreviewEnabled,
        previewChannelId = previewChannelId
    )

    LaunchedEffect(guideFilter, displayChannels, previewChannelId, guidePreviewEnabled) {
        if (!guidePreviewEnabled) return@LaunchedEffect
        if (displayChannels.isEmpty()) {
            viewModel.clearGuidePreviewUi()
            return@LaunchedEffect
        }
        val previewStillVisible = previewChannelId?.let { id ->
            displayChannels.any { it.id == id }
        } == true
        if (!previewStillVisible) {
            viewModel.clearGuidePreviewUi()
        }
    }

    val guideGroupCategories = remember(channelGroups, groupChannelCounts) {
        buildGuideGroupCategories(channelGroups, groupChannelCounts)
    }

    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gridFocusRequester = remember { FocusRequester() }
    val gridFilterFocusRequester = remember { FocusRequester() }
    val topNavFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val previewFocusRequester = remember { FocusRequester() }
    val timelineWidth = EpgLayout.timelineWidthMs(windowDurationMs)

    val controller = remember(ui) { HomeEpgGuideController(ui) }

    LaunchedEffect(listState, displayChannels) {
        if (displayChannels.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                displayChannels.getOrNull(info.index)?.id
            }
        }
            .distinctUntilChanged()
            .collect { visibleIds ->
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                if (lastVisible >= displayChannels.size - 20) {
                    viewModel.loadMoreChannels()
                }
                viewModel.onGuideScrollViewportChanged(visibleIds)
            }
    }

    LaunchedEffect(ui.focusChannelIndex, displayChannels) {
        viewModel.onGuideFocusChannelChanged(
            channelId = displayChannels.getOrNull(ui.focusChannelIndex)?.id,
            channelIndex = ui.focusChannelIndex
        )
    }

    LaunchedEffect(guidePosition, displayChannels.size, livePlayerManager) {
        if (ui.didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
        val liveChannelId = livePlayerManager.playbackUiState.value.activeChannelId
        if (guidePosition.hasSavedPosition) {
            ui.focusChannelIndex = guidePosition.focusChannelIndex.coerceIn(0, displayChannels.lastIndex)
            ui.focusProgramIndex = guidePosition.focusProgramIndex
            ui.focusOnChannelColumn = guidePosition.focusOnChannelColumn
            listState.scrollToItem(ui.focusChannelIndex)
            hScroll.scrollTo(guidePosition.timelineScrollPx.coerceIn(0, hScroll.maxValue))
            ui.didInitialScroll = true
        } else {
            val idx = displayChannels.indexOfFirst { it.id == liveChannelId }
            if (idx >= 0) {
                ui.focusChannelIndex = idx
                listState.scrollToItem(idx)
            }
        }
        ui.didRestoreGuide = true
    }

    LaunchedEffect(displayChannels.size, windowStart, guidePosition.hasSavedPosition) {
        if (!ui.didInitialScroll && displayChannels.isNotEmpty()) {
            if (guidePosition.hasSavedPosition) {
                ui.didInitialScroll = true
            } else {
                val offsetDp = EpgLayout.offsetForLocalNow(
                    windowStart,
                    windowDurationMs,
                    System.currentTimeMillis()
                )
                val offsetPx = density.run { offsetDp.toPx() }
                val target = (offsetPx - 400f).coerceAtLeast(0f).toInt()
                hScroll.scrollTo(target)
                ui.didInitialScroll = true
            }
        }
    }

    LaunchedEffect(
        ui.focusChannelIndex,
        ui.focusProgramIndex,
        ui.focusOnChannelColumn,
        hScroll.value,
        displayChannels.size,
        ui.didRestoreGuide
    ) {
        if (!ui.didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
        viewModel.saveGuidePositionDebounced(
            EpgGuidePosition(
                focusChannelIndex = ui.focusChannelIndex.coerceIn(0, displayChannels.lastIndex),
                focusProgramIndex = ui.focusProgramIndex,
                focusOnChannelColumn = ui.focusOnChannelColumn,
                timelineScrollPx = hScroll.value
            )
        )
    }

    LaunchedEffect(favoriteGroupFilter) {
        ui.selectedTab = when (favoriteGroupFilter) {
            HomeEpgViewModel.FAVORITES_FILTER -> EpgNavTab.Favorites
            else -> if (ui.selectedTab == EpgNavTab.Favorites) EpgNavTab.Guide else ui.selectedTab
        }
    }

    LaunchedEffect(openFavoritesInitially) {
        if (openFavoritesInitially) {
            viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            ui.selectedTab = EpgNavTab.Favorites
        }
    }

    val focusedChannel = displayChannels.getOrNull(ui.focusChannelIndex)
    val channelPrograms = remember(focusedChannel, programmeIndex) {
        focusedChannel?.let { programmeIndex.programsFor(it.id) } ?: emptyList()
    }
    val focusedProgram = if (ui.focusOnChannelColumn) {
        val nowMs = System.currentTimeMillis()
        channelPrograms.firstOrNull { nowMs in it.startTime..it.endTime }
            ?: channelPrograms.firstOrNull()
    } else {
        channelPrograms.getOrNull(ui.focusProgramIndex)
    }

    val previewChannel = previewChannelId?.let { channelId ->
        channels.find { it.id == channelId } ?: displayChannels.find { it.id == channelId }
    }
    val previewChannelPrograms = remember(previewChannel, programmeIndex) {
        previewChannel?.let { programmeIndex.programsFor(it.id) } ?: emptyList()
    }
    val previewProgram = remember(
        previewChannel,
        focusedChannel,
        focusedProgram,
        ui.focusOnChannelColumn,
        previewChannelPrograms
    ) {
        val nowMs = System.currentTimeMillis()
        if (previewChannel?.id == focusedChannel?.id && !ui.focusOnChannelColumn) {
            focusedProgram
        } else {
            previewChannelPrograms.firstOrNull { nowMs in it.startTime..it.endTime }
                ?: previewChannelPrograms.firstOrNull()
        }
    }
    val previewNextProgram = remember(previewProgram, previewChannelPrograms) {
        previewProgram?.let { current ->
            com.grid.tv.ui.component.nextProgramAfter(current, previewChannelPrograms)
        }
    }

    val showPreviewSection = guidePreviewEnabled && previewChannel != null
    val hasContinueWatching = false

    LaunchedEffect(hasCatalogChannels, guideFiltersConfigured, channelGroups, guideSettingsLoaded) {
        if (!guideSettingsLoaded) return@LaunchedEffect
        if (guideFiltersConfigured) {
            ui.showGuideGroupPicker = false
            return@LaunchedEffect
        }
        ui.showGuideGroupPicker = hasCatalogChannels && channelGroups.isNotEmpty()
    }

    LaunchedEffect(displayChannels.size, isReloadingChannels, programmeIndex) {
        if (isReloadingChannels) return@LaunchedEffect
        if (displayChannels.isEmpty()) {
            ui.focusChannelIndex = 0
            ui.focusProgramIndex = 0
            ui.focusOnChannelColumn = false
        } else if (ui.focusChannelIndex > displayChannels.lastIndex) {
            ui.focusChannelIndex = displayChannels.lastIndex
            val progs = displayChannels.getOrNull(ui.focusChannelIndex)
                ?.let { programmeIndex.programsFor(it.id) }
                ?: emptyList()
            ui.focusProgramIndex = ui.focusProgramIndex.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(ui.categoryMenuExpandedCategories, ui.showCategoryFilterMenu, guideFilter.selectedGroups) {
        if (!ui.showCategoryFilterMenu) return@LaunchedEffect
        val expanded = ui.categoryMenuExpandedCategories.ifEmpty {
            expandedCategoriesForSelection(guideGroupCategories, guideFilter.selectedGroups)
        }
        val count = buildVisibleGuideGroupRows(guideGroupCategories, expanded).size
        ui.categoryMenuFocusIndex = ui.categoryMenuFocusIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    if (HomeEpgScreenLoadingGate(
            isInitializing = isInitializing,
            guideSettingsLoaded = guideSettingsLoaded,
            showEmptyState = showEmptyState,
            onNavigateSettings = onNavigateSettings
        )
    ) {
        return
    }

    val deps = HomeEpgGuideDeps(
        context = context,
        scope = scope,
        listState = listState,
        hScroll = hScroll,
        channels = channels,
        displayChannels = displayChannels,
        programmeIndex = programmeIndex,
        viewModel = viewModel,
        recordingViewModel = recordingViewModel,
        searchViewModel = searchViewModel,
        windowStart = windowStart,
        windowDurationMs = windowDurationMs,
        density = density,
        gridFocusRequester = gridFocusRequester,
        gridFilterFocusRequester = gridFilterFocusRequester,
        topNavFocusRequester = topNavFocusRequester,
        continueWatchingFocusRequester = continueWatchingFocusRequester,
        previewFocusRequester = previewFocusRequester,
        previewChannel = previewChannel,
        focusedChannel = focusedChannel,
        focusedProgram = focusedProgram,
        previewProgram = previewProgram,
        previewNextProgram = previewNextProgram,
        guideGroupCategories = guideGroupCategories,
        guideFilter = guideFilter,
        demoFavoriteIds = demoFavoriteIds,
        vodProgress = vodProgress,
        continueWatchingItems = emptyList(),
        showPreviewSection = showPreviewSection,
        hasContinueWatching = hasContinueWatching,
        usePlaceholder = usePlaceholder,
        onWatchChannel = onWatchChannel,
        onPlayCatchup = onPlayCatchup,
        onNavigateRecordings = onNavigateRecordings,
        onNavigateSettings = onNavigateSettings,
        onNavigateVod = onNavigateVod,
        onNavigateSeries = onNavigateSeries,
        onPlayVod = onPlayVod,
        onResumeContinueWatching = onResumeContinueWatching,
    )
    controller.bind(deps)

    LaunchedEffect(isInitializing, guideSettingsLoaded, displayChannels.isNotEmpty(), ui.showGuideGroupPicker) {
        if (ui.showGuideGroupPicker) return@LaunchedEffect
        if (isInitializing || !guideSettingsLoaded) return@LaunchedEffect
        if (
            displayChannels.isNotEmpty() &&
            !ui.hasRequestedInitialGridFocus &&
            ui.focusZone == EpgFocusZone.GRID
        ) {
            ui.hasRequestedInitialGridFocus = true
            controller.requestEpgZoneFocus(EpgFocusZone.GRID)
        }
    }

    val liveScrollTargetPx = controller.liveScrollTarget()
    val scrolledAwayFromLive = kotlin.math.abs(hScroll.value - liveScrollTargetPx) > 80

    LaunchedEffect(displayChannels.isEmpty(), ui.selectedTab, ui.showGuideGroupPicker) {
        if (ui.showGuideGroupPicker) return@LaunchedEffect
        if (!displayChannels.isEmpty() || ui.focusZone != EpgFocusZone.GRID) return@LaunchedEffect
        if (ui.selectedTab == EpgNavTab.Favorites) {
            controller.focusEpgZone(EpgFocusZone.TOP_BAR)
        } else {
            controller.focusEpgZone(EpgFocusZone.GRID_FILTER)
        }
    }

    LaunchedEffect(ui.selectedTab) {
        if (ui.selectedTab == EpgNavTab.Favorites) {
            ui.showCategoryFilterMenu = false
            if (ui.focusZone == EpgFocusZone.GRID_FILTER) {
                controller.focusEpgZone(EpgFocusZone.GRID)
            }
        }
    }

    LaunchedEffect(showPreviewSection, previewChannelId, ui.pendingPreviewFocus) {
        if (!showPreviewSection || !ui.pendingPreviewFocus) return@LaunchedEffect
        controller.focusEpgZone(EpgFocusZone.PREVIEW)
        ui.pendingPreviewFocus = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .onPreviewKeyEvent { false }
    ) {
        HomeEpgScreenMainColumn(
            ui = ui,
            controller = controller,
            deps = deps,
            context = context,
            profileInitials = profileInitials,
            profileAvatarColor = profileAvatarColor,
            profileAccessMessage = profileAccessMessage,
            recordingViewModel = recordingViewModel,
            onNavigateRecordings = onNavigateRecordings,
            onNavigateProfile = onNavigateProfile,
            onNavigateSettings = onNavigateSettings,
            livePlayerManager = livePlayerManager,
            previewSurfaceAttached = previewSurfaceAttached && !fullscreenActive,
            channelGroups = channelGroups,
            viewModel = viewModel,
            timelineWidth = timelineWidth,
            scrolledAwayFromLive = scrolledAwayFromLive,
            showFilteredEmptyState = showFilteredEmptyState,
            hScroll = hScroll,
            listState = listState,
        )

        HomeEpgSearchOverlayHost(
            modifier = Modifier.fillMaxSize(),
            ui = ui,
            controller = controller,
            deps = deps,
            context = context,
            channelGroups = channelGroups,
            groupChannelCounts = groupChannelCounts,
            favoriteGroups = favoriteGroups,
            favoriteSavedMessage = favoriteSavedMessage,
            recordingViewModel = recordingViewModel,
            searchViewModel = searchViewModel,
        )
    }
}
