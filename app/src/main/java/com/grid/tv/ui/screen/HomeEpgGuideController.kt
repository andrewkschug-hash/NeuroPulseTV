package com.grid.tv.ui.screen

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import com.grid.tv.domain.epg.EpgProgramAction
import com.grid.tv.domain.epg.programmesForChannel
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.GuideGroupCategory
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.expandedCategoriesForSelection
import com.grid.tv.ui.component.guideFilterRowAction
import com.grid.tv.ui.component.isTvActivateKey
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import com.grid.tv.ui.component.animateScrollEpgChannelIntoView
import com.grid.tv.ui.component.animateScrollToItemIfNeeded
import com.grid.tv.ui.component.toggleCategoryExpansion
import kotlinx.coroutines.Job
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.util.TvTextInputSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val TopBarSearchIndex = 0

internal data class HomeEpgGuideDeps(
    val context: Context,
    val scope: CoroutineScope,
    val listState: LazyListState,
    val hScroll: ScrollState,
    val channels: List<Channel>,
    val displayChannels: List<Channel>,
    val displayPrograms: List<Program>,
    val viewModel: HomeEpgViewModel,
    val recordingViewModel: RecordingViewModel,
    val searchViewModel: SearchViewModel,
    val windowStart: Long,
    val windowDurationMs: Long,
    val density: Density,
    val gridFocusRequester: FocusRequester,
    val gridFilterFocusRequester: FocusRequester,
    val topNavFocusRequester: FocusRequester,
    val continueWatchingFocusRequester: FocusRequester,
    val previewFocusRequester: FocusRequester,
    val previewChannel: Channel?,
    val focusedChannel: Channel?,
    val focusedProgram: Program?,
    val previewProgram: Program?,
    val previewNextProgram: Program?,
    val guideGroupCategories: List<GuideGroupCategory>,
    val guideFilter: GuideChannelFilter,
    val demoFavoriteIds: Set<Long>,
    val vodProgress: Map<Long, Long>,
    val continueWatchingItems: List<ContinueWatchingItem>,
    val showPreviewSection: Boolean,
    val hasContinueWatching: Boolean,
    val usePlaceholder: Boolean,
    val searchQuery: String,
    val onWatchChannel: (Long) -> Unit,
    val onPlayCatchup: (String, String) -> Unit,
    val onNavigateRecordings: () -> Unit,
    val onNavigateSettings: () -> Unit,
    val onNavigateVod: (Int) -> Unit,
    val onNavigateSeries: (Long) -> Unit,
    val onPlayVod: (String, String, Boolean) -> Unit,
    val onResumeContinueWatching: (ContinueWatchingItem) -> Unit,
)

internal class HomeEpgGuideController(
    private val ui: HomeEpgUiState,
) {
    private lateinit var deps: HomeEpgGuideDeps
    private var channelScrollJob: Job? = null

    fun bind(deps: HomeEpgGuideDeps) {
        this.deps = deps
    }

    fun watchChannel(ch: Channel) {
        val full = deps.channels.find { it.id == ch.id } ?: ch
        deps.viewModel.setLastPlayedChannel(full)
        deps.viewModel.enableGuidePreview(full.id)
        deps.onWatchChannel(full.id)
    }

    fun selectChannelForPreview(channel: Channel) {
        if (deps.usePlaceholder || channel.streamUrl.isBlank()) return
        deps.viewModel.enableGuidePreview(channel.id)
        val fullChannel = deps.channels.find { it.id == channel.id } ?: channel
        deps.viewModel.previewChannel(deps.context, fullChannel)
    }

    fun openPreviewForChannel(channel: Channel) {
        if (deps.usePlaceholder || channel.streamUrl.isBlank()) return
        selectChannelForPreview(channel)
        ui.detailExpanded = true
        ui.pendingPreviewFocus = true
        focusEpgZone(EpgFocusZone.PREVIEW)
    }

    fun liveScrollTarget(): Int {
        val now = System.currentTimeMillis()
        val offsetDp = EpgLayout.offsetForLocalNow(deps.windowStart, deps.windowDurationMs, now)
        val offsetPx = deps.density.run { offsetDp.toPx() }
        return (offsetPx - 400f).coerceAtLeast(0f).toInt()
    }

    fun scrollToProgram(program: Program?) {
        program ?: return
        val offsetDp = EpgLayout.offsetForTime(program.startTime, deps.windowStart, deps.windowDurationMs)
        val widthDp = EpgLayout.widthForProgram(program.startTime, program.endTime, deps.windowDurationMs)
        val startPx = deps.density.run { offsetDp.toPx() }
        val widthPx = deps.density.run { widthDp.toPx() }
        val centerPx = startPx + widthPx / 2f
        val viewportHalf = 420f
        val target = (centerPx - viewportHalf).coerceAtLeast(0f).toInt()
        deps.scope.launch {
            deps.hScroll.animateScrollTo(target.coerceAtMost(deps.hScroll.maxValue))
        }
    }

    fun playProgram(channel: Channel, program: Program, instant: Boolean = false) {
        val full = deps.channels.find { it.id == channel.id } ?: channel
        val replay = deps.viewModel.replayState(program, full, System.currentTimeMillis())
        when (replay.action) {
            EpgProgramAction.WATCH_REPLAY -> {
                deps.scope.launch {
                    val url = deps.viewModel.stageCatchupPlayback(program, full) ?: return@launch
                    deps.onPlayCatchup(program.title, url)
                }
            }
            EpgProgramAction.REMINDER -> deps.recordingViewModel.scheduleProgram(full, program)
            else -> {
                if (instant) {
                    watchChannel(full)
                } else {
                    openPreviewForChannel(full)
                }
            }
        }
    }

    fun primaryActionLabel(channel: Channel?, program: Program?): String {
        if (channel == null || program == null) return "Watch Live"
        return deps.viewModel.replayState(
            program,
            deps.channels.find { it.id == channel.id } ?: channel,
            System.currentTimeMillis()
        ).action.label
    }

    fun openChannelFromTouch(channelIndex: Int, channel: Channel) {
        ui.focusChannelIndex = channelIndex
        ui.focusOnChannelColumn = true
        deps.scope.launch {
            deps.listState.animateScrollToItemIfNeeded(channelIndex)
        }
        openPreviewForChannel(channel)
    }

    fun openProgramFromTouch(channelIndex: Int, programIndex: Int, program: Program) {
        ui.focusChannelIndex = channelIndex
        ui.focusProgramIndex = programIndex
        ui.focusOnChannelColumn = false
        val channel = deps.displayChannels.getOrNull(channelIndex) ?: return
        scrollToProgram(program)
        val replay = deps.viewModel.replayState(
            program,
            deps.channels.find { it.id == channel.id } ?: channel,
            System.currentTimeMillis()
        )
        if (replay.action == EpgProgramAction.WATCH_REPLAY) {
            playProgram(channel, program, instant = true)
            return
        }
        openPreviewForChannel(channel)
    }

    fun programsForChannel(channel: Channel): List<Program> =
        programmesForChannel(channel, deps.displayPrograms)

    fun clampProgramIndex(channelIdx: Int, programIdx: Int): Int {
        val progs = deps.displayChannels.getOrNull(channelIdx)?.let { programsForChannel(it) } ?: emptyList()
        return programIdx.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
    }

    fun scrollByTimeSlot(direction: Int) {
        val slotPx = (EpgLayout.ThirtyMinWidthDp * direction).toInt()
        deps.scope.launch {
            val target = (deps.hScroll.value + slotPx).coerceIn(0, deps.hScroll.maxValue)
            deps.hScroll.animateScrollTo(target)
        }
    }

    fun scrollToLive() {
        deps.scope.launch { deps.hScroll.animateScrollTo(liveScrollTarget()) }
    }

    fun activateNavTab(tab: EpgNavTab) {
        ui.selectedTab = tab
        when (tab) {
            EpgNavTab.Guide, EpgNavTab.Home -> {
                deps.viewModel.setFavoriteGroupFilter(null)
                ui.focusZone = EpgFocusZone.GRID
                ui.focusOnChannelColumn = true
            }
            EpgNavTab.Search -> ui.showSearchOverlay = true
            EpgNavTab.Vod -> deps.onNavigateVod(0)
            EpgNavTab.Movies -> deps.onNavigateVod(0)
            EpgNavTab.Series -> deps.onNavigateVod(1)
            EpgNavTab.Recordings -> deps.onNavigateRecordings()
            EpgNavTab.Favorites -> deps.viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            EpgNavTab.Settings -> deps.onNavigateSettings()
        }
    }

    fun handleSearchResult(result: SearchResultItem) {
        when (result.type) {
            SearchResultType.ACTOR -> {
                val actor = result.actorName ?: return
                deps.searchViewModel.recordSelection(actor)
                deps.searchViewModel.applyTrendingOrRecent(actor)
                return
            }
            SearchResultType.GENRE -> {
                val genre = result.genreName ?: return
                deps.searchViewModel.recordSelection(genre)
                deps.searchViewModel.applyTrendingOrRecent(genre)
                return
            }
            else -> Unit
        }
        deps.searchViewModel.recordSelection(deps.searchQuery)
        ui.showSearchOverlay = false
        deps.searchViewModel.clearQuery()
        when (result.type) {
            SearchResultType.CHANNEL -> result.channelId?.let { chId ->
                val ch = deps.displayChannels.find { it.id == chId }
                    ?: deps.channels.find { it.id == chId }
                if (ch != null) {
                    val idx = deps.displayChannels.indexOfFirst { it.id == chId }
                    if (idx >= 0) {
                        ui.focusChannelIndex = idx
                        ui.focusOnChannelColumn = true
                        ui.focusZone = EpgFocusZone.GRID
                    }
                    deps.viewModel.setLastPlayedChannel(ch)
                    deps.viewModel.enableGuidePreview(ch.id)
                } else {
                    deps.viewModel.enableGuidePreview(chId)
                }
                deps.onWatchChannel(chId)
            }
            SearchResultType.PROGRAM -> {
                val chId = result.channelId
                val prog = result.program
                if (chId != null && prog != null) {
                    val chIdx = deps.displayChannels.indexOfFirst { it.id == chId }
                    if (chIdx >= 0) {
                        ui.focusChannelIndex = chIdx
                        val progs = programsForChannel(deps.displayChannels[chIdx])
                        ui.focusProgramIndex = progs.indexOfFirst { it.id == prog.id }.coerceAtLeast(0)
                        ui.focusOnChannelColumn = false
                        scrollToProgram(prog)
                        ui.focusZone = EpgFocusZone.GRID
                    }
                }
            }
            SearchResultType.VOD -> result.vodItem?.let { item ->
                VodPlaybackHelper.stageMovie(item)
                val resume = (deps.vodProgress[item.streamId] ?: 0L) > 5_000L
                deps.onPlayVod(item.streamUrl, item.title, resume)
            }
            SearchResultType.SERIES -> result.seriesShow?.let { deps.onNavigateSeries(it.id) }
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
                    deps.onPlayVod(episode.streamUrl, episode.title.ifBlank { show.name }, false)
                }
            }
            else -> Unit
        }
    }

    fun executeDetailAction() {
        val ch = deps.previewChannel ?: deps.focusedChannel ?: return
        val full = deps.channels.find { it.id == ch.id } ?: ch
        val prog = if (deps.previewChannel?.id == deps.focusedChannel?.id && !ui.focusOnChannelColumn) {
            deps.focusedProgram
        } else {
            deps.previewProgram
        }
        when (ui.detailActionIndex) {
            0 -> {
                if (prog != null) {
                    playProgram(full, prog, instant = true)
                } else {
                    watchChannel(full)
                }
            }
            1 -> {
                val isFav = if (ch.id < 0) ch.id in deps.demoFavoriteIds else ch.isFavorite
                deps.viewModel.toggleFavorite(ch.id, isFav)
            }
            2 -> {
                if (prog == null) {
                    deps.recordingViewModel.startImmediateRecording(deps.context, full, full.name)
                } else if (prog.startTime <= System.currentTimeMillis()) {
                    val duration = (prog.endTime - System.currentTimeMillis()).coerceAtLeast(10 * 60 * 1000)
                    deps.recordingViewModel.startImmediateRecording(deps.context, full, prog.title, duration)
                } else {
                    deps.recordingViewModel.scheduleProgram(full, prog)
                }
            }
        }
    }

    fun requestEpgZoneFocus(zone: EpgFocusZone) {
        if (ui.showSearchOverlay) return
        deps.scope.launch {
            when (zone) {
                EpgFocusZone.TOP_BAR -> deps.topNavFocusRequester.requestFocusSafelyAfterLayout()
                EpgFocusZone.CONTINUE_WATCHING -> if (deps.hasContinueWatching) {
                    deps.continueWatchingFocusRequester.requestFocusSafelyAfterLayout()
                }
                EpgFocusZone.PREVIEW -> if (deps.showPreviewSection) {
                    deps.previewFocusRequester.requestFocusSafelyAfterLayout(150)
                }
                EpgFocusZone.GRID_FILTER -> deps.gridFilterFocusRequester.requestFocusSafelyAfterLayout()
                EpgFocusZone.GRID -> if (deps.displayChannels.isNotEmpty()) {
                    deps.gridFocusRequester.requestFocusSafelyAfterLayout()
                }
            }
        }
    }

    fun focusEpgZone(zone: EpgFocusZone) {
        if (ui.showSearchOverlay) return
        val resolvedZone = if (zone == EpgFocusZone.GRID_FILTER && ui.selectedTab == EpgNavTab.Favorites) {
            when {
                deps.showPreviewSection -> EpgFocusZone.PREVIEW
                deps.hasContinueWatching -> EpgFocusZone.CONTINUE_WATCHING
                else -> EpgFocusZone.TOP_BAR
            }
        } else {
            zone
        }
        ui.focusZone = resolvedZone
        if (resolvedZone == EpgFocusZone.PREVIEW) ui.detailActionIndex = 0
        requestEpgZoneFocus(resolvedZone)
    }

    fun focusSearchTopBar() {
        ui.topBarFocusIndex = TopBarSearchIndex
        focusEpgZone(EpgFocusZone.TOP_BAR)
    }

    fun focusGuideFilter() {
        if (ui.selectedTab == EpgNavTab.Favorites) {
            when {
                deps.showPreviewSection -> focusPreviewSection()
                deps.hasContinueWatching -> focusEpgZone(EpgFocusZone.CONTINUE_WATCHING)
                else -> focusEpgZone(EpgFocusZone.TOP_BAR)
            }
        } else {
            focusEpgZone(EpgFocusZone.GRID_FILTER)
        }
    }

    fun focusGuideChannels(targetIndex: Int = ui.focusChannelIndex) {
        ui.focusChannelIndex = targetIndex.coerceIn(0, deps.displayChannels.lastIndex.coerceAtLeast(0))
        ui.focusOnChannelColumn = true
        focusEpgZone(EpgFocusZone.GRID)
    }

    fun focusPreviewSection() {
        if (deps.showPreviewSection) {
            focusEpgZone(EpgFocusZone.PREVIEW)
        } else {
            focusGuideFilter()
        }
    }

    fun moveGuideFocusVertical(from: EpgFocusZone, direction: Int) {
        val showGroupFilter = ui.selectedTab != EpgNavTab.Favorites
        val target = if (direction < 0) {
            epgZoneAbove(from, deps.showPreviewSection, deps.hasContinueWatching, showGroupFilter)
        } else {
            epgZoneBelow(from, deps.showPreviewSection, deps.hasContinueWatching, showGroupFilter)
        }
        target?.let(::focusEpgZone)
    }

    fun openCategoryFilterMenu() {
        if (ui.selectedTab == EpgNavTab.Favorites) return
        val expanded = expandedCategoriesForSelection(
            deps.guideGroupCategories,
            deps.guideFilter.selectedGroups
        )
        ui.categoryMenuExpandedCategories = expanded
        ui.categoryMenuFocusIndex = 0
        ui.showCategoryFilterMenu = true
    }

    fun handleCategoryFilterMenuToggle(index: Int) {
        val expanded = ui.categoryMenuExpandedCategories.ifEmpty {
            expandedCategoriesForSelection(deps.guideGroupCategories, deps.guideFilter.selectedGroups)
        }
        val visibleRows = buildVisibleGuideGroupRows(deps.guideGroupCategories, expanded)
        val row = visibleRows.getOrNull(index) ?: return
        when (row) {
            is GuideGroupVisibleRow.Category -> {
                val willExpand = row.categoryIndex !in ui.categoryMenuExpandedCategories
                ui.categoryMenuExpandedCategories = toggleCategoryExpansion(
                    ui.categoryMenuExpandedCategories,
                    row.categoryIndex
                )
                ui.categoryMenuFocusIndex = if (willExpand) index + 1 else index
            }
            is GuideGroupVisibleRow.SelectAll,
            is GuideGroupVisibleRow.Group -> {
                val next = guideFilterRowAction(row, deps.guideFilter.selectedGroups) ?: return
                deps.viewModel.setGuideFilter(next, markConfigured = true)
            }
            GuideGroupVisibleRow.AllChannels -> {
                deps.viewModel.setGuideFilter(GuideChannelFilter.All, markConfigured = true)
                ui.showCategoryFilterMenu = false
            }
        }
    }

    fun handleGridFilterKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.showSearchOverlay) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (ui.showCategoryFilterMenu) return false
        return when {
            event.key == Key.DirectionDown -> {
                if (deps.displayChannels.isNotEmpty()) {
                    focusGuideChannels()
                }
                true
            }
            event.key == Key.DirectionUp -> {
                moveGuideFocusVertical(EpgFocusZone.GRID_FILTER, direction = -1)
                true
            }
            isTvActivateKey(event) -> {
                openCategoryFilterMenu()
                true
            }
            event.key == Key.Back || event.key == Key.Escape -> {
                if (deps.displayChannels.isNotEmpty()) {
                    focusGuideChannels()
                }
                true
            }
            else -> false
        }
    }

    fun handleTopBarKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.showSearchOverlay) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (ui.showCategoryFilterMenu) return false
        if (ui.profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    ui.profileMenuOpen = false
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    ui.profileMenuOpen = false
                    false
                }
                else -> false
            }
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                ui.topBarFocusIndex = (ui.topBarFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                ui.topBarFocusIndex = (ui.topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                true
            }
            Key.DirectionDown -> {
                moveGuideFocusVertical(EpgFocusZone.TOP_BAR, direction = 1)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (ui.topBarFocusIndex) {
                    in GridNavTabs.indices -> activateNavTab(GridNavTabs[ui.topBarFocusIndex])
                    TopBarProfileIndex -> {
                        ui.profileMenuOpen = true
                        ui.profileMenuFocusIndex = 0
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleContinueWatchingKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.showSearchOverlay) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (deps.continueWatchingItems.isEmpty()) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                ui.focusedContinueIndex = (ui.focusedContinueIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                ui.focusedContinueIndex = (ui.focusedContinueIndex + 1)
                    .coerceAtMost(deps.continueWatchingItems.lastIndex)
                true
            }
            Key.DirectionUp -> {
                moveGuideFocusVertical(EpgFocusZone.CONTINUE_WATCHING, direction = -1)
                true
            }
            Key.DirectionDown -> {
                moveGuideFocusVertical(EpgFocusZone.CONTINUE_WATCHING, direction = 1)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                deps.continueWatchingItems.getOrNull(ui.focusedContinueIndex)?.let { item ->
                    if (deps.viewModel.isProfileAccessAllowed()) {
                        deps.onResumeContinueWatching(item)
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

    fun handlePreviewKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.showSearchOverlay) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (ui.detailActionIndex > 0) ui.detailActionIndex -= 1
                true
            }
            Key.DirectionRight -> {
                if (ui.detailActionIndex < 2) ui.detailActionIndex += 1
                true
            }
            Key.DirectionUp -> {
                moveGuideFocusVertical(EpgFocusZone.PREVIEW, direction = -1)
                true
            }
            Key.DirectionDown -> {
                moveGuideFocusVertical(EpgFocusZone.PREVIEW, direction = 1)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                executeDetailAction()
                true
            }
            Key.Back, Key.Escape -> {
                ui.focusZone = EpgFocusZone.GRID
                ui.detailExpanded = false
                true
            }
            else -> false
        }
    }

    private fun scrollFocusedChannelIntoView(direction: TvLazyFocusScrollDirection) {
        channelScrollJob?.cancel()
        channelScrollJob = deps.scope.launch {
            val rowHeightPx = with(deps.density) { EpgLayout.RowHeight.roundToPx() }
            deps.listState.animateScrollEpgChannelIntoView(
                index = ui.focusChannelIndex,
                direction = direction,
                rowHeightPx = rowHeightPx
            )
        }
    }

    fun handleGridKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.showSearchOverlay) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (ui.showCategoryFilterMenu || ui.showGuideGroupPicker) return false
        if (deps.displayChannels.isEmpty()) {
            return when (event.key) {
                Key.DirectionUp -> {
                    focusGuideFilter()
                    true
                }
                else -> false
            }
        }

        return when (event.key) {
            Key.DirectionDown -> {
                if (ui.focusChannelIndex < deps.displayChannels.lastIndex) {
                    ui.focusChannelIndex += 1
                    if (!ui.focusOnChannelColumn) {
                        ui.focusProgramIndex = clampProgramIndex(ui.focusChannelIndex, ui.focusProgramIndex)
                    }
                    scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.DOWN)
                }
                true
            }
            Key.DirectionUp -> {
                if (ui.focusChannelIndex > 0) {
                    ui.focusChannelIndex -= 1
                    if (!ui.focusOnChannelColumn) {
                        ui.focusProgramIndex = clampProgramIndex(ui.focusChannelIndex, ui.focusProgramIndex)
                    }
                    scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.UP)
                    true
                } else {
                    moveGuideFocusVertical(EpgFocusZone.GRID, direction = -1)
                    true
                }
            }
            Key.DirectionRight -> {
                val channel = deps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return false
                val progs = programsForChannel(channel)
                if (ui.focusOnChannelColumn) {
                    ui.focusOnChannelColumn = false
                    val progsNow = progs.indexOfFirst {
                        System.currentTimeMillis() in it.startTime..it.endTime
                    }
                    ui.focusProgramIndex = if (progsNow >= 0) progsNow else clampProgramIndex(ui.focusChannelIndex, 0)
                    scrollToProgram(progs.getOrNull(ui.focusProgramIndex))
                } else if (ui.focusProgramIndex < progs.lastIndex) {
                    ui.focusProgramIndex += 1
                    scrollToProgram(progs[ui.focusProgramIndex])
                } else {
                    val last = progs.lastOrNull()
                    val windowEnd = deps.windowStart + deps.windowDurationMs
                    if (last != null && last.endTime >= windowEnd - 15 * 60 * 1000) {
                        deps.viewModel.extendWindowForward()
                    }
                    scrollByTimeSlot(1)
                }
                true
            }
            Key.DirectionLeft -> {
                val channel = deps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return false
                val progs = programsForChannel(channel)
                if (!ui.focusOnChannelColumn && ui.focusProgramIndex > 0) {
                    ui.focusProgramIndex -= 1
                    scrollToProgram(progs[ui.focusProgramIndex])
                } else if (!ui.focusOnChannelColumn) {
                    ui.focusOnChannelColumn = true
                } else {
                    val first = progs.firstOrNull()
                    if (first != null && first.startTime <= deps.windowStart + 15 * 60 * 1000) {
                        val scrollAdjust = deps.viewModel.extendWindowBackward()
                        if (scrollAdjust > 0) {
                            deps.scope.launch { deps.hScroll.scrollTo(deps.hScroll.value + scrollAdjust) }
                        }
                    }
                    scrollByTimeSlot(-1)
                }
                true
            }
            Key.PageDown -> {
                ui.focusChannelIndex = (ui.focusChannelIndex + 10).coerceAtMost(deps.displayChannels.lastIndex)
                if (!ui.focusOnChannelColumn) {
                    ui.focusProgramIndex = clampProgramIndex(ui.focusChannelIndex, ui.focusProgramIndex)
                }
                scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.DOWN)
                true
            }
            Key.PageUp -> {
                ui.focusChannelIndex = (ui.focusChannelIndex - 10).coerceAtLeast(0)
                if (!ui.focusOnChannelColumn) {
                    ui.focusProgramIndex = clampProgramIndex(ui.focusChannelIndex, ui.focusProgramIndex)
                }
                scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.UP)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                val channel = deps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return true
                if (!ui.focusOnChannelColumn) {
                    val prog = programsForChannel(channel).getOrNull(ui.focusProgramIndex)
                    if (prog != null) {
                        playProgram(channel, prog, instant = true)
                        return true
                    }
                }
                openPreviewForChannel(channel)
                true
            }
            Key.Back, Key.Escape -> {
                if (ui.detailExpanded) {
                    ui.detailExpanded = false
                    ui.focusZone = EpgFocusZone.GRID
                    true
                } else if (ui.focusChannelIndex > 0) {
                    ui.focusChannelIndex = 0
                    if (!ui.focusOnChannelColumn) {
                        ui.focusProgramIndex = clampProgramIndex(0, ui.focusProgramIndex)
                    }
                    scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.UP)
                    true
                } else {
                    focusGuideFilter()
                    true
                }
            }
            else -> false
        }
    }
}
