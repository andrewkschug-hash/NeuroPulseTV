package com.grid.tv.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grid.tv.data.db.entity.ScheduledRecordingEntity
import com.grid.tv.domain.epg.EpgProgramReplayState
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchInputMode
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchUiState
import com.grid.tv.di.SearchEntryPoint
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.recording.RecordingHealth
import com.grid.tv.feature.recording.RecordingStatus
import com.grid.tv.player.StreamPlaybackStatus
import com.grid.tv.ui.component.ContinueWatchingRow
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgCategoryFilterChip
import com.grid.tv.ui.component.EpgChannelCell
import com.grid.tv.ui.component.EpgEmptyState
import com.grid.tv.ui.component.HomeScreenSkeletonOverlay
import com.grid.tv.ui.component.EpgJumpToLiveButton
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNowLine
import com.grid.tv.ui.component.EpgPreviewSection
import com.grid.tv.ui.component.EpgNoInformationCell
import com.grid.tv.ui.component.EpgProgramCell
import com.grid.tv.ui.component.EpgTimelineHeader
import com.grid.tv.ui.component.EpgGuideHeader
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.GuideNavDrawer
import com.grid.tv.ui.component.GuideGroupFilterMenu
import com.grid.tv.ui.component.GuideGroupPickerDialog
import com.grid.tv.ui.component.RecordingPrecheckDialog
import com.grid.tv.ui.component.SearchOverlay
import com.grid.tv.ui.component.StorageLocationPicker
import com.grid.tv.ui.component.EpgNowTicker
import com.grid.tv.ui.component.rememberEpgNowMillis
import com.grid.tv.ui.component.formatEpgDay
import com.grid.tv.ui.component.formatLastChecked
import com.grid.tv.ui.platform.LocalDeviceFormFactor
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.util.PerformanceAudit
import com.grid.tv.ui.compose.ProgramsForChannel
import com.grid.tv.ui.compose.rememberEpgGuideRowCache
import com.grid.tv.util.quitAppToHome
import com.grid.tv.util.quitAppToHome
import dagger.hilt.android.EntryPointAccessors

@Composable
internal fun HomeEpgScreenLoadingGate(
    isInitializing: Boolean,
    guideSettingsLoaded: Boolean,
    showEmptyState: Boolean,
    onNavigateSettings: () -> Unit
): Boolean {
    if (isInitializing || !guideSettingsLoaded) {
        HomeScreenSkeletonOverlay()
        return true
    }
    if (showEmptyState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EpgColors.Background),
            contentAlignment = Alignment.Center
        ) {
            EpgEmptyState(onAddPlaylist = onNavigateSettings)
        }
        return true
    }
    return false
}

@Composable
internal fun rememberDebouncedChannelScanStatuses(
    viewModel: HomeEpgViewModel
): Map<Long, ChannelScanSnapshot> {
    val statuses by viewModel.debouncedChannelScanStatuses.collectAsStateWithLifecycle()
    return statuses
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeEpgScreenMainColumn(
    modifier: Modifier = Modifier,
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    deps: HomeEpgGuideDeps,
    context: Context,
    profileInitials: String,
    profileAvatarColor: String,
    profileDisplayName: String?,
    profileAccessMessage: String?,
    recordingViewModel: RecordingViewModel,
    onNavigateRecordings: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateSettings: () -> Unit,
    livePlayerManager: com.grid.tv.player.LivePlayerManager,
    previewSurfaceAttached: Boolean,
    channelGroups: List<String>,
    viewModel: HomeEpgViewModel,
    timelineWidth: Dp,
    scrolledAwayFromLive: Boolean,
    showFilteredEmptyState: Boolean,
    hScroll: ScrollState,
    listState: LazyListState,
) {
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val recordingHealth by recordingViewModel.recordingHealth.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val showGroupFilter = false
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (ui.guideSubScreen != null) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                }
            )
    ) {
        EpgGuideHeader(
            isRecording = isRecording,
            activeRecordingTitle = activeRecordingTitle,
            recordingHealth = recordingHealth ?: RecordingHealth.RECORDING,
            onRecordingIndicatorClick = onNavigateRecordings,
        )

        if (profileAccessMessage != null) {
            androidx.tv.material3.Text(
                text = profileAccessMessage,
                color = androidx.compose.ui.graphics.Color(0xFFFFB020),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (deps.showPreviewSection && deps.previewChannel != null) {
            HomeEpgPreviewBlock(
                deps = deps,
                ui = ui,
                controller = controller,
                livePlayerManager = livePlayerManager,
                context = context,
                previewSurfaceAttached = previewSurfaceAttached,
                showGroupFilter = showGroupFilter,
                viewModel = viewModel
            )
        }

        val channelScanStatuses = rememberDebouncedChannelScanStatuses(viewModel)
        HomeEpgChannelList(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            gridFocusRequester = deps.gridFocusRequester,
            gridFocused = ui.focusZone == EpgFocusZone.GRID,
            displayChannelsEmpty = deps.displayChannels.isEmpty(),
            hScroll = hScroll,
            windowStart = deps.windowStart,
            windowDurationMs = deps.windowDurationMs,
            guideFilter = deps.guideFilter,
            channelGroups = channelGroups,
            showGroupFilter = showGroupFilter,
            gridFilterFocusRequester = deps.gridFilterFocusRequester,
            onOpenCategoryFilter = { controller.openNavDrawer() },
            onGridFocused = {
                if (ui.focusZone != EpgFocusZone.PREVIEW) {
                    ui.focusZone = EpgFocusZone.GRID
                }
            },
            listState = listState,
            displayChannels = deps.displayChannels,
            filteredEmptyMessage = when {
                !showFilteredEmptyState -> null
                deps.guideFilter.isActive -> "No channels match your selected groups"
                else -> "No channels in this favorites group"
            },
            programsForChannel = remember(controller) {
                { channel -> controller.programsForChannel(channel) }
            },
            channelScanStatuses = channelScanStatuses,
            focusChannelIndex = ui.focusChannelIndex,
            focusProgramIndex = ui.focusProgramIndex,
            focusOnChannelColumn = ui.focusOnChannelColumn,
            confirmedProgramId = if (ui.focusZone == EpgFocusZone.PREVIEW) deps.focusedProgram?.id else null,
            scheduled = scheduled,
            timelineWidth = timelineWidth,
            scrolledAwayFromLive = scrolledAwayFromLive,
            onJumpToLive = controller::scrollToLive,
            onChannelClick = controller::openChannelFromTouch,
            onProgramClick = controller::openProgramFromTouch,
            replayStateFor = remember(deps.channels, deps.viewModel) {
                { channel: Channel, program: Program ->
                    deps.viewModel.replayState(
                        program,
                        deps.channels.find { it.id == channel.id } ?: channel,
                        System.currentTimeMillis()
                    )
                }
            }
        )
    }
    }
}

@Composable
internal fun HomeEpgSearchScreenHost(
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    searchViewModel: SearchViewModel,
    context: Context
) {
    val searchUiState by searchViewModel.searchUiState.collectAsStateWithLifecycle()
    val searchBarState by searchViewModel.searchBarState.collectAsStateWithLifecycle()
    GuideSearchScreen(
        searchUiState = searchUiState,
        searchBarState = searchBarState,
        onQueryChange = searchViewModel::updateQuery,
        onClear = searchViewModel::clearQuery,
        onMicClick = { /* voice handled by overlay */ },
        onResultSelected = controller::handleSearchResult,
        onSuggestionSelected = searchViewModel::applyTrendingOrRecent,
        onClearHistory = searchViewModel::clearRecentHistory,
        onBack = {
            ui.guideSubScreen = null
            searchViewModel.clearQuery()
            controller.focusEpgZone(EpgFocusZone.GRID)
        }
    )
}

@Composable
internal fun HomeEpgScreenOverlaysOnly(
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    deps: HomeEpgGuideDeps,
    channelGroups: List<String>,
    groupChannelCounts: Map<String, Int>,
    favoriteGroups: List<FavoriteGroup>,
    favoriteSavedMessage: String?,
    recordingViewModel: RecordingViewModel,
) {
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val searchViewModel = deps.searchViewModel
    val searchUiState by searchViewModel.searchUiState.collectAsStateWithLifecycle()
    val searchBarState by searchViewModel.searchBarState.collectAsStateWithLifecycle()

    HomeEpgScreenOverlays(
        ui = ui,
        controller = controller,
        deps = deps,
        context = deps.context,
        channelGroups = channelGroups,
        groupChannelCounts = groupChannelCounts,
        favoriteGroups = favoriteGroups,
        favoriteSavedMessage = favoriteSavedMessage,
        recordingViewModel = recordingViewModel,
        showStoragePicker = showStoragePicker,
        storageOptions = storageOptions,
        precheck = precheck,
        searchUiState = searchUiState,
        searchBarState = searchBarState,
        searchViewModel = searchViewModel,
        onRequestVoiceSearch = {},
        includeSearchOverlay = false,
        includeCategoryMenu = false,
        includeGroupPicker = false,
    )
}

@Composable
internal fun HomeEpgPreviewBlock(
    deps: HomeEpgGuideDeps,
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    livePlayerManager: com.grid.tv.player.LivePlayerManager,
    context: Context,
    previewSurfaceAttached: Boolean,
    showGroupFilter: Boolean,
    viewModel: HomeEpgViewModel,
) {
    val previewChannel = deps.previewChannel ?: return
    val previewPlayer = rememberPreviewPlaybackPlayer(
        livePlayerManager = livePlayerManager,
        context = context,
        previewChannelId = previewChannel.id,
        guidePreviewEnabled = true
    )
    val previewStreamStatus = rememberPreviewStreamStatus(
        livePlayerManager = livePlayerManager,
        previewChannelId = previewChannel.id
    )
    val initialPreviewFavorite = if (previewChannel.id < 0) {
        previewChannel.id in deps.demoFavoriteIds
    } else {
        previewChannel.isFavorite
    }
    val previewIsFavorite by viewModel
        .observeChannelFavorite(previewChannel.id)
        .collectAsStateWithLifecycle(initialValue = initialPreviewFavorite)
    HomeEpgPreviewSection(
        channel = previewChannel,
        program = deps.previewProgram,
        nextProgram = deps.previewNextProgram,
        player = previewPlayer,
        streamStatus = previewStreamStatus,
        detailActionIndex = ui.detailActionIndex,
        previewFocused = ui.focusZone == EpgFocusZone.PREVIEW,
        attachSurface = previewSurfaceAttached,
        isFavorite = previewIsFavorite,
        primaryActionLabel = controller.primaryActionLabel(deps.previewChannel, deps.previewProgram),
        onWatch = {
            ui.detailActionIndex = 0
            controller.executeDetailAction()
        },
        onFavorite = {
            ui.detailActionIndex = 1
            controller.executeDetailAction()
        },
        onRecord = {
            ui.detailActionIndex = 2
            controller.executeDetailAction()
        },
        previewFocusRequester = deps.previewFocusRequester,
        onFocused = { ui.focusZone = EpgFocusZone.PREVIEW },
    )
}

@Composable
internal fun HomeEpgSearchOverlayHost(
    modifier: Modifier = Modifier,
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    deps: HomeEpgGuideDeps,
    context: Context,
    channelGroups: List<String>,
    groupChannelCounts: Map<String, Int>,
    favoriteGroups: List<FavoriteGroup>,
    favoriteSavedMessage: String?,
    recordingViewModel: RecordingViewModel,
    searchViewModel: SearchViewModel,
) {
    val searchUiState by searchViewModel.searchUiState.collectAsStateWithLifecycle()
    val searchBarState by searchViewModel.searchBarState.collectAsStateWithLifecycle()
    val preferredSearchInput by searchViewModel.preferredInputMode.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()

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

    LaunchedEffect(ui.showSearchOverlay, preferredSearchInput) {
        if (ui.showSearchOverlay && preferredSearchInput == SearchInputMode.VOICE) {
            requestVoiceSearch()
        } else if (!ui.showSearchOverlay) {
            searchViewModel.stopVoiceSearch()
        }
    }

    HomeEpgScreenOverlays(
        modifier = modifier,
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
        searchUiState = searchUiState,
        searchBarState = searchBarState,
        searchViewModel = searchViewModel,
        onRequestVoiceSearch = requestVoiceSearch,
    )
}

@Composable
internal fun HomeEpgScreenOverlays(
    modifier: Modifier = Modifier,
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    deps: HomeEpgGuideDeps,
    context: Context,
    channelGroups: List<String>,
    groupChannelCounts: Map<String, Int>,
    favoriteGroups: List<com.grid.tv.domain.model.FavoriteGroup>,
    favoriteSavedMessage: String?,
    recordingViewModel: RecordingViewModel,
    showStoragePicker: Boolean,
    storageOptions: List<com.grid.tv.feature.recording.StorageOption>,
    precheck: com.grid.tv.feature.recording.RecordingPrecheck?,
    searchUiState: SearchUiState,
    searchBarState: SearchBarState,
    searchViewModel: SearchViewModel,
    onRequestVoiceSearch: () -> Unit,
    includeSearchOverlay: Boolean = true,
    includeCategoryMenu: Boolean = true,
    includeGroupPicker: Boolean = true,
) {
    Box(modifier = modifier) {
        if (includeCategoryMenu && ui.showCategoryFilterMenu) {
            BackHandler {
                ui.showCategoryFilterMenu = false
                controller.focusEpgZone(EpgFocusZone.GRID)
            }
        }

        if (includeCategoryMenu) {
        GuideGroupFilterMenu(
            expanded = ui.showCategoryFilterMenu,
            channelGroups = channelGroups,
            selectedGroups = deps.guideFilter.selectedGroups,
            expandedCategories = ui.categoryMenuExpandedCategories,
            groupChannelCounts = groupChannelCounts,
            focusedIndex = ui.categoryMenuFocusIndex,
            onFocusedIndexChange = { ui.categoryMenuFocusIndex = it },
            onDismiss = {
                ui.showCategoryFilterMenu = false
                controller.focusEpgZone(EpgFocusZone.GRID)
            },
            onToggle = controller::handleCategoryFilterMenuToggle
        )
        }

        if (includeGroupPicker && ui.showGuideGroupPicker && channelGroups.isNotEmpty()) {
            GuideGroupPickerDialog(
                channelGroups = channelGroups,
                initialSelection = deps.guideFilter.selectedGroups,
                groupChannelCounts = groupChannelCounts,
                allowDismiss = false,
                subtitle = "Pick the groups you want in the live guide. Save to continue.",
                onDismiss = {},
                onConfirm = { groups ->
                    deps.viewModel.saveGuideChannelGroups(groups, markConfigured = true)
                    ui.focusChannelIndex = 0
                    ui.hasRequestedInitialGridFocus = false
                }
            )
        }

        if (ui.showFavoritePicker && favoriteGroups.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { ui.showFavoritePicker = false },
                title = { androidx.tv.material3.Text("Add to group") },
                text = {
                    Column {
                        LazyColumn {
                            items(favoriteGroups.size) { idx ->
                                val group = favoriteGroups[idx]
                                GlowFocusButton(onClick = {
                                    deps.focusedChannel?.let {
                                        deps.viewModel.addChannelToFavoriteGroup(it.id, group.id)
                                        ui.showFavoritePicker = false
                                    }
                                }) { androidx.tv.material3.Text(group.name) }
                            }
                        }
                    }
                },
                confirmButton = {
                    GlowFocusButton(onClick = { ui.showFavoritePicker = false }) {
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
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                androidx.tv.material3.Text(
                    text = message,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }

        if (ui.showCreateGroup) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { ui.showCreateGroup = false },
                title = { androidx.tv.material3.Text("New favorite group") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = ui.newGroupName,
                        onValueChange = { ui.newGroupName = it },
                        label = { androidx.tv.material3.Text("Group name") }
                    )
                },
                confirmButton = {
                    GlowFocusButton(onClick = {
                        if (ui.newGroupName.isNotBlank()) {
                            deps.viewModel.createFavoriteGroup(ui.newGroupName.trim())
                            ui.newGroupName = ""
                            ui.showCreateGroup = false
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

        if (includeSearchOverlay && ui.showSearchOverlay) {
            BackHandler {
                ui.showSearchOverlay = false
                searchViewModel.clearQuery()
                ui.focusZone = EpgFocusZone.GRID
            }
            SearchOverlay(
                searchUiState = searchUiState,
                searchBarState = searchBarState,
                onQueryChange = searchViewModel::updateQuery,
                onClear = searchViewModel::clearQuery,
                onDismiss = {
                    ui.showSearchOverlay = false
                    searchViewModel.clearQuery()
                    ui.focusZone = EpgFocusZone.GRID
                },
                onMicClick = {
                    if (searchBarState == SearchBarState.LISTENING) {
                        searchViewModel.stopVoiceSearch()
                    } else {
                        onRequestVoiceSearch()
                    }
                },
                onResultSelected = controller::handleSearchResult,
                onSuggestionSelected = { term ->
                    searchViewModel.applyTrendingOrRecent(term)
                },
                onClearHistory = searchViewModel::clearRecentHistory
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeEpgContinueWatchingRow(
    continueWatchingItems: List<ContinueWatchingItem>,
    focusedContinueIndex: Int,
    continueWatchingFocused: Boolean,
    onContinueSelect: (ContinueWatchingItem) -> Unit,
    continueWatchingFocusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    if (continueWatchingItems.isEmpty()) return

    ContinueWatchingRow(
        items = continueWatchingItems,
        focusedIndex = focusedContinueIndex,
        rowFocused = continueWatchingFocused,
        onSelect = onContinueSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(continueWatchingFocusRequester)
            .focusProperties { canFocus = continueWatchingFocused }
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
    )
}

@Composable
internal fun HomeEpgPreviewSection(
    channel: Channel?,
    program: Program?,
    nextProgram: Program? = null,
    player: androidx.media3.exoplayer.ExoPlayer?,
    streamStatus: StreamPlaybackStatus?,
    detailActionIndex: Int,
    previewFocused: Boolean,
    attachSurface: Boolean,
    isFavorite: Boolean,
    primaryActionLabel: String = "Watch Live",
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    previewFocusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    val ch = channel ?: return

    EpgPreviewSection(
        channel = ch,
        program = program,
        nextProgram = nextProgram,
        player = player,
        streamStatus = streamStatus,
        detailActionFocused = detailActionIndex,
        isFavorite = isFavorite,
        previewFocused = previewFocused,
        attachSurface = attachSurface,
        primaryActionLabel = primaryActionLabel,
        onWatch = onWatch,
        onFavorite = onFavorite,
        onRecord = onRecord,
        modifier = Modifier
            .focusRequester(previewFocusRequester)
            .focusProperties { canFocus = previewFocused }
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeEpgChannelList(
    modifier: Modifier = Modifier,
    gridFocusRequester: FocusRequester,
    gridFocused: Boolean,
    displayChannelsEmpty: Boolean,
    hScroll: androidx.compose.foundation.ScrollState,
    windowStart: Long,
    windowDurationMs: Long,
    guideFilter: GuideChannelFilter,
    channelGroups: List<String>,
    showGroupFilter: Boolean,
    gridFilterFocusRequester: FocusRequester,
    onOpenCategoryFilter: () -> Unit,
    onGridFocused: () -> Unit,
    listState: LazyListState,
    displayChannels: List<Channel>,
    filteredEmptyMessage: String? = null,
    programsForChannel: ProgramsForChannel,
    channelScanStatuses: Map<Long, ChannelScanSnapshot>,
    focusChannelIndex: Int,
    focusProgramIndex: Int,
    focusOnChannelColumn: Boolean,
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    timelineWidth: Dp,
    scrolledAwayFromLive: Boolean,
    onJumpToLive: () -> Unit,
    onChannelClick: (Int, Channel) -> Unit = { _, _ -> },
    onProgramClick: (Int, Int, Program) -> Unit = { _, _, _ -> },
    replayStateFor: (Channel, Program) -> EpgProgramReplayState = { _, _ ->
        EpgProgramReplayState(0, com.grid.tv.ui.component.ProgramTimeState.FUTURE, com.grid.tv.domain.epg.EpgProgramAction.NONE, false, null)
    }
) {
    if (PerformanceAudit.ENABLED) {
        SideEffect {
            PerformanceAudit.logGridSectionRecomposition("HomeEpgChannelList")
        }
    }
    val gridNow = rememberEpgNowMillis(EpgNowTicker.GRID_INTERVAL_MS)
    val touchGesturesEnabled = LocalDeviceFormFactor.current.enableTouchGestures
    val rowCache = rememberEpgGuideRowCache(
        displayChannels = displayChannels,
        windowStart = windowStart,
        windowDurationMs = windowDurationMs,
        channelScanStatuses = channelScanStatuses,
        gridNow = gridNow,
        programsForChannel = programsForChannel,
    )
    Box(
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(EpgLayout.ChannelColumnWidth)
                        .height(EpgLayout.TimelineHeaderHeight)
                        .background(EpgColors.ChannelColumnBg)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.tv.material3.Text(
                            text = formatEpgDay(gridNow),
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 12.sp
                        )
                        if (showGroupFilter) {
                            EpgCategoryFilterChip(
                                label = guideFilter.label,
                                active = guideFilter.isActive,
                                focused = false,
                                headerStyle = true,
                                onClick = onOpenCategoryFilter,
                                modifier = Modifier.focusRequester(gridFilterFocusRequester)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll, enabled = touchGesturesEnabled)
                ) {
                    EpgTimelineHeader(
                        windowStart = windowStart,
                        windowDurationMs = windowDurationMs
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(gridFocusRequester)
                    .focusProperties { canFocus = !displayChannelsEmpty && gridFocused }
                    .then(if (!displayChannelsEmpty) Modifier.focusable() else Modifier)
                    .onFocusChanged { if (it.isFocused) onGridFocused() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .heightIn(min = EpgLayout.GuideChannelListMinHeight)
                ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = displayChannels.size,
                                key = { index -> displayChannels[index].id },
                            ) { index ->
                                val channel = displayChannels[index]
                                EpgGuideChannelRow(
                                    channel = channel,
                                    index = index,
                                    programs = rowCache.programsByChannelId[channel.id].orEmpty(),
                                    gridFocused = gridFocused,
                                    focusChannelIndex = focusChannelIndex,
                                    focusProgramIndex = focusProgramIndex,
                                    focusOnChannelColumn = focusOnChannelColumn,
                                    windowStart = windowStart,
                                    windowDurationMs = windowDurationMs,
                                    gridNow = gridNow,
                                    scanStatus = rowCache.scanStatusByChannelId[channel.id],
                                    lastCheckedLabel = rowCache.scanLabelByChannelId[channel.id],
                                    displayChannelsLastIndex = rowCache.lastChannelIndex,
                                    confirmedProgramId = confirmedProgramId,
                                    scheduled = scheduled,
                                    hScroll = hScroll,
                                    touchGesturesEnabled = touchGesturesEnabled,
                                    timelineWidth = timelineWidth,
                                    onChannelClick = onChannelClick,
                                    onProgramClick = onProgramClick,
                                    replayStateFor = replayStateFor
                                )
                            }
                        }

                        if (displayChannels.isEmpty() && filteredEmptyMessage != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.tv.material3.Text(
                                    text = filteredEmptyMessage,
                                    color = EpgColors.TextSecondary,
                                    fontFamily = DmSansFamily,
                                    fontSize = 14.sp
                                )
                            }
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = EpgLayout.ChannelColumnWidth)
                    ) {
                        EpgNowLine(
                            windowStart = windowStart,
                            windowDurationMs = windowDurationMs,
                            scrollOffsetPx = hScroll.value,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                EpgJumpToLiveButton(
                    visible = scrolledAwayFromLive,
                    onClick = onJumpToLive,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpgGuideChannelRow(
    channel: Channel,
    index: Int,
    programs: List<Program>,
    gridFocused: Boolean,
    focusChannelIndex: Int,
    focusProgramIndex: Int,
    focusOnChannelColumn: Boolean,
    windowStart: Long,
    windowDurationMs: Long,
    gridNow: Long,
    scanStatus: com.grid.tv.domain.model.ChannelScanStatus?,
    lastCheckedLabel: String?,
    displayChannelsLastIndex: Int,
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    hScroll: ScrollState,
    touchGesturesEnabled: Boolean,
    timelineWidth: Dp,
    onChannelClick: (Int, Channel) -> Unit,
    onProgramClick: (Int, Int, Program) -> Unit,
    replayStateFor: (Channel, Program) -> EpgProgramReplayState
) {
    val isActiveRow = gridFocused && index == focusChannelIndex
    val isChannelCellFocused = isActiveRow && focusOnChannelColumn
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EpgLayout.RowHeight)
            .height(EpgLayout.RowHeight)
            .focusProperties { canFocus = false },
    ) {
        EpgChannelCell(
            channel = channel,
            isFocused = isChannelCellFocused,
            isRowHighlighted = isActiveRow,
            showBottomSeparator = index < displayChannelsLastIndex,
            scanStatus = scanStatus,
            lastCheckedLabel = lastCheckedLabel,
            onClick = if (touchGesturesEnabled) {
                { onChannelClick(index, channel) }
            } else {
                null
            },
            modifier = Modifier.width(EpgLayout.ChannelColumnWidth)
        )
        EpgChannelTimelineRow(
            channel = channel,
            programs = programs,
            windowStart = windowStart,
            windowDurationMs = windowDurationMs,
            now = gridNow,
            channelIndex = index,
            focusChannelIndex = focusChannelIndex,
            focusProgramIndex = focusProgramIndex,
            focusOnChannelColumn = focusOnChannelColumn,
            gridFocused = gridFocused,
            isRowHighlighted = isActiveRow,
            confirmedProgramId = confirmedProgramId,
            scheduled = scheduled,
            hScrollModifier = Modifier.horizontalScroll(
                hScroll,
                enabled = touchGesturesEnabled
            ),
            timelineWidth = timelineWidth,
            touchGesturesEnabled = touchGesturesEnabled,
            onProgramClick = onProgramClick,
            replayStateFor = replayStateFor
        )
    }
}

@Composable
private fun EpgChannelTimelineRow(
    channel: Channel,
    programs: List<Program>,
    windowStart: Long,
    windowDurationMs: Long,
    now: Long,
    channelIndex: Int,
    focusChannelIndex: Int,
    focusProgramIndex: Int,
    focusOnChannelColumn: Boolean,
    gridFocused: Boolean,
    isRowHighlighted: Boolean,
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    hScrollModifier: Modifier,
    timelineWidth: Dp,
    touchGesturesEnabled: Boolean = false,
    onProgramClick: (Int, Int, Program) -> Unit = { _, _, _ -> },
    replayStateFor: (Channel, Program) -> EpgProgramReplayState
) {
    val timelineBg = if (isRowHighlighted) Color.Transparent else EpgColors.GridBg
    Box(
        modifier = hScrollModifier
            .width(timelineWidth)
            .height(EpgLayout.RowHeight)
            .background(timelineBg)
    ) {
        if (programs.isEmpty()) {
            val isFocused = gridFocused &&
                channelIndex == focusChannelIndex &&
                !focusOnChannelColumn &&
                focusProgramIndex == 0
            EpgNoInformationCell(
                width = timelineWidth - EpgLayout.CellGap,
                isFocused = isFocused,
                windowDurationMs = windowDurationMs,
                modifier = Modifier.offset(x = 0.dp)
            )
        } else {
            programs.forEachIndexed { programIndex, program ->
            val width = EpgLayout.widthForProgram(
                start = program.startTime,
                end = program.endTime,
                windowDurationMs = windowDurationMs
            ) - EpgLayout.CellGap
            val offset = EpgLayout.offsetForTime(program.startTime, windowStart, windowDurationMs)
            val isFocused = gridFocused &&
                channelIndex == focusChannelIndex &&
                !focusOnChannelColumn &&
                programIndex == focusProgramIndex
            val isSelected = confirmedProgramId == program.id
            val hasRec = scheduled.any {
                it.channelId == channel.id &&
                    it.programTitle == program.title &&
                    it.startTime == program.startTime &&
                    it.status != RecordingStatus.FAILED.name
            }
            val title = buildString {
                if (hasRec) append("REC ")
                append(program.title)
            }
            val replayState = replayStateFor(channel, program)

            EpgProgramCell(
                program = program.copy(title = title),
                width = width,
                now = now,
                isFocused = isFocused,
                isSelected = isSelected,
                windowDurationMs = windowDurationMs,
                windowStart = windowStart,
                canReplay = replayState.canReplay,
                isFuture = replayState.timeState == com.grid.tv.ui.component.ProgramTimeState.FUTURE,
                onClick = if (touchGesturesEnabled) {
                    { onProgramClick(channelIndex, programIndex, program) }
                } else {
                    null
                },
                modifier = Modifier.offset(x = offset)
            )
        }
        }
    }
}
