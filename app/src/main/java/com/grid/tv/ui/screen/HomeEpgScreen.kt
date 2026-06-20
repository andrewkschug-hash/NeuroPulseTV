package com.grid.tv.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.di.PlayerEntryPoint
import com.grid.tv.di.SearchEntryPoint
import com.grid.tv.domain.epg.programmesForChannel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.feature.epg.EpgPlaceholderData
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.buildGuideGroupCategories
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.expandedCategoriesForSelection
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.EpgGuidePosition
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

internal enum class EpgFocusZone { TOP_BAR, CONTINUE_WATCHING, PREVIEW, GRID_FILTER, GRID }

internal fun epgZoneAbove(
    from: EpgFocusZone,
    showPreview: Boolean,
    hasContinueWatching: Boolean
): EpgFocusZone? = when (from) {
    EpgFocusZone.GRID -> EpgFocusZone.GRID_FILTER
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
    hasContinueWatching: Boolean
): EpgFocusZone? = when (from) {
    EpgFocusZone.TOP_BAR -> when {
        hasContinueWatching -> EpgFocusZone.CONTINUE_WATCHING
        showPreview -> EpgFocusZone.PREVIEW
        else -> EpgFocusZone.GRID_FILTER
    }
    EpgFocusZone.CONTINUE_WATCHING -> when {
        showPreview -> EpgFocusZone.PREVIEW
        else -> EpgFocusZone.GRID_FILTER
    }
    EpgFocusZone.PREVIEW -> EpgFocusZone.GRID_FILTER
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
    onNavigateSeries: (Long) -> Unit = {},
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
    val sleepTimer = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .sleepTimerController()
    }
    val sleepRemaining by sleepTimer.remainingSec.collectAsStateWithLifecycle()
    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
    val hasConnection by viewModel.hasConnection.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val continueWatchingItems by viewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val featuredMovies by viewModel.featuredMovies.collectAsStateWithLifecycle()
    val featuredSeries by viewModel.featuredSeries.collectAsStateWithLifecycle()
    val vodProgress by viewModel.vodProgress.collectAsStateWithLifecycle()
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val recordingHealth by recordingViewModel.recordingHealth.collectAsStateWithLifecycle()
    val favoriteGroups by viewModel.favoriteGroups.collectAsStateWithLifecycle()
    val favoriteGroupFilter by viewModel.favoriteGroupFilter.collectAsStateWithLifecycle()
    val guideFilter by viewModel.guideFilter.collectAsStateWithLifecycle()
    val guideFiltersConfigured by viewModel.guideFiltersConfigured.collectAsStateWithLifecycle()
    val guideSettingsLoaded by viewModel.guideSettingsLoaded.collectAsStateWithLifecycle()
    val isReloadingChannels by viewModel.isReloadingChannels.collectAsStateWithLifecycle()
    val channelGroups by viewModel.channelGroups.collectAsStateWithLifecycle()
    val groupChannelCounts by viewModel.groupChannelCounts.collectAsStateWithLifecycle()
    val hasCatalogChannels by viewModel.hasCatalogChannels.collectAsStateWithLifecycle()
    val demoFavoriteIds by viewModel.demoFavoriteIds.collectAsStateWithLifecycle()
    val favoriteSavedMessage by viewModel.favoriteSavedMessage.collectAsStateWithLifecycle()
    val profileAccessMessage = viewModel.profileAccessMessage()
    val epgWindow by viewModel.epgPrograms.collectAsStateWithLifecycle()
    val windowStart by viewModel.windowStart.collectAsStateWithLifecycle()
    val windowDurationMs by viewModel.windowDurationMs.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val livePlayerManager = viewModel.livePlayerManager

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.reloadPlaybackSettings(context)
    }

    val guidePreviewEnabled by viewModel.guidePreviewEnabled.collectAsStateWithLifecycle()
    val previewChannelId by viewModel.guidePreviewChannelId.collectAsStateWithLifecycle()
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
                    if (guidePreviewEnabledState.value) {
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
            else -> channels
        }
    }
    val showFilteredEmptyState = !usePlaceholder && !showEmptyState &&
        displayChannels.isEmpty() && (guideFilter.isActive || favoriteGroupFilter != null)
    val displayPrograms = remember(displayChannels, epgWindow, windowStart, windowDurationMs, usePlaceholder, showEmptyState) {
        when {
            showEmptyState -> emptyList()
            usePlaceholder -> EpgPlaceholderData.programs(
                windowStart,
                windowStart + windowDurationMs
            )
            else -> epgWindow
        }
    }

    LaunchedEffect(favoriteSavedMessage) {
        if (favoriteSavedMessage != null) {
            delay(2500)
            viewModel.clearFavoriteSavedMessage()
        }
    }

    val liveChannelId by livePlayerManager.activeChannelIdFlow.collectAsStateWithLifecycle()
    val playbackStatus by livePlayerManager.playbackStatus.collectAsStateWithLifecycle()

    LaunchedEffect(liveChannelId, channels) {
        val id = liveChannelId ?: return@LaunchedEffect
        channels.find { it.id == id }?.let { viewModel.setLastPlayedChannel(it) }
    }
    val channelScanStatuses by viewModel.channelScanStatuses.collectAsStateWithLifecycle()
    val guidePosition by viewModel.guidePosition.collectAsStateWithLifecycle()

    val searchQuery by searchViewModel.queryText.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()
    val unifiedSearchResults by searchViewModel.unifiedResults.collectAsStateWithLifecycle()
    val searchBarState by searchViewModel.searchBarState.collectAsStateWithLifecycle()
    val preferredSearchInput by searchViewModel.preferredInputMode.collectAsStateWithLifecycle()

    val micSearchTrigger = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SearchEntryPoint::class.java)
            .micSearchTrigger()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            searchViewModel.beginVoiceSearch()
        }
    }

    val requestVoiceSearch: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            searchViewModel.beginVoiceSearch()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val openVoiceSearch: () -> Unit = {
        ui.showSearchOverlay = true
        requestVoiceSearch()
    }

    LaunchedEffect(Unit) {
        micSearchTrigger.events.collect {
            openVoiceSearch()
        }
    }

    LaunchedEffect(Unit) {
        searchViewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(ui.showSearchOverlay) {
        if (ui.showSearchOverlay && preferredSearchInput == SearchInputMode.VOICE) {
            requestVoiceSearch()
        } else if (!ui.showSearchOverlay) {
            searchViewModel.stopVoiceSearch()
        }
    }

    LaunchedEffect(guidePreviewEnabled, previewChannelId, liveChannelId) {
        if (!guidePreviewEnabled) return@LaunchedEffect
        if (previewChannelId == null && liveChannelId == null) return@LaunchedEffect
        viewModel.resumeGuidePreviewIfEnabled(context)
    }

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
    val needsPreviewPlayer = guidePreviewEnabled && previewChannelId != null
    val playerGeneration by livePlayerManager.playerGeneration.collectAsStateWithLifecycle()
    val previewPlayer = remember(playerGeneration, context, needsPreviewPlayer) {
        if (needsPreviewPlayer) livePlayerManager.getOrCreatePlayer(context) else null
    }

    val controller = remember(ui) { HomeEpgGuideController(ui) }

    LaunchedEffect(ui.focusChannelIndex, displayChannels, listState.layoutInfo.visibleItemsInfo) {
        if (displayChannels.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible >= displayChannels.size - 20) {
            viewModel.loadMoreChannels()
        }
        val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            displayChannels.getOrNull(info.index)?.id
        }.toSet()
        val focusWindow = (ui.focusChannelIndex - 3..ui.focusChannelIndex + 3)
            .mapNotNull { displayChannels.getOrNull(it)?.id }
        viewModel.updateScannerViewport((visible + focusWindow).toList())
    }

    LaunchedEffect(guidePosition, displayChannels.size, liveChannelId) {
        if (ui.didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
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
                val offsetDp = EpgLayout.offsetForTime(now, windowStart, windowDurationMs)
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
        viewModel.saveGuidePosition(
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

    LaunchedEffect(continueWatchingItems.size) {
        if (ui.focusedContinueIndex > continueWatchingItems.lastIndex) {
            ui.focusedContinueIndex = continueWatchingItems.lastIndex.coerceAtLeast(0)
        }
    }

    val focusedChannel = displayChannels.getOrNull(ui.focusChannelIndex)
    val channelPrograms = remember(focusedChannel, displayPrograms) {
        focusedChannel?.let { programmesForChannel(it, displayPrograms) } ?: emptyList()
    }
    val focusedProgram = if (ui.focusOnChannelColumn) {
        channelPrograms.firstOrNull { now in it.startTime..it.endTime }
            ?: channelPrograms.firstOrNull()
    } else {
        channelPrograms.getOrNull(ui.focusProgramIndex)
    }

    val previewChannel = previewChannelId?.let { channelId ->
        channels.find { it.id == channelId } ?: displayChannels.find { it.id == channelId }
    }
    val previewChannelPrograms = remember(previewChannel, displayPrograms) {
        previewChannel?.let { programmesForChannel(it, displayPrograms) } ?: emptyList()
    }
    val previewProgram = remember(
        previewChannel,
        focusedChannel,
        focusedProgram,
        ui.focusOnChannelColumn,
        previewChannelPrograms,
        now
    ) {
        if (previewChannel?.id == focusedChannel?.id && !ui.focusOnChannelColumn) {
            focusedProgram
        } else {
            previewChannelPrograms.firstOrNull { now in it.startTime..it.endTime }
                ?: previewChannelPrograms.firstOrNull()
        }
    }

    val previewStreamStatus = if (previewChannelId == liveChannelId) {
        playbackStatus
    } else {
        com.grid.tv.player.StreamPlaybackStatus.LOADING
    }
    val showPreviewSection = guidePreviewEnabled && previewChannel != null
    val hasContinueWatching = continueWatchingItems.isNotEmpty()

    val upcomingPrograms = remember(previewProgram, previewChannelPrograms, now) {
        val anchor = previewProgram ?: previewChannelPrograms.firstOrNull { now in it.startTime..it.endTime }
        if (anchor != null) {
            previewChannelPrograms.filter { it.startTime > anchor.startTime }.take(4)
        } else {
            previewChannelPrograms.filter { it.startTime > now }.take(4)
        }
    }

    LaunchedEffect(liveChannelId, playbackStatus) {
        liveChannelId?.let { viewModel.reportPlaybackHealth(it, playbackStatus) }
    }

    LaunchedEffect(hasCatalogChannels, guideFiltersConfigured, channelGroups, guideSettingsLoaded) {
        if (!guideSettingsLoaded) return@LaunchedEffect
        if (guideFiltersConfigured) {
            ui.showGuideGroupPicker = false
            return@LaunchedEffect
        }
        ui.showGuideGroupPicker = hasCatalogChannels &&
            channelGroups.isNotEmpty() &&
            !ui.initialGuidePickerDismissed
    }

    LaunchedEffect(displayChannels.size, isReloadingChannels, displayPrograms) {
        if (isReloadingChannels) return@LaunchedEffect
        if (displayChannels.isEmpty()) {
            ui.focusChannelIndex = 0
            ui.focusProgramIndex = 0
            ui.focusOnChannelColumn = false
        } else if (ui.focusChannelIndex > displayChannels.lastIndex) {
            ui.focusChannelIndex = displayChannels.lastIndex
            val progs = displayChannels.getOrNull(ui.focusChannelIndex)
                ?.let { programmesForChannel(it, displayPrograms) }
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
        displayPrograms = displayPrograms,
        viewModel = viewModel,
        recordingViewModel = recordingViewModel,
        searchViewModel = searchViewModel,
        now = now,
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
        guideGroupCategories = guideGroupCategories,
        guideFilter = guideFilter,
        demoFavoriteIds = demoFavoriteIds,
        vodProgress = vodProgress,
        continueWatchingItems = continueWatchingItems,
        showPreviewSection = showPreviewSection,
        hasContinueWatching = hasContinueWatching,
        usePlaceholder = usePlaceholder,
        searchQuery = searchQuery,
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

    val liveScrollTargetPx = controller.liveScrollTarget()
    val scrolledAwayFromLive = kotlin.math.abs(hScroll.value - liveScrollTargetPx) > 80

    LaunchedEffect(displayChannels.isEmpty()) {
        if (displayChannels.isEmpty() && ui.focusZone == EpgFocusZone.GRID) {
            controller.focusEpgZone(EpgFocusZone.GRID_FILTER)
        }
    }

    LaunchedEffect(isInitializing, displayChannels.isNotEmpty()) {
        if (
            !isInitializing &&
            displayChannels.isNotEmpty() &&
            !ui.hasRequestedInitialGridFocus &&
            ui.focusZone == EpgFocusZone.GRID
        ) {
            ui.hasRequestedInitialGridFocus = true
            controller.requestEpgZoneFocus(EpgFocusZone.GRID)
        }
    }

    LaunchedEffect(showPreviewSection, previewChannelId, ui.focusZone) {
        if (!showPreviewSection || ui.focusZone != EpgFocusZone.PREVIEW) return@LaunchedEffect
        controller.requestEpgZoneFocus(EpgFocusZone.PREVIEW)
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
            now = now,
            profileInitials = profileInitials,
            profileAvatarColor = profileAvatarColor,
            profileAccessMessage = profileAccessMessage,
            isRecording = isRecording,
            activeRecordingTitle = activeRecordingTitle,
            recordingHealth = recordingHealth,
            onNavigateRecordings = onNavigateRecordings,
            onNavigateProfile = onNavigateProfile,
            onNavigateSettings = onNavigateSettings,
            upcomingPrograms = upcomingPrograms,
            previewPlayer = previewPlayer,
            previewStreamStatus = previewStreamStatus,
            previewSurfaceAttached = previewSurfaceAttached,
            featuredMovies = featuredMovies,
            featuredSeries = featuredSeries,
            channelGroups = channelGroups,
            channelScanStatuses = channelScanStatuses,
            scheduled = scheduled,
            timelineWidth = timelineWidth,
            scrolledAwayFromLive = scrolledAwayFromLive,
            showFilteredEmptyState = showFilteredEmptyState,
            hScroll = hScroll,
            listState = listState,
        )

        HomeEpgScreenOverlays(
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
            showStoragePicker = showStoragePicker,
            storageOptions = storageOptions,
            precheck = precheck,
            searchQuery = searchQuery,
            unifiedSearchResults = unifiedSearchResults,
            searchResults = searchResults,
            searchBarState = searchBarState,
            searchViewModel = searchViewModel,
            onRequestVoiceSearch = requestVoiceSearch,
        )
    }
}
