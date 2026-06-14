package com.neuropulse.tv.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.data.db.entity.SeriesRecordingRuleEntity
import com.neuropulse.tv.feature.recording.RecordingCountdown
import com.neuropulse.tv.feature.recording.RecordingSort
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.feature.recording.SeriesTitleMatcher
import com.neuropulse.tv.feature.recording.StorageFormat
import com.neuropulse.tv.player.LivePlayerManager
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.component.RecordingsBottomSheetPanel
import com.neuropulse.tv.ui.component.RecordingDeleteDialog
import com.neuropulse.tv.ui.component.RecordingGridCard
import com.neuropulse.tv.ui.component.canResumeRecording
import com.neuropulse.tv.ui.component.RecordingsSimpleDetailPanel
import com.neuropulse.tv.ui.component.RecordingsListRow
import com.neuropulse.tv.ui.component.RecordingsChipFilterBar
import com.neuropulse.tv.ui.component.RecordingsEmptyState
import com.neuropulse.tv.ui.component.RecordingsHubHeader
import com.neuropulse.tv.ui.component.formatEpgTime
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RecFocusZone { TOP_BAR, LIST, DETAIL }

private val TopBarProfileIndex get() = GridNavTabs.size

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

    data class SeriesGroupHeader(
        val seriesTitle: String,
        val episodeCount: Int
    ) : RecordingRow() {
        override val id = seriesTitle.hashCode().toLong()
        override val title = seriesTitle
        override val subtitle = "$episodeCount episode(s)"
        override val badge = "SERIES"
        override val thumbnailPath: String? = null
    }

    data class SeriesEpisode(
        val item: RecordedMediaEntity,
        val episodeLabel: String,
        val seriesTitle: String
    ) : RecordingRow() {
        override val id = item.id
        override val title = episodeLabel
        override val subtitle = buildString {
            append(seriesTitle)
            append(" · ")
            append(item.channelName)
            append(" · ")
            append(formatRecordedDate(item.recordedAt))
        }
        override val badge = null
        override val thumbnailPath = item.thumbnailPath
    }

    data class SeriesRule(val rule: SeriesRecordingRuleEntity) : RecordingRow() {
        override val id = rule.id
        override val title = rule.seriesTitle
        override val subtitle = buildString {
            append(if (rule.recordNewOnly) "New episodes only" else "All airings")
            append(" · pad ${rule.paddingStartMins}/${rule.paddingEndMins} min")
            if (rule.maxEpisodesToKeep > 0) {
                append(" · keep ${rule.maxEpisodesToKeep}")
            } else {
                append(" · unlimited")
            }
        }
        override val badge = "RULE"
        override val thumbnailPath: String? = null
    }
}

private fun RecordingRow.recordedMedia(): RecordedMediaEntity? = when (this) {
    is RecordingRow.Saved -> item
    is RecordingRow.SeriesEpisode -> item
    else -> null
}

private fun RecordingRow.isGridCard(): Boolean = this is RecordingRow.Saved || this is RecordingRow.SeriesEpisode

@Composable
fun RecordingsScreen(
    profileInitials: String = "?",
    onNavigateHome: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onWatchChannel: (Long) -> Unit = {},
    onPlayRecording: (
        title: String,
        url: String,
        recordingId: Long,
        recordedAt: Long,
        resume: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    viewModel: RecordingViewModel = hiltViewModel(),
    homeViewModel: HomeEpgViewModel = hiltViewModel()
) {
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()
    val seriesRules by viewModel.seriesRules.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by viewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val livePlayerManager = homeViewModel.livePlayerManager

    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }

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
    var topBarFocusIndex by remember {
        mutableIntStateOf(GridNavTabs.indexOf(EpgNavTab.Recordings).coerceAtLeast(0))
    }
    var tabFocusIndex by remember { mutableIntStateOf(0) }
    var sortFocusIndex by remember { mutableIntStateOf(0) }
    var listFocusIndex by remember { mutableIntStateOf(0) }
    var detailActionIndex by remember { mutableIntStateOf(0) }

    var deleteScheduledId by remember { mutableStateOf<Long?>(null) }
    var deleteMedia by remember { mutableStateOf<RecordedMediaEntity?>(null) }
    var deleteSeriesRuleId by remember { mutableStateOf<Long?>(null) }

    val gridState = rememberLazyGridState()
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
    val rows: List<RecordingRow> = remember(tab, upcoming, recorded, seriesRules, now) {
        when (tab) {
            0 -> buildGroupedRecordingRows(recorded, seriesRules)
            1 -> upcoming.map { RecordingRow.Scheduled(it, now) }
            else -> seriesRules.map { RecordingRow.SeriesRule(it) }
        }
    }
    val sortLabels = listOf("Date", "Channel", "Duration", "Size")
    val sortValues = listOf(RecordingSort.DATE, RecordingSort.CHANNEL, RecordingSort.DURATION, RecordingSort.FILE_SIZE)
    val activeSortIndex = sortValues.indexOf(sort).coerceAtLeast(0)
    val selectedRow = rows.getOrNull(listFocusIndex)

    val hubTitle = when (tab) {
        0 -> "My Recordings"
        1 -> "Schedule"
        else -> "Series Rules"
    }
    val hubSubtitle = when (tab) {
        0 -> "Saved programs from your library"
        1 -> "Upcoming and in-progress recordings"
        else -> "Auto-record episodes from the TV Guide"
    }

    LaunchedEffect(focusZone, topBarRow) {
        when (focusZone) {
            RecFocusZone.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            RecFocusZone.LIST -> listFocusRequester.requestFocusSafelyAfterLayout()
            RecFocusZone.DETAIL -> detailFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(rows.size, tab) {
        if (listFocusIndex > rows.lastIndex) listFocusIndex = rows.lastIndex.coerceAtLeast(0)
    }

    fun activateNavTab(tabItem: EpgNavTab) {
        when (tabItem) {
            EpgNavTab.Guide, EpgNavTab.Home -> onNavigateHome()
            EpgNavTab.Vod -> onNavigateVod(0)
            EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(1)
            EpgNavTab.Recordings -> Unit
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Settings -> onNavigateSettings()
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

    fun detailActions(): List<String> = when (val row = selectedRow) {
        is RecordingRow.Scheduled -> listOf("✕ Cancel", "ℹ Info")
        is RecordingRow.Saved, is RecordingRow.SeriesEpisode -> buildList {
            add("▶ Play")
            row.recordedMedia()?.let { if (canResumeRecording(it)) add("↩ Resume") }
            add("✕ Delete")
            add("ℹ Info")
        }
        is RecordingRow.SeriesGroupHeader -> emptyList()
        is RecordingRow.SeriesRule -> listOf("✕ Delete rule")
        null -> emptyList()
    }

    fun playRecording(entity: RecordedMediaEntity, resume: Boolean) {
        onPlayRecording(
            entity.programTitle,
            Uri.fromFile(File(entity.filePath)).toString(),
            entity.id,
            entity.recordedAt,
            resume
        )
    }

    fun playSavedRecording(row: RecordingRow.Saved) = playRecording(row.item, resume = false)

    fun playSeriesEpisode(row: RecordingRow.SeriesEpisode) = playRecording(row.item, resume = false)

    fun executeDetailAction() {
        when (val row = selectedRow) {
            is RecordingRow.Scheduled -> when (detailActions().getOrNull(detailActionIndex)) {
                "✕ Cancel" -> deleteScheduledId = row.item.id
                else -> Unit
            }
            is RecordingRow.Saved, is RecordingRow.SeriesEpisode -> {
                val media = row.recordedMedia() ?: return
                when (detailActions().getOrNull(detailActionIndex)) {
                    "▶ Play" -> playRecording(media, resume = false)
                    "↩ Resume" -> playRecording(media, resume = true)
                    "✕ Delete" -> deleteMedia = media
                    else -> Unit
                }
            }
            is RecordingRow.SeriesRule -> when (detailActionIndex) {
                0 -> deleteSeriesRuleId = row.rule.id
            }
            is RecordingRow.SeriesGroupHeader -> Unit
            null -> Unit
        }
    }

    fun mediaRowIndices(): List<Int> = rows.indices.filter { rows[it].isGridCard() }

    fun moveGridFocusHorizontal(delta: Int) {
        val mediaIndices = mediaRowIndices()
        if (mediaIndices.isEmpty()) return
        val current = mediaIndices.indexOf(listFocusIndex)
        if (current < 0) {
            listFocusIndex = mediaIndices.first()
            return
        }
        val next = (current + delta).coerceIn(0, mediaIndices.lastIndex)
        listFocusIndex = mediaIndices[next]
    }

    fun handleTopBarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
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
                when (topBarRow) {
                    1 -> tabFocusIndex = (tabFocusIndex - 1).coerceAtLeast(0)
                    2 -> sortFocusIndex = (sortFocusIndex - 1).coerceAtLeast(0)
                    else -> topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                }
                true
            }
            Key.DirectionRight -> {
                when (topBarRow) {
                    1 -> tabFocusIndex = (tabFocusIndex + 1).coerceAtMost(2)
                    2 -> sortFocusIndex = (sortFocusIndex + 1).coerceAtMost(sortLabels.lastIndex)
                    else -> topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
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
                        if (tab == 0) {
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
                        TopBarProfileIndex -> {
                            profileMenuOpen = true
                            profileMenuFocusIndex = 0
                        }
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
                topBarRow = if (tab == 0) 2 else 1
                return true
            }
            return false
        }
        return when (event.key) {
            Key.DirectionDown -> {
                if (tab == 0 && rows.getOrNull(listFocusIndex)?.isGridCard() == true) {
                    focusZone = RecFocusZone.DETAIL
                    detailActionIndex = 0
                } else if (listFocusIndex < rows.lastIndex) {
                    listFocusIndex += 1
                    if (tab == 0) {
                        scope.launch { gridState.animateScrollToItem(listFocusIndex) }
                    } else {
                        scope.launch { listState.animateScrollToItem(listFocusIndex) }
                    }
                }
                true
            }
            Key.DirectionUp -> {
                if (listFocusIndex > 0) {
                    listFocusIndex -= 1
                    if (tab == 0) {
                        scope.launch { gridState.animateScrollToItem(listFocusIndex) }
                    } else {
                        scope.launch { listState.animateScrollToItem(listFocusIndex) }
                    }
                } else {
                    focusZone = RecFocusZone.TOP_BAR
                    topBarRow = if (tab == 0) 2 else 1
                }
                true
            }
            Key.DirectionLeft -> {
                if (tab == 0 && rows.getOrNull(listFocusIndex)?.isGridCard() == true) {
                    moveGridFocusHorizontal(-1)
                    scope.launch { gridState.animateScrollToItem(listFocusIndex) }
                }
                true
            }
            Key.DirectionRight -> {
                if (tab == 0 && rows.getOrNull(listFocusIndex)?.isGridCard() == true) {
                    moveGridFocusHorizontal(1)
                    scope.launch { gridState.animateScrollToItem(listFocusIndex) }
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (val row = rows.getOrNull(listFocusIndex)) {
                    is RecordingRow.Saved -> playSavedRecording(row)
                    is RecordingRow.SeriesEpisode -> playSeriesEpisode(row)
                    is RecordingRow.Scheduled, is RecordingRow.SeriesRule -> {
                        focusZone = RecFocusZone.DETAIL
                        detailActionIndex = 0
                    }
                    is RecordingRow.SeriesGroupHeader -> Unit
                    null -> Unit
                }
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
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> when {
                        deleteScheduledId != null -> {
                            deleteScheduledId = null
                            true
                        }
                        deleteMedia != null -> {
                            deleteMedia = null
                            true
                        }
                        deleteSeriesRuleId != null -> {
                            deleteSeriesRuleId = null
                            true
                        }
                        profileMenuOpen -> {
                            profileMenuOpen = false
                            true
                        }
                        focusZone == RecFocusZone.DETAIL -> {
                            focusZone = RecFocusZone.LIST
                            true
                        }
                        focusZone == RecFocusZone.LIST && topBarRow > 0 -> {
                            focusZone = RecFocusZone.TOP_BAR
                            true
                        }
                        else -> {
                            onNavigateHome()
                            true
                        }
                    }
                    else -> false
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = EpgNavTab.Recordings,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 0 && topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 0 && topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                onProfileClick = {
                    focusZone = RecFocusZone.TOP_BAR
                    topBarRow = 0
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
                onTabSelected = { tabItem ->
                    focusZone = RecFocusZone.TOP_BAR
                    topBarRow = 0
                    topBarFocusIndex = GridNavTabs.indexOf(tabItem).coerceAtLeast(0)
                    activateNavTab(tabItem)
                },
                miniPlayer = {},
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == RecFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF141420), EpgColors.Background)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RecordingsHubHeader(
                    title = hubTitle,
                    subtitle = hubSubtitle
                )
                RecordingsChipFilterBar(
                    labels = listOf("My Recordings", "Schedule", "Series Rules"),
                    activeIndex = tab,
                    focusedIndex = tabFocusIndex,
                    barFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 1
                )
                if (tab == 0) {
                    Text(
                        text = "Sort by",
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    RecordingsChipFilterBar(
                        labels = sortLabels,
                        activeIndex = activeSortIndex,
                        focusedIndex = sortFocusIndex,
                        barFocused = focusZone == RecFocusZone.TOP_BAR && topBarRow == 2
                    )
                }
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
                    RecordingsEmptyState(
                        title = when (tab) {
                            0 -> "No recordings yet"
                            1 -> "No upcoming recordings scheduled"
                            else -> "No series recording rules"
                        },
                        message = when (tab) {
                            0 -> "Go to TV Guide to schedule a recording"
                            1 -> "Browse the guide and tap Record on a program"
                            else -> "Open Series & Shows and tap Record Series"
                        },
                        icon = when (tab) {
                            0 -> "●"
                            1 -> "◷"
                            else -> "▤"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (tab == 0) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = rows,
                            key = { _, row -> "rec_${row.id}" },
                            span = { _, row ->
                                if (row is RecordingRow.SeriesGroupHeader) GridItemSpan(3) else GridItemSpan(1)
                            }
                        ) { index, row ->
                            when (row) {
                                is RecordingRow.SeriesGroupHeader -> {
                                    RecordingsListRow(
                                        title = row.title,
                                        subtitle = row.subtitle,
                                        badge = row.badge,
                                        thumbnailPath = null,
                                        isFocused = focusZone == RecFocusZone.LIST && index == listFocusIndex
                                    )
                                }
                                is RecordingRow.Saved -> {
                                    RecordingGridCard(
                                        title = row.item.programTitle,
                                        channelName = row.item.channelName,
                                        recordedAt = row.item.recordedAt,
                                        durationMs = row.item.durationMs,
                                        fileSizeBytes = row.item.fileSizeBytes,
                                        thumbnailPath = row.item.thumbnailPath,
                                        playbackPositionMs = row.item.playbackPositionMs,
                                        isFocused = focusZone == RecFocusZone.LIST && index == listFocusIndex,
                                        nowMs = now
                                    )
                                }
                                is RecordingRow.SeriesEpisode -> {
                                    RecordingGridCard(
                                        title = row.title,
                                        channelName = row.item.channelName,
                                        recordedAt = row.item.recordedAt,
                                        durationMs = row.item.durationMs,
                                        fileSizeBytes = row.item.fileSizeBytes,
                                        thumbnailPath = row.item.thumbnailPath,
                                        playbackPositionMs = row.item.playbackPositionMs,
                                        isFocused = focusZone == RecFocusZone.LIST && index == listFocusIndex,
                                        nowMs = now
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                val media = row.recordedMedia()
                val actions = detailActions()
                when {
                    media != null -> RecordingsBottomSheetPanel(
                        title = if (row is RecordingRow.SeriesEpisode) row.title else media.programTitle,
                        channelName = media.channelName,
                        recordedAt = media.recordedAt,
                        durationMs = media.durationMs,
                        fileSizeBytes = media.fileSizeBytes,
                        thumbnailPath = media.thumbnailPath,
                        actions = actions,
                        detailActionFocused = if (focusZone == RecFocusZone.DETAIL) detailActionIndex else -1,
                        visible = focusZone == RecFocusZone.DETAIL || focusZone == RecFocusZone.LIST,
                        onAction = {
                            detailActionIndex = it
                            executeDetailAction()
                        },
                        modifier = Modifier
                            .focusRequester(detailFocusRequester)
                            .focusable()
                            .onPreviewKeyEvent {
                                if (focusZone == RecFocusZone.DETAIL) handleDetailKey(it) else false
                            }
                    )
                    row is RecordingRow.Scheduled || row is RecordingRow.SeriesRule -> {
                        val meta = when (row) {
                            is RecordingRow.Scheduled ->
                                "${formatEpgTime(row.item.startTime)} – ${formatEpgTime(row.item.endTime)}"
                            is RecordingRow.SeriesRule -> row.subtitle
                            else -> ""
                        }
                        RecordingsSimpleDetailPanel(
                            title = row.title,
                            subtitle = when (row) {
                                is RecordingRow.Scheduled -> row.item.channelName
                                is RecordingRow.SeriesRule -> "Auto-record from EPG"
                                else -> ""
                            },
                            meta = meta,
                            actions = actions,
                            detailActionFocused = if (focusZone == RecFocusZone.DETAIL) detailActionIndex else -1,
                            visible = focusZone == RecFocusZone.DETAIL || focusZone == RecFocusZone.LIST,
                            onAction = {
                                detailActionIndex = it
                                executeDetailAction()
                            },
                            modifier = Modifier
                                .focusRequester(detailFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent {
                                    if (focusZone == RecFocusZone.DETAIL) handleDetailKey(it) else false
                                }
                        )
                    }
                }
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
            RecordingDeleteDialog(
                title = media.programTitle,
                fileSizeBytes = media.fileSizeBytes,
                onDismiss = { deleteMedia = null },
                onConfirm = {
                    viewModel.deleteRecording(media.id, media.filePath, media.thumbnailPath)
                    deleteMedia = null
                }
            )
        }

        if (deleteSeriesRuleId != null) {
            val ruleId = deleteSeriesRuleId!!
            AlertDialog(
                onDismissRequest = { deleteSeriesRuleId = null },
                title = { Text("Delete series rule?") },
                text = { Text("Future episodes will no longer be scheduled automatically.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteSeriesRule(ruleId)
                        deleteSeriesRuleId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { deleteSeriesRuleId = null }) { Text("Close") }
                }
            )
        }
    }
}

private fun buildGroupedRecordingRows(
    recorded: List<RecordedMediaEntity>,
    rules: List<SeriesRecordingRuleEntity>
): List<RecordingRow> {
    if (rules.isEmpty()) return recorded.map { RecordingRow.Saved(it) }

    val grouped = mutableListOf<RecordingRow>()
    val matchedIds = mutableSetOf<Long>()

    rules.forEach { rule ->
        val episodes = recorded.filter { item ->
            SeriesTitleMatcher.matchesProgramTitle(rule.seriesTitle, item.programTitle)
        }.sortedWith(
            compareBy(
                { SeriesTitleMatcher.seasonSortKey(it.programTitle) },
                { it.recordedAt }
            )
        )
        if (episodes.isEmpty()) return@forEach
        grouped += RecordingRow.SeriesGroupHeader(rule.seriesTitle, episodes.size)
        episodes.forEach { item ->
            matchedIds += item.id
            grouped += RecordingRow.SeriesEpisode(
                item = item,
                episodeLabel = SeriesTitleMatcher.episodeLabel(item.programTitle, rule.seriesTitle),
                seriesTitle = rule.seriesTitle
            )
        }
    }

    recorded.filter { it.id !in matchedIds }.forEach { grouped += RecordingRow.Saved(it) }
    return grouped
}

private fun formatRecordedDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(epochMs))
