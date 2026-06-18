package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.data.db.entity.ScheduledRecordingEntity
import com.grid.tv.domain.epg.programmesForChannel
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.feature.epg.EpgPlaceholderData
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.recording.RecordingStatus
import com.grid.tv.di.PlayerEntryPoint
import com.grid.tv.di.SearchEntryPoint
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.player.LivePlayerManager
import dagger.hilt.android.EntryPointAccessors
import com.grid.tv.ui.component.EpgChannelCell
import com.grid.tv.ui.component.EpgEmptyState
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgProgramCell
import com.grid.tv.ui.component.EpgJumpToLiveButton
import com.grid.tv.ui.component.EpgTimelineHeader
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.ui.component.ContinueWatchingRow
import com.grid.tv.ui.component.GuideGroupFilterMenu
import com.grid.tv.ui.component.GuideGroupPickerDialog
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.component.buildGuideGroupCategories
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.expandedCategoriesForSelection
import com.grid.tv.ui.component.guideFilterRowAction
import com.grid.tv.ui.component.toggleCategoryExpansion
import com.grid.tv.ui.component.visibleRowIndexForSelection
import com.grid.tv.ui.component.MoviesHomeRow
import com.grid.tv.ui.component.SeriesHomeRow
import com.grid.tv.ui.component.EpgCategoryFilterChip
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.RecordingPrecheckDialog
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.SearchOverlay
import com.grid.tv.ui.component.StorageLocationPicker
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.domain.epg.EpgProgramAction
import com.grid.tv.domain.epg.EpgProgramReplayState
import com.grid.tv.ui.viewmodel.EpgGuidePosition
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.consumeImeNavigationKeysWhenTyping
import com.grid.tv.util.quitAppToHome
import kotlinx.coroutines.launch

private enum class EpgFocusZone { TOP_BAR, CONTINUE_WATCHING, PREVIEW, GRID_FILTER, GRID }

private val TopBarProfileIndex get() = GridNavTabs.size
private const val TopBarSearchIndex = 0

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
    val context = LocalContext.current
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
    val isReloadingChannels by viewModel.isReloadingChannels.collectAsStateWithLifecycle()
    val channelGroups by viewModel.channelGroups.collectAsStateWithLifecycle()
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

    /** Channel shown in the preview pane — persisted in ViewModel across navigation. */
    val guidePreviewEnabled by viewModel.guidePreviewEnabled.collectAsStateWithLifecycle()
    val previewChannelId by viewModel.guidePreviewChannelId.collectAsStateWithLifecycle()
    val guidePreviewEnabledState = rememberUpdatedState(guidePreviewEnabled)

    val lifecycleOwner = LocalLifecycleOwner.current
    var previewSurfaceAttached by remember { mutableStateOf(true) }
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
    var showSearchOverlay by remember { mutableStateOf(false) }

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

    fun requestVoiceSearch() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            searchViewModel.beginVoiceSearch()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openVoiceSearch() {
        showSearchOverlay = true
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

    LaunchedEffect(showSearchOverlay) {
        if (showSearchOverlay && preferredSearchInput == SearchInputMode.VOICE) {
            requestVoiceSearch()
        } else if (!showSearchOverlay) {
            searchViewModel.stopVoiceSearch()
        }
    }

    var selectedTab by remember { mutableStateOf(EpgNavTab.Guide) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(EpgFocusZone.GRID) }
    var topBarFocusIndex by remember { mutableIntStateOf(0) }
    var focusedContinueIndex by remember { mutableIntStateOf(0) }
    var focusChannelIndex by remember { mutableIntStateOf(0) }
    var focusProgramIndex by remember { mutableIntStateOf(0) }
    var focusOnChannelColumn by remember { mutableStateOf(true) }
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

    var detailExpanded by remember { mutableStateOf(false) }
    var detailActionIndex by remember { mutableIntStateOf(0) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showGuideGroupPicker by remember { mutableStateOf(false) }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var categoryMenuFocusIndex by remember { mutableIntStateOf(0) }
    var categoryMenuExpandedCategories by remember { mutableStateOf(setOf<Int>()) }

    val guideGroupCategories = remember(channelGroups) { buildGuideGroupCategories(channelGroups) }

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

    var didInitialScroll by remember { mutableStateOf(false) }
    var didRestoreGuide by remember { mutableStateOf(false) }

    LaunchedEffect(focusChannelIndex, displayChannels, listState.layoutInfo.visibleItemsInfo) {
        if (displayChannels.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible >= displayChannels.size - 20) {
            viewModel.loadMoreChannels()
        }
        val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            displayChannels.getOrNull(info.index)?.id
        }.toSet()
        val focusWindow = (focusChannelIndex - 3..focusChannelIndex + 3)
            .mapNotNull { displayChannels.getOrNull(it)?.id }
        viewModel.updateScannerViewport((visible + focusWindow).toList())
    }

    LaunchedEffect(guidePosition, displayChannels.size, liveChannelId) {
        if (didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
        if (guidePosition.hasSavedPosition) {
            focusChannelIndex = guidePosition.focusChannelIndex.coerceIn(0, displayChannels.lastIndex)
            focusProgramIndex = guidePosition.focusProgramIndex
            focusOnChannelColumn = guidePosition.focusOnChannelColumn
            listState.scrollToItem(focusChannelIndex)
            hScroll.scrollTo(guidePosition.timelineScrollPx.coerceIn(0, hScroll.maxValue))
            didInitialScroll = true
        } else {
            val idx = displayChannels.indexOfFirst { it.id == liveChannelId }
            if (idx >= 0) {
                focusChannelIndex = idx
                listState.scrollToItem(idx)
            }
        }
        didRestoreGuide = true
    }

    LaunchedEffect(displayChannels.size, windowStart, guidePosition.hasSavedPosition) {
        if (!didInitialScroll && displayChannels.isNotEmpty()) {
            if (guidePosition.hasSavedPosition) {
                didInitialScroll = true
            } else {
                val target = ((now - windowStart) * EpgLayout.dpPerMs() - 400f).coerceAtLeast(0f).toInt()
                hScroll.scrollTo(target)
                didInitialScroll = true
            }
        }
    }

    LaunchedEffect(
        focusChannelIndex,
        focusProgramIndex,
        focusOnChannelColumn,
        hScroll.value,
        displayChannels.size,
        didRestoreGuide
    ) {
        if (!didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
        viewModel.saveGuidePosition(
            EpgGuidePosition(
                focusChannelIndex = focusChannelIndex.coerceIn(0, displayChannels.lastIndex),
                focusProgramIndex = focusProgramIndex,
                focusOnChannelColumn = focusOnChannelColumn,
                timelineScrollPx = hScroll.value
            )
        )
    }

    LaunchedEffect(favoriteGroupFilter) {
        selectedTab = when (favoriteGroupFilter) {
            HomeEpgViewModel.FAVORITES_FILTER -> EpgNavTab.Favorites
            else -> if (selectedTab == EpgNavTab.Favorites) EpgNavTab.Guide else selectedTab
        }
    }

    LaunchedEffect(openFavoritesInitially) {
        if (openFavoritesInitially) {
            viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            selectedTab = EpgNavTab.Favorites
        }
    }

    LaunchedEffect(continueWatchingItems.size) {
        if (focusedContinueIndex > continueWatchingItems.lastIndex) {
            focusedContinueIndex = continueWatchingItems.lastIndex.coerceAtLeast(0)
        }
    }

    fun watchChannel(ch: Channel) {
        val full = channels.find { it.id == ch.id } ?: ch
        viewModel.setLastPlayedChannel(full)
        viewModel.enableGuidePreview(full.id)
        onWatchChannel(full.id)
    }

    fun selectChannelForPreview(channel: Channel) {
        if (usePlaceholder || channel.streamUrl.isBlank()) return
        viewModel.enableGuidePreview(channel.id)
        val fullChannel = channels.find { it.id == channel.id } ?: channel
        viewModel.previewChannel(context, fullChannel)
    }

    fun liveScrollTarget(): Int =
        ((now - windowStart) * EpgLayout.dpPerMs() - 400f).coerceAtLeast(0f).toInt()

    fun scrollToProgram(program: Program?) {
        program ?: return
        val startPx = ((program.startTime - windowStart) * EpgLayout.dpPerMs()).coerceAtLeast(0f)
        val widthPx = (program.endTime - program.startTime) * EpgLayout.dpPerMs()
        val centerPx = startPx + widthPx / 2f
        val viewportHalf = 420f
        val target = (centerPx - viewportHalf).coerceAtLeast(0f).toInt()
        scope.launch { hScroll.animateScrollTo(target.coerceAtMost(hScroll.maxValue)) }
    }

    fun playProgram(channel: Channel, program: Program, instant: Boolean = false) {
        val full = channels.find { it.id == channel.id } ?: channel
        val replay = viewModel.replayState(program, full, now)
        when (replay.action) {
            EpgProgramAction.WATCH_REPLAY -> {
                scope.launch {
                    val url = viewModel.stageCatchupPlayback(program, full) ?: return@launch
                    onPlayCatchup(program.title, url)
                }
            }
            EpgProgramAction.REMINDER -> recordingViewModel.scheduleProgram(full, program)
            else -> {
                if (instant) watchChannel(full) else {
                    selectChannelForPreview(full)
                    focusZone = EpgFocusZone.PREVIEW
                    detailActionIndex = 0
                    detailExpanded = true
                }
            }
        }
    }

    fun primaryActionLabel(channel: Channel?, program: Program?): String {
        if (channel == null || program == null) return "Watch Live"
        return viewModel.replayState(program, channels.find { it.id == channel.id } ?: channel, now).action.label
    }

    val focusedChannel = displayChannels.getOrNull(focusChannelIndex)
    val channelPrograms = remember(focusedChannel, displayPrograms) {
        focusedChannel?.let { programmesForChannel(it, displayPrograms) } ?: emptyList()
    }
    val focusedProgram = if (focusOnChannelColumn) {
        channelPrograms.firstOrNull { now in it.startTime..it.endTime }
            ?: channelPrograms.firstOrNull()
    } else {
        channelPrograms.getOrNull(focusProgramIndex)
    }

    fun channelById(id: Long?): Channel? =
        id?.let { channelId -> channels.find { it.id == channelId } ?: displayChannels.find { it.id == channelId } }

    val previewChannel = channelById(previewChannelId)
    val previewChannelPrograms = remember(previewChannel, displayPrograms) {
        previewChannel?.let { programmesForChannel(it, displayPrograms) } ?: emptyList()
    }
    val previewProgram = remember(
        previewChannel,
        focusedChannel,
        focusedProgram,
        focusOnChannelColumn,
        previewChannelPrograms,
        now
    ) {
        if (previewChannel?.id == focusedChannel?.id && !focusOnChannelColumn) {
            focusedProgram
        } else {
            previewChannelPrograms.firstOrNull { now in it.startTime..it.endTime }
                ?: previewChannelPrograms.firstOrNull()
        }
    }

    fun openChannelFromTouch(channelIndex: Int, channel: Channel) {
        focusChannelIndex = channelIndex
        focusOnChannelColumn = true
        scope.launch { listState.animateScrollToItem(channelIndex) }
        selectChannelForPreview(channel)
        focusZone = EpgFocusZone.PREVIEW
        detailActionIndex = 0
        detailExpanded = true
    }

    fun openProgramFromTouch(channelIndex: Int, programIndex: Int, program: Program) {
        focusChannelIndex = channelIndex
        focusProgramIndex = programIndex
        focusOnChannelColumn = false
        val channel = displayChannels.getOrNull(channelIndex) ?: return
        scrollToProgram(program)
        val replay = viewModel.replayState(program, channels.find { it.id == channel.id } ?: channel, now)
        if (replay.action == EpgProgramAction.WATCH_REPLAY) {
            playProgram(channel, program, instant = true)
            return
        }
        selectChannelForPreview(channel)
        focusZone = EpgFocusZone.PREVIEW
        detailActionIndex = 0
        detailExpanded = true
    }

    val previewStreamStatus = if (previewChannelId == liveChannelId) {
        playbackStatus
    } else {
        com.grid.tv.player.StreamPlaybackStatus.LOADING
    }
    val showPreviewSection = guidePreviewEnabled && previewChannel != null

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

    fun programsForChannel(channel: Channel): List<Program> =
        programmesForChannel(channel, displayPrograms)

    fun clampProgramIndex(channelIdx: Int, programIdx: Int): Int {
        val progs = displayChannels.getOrNull(channelIdx)?.let { programsForChannel(it) } ?: emptyList()
        return programIdx.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(hasCatalogChannels, guideFiltersConfigured, channelGroups) {
        if (hasCatalogChannels && !guideFiltersConfigured && channelGroups.isNotEmpty()) {
            showGuideGroupPicker = true
        }
    }

    LaunchedEffect(displayChannels.size, isReloadingChannels) {
        if (isReloadingChannels) return@LaunchedEffect
        if (displayChannels.isEmpty()) {
            focusChannelIndex = 0
            focusProgramIndex = 0
            focusOnChannelColumn = false
        } else if (focusChannelIndex > displayChannels.lastIndex) {
            focusChannelIndex = displayChannels.lastIndex
            focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
        }
    }

    fun scrollByTimeSlot(direction: Int) {
        val slotPx = (EpgLayout.ThirtyMinWidthDp * direction).toInt()
        scope.launch {
            val target = (hScroll.value + slotPx).coerceIn(0, hScroll.maxValue)
            hScroll.animateScrollTo(target)
        }
    }

    fun scrollToLive() {
        scope.launch { hScroll.animateScrollTo(liveScrollTarget()) }
    }

    fun activateNavTab(tab: EpgNavTab) {
        selectedTab = tab
        when (tab) {
            EpgNavTab.Guide, EpgNavTab.Home -> {
                viewModel.setFavoriteGroupFilter(null)
                focusZone = EpgFocusZone.GRID
                focusOnChannelColumn = true
            }
            EpgNavTab.Search -> showSearchOverlay = true
            EpgNavTab.Vod -> onNavigateVod(0)
            EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(1)
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            EpgNavTab.Settings -> onNavigateSettings()
        }
    }

    fun handleSearchResult(result: SearchResultItem) {
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
        searchViewModel.recordSelection(searchQuery)
        showSearchOverlay = false
        searchViewModel.clearQuery()
        when (result.type) {
            SearchResultType.CHANNEL -> result.channelId?.let { chId ->
                val ch = displayChannels.find { it.id == chId } ?: channels.find { it.id == chId }
                if (ch != null) {
                    val idx = displayChannels.indexOfFirst { it.id == chId }
                    if (idx >= 0) {
                        focusChannelIndex = idx
                        focusOnChannelColumn = true
                        focusZone = EpgFocusZone.GRID
                    }
                    viewModel.setLastPlayedChannel(ch)
                    viewModel.enableGuidePreview(ch.id)
                } else {
                    viewModel.enableGuidePreview(chId)
                }
                onWatchChannel(chId)
            }
            SearchResultType.PROGRAM -> {
                val chId = result.channelId
                val prog = result.program
                if (chId != null && prog != null) {
                    val chIdx = displayChannels.indexOfFirst { it.id == chId }
                    if (chIdx >= 0) {
                        focusChannelIndex = chIdx
                        val progs = programsForChannel(displayChannels[chIdx])
                        focusProgramIndex = progs.indexOfFirst { it.id == prog.id }.coerceAtLeast(0)
                        focusOnChannelColumn = false
                        scrollToProgram(prog)
                        focusZone = EpgFocusZone.GRID
                    }
                }
            }
            SearchResultType.VOD -> result.vodItem?.let { item ->
                VodPlaybackHelper.stageMovie(item)
                val resume = (vodProgress[item.streamId] ?: 0L) > 5_000L
                onPlayVod(item.streamUrl, item.title, resume)
            }
            SearchResultType.SERIES -> result.seriesShow?.let { onNavigateSeries(it.id) }
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
                    onPlayVod(episode.streamUrl, episode.title.ifBlank { show.name }, false)
                }
            }
            else -> Unit
        }
    }

    fun executeDetailAction() {
        val ch = previewChannel ?: focusedChannel ?: return
        val full = channels.find { it.id == ch.id } ?: ch
        val prog = if (previewChannel?.id == focusedChannel?.id && !focusOnChannelColumn) {
            focusedProgram
        } else {
            previewProgram
        }
        when (detailActionIndex) {
            0 -> {
                if (prog != null) {
                    playProgram(full, prog, instant = true)
                } else {
                    watchChannel(full)
                }
            }
            1 -> {
                val isFav = if (ch.id < 0) ch.id in demoFavoriteIds else ch.isFavorite
                viewModel.toggleFavorite(ch.id, isFav)
            }
            2 -> {
                if (prog == null) {
                    recordingViewModel.startImmediateRecording(context, full, full.name)
                } else if (prog.startTime <= now) {
                    val duration = (prog.endTime - now).coerceAtLeast(10 * 60 * 1000)
                    recordingViewModel.startImmediateRecording(context, full, prog.title, duration)
                } else {
                    recordingViewModel.scheduleProgram(full, prog)
                }
            }
        }
    }

    fun focusSearchTopBar() {
        topBarFocusIndex = TopBarSearchIndex
        focusZone = EpgFocusZone.TOP_BAR
    }

    fun focusGuideFilter() {
        focusZone = EpgFocusZone.GRID_FILTER
    }

    fun focusGuideChannels(targetIndex: Int = focusChannelIndex) {
        focusZone = EpgFocusZone.GRID
        focusChannelIndex = targetIndex.coerceIn(0, displayChannels.lastIndex.coerceAtLeast(0))
        focusOnChannelColumn = true
    }

    fun focusPreviewSection() {
        if (showPreviewSection) {
            focusZone = EpgFocusZone.PREVIEW
            detailActionIndex = 0
        } else {
            focusGuideFilter()
        }
    }

    fun moveGuideFocusUp(from: EpgFocusZone) {
        when (from) {
            EpgFocusZone.GRID_FILTER -> {
                if (showPreviewSection) {
                    focusPreviewSection()
                } else if (continueWatchingItems.isNotEmpty()) {
                    focusZone = EpgFocusZone.CONTINUE_WATCHING
                } else {
                    focusSearchTopBar()
                }
            }
            EpgFocusZone.CONTINUE_WATCHING -> focusSearchTopBar()
            else -> Unit
        }
    }

    fun moveGuideFocusDown(from: EpgFocusZone) {
        when (from) {
            EpgFocusZone.TOP_BAR -> {
                focusZone = when {
                    continueWatchingItems.isNotEmpty() -> EpgFocusZone.CONTINUE_WATCHING
                    showPreviewSection -> EpgFocusZone.PREVIEW
                    else -> EpgFocusZone.GRID_FILTER
                }
                if (focusZone == EpgFocusZone.PREVIEW) detailActionIndex = 0
            }
            EpgFocusZone.CONTINUE_WATCHING -> {
                if (showPreviewSection) {
                    focusPreviewSection()
                } else {
                    focusGuideFilter()
                }
            }
            EpgFocusZone.PREVIEW -> focusGuideFilter()
            else -> Unit
        }
    }

    fun openCategoryFilterMenu() {
        val expanded = expandedCategoriesForSelection(
            guideGroupCategories,
            guideFilter.selectedGroups
        )
        categoryMenuExpandedCategories = expanded
        categoryMenuFocusIndex = visibleRowIndexForSelection(
            guideGroupCategories,
            expanded,
            guideFilter.selectedGroups
        )
        showCategoryFilterMenu = true
    }

    LaunchedEffect(categoryMenuExpandedCategories, showCategoryFilterMenu, guideFilter.selectedGroups) {
        if (!showCategoryFilterMenu) return@LaunchedEffect
        val expanded = categoryMenuExpandedCategories.ifEmpty {
            expandedCategoriesForSelection(guideGroupCategories, guideFilter.selectedGroups)
        }
        val count = buildVisibleGuideGroupRows(guideGroupCategories, expanded).size
        categoryMenuFocusIndex = categoryMenuFocusIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun handleCategoryFilterMenuToggle(index: Int) {
        val expanded = categoryMenuExpandedCategories.ifEmpty {
            expandedCategoriesForSelection(guideGroupCategories, guideFilter.selectedGroups)
        }
        val visibleRows = buildVisibleGuideGroupRows(guideGroupCategories, expanded)
        val row = visibleRows.getOrNull(index) ?: return
        when (row) {
            is GuideGroupVisibleRow.Category -> {
                val willExpand = row.categoryIndex !in categoryMenuExpandedCategories
                categoryMenuExpandedCategories = toggleCategoryExpansion(
                    categoryMenuExpandedCategories,
                    row.categoryIndex
                )
                categoryMenuFocusIndex = if (willExpand) index + 1 else index
            }
            is GuideGroupVisibleRow.SelectAll,
            is GuideGroupVisibleRow.Group -> {
                val next = guideFilterRowAction(row, guideFilter.selectedGroups) ?: return
                viewModel.setGuideFilter(next, markConfigured = true)
            }
            GuideGroupVisibleRow.AllChannels -> {
                viewModel.setGuideFilter(GuideChannelFilter.All, markConfigured = true)
                showCategoryFilterMenu = false
            }
        }
    }

    fun handleGridFilterKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.consumesImeNavigationKeys(event)) return true
        if (showCategoryFilterMenu) return false
        return when (event.key) {
            Key.DirectionDown -> {
                focusGuideChannels()
                true
            }
            Key.DirectionUp -> {
                moveGuideFocusUp(EpgFocusZone.GRID_FILTER)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                openCategoryFilterMenu()
                true
            }
            Key.Back, Key.Escape -> {
                focusZone = EpgFocusZone.GRID
                true
            }
            else -> false
        }
    }

    fun handleTopBarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.consumesImeNavigationKeys(event)) return true
        if (showCategoryFilterMenu) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    profileMenuOpen = false
                    false
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
                moveGuideFocusDown(EpgFocusZone.TOP_BAR)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (topBarFocusIndex) {
                    in GridNavTabs.indices -> activateNavTab(GridNavTabs[topBarFocusIndex])
                    TopBarProfileIndex -> {
                        profileMenuOpen = true
                        profileMenuFocusIndex = 0
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleContinueWatchingKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.consumesImeNavigationKeys(event)) return true
        if (continueWatchingItems.isEmpty()) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                focusedContinueIndex = (focusedContinueIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                focusedContinueIndex = (focusedContinueIndex + 1)
                    .coerceAtMost(continueWatchingItems.lastIndex)
                true
            }
            Key.DirectionUp -> {
                focusSearchTopBar()
                true
            }
            Key.DirectionDown -> {
                if (showPreviewSection) {
                    focusPreviewSection()
                } else {
                    focusGuideFilter()
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                continueWatchingItems.getOrNull(focusedContinueIndex)?.let { item ->
                    if (viewModel.isProfileAccessAllowed()) {
                        onResumeContinueWatching(item)
                    }
                }
                true
            }
            Key.Back, Key.Escape -> {
                focusSearchTopBar()
                true
            }
            else -> false
        }
    }

    fun handlePreviewKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.consumesImeNavigationKeys(event)) return true
        return when (event.key) {
            Key.DirectionLeft -> {
                if (detailActionIndex > 0) detailActionIndex -= 1
                true
            }
            Key.DirectionRight -> {
                if (detailActionIndex < 2) detailActionIndex += 1
                true
            }
            Key.DirectionUp -> {
                if (continueWatchingItems.isNotEmpty()) {
                    focusZone = EpgFocusZone.CONTINUE_WATCHING
                } else {
                    focusSearchTopBar()
                }
                true
            }
            Key.DirectionDown -> {
                focusGuideFilter()
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                executeDetailAction()
                true
            }
            Key.Back, Key.Escape -> {
                focusZone = EpgFocusZone.GRID
                detailExpanded = false
                true
            }
            else -> false
        }
    }

    fun handleGridKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.consumesImeNavigationKeys(event)) return true
        if (showCategoryFilterMenu || showGuideGroupPicker) return false
        if (displayChannels.isEmpty()) return false

        return when (event.key) {
            Key.DirectionDown -> {
                if (focusChannelIndex < displayChannels.lastIndex) {
                    focusChannelIndex += 1
                    if (!focusOnChannelColumn) {
                        focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                    }
                    scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                }
                true
            }
            Key.DirectionUp -> {
                if (focusChannelIndex > 0) {
                    focusChannelIndex -= 1
                    if (!focusOnChannelColumn) {
                        focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                    }
                    scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                } else {
                    focusGuideFilter()
                }
                true
            }
            Key.DirectionRight -> {
                val channel = displayChannels.getOrNull(focusChannelIndex) ?: return false
                val progs = programsForChannel(channel)
                if (focusOnChannelColumn) {
                    focusOnChannelColumn = false
                    val progsNow = progs.indexOfFirst { now in it.startTime..it.endTime }
                    focusProgramIndex = if (progsNow >= 0) progsNow else clampProgramIndex(focusChannelIndex, 0)
                    scrollToProgram(progs.getOrNull(focusProgramIndex))
                } else if (focusProgramIndex < progs.lastIndex) {
                    focusProgramIndex += 1
                    scrollToProgram(progs[focusProgramIndex])
                } else {
                    val last = progs.lastOrNull()
                    val windowEnd = windowStart + windowDurationMs
                    if (last != null && last.endTime >= windowEnd - 15 * 60 * 1000) {
                        viewModel.extendWindowForward()
                    }
                    scrollByTimeSlot(1)
                }
                true
            }
            Key.DirectionLeft -> {
                val channel = displayChannels.getOrNull(focusChannelIndex) ?: return false
                val progs = programsForChannel(channel)
                if (!focusOnChannelColumn && focusProgramIndex > 0) {
                    focusProgramIndex -= 1
                    scrollToProgram(progs[focusProgramIndex])
                } else if (!focusOnChannelColumn) {
                    focusOnChannelColumn = true
                } else {
                    val first = progs.firstOrNull()
                    if (first != null && first.startTime <= windowStart + 15 * 60 * 1000) {
                        val scrollAdjust = viewModel.extendWindowBackward()
                        if (scrollAdjust > 0) {
                            scope.launch { hScroll.scrollTo(hScroll.value + scrollAdjust) }
                        }
                    }
                    scrollByTimeSlot(-1)
                }
                true
            }
            Key.PageDown -> {
                focusChannelIndex = (focusChannelIndex + 10).coerceAtMost(displayChannels.lastIndex)
                if (!focusOnChannelColumn) focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                true
            }
            Key.PageUp -> {
                focusChannelIndex = (focusChannelIndex - 10).coerceAtLeast(0)
                if (!focusOnChannelColumn) focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                val channel = displayChannels.getOrNull(focusChannelIndex) ?: return true
                if (!focusOnChannelColumn) {
                    val prog = programsForChannel(channel).getOrNull(focusProgramIndex)
                    if (prog != null) {
                        playProgram(channel, prog, instant = true)
                        return true
                    }
                }
                selectChannelForPreview(channel)
                focusZone = EpgFocusZone.PREVIEW
                detailActionIndex = 0
                detailExpanded = true
                true
            }
            Key.Back, Key.Escape -> {
                if (detailExpanded) {
                    detailExpanded = false
                    focusZone = EpgFocusZone.GRID
                    true
                } else if (focusChannelIndex > 0) {
                    focusChannelIndex = 0
                    if (!focusOnChannelColumn) {
                        focusProgramIndex = clampProgramIndex(0, focusProgramIndex)
                    }
                    scope.launch { listState.animateScrollToItem(0) }
                    true
                } else {
                    focusGuideFilter()
                    true
                }
            }
            else -> false
        }
    }

    val liveScrollTargetPx = liveScrollTarget()
    val scrolledAwayFromLive = kotlin.math.abs(hScroll.value - liveScrollTargetPx) > 80

    when {
        isInitializing -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading guide…",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp
                )
            }
            return
        }
        showEmptyState -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background),
                contentAlignment = Alignment.Center
            ) {
                EpgEmptyState(onAddPlaylist = onNavigateSettings)
            }
            return
        }
    }

    LaunchedEffect(
        focusZone,
        displayChannels.size,
        showPreviewSection,
        continueWatchingItems.isNotEmpty(),
        showCategoryFilterMenu,
        showGuideGroupPicker
    ) {
        if (showCategoryFilterMenu || showGuideGroupPicker) return@LaunchedEffect
        when (focusZone) {
            EpgFocusZone.GRID -> if (displayChannels.isNotEmpty()) {
                gridFocusRequester.requestFocusSafelyAfterLayout()
            }
            EpgFocusZone.GRID_FILTER -> if (displayChannels.isNotEmpty()) {
                gridFilterFocusRequester.requestFocusSafelyAfterLayout()
            }
            EpgFocusZone.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            EpgFocusZone.CONTINUE_WATCHING -> if (continueWatchingItems.isNotEmpty()) {
                continueWatchingFocusRequester.requestFocusSafelyAfterLayout()
            }
            EpgFocusZone.PREVIEW -> if (showPreviewSection) {
                previewFocusRequester.requestFocusSafelyAfterLayout(150)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .onPreviewKeyEvent { event ->
                false
            }
            .consumeImeNavigationKeysWhenTyping()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = selectedTab,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == EpgFocusZone.TOP_BAR && topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusZone == EpgFocusZone.TOP_BAR && topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                profileAvatarColor = profileAvatarColor,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                onProfileClick = {
                    focusZone = EpgFocusZone.TOP_BAR
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
                onTabSelected = { tab ->
                    focusZone = EpgFocusZone.TOP_BAR
                    topBarFocusIndex = GridNavTabs.indexOf(tab)
                    activateNavTab(tab)
                },
                miniPlayer = {},
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                recordingHealth = recordingHealth,
                onRecordingIndicatorClick = onNavigateRecordings,
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == EpgFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            if (profileAccessMessage != null) {
                androidx.tv.material3.Text(
                    text = profileAccessMessage,
                    color = androidx.compose.ui.graphics.Color(0xFFFFB020),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (continueWatchingItems.isNotEmpty()) {
                HomeEpgContinueWatchingRow(
                    continueWatchingItems = continueWatchingItems,
                    focusedContinueIndex = focusedContinueIndex,
                    continueWatchingFocused = focusZone == EpgFocusZone.CONTINUE_WATCHING,
                    onContinueSelect = { item ->
                        if (viewModel.isProfileAccessAllowed()) {
                            onResumeContinueWatching(item)
                        }
                    },
                    continueWatchingFocusRequester = continueWatchingFocusRequester,
                    onContinueWatchingKey = ::handleContinueWatchingKey
                )
            }

            if (showPreviewSection) {
                HomeEpgPreviewSection(
                    channel = previewChannel,
                    program = previewProgram,
                    upcomingPrograms = upcomingPrograms,
                    now = now,
                    player = previewPlayer,
                    streamStatus = previewStreamStatus,
                    detailActionIndex = detailActionIndex,
                    previewFocused = focusZone == EpgFocusZone.PREVIEW,
                    attachSurface = previewSurfaceAttached,
                    isFavorite = previewChannel?.let { ch ->
                        if (ch.id < 0) ch.id in demoFavoriteIds else ch.isFavorite
                    } ?: false,
                    primaryActionLabel = primaryActionLabel(previewChannel, previewProgram),
                    onWatch = {
                        detailActionIndex = 0
                        executeDetailAction()
                    },
                    onFavorite = {
                        detailActionIndex = 1
                        executeDetailAction()
                    },
                    onRecord = {
                        detailActionIndex = 2
                        executeDetailAction()
                    },
                    previewFocusRequester = previewFocusRequester,
                    onPreviewKey = ::handlePreviewKey
                )
            }

            if (featuredMovies.isNotEmpty() && !showPreviewSection) {
                MoviesHomeRow(
                    movies = featuredMovies,
                    progressByStreamId = vodProgress,
                    onPlayMovie = { movie ->
                        VodPlaybackHelper.stageMovie(movie)
                        val resume = (vodProgress[movie.streamId] ?: 0L) > 5_000L
                        onPlayVod(movie.streamUrl, movie.title, resume)
                    },
                    onSeeAll = { onNavigateVod(0) }
                )
                SeriesHomeRow(
                    shows = featuredSeries,
                    onOpenSeries = { show -> onNavigateSeries(show.id) },
                    onSeeAll = { onNavigateVod(1) }
                )
            }

            HomeEpgChannelList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                gridFocusRequester = gridFocusRequester,
                onGridKey = ::handleGridKey,
                gridFocused = focusZone == EpgFocusZone.GRID,
                hScroll = hScroll,
                now = now,
                windowStart = windowStart,
                windowDurationMs = windowDurationMs,
                guideFilter = guideFilter,
                channelGroups = channelGroups,
                gridFilterFocused = focusZone == EpgFocusZone.GRID_FILTER && !showCategoryFilterMenu,
                gridFilterFocusRequester = gridFilterFocusRequester,
                onOpenCategoryFilter = {
                    focusZone = EpgFocusZone.GRID_FILTER
                    openCategoryFilterMenu()
                },
                onGridFilterKey = ::handleGridFilterKey,
                listState = listState,
                displayChannels = displayChannels,
                filteredEmptyMessage = when {
                    !showFilteredEmptyState -> null
                    guideFilter.isActive -> "No channels match your selected groups"
                    else -> "No channels in this favorites group"
                },
                programsForChannel = ::programsForChannel,
                channelScanStatuses = channelScanStatuses,
                focusChannelIndex = focusChannelIndex,
                focusProgramIndex = focusProgramIndex,
                focusOnChannelColumn = focusOnChannelColumn,
                confirmedProgramId = if (focusZone == EpgFocusZone.PREVIEW) focusedProgram?.id else null,
                scheduled = scheduled,
                timelineWidth = timelineWidth,
                scrolledAwayFromLive = scrolledAwayFromLive,
                onJumpToLive = ::scrollToLive,
                onChannelClick = ::openChannelFromTouch,
                onProgramClick = ::openProgramFromTouch,
                replayStateFor = { channel, program ->
                    viewModel.replayState(program, channels.find { it.id == channel.id } ?: channel, now)
                }
            )
        }

        if (showCategoryFilterMenu) {
            BackHandler {
                showCategoryFilterMenu = false
                focusZone = EpgFocusZone.GRID_FILTER
            }
        }

        GuideGroupFilterMenu(
            expanded = showCategoryFilterMenu,
            channelGroups = channelGroups,
            selectedGroups = guideFilter.selectedGroups,
            expandedCategories = categoryMenuExpandedCategories,
            focusedIndex = categoryMenuFocusIndex,
            onFocusedIndexChange = { categoryMenuFocusIndex = it },
            onDismiss = {
                showCategoryFilterMenu = false
                focusZone = EpgFocusZone.GRID_FILTER
            },
            onToggle = ::handleCategoryFilterMenuToggle
        )

        if (showGuideGroupPicker && channelGroups.isNotEmpty()) {
            BackHandler {
                showGuideGroupPicker = false
                viewModel.saveGuideChannelGroups(emptySet(), markConfigured = true)
            }
            GuideGroupPickerDialog(
                channelGroups = channelGroups,
                initialSelection = guideFilter.selectedGroups,
                onDismiss = {
                    showGuideGroupPicker = false
                    viewModel.saveGuideChannelGroups(emptySet(), markConfigured = true)
                },
                onConfirm = { groups ->
                    showGuideGroupPicker = false
                    viewModel.saveGuideChannelGroups(groups, markConfigured = true)
                    focusChannelIndex = 0
                }
            )
        }

        if (showFavoritePicker && favoriteGroups.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showFavoritePicker = false },
                title = { androidx.tv.material3.Text("Add to group") },
                text = {
                    Column {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(favoriteGroups.size) { idx ->
                                val group = favoriteGroups[idx]
                                GlowFocusButton(onClick = {
                                    focusedChannel?.let {
                                        viewModel.addChannelToFavoriteGroup(it.id, group.id)
                                        showFavoritePicker = false
                                    }
                                }) { androidx.tv.material3.Text(group.name) }
                            }
                        }
                    }
                },
                confirmButton = {
                    GlowFocusButton(onClick = { showFavoritePicker = false }) {
                        androidx.tv.material3.Text("Close")
                    }
                }
            )
        }

        favoriteSavedMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = EpgLayout.DetailPanelHeight + 16.dp)
                    .background(
                        EpgColors.DetailPanelBg,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                androidx.tv.material3.Text(
                    text = message,
                    color = EpgColors.TextPrimary,
                    fontFamily = com.grid.tv.ui.theme.DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }

        if (showCreateGroup) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCreateGroup = false },
                title = { androidx.tv.material3.Text("New favorite group") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { androidx.tv.material3.Text("Group name") }
                    )
                },
                confirmButton = {
                    GlowFocusButton(onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.createFavoriteGroup(newGroupName.trim())
                            newGroupName = ""
                            showCreateGroup = false
                        }
                    }) { androidx.tv.material3.Text("Create") }
                }
            )
        }

        if (showStoragePicker) {
            StorageLocationPicker(
                options = storageOptions,
                onSelect = { recordingViewModel.onStorageSelected(it, context) },
                onDismiss = { recordingViewModel.dismissStoragePicker() }
            )
        }

        precheck?.let { check ->
            RecordingPrecheckDialog(
                precheck = check,
                onQualitySelected = recordingViewModel::updatePrecheckQuality,
                onConfirm = { recordingViewModel.confirmImmediateRecording(context) },
                onDismiss = { recordingViewModel.dismissPrecheck() }
            )
        }

        if (showSearchOverlay) {
            SearchOverlay(
                query = searchQuery,
                unifiedResults = unifiedSearchResults,
                flatResults = searchResults,
                searchBarState = searchBarState,
                onQueryChange = searchViewModel::updateQuery,
                onClear = searchViewModel::clearQuery,
                onDismiss = {
                    showSearchOverlay = false
                    searchViewModel.clearQuery()
                    focusZone = EpgFocusZone.GRID
                },
                onMicClick = {
                    if (searchBarState == SearchBarState.LISTENING) {
                        searchViewModel.stopVoiceSearch()
                    } else {
                        requestVoiceSearch()
                    }
                },
                onResultSelected = { handleSearchResult(it) },
                onSuggestionSelected = { term ->
                    searchViewModel.applyTrendingOrRecent(term)
                }
            )
        }
    }
}
