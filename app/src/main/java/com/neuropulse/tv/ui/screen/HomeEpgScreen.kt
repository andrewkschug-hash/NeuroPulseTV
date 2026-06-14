package com.neuropulse.tv.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
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
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.feature.epg.EpgPlaceholderData
import com.neuropulse.tv.feature.epg.ChannelCategoryPresets
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.di.PlayerEntryPoint
import com.neuropulse.tv.di.SearchEntryPoint
import com.neuropulse.tv.domain.model.SearchBarState
import com.neuropulse.tv.domain.model.SearchInputMode
import com.neuropulse.tv.player.LivePlayerManager
import dagger.hilt.android.EntryPointAccessors
import com.neuropulse.tv.ui.component.EpgChannelCell
import com.neuropulse.tv.ui.component.EpgDetailPanel
import com.neuropulse.tv.ui.component.EpgEmptyState
import com.neuropulse.tv.ui.component.EpgLayout
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgProgramCell
import com.neuropulse.tv.ui.component.EpgJumpToLiveButton
import com.neuropulse.tv.ui.component.EpgTimelineHeader
import com.neuropulse.tv.domain.model.SearchResultItem
import com.neuropulse.tv.domain.model.SearchResultType
import com.neuropulse.tv.ui.component.CategoryFilterMenu
import com.neuropulse.tv.ui.component.ContinueWatchingRow
import com.neuropulse.tv.ui.component.MoviesHomeRow
import com.neuropulse.tv.ui.component.SeriesHomeRow
import com.neuropulse.tv.ui.component.categoryFilterForMenuIndex
import com.neuropulse.tv.ui.component.categoryFilterMenuItemCount
import com.neuropulse.tv.ui.component.currentCategoryMenuIndex
import com.neuropulse.tv.ui.component.EpgCategoryFilterChip
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.component.MiniPlayerOverlay
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.component.RecordingPrecheckDialog
import com.neuropulse.tv.ui.component.SearchOverlay
import com.neuropulse.tv.ui.component.StorageLocationPicker
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.EpgGuidePosition
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import com.neuropulse.tv.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

private enum class EpgFocusZone { TOP_BAR, CONTINUE_WATCHING, GRID_FILTER, GRID, DETAIL, MINI_PLAYER }

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
    onPlayVod: (String, String) -> Unit = { _, _ -> },
    onResumeContinueWatching: (ContinueWatchingItem) -> Unit = {},
    profileInitials: String = "?",
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
    val favoriteGroups by viewModel.favoriteGroups.collectAsStateWithLifecycle()
    val favoriteGroupFilter by viewModel.favoriteGroupFilter.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val channelGroups by viewModel.channelGroups.collectAsStateWithLifecycle()
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
        viewModel.reloadPlaybackSettings()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.reloadPlaybackSettings()
                    viewModel.setScannerForeground(true)
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.setScannerForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showEmptyState = !isInitializing && !hasConnection
    val usePlaceholder = !isInitializing && hasConnection && channels.isEmpty()
    val displayChannels = remember(
        channels,
        usePlaceholder,
        showEmptyState,
        favoriteGroupFilter,
        demoFavoriteIds,
        categoryFilter
    ) {
        when {
            showEmptyState -> emptyList()
            usePlaceholder -> {
                val all = ChannelCategoryPresets.apply(EpgPlaceholderData.channels(), categoryFilter)
                when (favoriteGroupFilter) {
                    null -> all
                    HomeEpgViewModel.FAVORITES_FILTER -> all.filter { it.id in demoFavoriteIds }
                    else -> emptyList()
                }
            }
            else -> channels
        }
    }
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

    LaunchedEffect(channels, usePlaceholder) {
        if (channels.isNotEmpty() && !usePlaceholder) {
            viewModel.tuneLastWatched(context)
        }
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
    }

    LaunchedEffect(usePlaceholder, displayChannels) {
        if (usePlaceholder && displayChannels.isNotEmpty()) {
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
        }
    }

    val liveChannelId by livePlayerManager.activeChannelIdFlow.collectAsStateWithLifecycle()
    val playbackStatus by livePlayerManager.playbackStatus.collectAsStateWithLifecycle()
    val lastPlayedChannel by viewModel.lastPlayedChannel.collectAsStateWithLifecycle()
    val channelScanStatuses by viewModel.channelScanStatuses.collectAsStateWithLifecycle()
    val guidePosition by viewModel.guidePosition.collectAsStateWithLifecycle()

    val searchQuery by searchViewModel.queryText.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()
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
    var detailExpanded by remember { mutableStateOf(false) }
    var detailActionIndex by remember { mutableIntStateOf(0) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var categoryMenuFocusIndex by remember { mutableIntStateOf(0) }

    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gridFocusRequester = remember { FocusRequester() }
    val gridFilterFocusRequester = remember { FocusRequester() }
    val topNavFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    val miniPlayerFocusRequester = remember { FocusRequester() }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var miniPlayerIdleShrunk by remember { mutableStateOf(false) }
    val timelineWidth = EpgLayout.timelineWidthMs(windowDurationMs)

    LaunchedEffect(lastInteractionMs, lastPlayedChannel) {
        miniPlayerIdleShrunk = false
        if (lastPlayedChannel == null) return@LaunchedEffect
        delay(5000)
        miniPlayerIdleShrunk = true
    }

    var didInitialScroll by remember { mutableStateOf(false) }
    var didRestoreGuide by remember { mutableStateOf(false) }

    LaunchedEffect(focusChannelIndex, displayChannels, listState.layoutInfo.visibleItemsInfo) {
        if (displayChannels.isEmpty()) return@LaunchedEffect
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
        onWatchChannel(full.id)
    }

    val focusedChannel = displayChannels.getOrNull(focusChannelIndex)
    val channelPrograms = remember(focusedChannel, displayPrograms) {
        focusedChannel?.epgId?.let { epgId ->
            displayPrograms.filter { it.channelEpgId == epgId }.sortedBy { it.startTime }
        } ?: emptyList()
    }
    val focusedProgram = if (focusOnChannelColumn) {
        channelPrograms.firstOrNull { now in it.startTime..it.endTime }
            ?: channelPrograms.firstOrNull()
    } else {
        channelPrograms.getOrNull(focusProgramIndex)
    }

    fun previewFocusedChannel() {
        if (usePlaceholder) return
        if (lastPlayedChannel != null) return
        val ch = displayChannels.getOrNull(focusChannelIndex) ?: return
        val fullChannel = channels.find { it.id == ch.id } ?: ch
        if (fullChannel.streamUrl.isBlank()) return
        val title = focusedProgram?.title ?: ch.epgId?.let { epgId ->
            displayPrograms.firstOrNull { it.channelEpgId == epgId && now in it.startTime..it.endTime }?.title
        }
        viewModel.previewChannel(context, fullChannel, title)
    }

    LaunchedEffect(focusChannelIndex, focusedProgram?.id, displayChannels.size, usePlaceholder) {
        if (usePlaceholder) return@LaunchedEffect
        previewFocusedChannel()
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
    }

    val previewStreamStatus = if (focusedChannel?.id == liveChannelId) {
        playbackStatus
    } else {
        com.neuropulse.tv.player.StreamPlaybackStatus.IDLE
    }
    val showDetailPanel = focusedChannel != null && (focusedProgram != null || channelPrograms.isEmpty())

    LaunchedEffect(liveChannelId, playbackStatus) {
        liveChannelId?.let { viewModel.reportPlaybackHealth(it, playbackStatus) }
    }

    fun programsForChannel(channel: Channel): List<Program> =
        channel.epgId?.let { epgId ->
            displayPrograms.filter { it.channelEpgId == epgId }.sortedBy { it.startTime }
        } ?: emptyList()

    fun clampProgramIndex(channelIdx: Int, programIdx: Int): Int {
        val progs = displayChannels.getOrNull(channelIdx)?.let { programsForChannel(it) } ?: emptyList()
        return programIdx.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(displayChannels.size) {
        if (displayChannels.isEmpty()) {
            focusChannelIndex = 0
            focusProgramIndex = 0
            focusOnChannelColumn = false
        } else if (focusChannelIndex > displayChannels.lastIndex) {
            focusChannelIndex = displayChannels.lastIndex
            focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
        }
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
        showSearchOverlay = false
        searchViewModel.clearQuery()
        when (result.type) {
            SearchResultType.CHANNEL -> result.channelId?.let { chId ->
                val ch = displayChannels.find { it.id == chId } ?: channels.find { it.id == chId }
                if (ch != null) {
                    if (!usePlaceholder) {
                        scope.launch {
                            livePlayerManager.tuneChannel(context, ch)
                            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
                        }
                    }
                    val idx = displayChannels.indexOfFirst { it.id == chId }
                    if (idx >= 0) {
                        focusChannelIndex = idx
                        focusOnChannelColumn = true
                        focusZone = EpgFocusZone.GRID
                    }
                    if (ch.streamUrl.isNotBlank()) {
                        viewModel.setLastPlayedChannel(ch)
                        onWatchChannel(ch.id)
                    }
                }
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
            SearchResultType.VOD -> result.vodItem?.let { onPlayVod(it.streamUrl, it.title) }
            SearchResultType.SERIES -> result.seriesShow?.let { onNavigateSeries(it.id) }
        }
    }

    fun executeDetailAction() {
        when (detailActionIndex) {
            0 -> {
                val ch = focusedChannel ?: return
                val prog = focusedProgram
                if (prog != null && prog.endTime < now && (prog.catchupUrl != null || ch.catchupDays > 0)) {
                    scope.launch {
                        val url = viewModel.buildCatchupUrl(prog, ch)
                        if (url != null) onPlayCatchup(prog.title, url) else watchChannel(ch)
                    }
                } else {
                    watchChannel(ch)
                }
            }
            1 -> {
                val ch = focusedChannel ?: return
                val isFav = if (ch.id < 0) ch.id in demoFavoriteIds else ch.isFavorite
                viewModel.toggleFavorite(ch.id, isFav)
            }
            2 -> {
                val ch = focusedChannel ?: return
                val prog = focusedProgram
                if (prog == null) {
                    recordingViewModel.startImmediateRecording(context, ch, ch.name)
                } else if (prog.startTime <= now) {
                    val duration = (prog.endTime - now).coerceAtLeast(10 * 60 * 1000)
                    recordingViewModel.startImmediateRecording(context, ch, prog.title, duration)
                } else {
                    recordingViewModel.scheduleProgram(ch, prog)
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

    fun focusGuideChannels() {
        focusZone = EpgFocusZone.GRID
        focusChannelIndex = 0
        focusOnChannelColumn = true
    }

    fun moveGuideFocusUp(from: EpgFocusZone) {
        when (from) {
            EpgFocusZone.GRID_FILTER -> {
                if (continueWatchingItems.isNotEmpty()) {
                    focusZone = EpgFocusZone.CONTINUE_WATCHING
                } else {
                    focusSearchTopBar()
                }
            }
            EpgFocusZone.CONTINUE_WATCHING -> focusSearchTopBar()
            EpgFocusZone.MINI_PLAYER -> focusSearchTopBar()
            else -> Unit
        }
    }

    fun moveGuideFocusDown(from: EpgFocusZone) {
        when (from) {
            EpgFocusZone.TOP_BAR -> {
                focusZone = if (continueWatchingItems.isNotEmpty()) {
                    EpgFocusZone.CONTINUE_WATCHING
                } else {
                    EpgFocusZone.GRID_FILTER
                }
            }
            EpgFocusZone.CONTINUE_WATCHING -> focusGuideFilter()
            EpgFocusZone.GRID_FILTER -> focusGuideChannels()
            EpgFocusZone.MINI_PLAYER -> focusGuideFilter()
            else -> Unit
        }
    }

    fun handleCategoryFilterMenuKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val menuCount = categoryFilterMenuItemCount(channelGroups)
        return when (event.key) {
            Key.DirectionUp -> {
                categoryMenuFocusIndex = (categoryMenuFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionDown -> {
                categoryMenuFocusIndex = (categoryMenuFocusIndex + 1).coerceAtMost(menuCount - 1)
                true
            }
            Key.Back, Key.Escape -> {
                showCategoryFilterMenu = false
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                viewModel.setCategoryFilter(
                    categoryFilterForMenuIndex(categoryMenuFocusIndex, channelGroups)
                )
                showCategoryFilterMenu = false
                true
            }
            else -> false
        }
    }

    fun handleGridFilterKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (showCategoryFilterMenu) return handleCategoryFilterMenuKey(event)
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
                categoryMenuFocusIndex = currentCategoryMenuIndex(categoryFilter, channelGroups)
                showCategoryFilterMenu = true
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
        if (showCategoryFilterMenu) return handleCategoryFilterMenuKey(event)
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
                focusGuideFilter()
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

    fun handleDetailKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (detailActionIndex > 0) {
                    detailActionIndex -= 1
                }
                true
            }
            Key.DirectionRight -> {
                if (detailActionIndex < 2) {
                    detailActionIndex += 1
                } else if (lastPlayedChannel != null) {
                    focusZone = EpgFocusZone.MINI_PLAYER
                }
                true
            }
            Key.DirectionUp -> {
                focusZone = EpgFocusZone.GRID
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

    fun handleMiniPlayerKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                focusZone = EpgFocusZone.GRID
                focusOnChannelColumn = false
                val progs = displayChannels.getOrNull(focusChannelIndex)
                    ?.let { programsForChannel(it) }
                    ?: emptyList()
                if (progs.isNotEmpty()) {
                    focusProgramIndex = progs.lastIndex
                }
                true
            }
            Key.DirectionUp -> {
                focusSearchTopBar()
                true
            }
            Key.DirectionDown -> {
                focusGuideFilter()
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                lastPlayedChannel?.let { watchChannel(it) }
                true
            }
            Key.Back, Key.Escape -> {
                focusZone = EpgFocusZone.GRID
                true
            }
            else -> false
        }
    }

    fun handleGridKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
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
                } else if (lastPlayedChannel != null) {
                    focusZone = EpgFocusZone.MINI_PLAYER
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
                previewFocusedChannel()
                if (focusedChannel != null) {
                    focusZone = EpgFocusZone.DETAIL
                    detailActionIndex = 0
                    detailExpanded = true
                }
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
                    .background(EpgColors.Background)
            )
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

    val miniPlayerAudioEnabled by viewModel.miniPlayerAudioEnabled.collectAsStateWithLifecycle()
    val miniPlayerChannel = lastPlayedChannel?.let { played ->
        val streamUrl = channels.find { it.id == played.id }?.streamUrl ?: played.streamUrl
        if (streamUrl.isNotBlank()) played to streamUrl else null
    }

    LaunchedEffect(
        focusZone,
        displayChannels.size,
        showDetailPanel,
        continueWatchingItems.isNotEmpty(),
        miniPlayerChannel != null
    ) {
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
            EpgFocusZone.DETAIL -> if (showDetailPanel) {
                detailFocusRequester.requestFocusSafelyAfterLayout(150)
            }
            EpgFocusZone.MINI_PLAYER -> if (miniPlayerChannel != null) {
                miniPlayerFocusRequester.requestFocusSafelyAfterLayout(150)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionMs = System.currentTimeMillis()
                    miniPlayerIdleShrunk = false
                }
                if (showCategoryFilterMenu) handleCategoryFilterMenuKey(event) else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = selectedTab,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == EpgFocusZone.TOP_BAR && topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusZone == EpgFocusZone.TOP_BAR && topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
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
                onProfileMenuDismiss = { profileMenuOpen = false },
                onTabSelected = { tab ->
                    focusZone = EpgFocusZone.TOP_BAR
                    topBarFocusIndex = GridNavTabs.indexOf(tab)
                    activateNavTab(tab)
                },
                miniPlayer = {},
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
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

            if (continueWatchingItems.isNotEmpty() || miniPlayerChannel != null) {
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
                    onContinueWatchingKey = ::handleContinueWatchingKey,
                    miniPlayerChannel = miniPlayerChannel,
                    miniPlayerFocused = focusZone == EpgFocusZone.MINI_PLAYER,
                    miniPlayerIdleShrunk = miniPlayerIdleShrunk,
                    miniPlayerAudioEnabled = miniPlayerAudioEnabled,
                    onMiniPlayerClick = ::watchChannel,
                    miniPlayerFocusRequester = miniPlayerFocusRequester,
                    onMiniPlayerKey = ::handleMiniPlayerKey
                )
            }

            if (featuredMovies.isNotEmpty()) {
                MoviesHomeRow(
                    movies = featuredMovies,
                    progressByStreamId = vodProgress,
                    onPlayMovie = { movie -> onPlayVod(movie.streamUrl, movie.title) },
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
                categoryFilter = categoryFilter,
                channelGroups = channelGroups,
                gridFilterFocused = focusZone == EpgFocusZone.GRID_FILTER,
                gridFilterFocusRequester = gridFilterFocusRequester,
                onOpenCategoryFilter = {
                    focusZone = EpgFocusZone.GRID_FILTER
                    categoryMenuFocusIndex = currentCategoryMenuIndex(categoryFilter, channelGroups)
                    showCategoryFilterMenu = true
                },
                onGridFilterKey = ::handleGridFilterKey,
                listState = listState,
                displayChannels = displayChannels,
                programsForChannel = ::programsForChannel,
                channelScanStatuses = channelScanStatuses,
                focusChannelIndex = focusChannelIndex,
                focusProgramIndex = focusProgramIndex,
                focusOnChannelColumn = focusOnChannelColumn,
                confirmedProgramId = if (focusZone == EpgFocusZone.DETAIL) focusedProgram?.id else null,
                scheduled = scheduled,
                timelineWidth = timelineWidth,
                scrolledAwayFromLive = scrolledAwayFromLive,
                onJumpToLive = ::scrollToLive
            )

            HomeEpgDetailBar(
                showDetailPanel = showDetailPanel,
                channel = focusedChannel,
                program = focusedProgram,
                now = now,
                detailFocused = focusZone == EpgFocusZone.DETAIL,
                detailActionIndex = detailActionIndex,
                onDetailActionIndexChange = { detailActionIndex = it },
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
                isFavorite = focusedChannel?.let { ch ->
                    if (ch.id < 0) ch.id in demoFavoriteIds else ch.isFavorite
                } ?: false,
                streamStatus = previewStreamStatus,
                detailFocusRequester = detailFocusRequester,
                onDetailKey = ::handleDetailKey
            )
        }

        CategoryFilterMenu(
            expanded = showCategoryFilterMenu,
            focusedIndex = categoryMenuFocusIndex,
            playlistGroups = channelGroups,
            onDismiss = { showCategoryFilterMenu = false },
            onSelect = { index ->
                viewModel.setCategoryFilter(categoryFilterForMenuIndex(index, channelGroups))
                showCategoryFilterMenu = false
            }
        )

        if (showFavoritePicker && favoriteGroups.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showFavoritePicker = false },
                title = { androidx.tv.material3.Text("Add to group") },
                text = {
                    Column {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(favoriteGroups.size) { idx ->
                                val group = favoriteGroups[idx]
                                androidx.tv.material3.Button(onClick = {
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
                    androidx.tv.material3.Button(onClick = { showFavoritePicker = false }) {
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
                    fontFamily = com.neuropulse.tv.ui.theme.DmSansFamily,
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
                    androidx.tv.material3.Button(onClick = {
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
                results = searchResults,
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
                onResultSelected = { handleSearchResult(it) }
            )
        }
    }
}
