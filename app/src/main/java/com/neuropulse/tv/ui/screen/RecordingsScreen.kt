package com.neuropulse.tv.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.di.PlayerEntryPoint
import com.neuropulse.tv.feature.recording.RecordingCountdown
import com.neuropulse.tv.feature.recording.RecordingSort
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.feature.recording.StorageFormat
import com.neuropulse.tv.player.LivePlayerManager
import com.neuropulse.tv.ui.component.EpgChipFilterBar
import com.neuropulse.tv.ui.component.EpgListEmptyState
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.component.MiniNowPlayingPlayer
import com.neuropulse.tv.ui.component.RecordingsDetailPanel
import com.neuropulse.tv.ui.component.RecordingsListRow
import com.neuropulse.tv.ui.component.formatEpgTime
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RecFocusZone { TOP_BAR, LIST, DETAIL }

private val TopBarProfileIndex get() = GridNavTabs.size
private val TopBarMiniIndex get() = GridNavTabs.size + 1

private sealed class RecordingRow {
    abstract val id: Long
    abstract val title: String
    abstract val subtitle: String
    abstract val badge: String?
    abstract val thumbnailPath: String?

    data class Scheduled(val item: ScheduledRecordingEntity, val now: Long) : RecordingRow() {
        override val id = item.id
        override val title = item.programTitle
        override val subtitle = "${item.channelName} · ${formatRecordedDate(item.startTime)}"
        override val badge = when (item.status) {
            RecordingStatus.RECORDING.name -> "● REC"
            else -> RecordingCountdown.formatUntilStart(item.startTime, now)
        }
        override val thumbnailPath: String? = null
    }

    data class Saved(val item: RecordedMediaEntity) : RecordingRow() {
        override val id = item.id
        override val title = item.programTitle
        override val subtitle = buildString {
            append(item.channelName)
            append(" · ")
            append(formatRecordedDate(item.recordedAt))
            append(" · ")
            append((item.durationMs / 60_000).coerceAtLeast(1))
            append(" min · ")
            append(StorageFormat.formatFileSize(item.fileSizeBytes))
        }
        override val badge = null
        override val thumbnailPath = item.thumbnailPath
    }
}

@Composable
fun RecordingsScreen(
    profileInitials: String = "?",
    onNavigateHome: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onWatchChannel: (Long) -> Unit = {},
    onPlayRecording: (String, String) -> Unit = { _, _ -> },
    viewModel: RecordingViewModel = hiltViewModel(),
    homeViewModel: HomeEpgViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sleepTimer = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .sleepTimerController()
    }
    val sleepRemaining by sleepTimer.remainingSec.collectAsStateWithLifecycle()
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val isRecordingActive by viewModel.isRecordingActive.collectAsStateWithLifecycle()
    val livePlayerManager = homeViewModel.livePlayerManager

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(RecFocusZone.LIST) }
    var topBarRow by remember { mutableIntStateOf(0) }
    var topBarFocusIndex by remember { mutableIntStateOf(2) }
    var tabFocusIndex by remember { mutableIntStateOf(0) }
    var sortFocusIndex by remember { mutableIntStateOf(0) }
    var listFocusIndex by remember { mutableIntStateOf(0) }
    var detailActionIndex by remember { mutableIntStateOf(0) }

    var deleteScheduledId by remember { mutableStateOf<Long?>(null) }
    var deleteMedia by remember { mutableStateOf<RecordedMediaEntity?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topNavFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }

    val upcoming = remember(scheduled) {
        scheduled.filter {
            it.status == RecordingStatus.SCHEDULED.name || it.status == RecordingStatus.RECORDING.name
        }
    }
    val rows: List<RecordingRow> = remember(tab, upcoming, recorded, now) {
        if (tab == 0) upcoming.map { RecordingRow.Scheduled(it, now) }
        else recorded.map { RecordingRow.Saved(it) }
    }
    val sortLabels = listOf("Date", "Channel", "Duration", "Size")
    val sortValues = listOf(RecordingSort.DATE, RecordingSort.CHANNEL, RecordingSort.DURATION, RecordingSort.FILE_SIZE)
    val activeSortIndex = sortValues.indexOf(sort).coerceAtLeast(0)
    val selectedRow = rows.getOrNull(listFocusIndex)

    val channels by homeViewModel.channels.collectAsStateWithLifecycle()
    val continueWatching by homeViewModel.continueWatching.collectAsStateWithLifecycle()
    val liveChannelId = livePlayerManager.activeChannelId()
    val watchingChannel = channels.find { it.id == liveChannelId } ?: continueWatching.firstOrNull()

    LaunchedEffect(focusZone, topBarRow) {
        when (focusZone) {
            RecFocusZone.TOP_BAR -> topNavFocusRequester.requestFocus()
            RecFocusZone.LIST -> listFocusRequester.requestFocus()
            RecFocusZone.DETAIL -> detailFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(rows.size, tab) {
        if (listFocusIndex > rows.lastIndex) listFocusIndex = rows.lastIndex.coerceAtLeast(0)
    }

    fun activateNavTab(tabItem: EpgNavTab) {
        when (tabItem) {
            EpgNavTab.Home -> onNavigateHome()
            EpgNavTab.Recordings -> Unit
            EpgNavTab.Settings -> onNavigateSettings()
            EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Profile -> onNavigateProfile()
        }
    }

    fun applyTab(index: Int) {
        tab = index
        tabFocusIndex = index
        listFocusIndex = 0
    }

    fun applySort(index: Int) {
        sortFocusIndex = index
        viewModel.setSort(sortValues[index])
    }

    fun detailActions(): List<String> = when (selectedRow) {
        is RecordingRow.Scheduled -> listOf("✕ Cancel", "ℹ Info")
        is RecordingRow.Saved -> listOf("▶ Play", "✕ Delete", "ℹ Info")
        null -> emptyList()
    }

    fun executeDetailAction() {
        when (val row = selectedRow) {
            is RecordingRow.Scheduled -> when (detailActionIndex) {
                0 -> deleteScheduledId = row.item.id
            }
            is RecordingRow.Saved -> when (detailActionIndex) {
                0 -> onPlayRecording(row.item.programTitle, Uri.fromFile(File(row.item.filePath)).toString())
                1 -> deleteMedia = row.item
            }
            null -> Unit
        }
    }

    fun handleTopBarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                when (topBarRow) {
                    1 -> tabFocusIndex = (tabFocusIndex - 1).coerceAtLeast(0)
                    2 -> sortFocusIndex = (sortFocusIndex - 1).coerceAtLeast(0)
                    else -> topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                }
                true
            }
            Key.DirectionRight -> {
                when (topBarRow) {
                    1 -> tabFocusIndex = (tabFocusIndex + 1).coerceAtMost(1)
                    2 -> sortFocusIndex = (sortFocusIndex + 1).coerceAtMost(sortLabels.lastIndex)
                    else -> topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarMiniIndex)
                }
                true
            }
            Key.DirectionDown -> {
                when (topBarRow) {
                    0 -> {
                        topBarRow = 1
                        tabFocusIndex = tab
                    }
                    1 -> {
                        if (tab == 1) {
                            topBarRow = 2
                            sortFocusIndex = activeSortIndex
                        } else {
                            focusZone = RecFocusZone.LIST
                            topBarRow = 0
                        }
                    }
                    else -> {
                        focusZone = RecFocusZone.LIST
                        topBarRow = 0
                    }
                }
                true
            }
            Key.DirectionUp -> when (topBarRow) {
                2 -> { topBarRow = 1; true }
                1 -> { topBarRow = 0; true }
                else -> false
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (topBarRow) {
                    1 -> applyTab(tabFocusIndex)
                    2 -> applySort(sortFocusIndex)
                    else -> when (topBarFocusIndex) {
                        in GridNavTabs.indices -> activateNavTab(GridNavTabs[topBarFocusIndex])
                        TopBarProfileIndex -> onNavigateProfile()
                        TopBarMiniIndex -> watchingChannel?.let { onWatchChannel(it.id) }
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleListKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (rows.isEmpty()) {
            if (event.key == Key.DirectionUp) {
                focusZone = RecFocusZone.TOP_BAR
                topBarRow = if (tab == 1) 2 else 1
                return true
            }
            return false
        }
        return when (event.key) {
            Key.DirectionDown -> {
                if (listFocusIndex < rows.lastIndex) {
                    listFocusIndex += 1
                    scope.launch { listState.animateScrollToItem(listFocusIndex) }
                }
                true
            }
            Key.DirectionUp -> {
                if (listFocusIndex > 0) {
                    listFocusIndex -= 1
                    scope.launch { listState.animateScrollToItem(listFocusIndex) }
                } else {
                    focusZone = RecFocusZone.TOP_BAR
                    topBarRow = if (tab == 1) 2 else 1
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                focusZone = RecFocusZone.DETAIL
                detailActionIndex = 0
                true
            }
            else -> false
        }
    }

    fun handleDetailKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val actions = detailActions()
        return when (event.key) {
            Key.DirectionLeft -> {
                detailActionIndex = (detailActionIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                detailActionIndex = (detailActionIndex + 1).coerceAtMost(actions.lastIndex)
                true
            }
            Key.DirectionUp -> {
                focusZone = RecFocusZone.LIST
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                executeDetailAction()
                true
            }
            Key.Back, Key.Escape -> {
                focusZone = RecFocusZone.LIST
                true
            }
            else -> false
        }
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = EpgNavTab.Recordings,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 0 && topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 0 && topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                isRecordingActive = isRecordingActive,
                onProfileClick = {
                    focusZone = RecFocusZone.TOP_BAR
                    topBarRow = 0
                    topBarFocusIndex = TopBarProfileIndex
                    onNavigateProfile()
                },
                onTabSelected = { tabItem ->
                    focusZone = RecFocusZone.TOP_BAR
                    topBarRow = 0
                    topBarFocusIndex = GridNavTabs.indexOf(tabItem).coerceAtLeast(0)
                    activateNavTab(tabItem)
                },
                miniPlayer = {
                    MiniNowPlayingPlayer(
                        channel = watchingChannel,
                        program = null,
                        player = livePlayerManager.activePlayer(),
                        isFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 0 && topBarFocusIndex == TopBarMiniIndex,
                        sleepCountdown = sleepTimer.formatCountdown().takeIf { sleepRemaining > 0 },
                        onFocus = {
                            focusZone = RecFocusZone.TOP_BAR
                            topBarRow = 0
                            topBarFocusIndex = TopBarMiniIndex
                        },
                        modifier = Modifier.focusable()
                    )
                },
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == RecFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            EpgChipFilterBar(
                labels = listOf("Scheduled", "Recordings"),
                activeIndex = tab,
                focusedIndex = tabFocusIndex,
                barFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (tab == 1) {
                EpgChipFilterBar(
                    labels = sortLabels,
                    activeIndex = activeSortIndex,
                    focusedIndex = sortFocusIndex,
                    barFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(listFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == RecFocusZone.LIST) handleListKey(it) else false
                    }
            ) {
                if (rows.isEmpty()) {
                    EpgListEmptyState(
                        message = if (tab == 0) "No scheduled recordings" else "No saved recordings",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, row -> "${tab}_${row.id}" }) { index, row ->
                            RecordingsListRow(
                                title = row.title,
                                subtitle = row.subtitle,
                                badge = row.badge,
                                thumbnailPath = row.thumbnailPath,
                                isFocused = focusZone == RecFocusZone.LIST && index == listFocusIndex
                            )
                        }
                    }
                }
            }

            selectedRow?.let { row ->
                val meta = when (row) {
                    is RecordingRow.Scheduled -> {
                        "${formatEpgTime(row.item.startTime)} – ${formatEpgTime(row.item.endTime)}"
                    }
                    is RecordingRow.Saved -> row.subtitle
                }
                RecordingsDetailPanel(
                    title = row.title,
                    subtitle = when (row) {
                        is RecordingRow.Scheduled -> row.item.channelName
                        is RecordingRow.Saved -> row.item.channelName
                    },
                    meta = meta,
                    thumbnailPath = row.thumbnailPath,
                    detailActionFocused = if (focusZone == RecFocusZone.DETAIL) detailActionIndex else -1,
                    actions = detailActions(),
                    onActionFocusChange = { detailActionIndex = it },
                    onAction = {
                        detailActionIndex = it
                        executeDetailAction()
                    },
                    visible = focusZone == RecFocusZone.DETAIL || focusZone == RecFocusZone.LIST,
                    modifier = Modifier
                        .focusRequester(detailFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (focusZone == RecFocusZone.DETAIL) handleDetailKey(it) else false
                        }
                )
            }
        }

        if (deleteScheduledId != null) {
            val id = deleteScheduledId!!
            AlertDialog(
                onDismissRequest = { deleteScheduledId = null },
                title = { Text("Cancel scheduled recording?") },
                text = { Text("This will remove the recording timer.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteScheduled(id)
                        deleteScheduledId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { deleteScheduledId = null }) { Text("Close") }
                }
            )
        }

        deleteMedia?.let { media ->
            AlertDialog(
                onDismissRequest = { deleteMedia = null },
                title = { Text("Delete recording?") },
                text = { Text("The media file will be removed from storage.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteRecording(media.id, media.filePath, media.thumbnailPath)
                        deleteMedia = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { deleteMedia = null }) { Text("Close") }
                }
            )
        }
    }
}

private fun formatRecordedDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(epochMs))
