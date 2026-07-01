package com.grid.tv.ui.screen

import android.content.Context
import android.util.Log
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
import com.grid.tv.domain.epg.ProgrammeIndex
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ChannelGroupNavigationMode
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.programIndexForTime
import com.grid.tv.util.GuideNavigationLogger
import com.grid.tv.util.PerformanceAudit
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.GuideGroupCategory
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.component.buildFlatProviderVisibleRows
import com.grid.tv.ui.component.buildFlatSmartBucketRows
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.expandedCategoriesForSelection
import com.grid.tv.ui.component.guideChannelFilterForVisibleRow
import com.grid.tv.ui.component.guideFilterRowAction
import com.grid.tv.ui.component.guideNavDrawerItemFocusIndex
import com.grid.tv.ui.component.nextFocusableGroupRowIndex
import com.grid.tv.ui.component.isFocusableGroupRow
import com.grid.tv.ui.component.guideGroupVisibleRowKey
import com.grid.tv.ui.component.GuideNavDrawerItem
import com.grid.tv.ui.component.isTvActivateKey
import com.grid.tv.ui.component.isTvActivateKeyUp
import com.grid.tv.ui.component.isTvLongPress
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import com.grid.tv.ui.focus.TvFocusController
import com.grid.tv.ui.component.animateScrollEpgChannelIntoView
import com.grid.tv.ui.component.animateScrollToItemIfNeeded
import com.grid.tv.ui.component.SidebarContentFocus
import com.grid.tv.ui.component.SidebarContentFocus.sidebarHorizontalResult
import com.grid.tv.ui.component.toggleCategoryExpansion
import kotlinx.coroutines.Job
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.util.TvTextInputSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val TopBarSearchIndex = 0

internal data class HomeEpgGuideDeps(
    val context: Context,
    val scope: CoroutineScope,
    val listState: LazyListState,
    val channelGroupsListState: LazyListState,
    val hScroll: ScrollState,
    val channels: List<Channel>,
    val displayChannels: List<Channel>,
    val programmeIndex: ProgrammeIndex,
    val viewModel: HomeEpgViewModel,
    val recordingViewModel: RecordingViewModel,
    val searchViewModel: SearchViewModel,
    val windowStart: Long,
    val windowDurationMs: Long,
    val density: Density,
    val gridFocusRequester: FocusRequester,
    val gridFilterFocusRequester: FocusRequester,
    val channelGroups: List<String>,
    val channelGroupNavigationMode: ChannelGroupNavigationMode,
    val continueWatchingFocusRequester: FocusRequester,
    val previewFocusRequester: FocusRequester,
    val previewChannel: Channel?,
    val focusedChannel: Channel?,
    val focusedProgram: Program?,
    val previewProgram: Program?,
    val previewNextProgram: Program?,
    val guideGroupCategories: List<GuideGroupCategory>,
    val guideFilter: GuideChannelFilter,
    val committedGuideFilter: GuideChannelFilter,
    val demoFavoriteIds: Set<Long>,
    val vodProgress: Map<Pair<Long, Long>, Long>,
    val continueWatchingItems: List<ContinueWatchingItem>,
    val showPreviewSection: Boolean,
    val hasContinueWatching: Boolean,
    val usePlaceholder: Boolean,
    val onWatchChannel: (Long) -> Unit,
    val onPlayCatchup: (String, String) -> Unit,
    val onNavigateRecordings: () -> Unit,
    val onNavigateSettings: () -> Unit,
    val onNavigateProfile: () -> Unit,
    val onNavigateMultiview: () -> Unit,
    val onNavigateVod: (Int) -> Unit,
    val onNavigateSeries: (playlistId: Long, seriesId: Long) -> Unit,
    val onPlayVod: (String, String, Boolean) -> Unit,
    val onResumeContinueWatching: (ContinueWatchingItem) -> Unit,
)

internal class HomeEpgGuideController(
    private val ui: HomeEpgUiState,
) : TvFocusController<EpgFocusZone> {
    private var deps: HomeEpgGuideDeps? = null
    private var channelScrollJob: Job? = null
    private var channelGroupsPressJob: Job? = null
    private var channelGroupsPressedGroupKey: String? = null

    override val focusZone: EpgFocusZone
        get() = ui.focusZone

    private companion object {
        const val TAG = "HomeEpgGuideController"
        const val GRID_VIEWPORT_CENTER_PX = 420f
        const val CHANNEL_GROUPS_LONG_PRESS_MS = 500L
    }

    fun bind(deps: HomeEpgGuideDeps) {
        this.deps = deps
    }

    internal fun isBound(): Boolean = deps != null

    /** Guide UI is composed only after [bind]; this is safe for handlers wired post-bind. */
    private val boundDeps: HomeEpgGuideDeps
        get() = deps ?: error("HomeEpgGuideController.bind() must run before guide interaction")

    fun watchChannel(ch: Channel) {
        val full = boundDeps.channels.find { it.id == ch.id } ?: ch
        boundDeps.viewModel.setLastPlayedChannel(full)
        boundDeps.viewModel.enableGuidePreview(full.id)
        boundDeps.viewModel.livePlayerManager.prepareFullscreenHandoff()
        boundDeps.onWatchChannel(full.id)
    }

    fun selectChannelForPreview(channel: Channel) {
        if (boundDeps.usePlaceholder || channel.streamUrl.isBlank()) return
        boundDeps.viewModel.enableGuidePreview(channel.id)
        val fullChannel = boundDeps.channels.find { it.id == channel.id } ?: channel
        boundDeps.viewModel.previewChannel(boundDeps.context, fullChannel)
    }

    fun openPreviewForChannel(channel: Channel) {
        if (boundDeps.usePlaceholder || channel.streamUrl.isBlank()) return
        selectChannelForPreview(channel)
        ui.detailExpanded = true
        ui.detailActionIndex = 0
        focusEpgZone(EpgFocusZone.PREVIEW)
    }

    override fun transitionToZone(zone: EpgFocusZone, detail: String) {
        focusEpgZone(zone)
    }

    override fun handleKey(event: KeyEvent): Boolean {
        if (ui.guideSubScreen != null) return false
        if (ui.showCategoryFilterMenu || ui.showGuideGroupPicker) return false
        if (ui.profileMenuOpen) {
            return when (event.type) {
                KeyEventType.KeyDown -> when (event.key) {
                    Key.Back, Key.Escape -> {
                        ui.profileMenuOpen = false
                        true
                    }
                    else -> false
                }
                else -> false
            }
        }
        if (ui.focusZone == EpgFocusZone.CHANNEL_GROUPS) {
            return handleChannelGroupsKey(event)
        }
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (ui.focusZone) {
            EpgFocusZone.NAV_DRAWER -> handleNavDrawerKey(event)
            EpgFocusZone.CONTINUE_WATCHING -> handleContinueWatchingKey(event)
            EpgFocusZone.PREVIEW -> handlePreviewKey(event)
            EpgFocusZone.GRID -> handleGridKey(event)
            EpgFocusZone.CHANNEL_GROUPS -> false
        }
    }

    fun toggleChannelGroupFavorite(groupKey: String) {
        ui.channelGroupsActivatePending = false
        boundDeps.viewModel.toggleFavoriteChannelGroupWithToast(groupKey)
    }

    fun liveScrollTarget(): Int {
        val now = System.currentTimeMillis()
        val offsetDp = EpgLayout.offsetForLocalNow(boundDeps.windowStart, boundDeps.windowDurationMs, now)
        val offsetPx = boundDeps.density.run { offsetDp.toPx() }
        return (offsetPx - 400f).coerceAtLeast(0f).toInt()
    }

    fun scrollToProgram(program: Program?) {
        program ?: return
        val offsetDp = EpgLayout.offsetForTime(program.startTime, boundDeps.windowStart, boundDeps.windowDurationMs)
        val widthDp = EpgLayout.widthForProgram(program.startTime, program.endTime, boundDeps.windowDurationMs)
        val startPx = boundDeps.density.run { offsetDp.toPx() }
        val widthPx = boundDeps.density.run { widthDp.toPx() }
        val centerPx = startPx + widthPx / 2f
        val viewportHalf = GRID_VIEWPORT_CENTER_PX
        val target = (centerPx - viewportHalf).coerceAtLeast(0f).toInt()
        boundDeps.scope.launch {
            boundDeps.hScroll.animateScrollTo(target.coerceAtMost(boundDeps.hScroll.maxValue))
        }
    }

    private fun viewportCenterTimeMs(): Long =
        EpgLayout.timeForScrollOffsetPx(
            scrollPx = boundDeps.hScroll.value.toFloat(),
            viewportCenterOffsetPx = GRID_VIEWPORT_CENTER_PX,
            windowStart = boundDeps.windowStart,
            windowDurationMs = boundDeps.windowDurationMs,
            density = boundDeps.density.density
        )

    private fun programAnchorTimeMs(): Long {
        if (ui.focusOnChannelColumn) return viewportCenterTimeMs()
        val channel = boundDeps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return viewportCenterTimeMs()
        val progs = programsForChannel(channel)
        val focused = progs.getOrNull(ui.focusProgramIndex)
        return focused?.let { (it.startTime + it.endTime) / 2 } ?: viewportCenterTimeMs()
    }

    private fun alignProgramIndexToTime(channelIdx: Int, timeMs: Long): Int {
        val channel = boundDeps.displayChannels.getOrNull(channelIdx) ?: return 0
        return programIndexForTime(programsForChannel(channel), timeMs)
    }

    private fun syncFocusedProgramScroll() {
        val channel = boundDeps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return
        if (ui.focusOnChannelColumn) return
        scrollToProgram(programsForChannel(channel).getOrNull(ui.focusProgramIndex))
    }

    fun playProgram(channel: Channel, program: Program, instant: Boolean = false) {
        val full = boundDeps.channels.find { it.id == channel.id } ?: channel
        val replay = boundDeps.viewModel.replayState(program, full, System.currentTimeMillis())
        when (replay.action) {
            EpgProgramAction.WATCH_REPLAY -> {
                boundDeps.scope.launch {
                    val url = boundDeps.viewModel.stageCatchupPlayback(program, full) ?: return@launch
                    boundDeps.onPlayCatchup(program.title, url)
                }
            }
            EpgProgramAction.REMINDER -> boundDeps.recordingViewModel.scheduleProgram(full, program)
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
        return boundDeps.viewModel.replayState(
            program,
            boundDeps.channels.find { it.id == channel.id } ?: channel,
            System.currentTimeMillis()
        ).action.label
    }

    fun openChannelFromTouch(channelIndex: Int, channel: Channel) {
        ui.focusChannelIndex = channelIndex
        ui.focusOnChannelColumn = true
        boundDeps.scope.launch {
            boundDeps.listState.animateScrollToItemIfNeeded(channelIndex)
        }
        openPreviewForChannel(channel)
    }

    fun openProgramFromTouch(channelIndex: Int, programIndex: Int, program: Program) {
        ui.focusChannelIndex = channelIndex
        ui.focusProgramIndex = programIndex
        ui.focusOnChannelColumn = false
        val channel = boundDeps.displayChannels.getOrNull(channelIndex) ?: return
        scrollToProgram(program)
        val replay = boundDeps.viewModel.replayState(
            program,
            boundDeps.channels.find { it.id == channel.id } ?: channel,
            System.currentTimeMillis()
        )
        if (replay.action == EpgProgramAction.WATCH_REPLAY) {
            playProgram(channel, program, instant = true)
            return
        }
        openPreviewForChannel(channel)
    }

    fun programsForChannel(channel: Channel): List<Program> =
        boundDeps.programmeIndex.programsFor(channel.id)

    fun clampProgramIndex(channelIdx: Int, programIdx: Int): Int {
        PerformanceAudit.logGridKeyFilter(
            keyName = "clampProgramIndex",
            channelIndex = channelIdx,
            totalProgramCount = boundDeps.programmeIndex.totalProgramCount
        )
        val channel = boundDeps.displayChannels.getOrNull(channelIdx) ?: return 0
        val startNs = if (PerformanceAudit.ENABLED) System.nanoTime() else 0L
        val progs = programsForChannel(channel)
        if (PerformanceAudit.ENABLED) {
            PerformanceAudit.logProgrammeIndexLookup(
                channelId = channel.id,
                resultCount = progs.size,
                elapsedNs = System.nanoTime() - startNs
            )
        }
        return programIdx.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
    }

    private fun programsForChannelTimed(channel: Channel, keyName: String): List<Program> {
        PerformanceAudit.logGridKeyFilter(keyName, ui.focusChannelIndex, boundDeps.programmeIndex.totalProgramCount)
        val startNs = if (PerformanceAudit.ENABLED) System.nanoTime() else 0L
        val progs = programsForChannel(channel)
        if (PerformanceAudit.ENABLED) {
            PerformanceAudit.logProgrammeIndexLookup(
                channelId = channel.id,
                resultCount = progs.size,
                elapsedNs = System.nanoTime() - startNs
            )
        }
        return progs
    }

    fun scrollByTimeSlot(direction: Int) {
        val slotPx = (EpgLayout.ThirtyMinWidthDp * direction).toInt()
        boundDeps.scope.launch {
            val target = (boundDeps.hScroll.value + slotPx).coerceIn(0, boundDeps.hScroll.maxValue)
            boundDeps.hScroll.animateScrollTo(target)
        }
    }

    fun scrollToLive() {
        boundDeps.viewModel.syncTimelineWindowToLocalNow()
        boundDeps.scope.launch { boundDeps.hScroll.animateScrollTo(liveScrollTarget()) }
    }

    fun activateNavTab(tab: EpgNavTab) {
        ui.selectedTab = tab
        when (tab) {
            EpgNavTab.Guide, EpgNavTab.Home -> {
                boundDeps.viewModel.setFavoriteGroupFilter(null)
                ui.focusZone = EpgFocusZone.GRID
                ui.focusOnChannelColumn = true
            }
            EpgNavTab.Search -> ui.guideSubScreen = GuideSubScreen.Search
            EpgNavTab.Vod -> boundDeps.onNavigateVod(0)
            EpgNavTab.Movies -> boundDeps.onNavigateVod(0)
            EpgNavTab.Series -> boundDeps.onNavigateVod(1)
            EpgNavTab.Recordings -> boundDeps.onNavigateRecordings()
            EpgNavTab.Favorites -> boundDeps.viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            EpgNavTab.Settings -> boundDeps.onNavigateSettings()
        }
    }

    fun handleSearchResult(result: SearchResultItem) {
        when (result.type) {
            SearchResultType.ACTOR -> {
                val actor = result.actorName ?: return
                boundDeps.searchViewModel.recordSelection(actor)
                boundDeps.searchViewModel.applyTrendingOrRecent(actor)
                return
            }
            SearchResultType.GENRE -> {
                val genre = result.genreName ?: return
                boundDeps.searchViewModel.recordSelection(genre)
                boundDeps.searchViewModel.applyTrendingOrRecent(genre)
                return
            }
            else -> Unit
        }
        boundDeps.searchViewModel.recordSelection(boundDeps.searchViewModel.queryText.value)
        ui.guideSubScreen = null
        boundDeps.searchViewModel.clearQuery()
        when (result.type) {
            SearchResultType.CHANNEL -> result.channelId?.let { chId ->
                val ch = boundDeps.displayChannels.find { it.id == chId }
                    ?: boundDeps.channels.find { it.id == chId }
                if (ch != null) {
                    val idx = boundDeps.displayChannels.indexOfFirst { it.id == chId }
                    if (idx >= 0) {
                        ui.focusChannelIndex = idx
                        ui.focusOnChannelColumn = true
                        ui.focusZone = EpgFocusZone.GRID
                    }
                    boundDeps.viewModel.setLastPlayedChannel(ch)
                    boundDeps.viewModel.enableGuidePreview(ch.id)
                } else {
                    boundDeps.viewModel.enableGuidePreview(chId)
                }
                boundDeps.onWatchChannel(chId)
            }
            SearchResultType.PROGRAM -> {
                val chId = result.channelId
                val prog = result.program
                if (chId != null && prog != null) {
                    val chIdx = boundDeps.displayChannels.indexOfFirst { it.id == chId }
                    if (chIdx >= 0) {
                        ui.focusChannelIndex = chIdx
                        val progs = programsForChannel(boundDeps.displayChannels[chIdx])
                        ui.focusProgramIndex = progs.indexOfFirst { it.id == prog.id }.coerceAtLeast(0)
                        ui.focusOnChannelColumn = false
                        scrollToProgram(prog)
                        ui.focusZone = EpgFocusZone.GRID
                    }
                }
            }
            SearchResultType.VOD -> result.vodItem?.let { item ->
                VodPlaybackHelper.stageMovie(item)
                val resume = (boundDeps.vodProgress[item.playlistId to item.streamId] ?: 0L) > 5_000L
                boundDeps.onPlayVod(item.streamUrl, item.title, resume)
            }
            SearchResultType.SERIES -> result.seriesShow?.let { boundDeps.onNavigateSeries(it.playlistId, it.id) }
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
                    boundDeps.onPlayVod(episode.streamUrl, episode.title.ifBlank { show.name }, true)
                }
            }
            else -> Unit
        }
    }

    fun executeDetailAction() {
        val ch = boundDeps.previewChannel ?: boundDeps.focusedChannel ?: return
        val full = boundDeps.channels.find { it.id == ch.id } ?: ch
        val prog = if (boundDeps.previewChannel?.id == boundDeps.focusedChannel?.id && !ui.focusOnChannelColumn) {
            boundDeps.focusedProgram
        } else {
            boundDeps.previewProgram
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
                val isFav = if (ch.id < 0) ch.id in boundDeps.demoFavoriteIds else ch.isFavorite
                boundDeps.viewModel.toggleFavorite(ch.id, isFav)
            }
            2 -> {
                if (prog == null) {
                    boundDeps.recordingViewModel.startImmediateRecording(boundDeps.context, full, full.name)
                } else if (prog.startTime <= System.currentTimeMillis()) {
                    val duration = (prog.endTime - System.currentTimeMillis()).coerceAtLeast(10 * 60 * 1000)
                    boundDeps.recordingViewModel.startImmediateRecording(boundDeps.context, full, prog.title, duration)
                } else {
                    boundDeps.recordingViewModel.scheduleProgram(full, prog)
                }
            }
        }
    }

    fun openNavDrawer() {
        focusNavDrawerItem(GuideNavDrawerItem.LiveView)
    }

    fun closeNavDrawer(restoreGrid: Boolean = true) {
        if (restoreGrid) {
            focusEpgZone(EpgFocusZone.GRID)
        }
    }

    private fun canShowChannelGroupsPanel(): Boolean =
        isLiveViewLayoutActive(ui.selectedTab, ui.guideSubScreen) &&
            (boundDeps.channelGroups.isNotEmpty() || boundDeps.viewModel.hasCatalogChannels.value)

    private fun showChannelGroupsPanel(): Boolean =
        canShowChannelGroupsPanel() && ui.channelGroupsPanelVisible

    fun focusNavDrawerItem(item: GuideNavDrawerItem) {
        ui.navDrawerFocusIndex = guideNavDrawerItemFocusIndex(item)
        focusEpgZone(EpgFocusZone.NAV_DRAWER)
    }

    fun collapseChannelGroupsPanel(focusGrid: Boolean = true) {
        cancelChannelGroupsLongPress()
        if (ui.focusZone == EpgFocusZone.CHANNEL_GROUPS) {
            rememberChannelGroupsRowFocus(ui.channelGroupsFocusIndex)
        }
        ui.channelGroupsPanelVisible = false
        if (focusGrid) {
            ui.focusOnChannelColumn = true
            focusEpgZone(EpgFocusZone.GRID)
            scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.NEUTRAL)
        }
    }

    fun openChannelGroupsPanel() {
        if (!canShowChannelGroupsPanel()) return
        boundDeps.viewModel.clearPreviewGuideFilter(reloadCommitted = true)
        val committedFilter = boundDeps.committedGuideFilter
        val visibleRows = flatVisibleGroupRows()
        ui.channelGroupsFocusIndex = resolveCurrentChannelGroupsFocusIndex(
            visibleRows = visibleRows,
            committedFilter = committedFilter,
            currentIndex = ui.lastChannelGroupsFocusIndex,
            lastRowKey = ui.lastChannelGroupRowKey,
        )
        rememberChannelGroupsRowFocus(ui.channelGroupsFocusIndex)
        ui.channelGroupsPanelVisible = true
        focusEpgZone(EpgFocusZone.CHANNEL_GROUPS)
        val row = flatVisibleGroupRows().getOrNull(ui.channelGroupsFocusIndex)
        if (row != null && guideChannelFilterForVisibleRow(row) != committedFilter) {
            previewChannelGroupForFocusedRow()
        }
    }

    fun onChannelGroupsFocusedIndexChanged(index: Int) {
        if (!ui.channelGroupsPanelVisible) return
        if (index != ui.channelGroupsFocusIndex) {
            cancelChannelGroupsLongPress()
        }
        rememberChannelGroupsRowFocus(index)
    }

    fun focusChannelGroupsFromGrid() {
        if (canShowChannelGroupsPanel()) {
            openChannelGroupsPanel()
        } else {
            focusNavDrawerItem(GuideNavDrawerItem.LiveView)
        }
    }

    private fun flatVisibleGroupRows(): List<GuideGroupVisibleRow> {
        val favorites = boundDeps.viewModel.favoriteChannelGroups.value
        return if (
            boundDeps.channelGroupNavigationMode == ChannelGroupNavigationMode.SMART &&
            boundDeps.guideGroupCategories.isNotEmpty()
        ) {
            buildFlatSmartBucketRows(boundDeps.guideGroupCategories, favorites)
        } else {
            buildFlatProviderVisibleRows(boundDeps.channelGroups, favorites)
        }
    }

    private fun resolveCurrentChannelGroupsFocusIndex(
        visibleRows: List<GuideGroupVisibleRow>,
        committedFilter: GuideChannelFilter,
        currentIndex: Int,
        lastRowKey: String?,
    ): Int {
        if (visibleRows.isEmpty()) return 0

        if (committedFilter.isActive) {
            val committedIndex = visibleRows.indexOfFirst { row ->
                row.isFocusableGroupRow() && guideChannelFilterForVisibleRow(row) == committedFilter
            }
            if (committedIndex >= 0) return committedIndex
        }

        if (!lastRowKey.isNullOrBlank()) {
            val keyedIndex = visibleRows.indexOfFirst { guideGroupVisibleRowKey(it) == lastRowKey }
            if (keyedIndex >= 0 && visibleRows[keyedIndex].isFocusableGroupRow()) {
                return keyedIndex
            }
        }

        val current = visibleRows.getOrNull(currentIndex)
        if (current != null && current.isFocusableGroupRow() &&
            guideChannelFilterForVisibleRow(current) == committedFilter
        ) {
            return currentIndex
        }

        return visibleRows.indexOfFirst { it.isFocusableGroupRow() }.coerceAtLeast(0)
    }

    private fun currentFocusedGroupKey(): String? {
        val row = flatVisibleGroupRows().getOrNull(ui.channelGroupsFocusIndex)
        return (row as? GuideGroupVisibleRow.Group)?.fullName
    }

    private fun cancelChannelGroupsLongPress() {
        channelGroupsPressJob?.cancel()
        channelGroupsPressJob = null
        channelGroupsPressedGroupKey = null
    }

    private fun rememberChannelGroupsRowFocus(index: Int) {
        val rows = flatVisibleGroupRows()
        val clamped = index.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        ui.channelGroupsFocusIndex = clamped
        ui.lastChannelGroupsFocusIndex = clamped
        ui.lastChannelGroupRowKey = rows.getOrNull(clamped)?.let(::guideGroupVisibleRowKey)
    }

    fun previewChannelGroupForFocusedRow() {
        if (!canShowChannelGroupsPanel() || ui.focusZone != EpgFocusZone.CHANNEL_GROUPS) return
        val row = flatVisibleGroupRows().getOrNull(ui.channelGroupsFocusIndex) ?: return
        val filter = guideChannelFilterForVisibleRow(row)
        if (filter == boundDeps.viewModel.currentGuideFilter()) return
        boundDeps.viewModel.previewGuideFilter(filter)
    }

    fun applyChannelGroupFilter(filter: GuideChannelFilter, refocusGrid: Boolean = true) {
        boundDeps.viewModel.setGuideFilter(filter, markConfigured = true)
        rememberChannelGroupsRowFocus(ui.channelGroupsFocusIndex)
        if (!refocusGrid) return
        ui.focusChannelIndex = 0
        ui.focusProgramIndex = 0
        ui.focusOnChannelColumn = true
        ui.focusChannelAfterGroupFilter = true
        ui.hasRequestedInitialGridFocus = false
    }

    /** Persist the focused group and optionally return focus to the live grid. */
    fun commitChannelGroupSelection(focusGrid: Boolean) {
        if (!canShowChannelGroupsPanel()) return
        val visibleRows = flatVisibleGroupRows()
        val row = visibleRows.getOrNull(ui.channelGroupsFocusIndex)
        if (row != null) {
            applyChannelGroupFilter(
                guideChannelFilterForVisibleRow(row),
                refocusGrid = focusGrid,
            )
        }
        if (focusGrid) {
            collapseChannelGroupsPanel(focusGrid = true)
        }
    }

    fun handleChannelGroupsCategoryToggle(categoryIndex: Int) {
        val expanded = ui.channelGroupsExpandedCategories.ifEmpty {
            expandedCategoriesForSelection(boundDeps.guideGroupCategories, boundDeps.guideFilter.selectedGroups)
        }
        ui.channelGroupsExpandedCategories = toggleCategoryExpansion(
            ui.channelGroupsExpandedCategories.ifEmpty { expanded },
            categoryIndex
        )
        val visibleRows = buildVisibleGuideGroupRows(boundDeps.guideGroupCategories, ui.channelGroupsExpandedCategories)
        ui.channelGroupsFocusIndex = visibleRows.indexOfFirst { row ->
            row is GuideGroupVisibleRow.Category && row.categoryIndex == categoryIndex
        }.coerceAtLeast(0)
    }

    fun returnToGridFromSidebar() {
        commitChannelGroupSelection(focusGrid = true)
    }

    fun handleChannelGroupsKey(event: KeyEvent): Boolean {
        if (!showChannelGroupsPanel()) return false
        val visibleRows = flatVisibleGroupRows()
        if (visibleRows.isEmpty()) return false

        if (boundDeps.channelGroupsListState.isScrollInProgress) {
            cancelChannelGroupsLongPress()
        }

        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
        ) {
            cancelChannelGroupsLongPress()
        }

        if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
            currentFocusedGroupKey()?.let(::toggleChannelGroupFavorite)
            return true
        }

        if (isTvActivateKey(event) || isTvActivateKeyUp(event)) {
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (boundDeps.channelGroupsListState.isScrollInProgress) return true
                    if (event.isTvLongPress()) return true

                    channelGroupsPressedGroupKey = currentFocusedGroupKey()
                    channelGroupsPressJob?.cancel()
                    channelGroupsPressJob = boundDeps.scope.launch {
                        delay(CHANNEL_GROUPS_LONG_PRESS_MS)
                        val pressed = channelGroupsPressedGroupKey
                        Log.d(
                            "FAVORITE_DEBUG",
                            "Pressed=$pressed, Focus=${currentFocusedGroupKey()}"
                        )
                        pressed?.let(::toggleChannelGroupFavorite)
                        channelGroupsPressedGroupKey = null
                        channelGroupsPressJob = null
                    }
                    ui.channelGroupsActivatePending = true
                    return true
                }
                KeyEventType.KeyUp -> {
                    cancelChannelGroupsLongPress()
                    if (!ui.channelGroupsActivatePending) return true
                    ui.channelGroupsActivatePending = false
                    val activeRow = visibleRows.getOrNull(ui.channelGroupsFocusIndex) ?: return true
                    if (!activeRow.isFocusableGroupRow()) return true
                    rememberChannelGroupsRowFocus(ui.channelGroupsFocusIndex)
                    applyChannelGroupFilter(guideChannelFilterForVisibleRow(activeRow))
                    collapseChannelGroupsPanel(focusGrid = true)
                    return true
                }
                else -> return false
            }
        }

        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.Back, Key.Escape -> {
                commitChannelGroupSelection(focusGrid = true)
                true
            }
            Key.DirectionLeft -> {
                when (sidebarHorizontalResult(event.key, allowLeftToRail = true)) {
                    SidebarContentFocus.SidebarHorizontalResult.OpenRail -> {
                        commitChannelGroupSelection(focusGrid = false)
                        focusEpgZone(EpgFocusZone.NAV_DRAWER)
                    }
                    else -> Unit
                }
                true
            }
            Key.DirectionRight -> {
                val focusedGroup = currentFocusedGroupKey()
                if (focusedGroup != null) {
                    toggleChannelGroupFavorite(focusedGroup)
                } else {
                    returnToGridFromSidebar()
                }
                true
            }
            Key.DirectionDown -> {
                val next = nextFocusableGroupRowIndex(visibleRows, ui.channelGroupsFocusIndex, delta = 1)
                if (next != ui.channelGroupsFocusIndex) {
                    rememberChannelGroupsRowFocus(next)
                    previewChannelGroupForFocusedRow()
                }
                true
            }
            Key.DirectionUp -> {
                val next = nextFocusableGroupRowIndex(visibleRows, ui.channelGroupsFocusIndex, delta = -1)
                if (next != ui.channelGroupsFocusIndex) {
                    rememberChannelGroupsRowFocus(next)
                    previewChannelGroupForFocusedRow()
                }
                true
            }
            else -> false
        }
    }

    fun selectDrawerItem(item: com.grid.tv.ui.component.GuideNavDrawerItem) {
        when (item) {
            com.grid.tv.ui.component.GuideNavDrawerItem.Search -> {
                ui.guideSubScreen = GuideSubScreen.Search
                ui.focusZone = EpgFocusZone.GRID
            }
            com.grid.tv.ui.component.GuideNavDrawerItem.LiveView -> {
                ui.guideSubScreen = null
                ui.selectedTab = EpgNavTab.Guide
                boundDeps.viewModel.setFavoriteGroupFilter(null)
                focusEpgZone(EpgFocusZone.GRID)
            }
            com.grid.tv.ui.component.GuideNavDrawerItem.Favorites -> {
                boundDeps.viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
                ui.selectedTab = EpgNavTab.Favorites
                ui.guideSubScreen = null
                focusEpgZone(EpgFocusZone.GRID)
            }
            com.grid.tv.ui.component.GuideNavDrawerItem.Vod -> {
                ui.guideSubScreen = null
                boundDeps.onNavigateVod(0)
            }
            com.grid.tv.ui.component.GuideNavDrawerItem.Recordings -> {
                ui.guideSubScreen = null
                boundDeps.onNavigateRecordings()
            }
        }
    }

    fun handleNavDrawerKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    ui.profileMenuOpen = false
                    true
                }
                else -> false
            }
        }
        val lastIndex = com.grid.tv.ui.component.GuideNavDrawerItems.size
        return when (event.key) {
            Key.DirectionLeft -> {
                if (ui.navDrawerFocusIndex == guideNavDrawerItemFocusIndex(GuideNavDrawerItem.LiveView) &&
                    canShowChannelGroupsPanel()
                ) {
                    openChannelGroupsPanel()
                    true
                } else {
                    false
                }
            }
            Key.Back, Key.Escape -> {
                if (ui.navDrawerFocusIndex > com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex) {
                    ui.navDrawerFocusIndex = com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex
                }
                true
            }
            Key.DirectionRight -> {
                if (ui.navDrawerFocusIndex == com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex) {
                    ui.navDrawerFocusIndex = guideNavDrawerItemFocusIndex(GuideNavDrawerItem.LiveView)
                } else if (boundDeps.displayChannels.isEmpty() && canShowChannelGroupsPanel()) {
                    openChannelGroupsPanel()
                } else {
                    focusEpgZone(EpgFocusZone.GRID)
                }
                true
            }
            Key.DirectionDown -> {
                if (ui.navDrawerFocusIndex < lastIndex) {
                    ui.navDrawerFocusIndex += 1
                }
                true
            }
            Key.DirectionUp -> {
                if (ui.navDrawerFocusIndex > com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex) {
                    ui.navDrawerFocusIndex -= 1
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (ui.navDrawerFocusIndex == com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex) {
                    ui.profileMenuOpen = true
                    ui.profileMenuFocusIndex = 0
                    true
                } else {
                    com.grid.tv.ui.component.GuideNavDrawerItems
                        .getOrNull(ui.navDrawerFocusIndex - 1)
                        ?.let { item ->
                            selectDrawerItem(item)
                            true
                        } ?: false
                }
            }
            else -> false
        }
    }

    fun focusEpgZone(zone: EpgFocusZone) {
        if (ui.guideSubScreen != null) return
        if (deps == null) {
            Log.w(TAG, "Focus requested before deps initialized zone=$zone")
            return
        }
        ui.focusZone = zone
        if (zone == EpgFocusZone.PREVIEW) ui.detailActionIndex = 0
    }

    fun focusGuideChannels(targetIndex: Int = ui.focusChannelIndex) {
        ui.focusChannelIndex = targetIndex.coerceIn(0, boundDeps.displayChannels.lastIndex.coerceAtLeast(0))
        ui.focusOnChannelColumn = true
        focusEpgZone(EpgFocusZone.GRID)
    }

    fun focusPreviewSection() {
        if (boundDeps.showPreviewSection) {
            focusEpgZone(EpgFocusZone.PREVIEW)
        } else {
            focusGuideChannels()
        }
    }

    fun moveGuideFocusVertical(from: EpgFocusZone, direction: Int) {
        val target = if (direction < 0) {
            epgZoneAbove(from, boundDeps.showPreviewSection, boundDeps.hasContinueWatching)
        } else {
            epgZoneBelow(from, boundDeps.showPreviewSection, boundDeps.hasContinueWatching)
        }
        target?.let(::focusEpgZone)
    }

    fun openCategoryFilterMenu() {
        if (ui.selectedTab == EpgNavTab.Favorites) return
        val expanded = expandedCategoriesForSelection(
            boundDeps.guideGroupCategories,
            boundDeps.guideFilter.selectedGroups
        )
        ui.categoryMenuExpandedCategories = expanded
        ui.categoryMenuFocusIndex = 0
        ui.showCategoryFilterMenu = true
    }

    fun handleCategoryFilterMenuToggle(index: Int) {
        val expanded = ui.categoryMenuExpandedCategories.ifEmpty {
            expandedCategoriesForSelection(boundDeps.guideGroupCategories, boundDeps.guideFilter.selectedGroups)
        }
        val visibleRows = buildVisibleGuideGroupRows(boundDeps.guideGroupCategories, expanded)
        val row = visibleRows.getOrNull(index) ?: return
        when (row) {
            is GuideGroupVisibleRow.Category -> {
                ui.categoryMenuExpandedCategories = toggleCategoryExpansion(
                    ui.categoryMenuExpandedCategories,
                    row.categoryIndex
                )
                ui.categoryMenuFocusIndex = index
            }
            is GuideGroupVisibleRow.SelectAll,
            is GuideGroupVisibleRow.Group -> {
                val next = guideFilterRowAction(row, boundDeps.guideFilter.selectedGroups) ?: return
                boundDeps.viewModel.setGuideFilter(next, markConfigured = true)
            }
            GuideGroupVisibleRow.FavoriteSectionHeader -> Unit
            GuideGroupVisibleRow.FavoriteSectionEmpty -> Unit
            GuideGroupVisibleRow.AllChannels -> {
                boundDeps.viewModel.setGuideFilter(GuideChannelFilter.All, markConfigured = true)
                ui.showCategoryFilterMenu = false
            }
        }
    }

    fun handleContinueWatchingKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.guideSubScreen != null) return false
        if (ui.focusZone == EpgFocusZone.NAV_DRAWER) return false
        if (ui.focusZone == EpgFocusZone.CHANNEL_GROUPS) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (boundDeps.continueWatchingItems.isEmpty()) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                ui.focusedContinueIndex = (ui.focusedContinueIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                ui.focusedContinueIndex = (ui.focusedContinueIndex + 1)
                    .coerceAtMost(boundDeps.continueWatchingItems.lastIndex)
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
                boundDeps.continueWatchingItems.getOrNull(ui.focusedContinueIndex)?.let { item ->
                    if (boundDeps.viewModel.isProfileAccessAllowed()) {
                        boundDeps.onResumeContinueWatching(item)
                    }
                }
                true
            }
            Key.Back, Key.Escape -> {
                focusEpgZone(EpgFocusZone.NAV_DRAWER)
                true
            }
            else -> false
        }
    }

    fun handlePreviewKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.guideSubScreen != null) return false
        if (ui.focusZone == EpgFocusZone.NAV_DRAWER) return false
        if (ui.focusZone == EpgFocusZone.CHANNEL_GROUPS) return false
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
                GuideNavigationLogger.backPressed(
                    zone = ui.focusZone.name,
                    channelIndex = ui.focusChannelIndex,
                    onChannelColumn = ui.focusOnChannelColumn,
                    detailExpanded = ui.detailExpanded,
                    branch = "preview_to_grid"
                )
                ui.detailExpanded = false
                focusEpgZone(EpgFocusZone.GRID)
                true
            }
            else -> false
        }
    }

    private fun scrollFocusedChannelIntoView(direction: TvLazyFocusScrollDirection) {
        channelScrollJob?.cancel()
        channelScrollJob = boundDeps.scope.launch {
            val rowHeightPx = with(boundDeps.density) { EpgLayout.RowHeight.roundToPx() }
            boundDeps.listState.animateScrollEpgChannelIntoView(
                index = ui.focusChannelIndex,
                direction = direction,
                rowHeightPx = rowHeightPx
            )
        }
    }

    fun handleGridKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (ui.guideSubScreen != null) return false
        if (ui.profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    ui.profileMenuOpen = false
                    true
                }
                else -> false
            }
        }
        if (ui.focusZone == EpgFocusZone.NAV_DRAWER) return false
        if (ui.focusZone == EpgFocusZone.CHANNEL_GROUPS) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (ui.showCategoryFilterMenu || ui.showGuideGroupPicker) return false
        if (boundDeps.displayChannels.isEmpty()) {
            return when (event.key) {
                Key.DirectionLeft -> {
                    focusChannelGroupsFromGrid()
                    true
                }
                Key.DirectionUp -> {
                    focusNavDrawerItem(GuideNavDrawerItem.LiveView)
                    true
                }
                else -> false
            }
        }

        return when (event.key) {
            Key.DirectionDown -> {
                if (ui.focusChannelIndex < boundDeps.displayChannels.lastIndex) {
                    val anchorTime = programAnchorTimeMs()
                    ui.focusChannelIndex += 1
                    if (!ui.focusOnChannelColumn) {
                        ui.focusProgramIndex = alignProgramIndexToTime(ui.focusChannelIndex, anchorTime)
                        syncFocusedProgramScroll()
                    }
                    scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.DOWN)
                }
                true
            }
            Key.DirectionUp -> {
                if (ui.focusChannelIndex > 0) {
                    val anchorTime = programAnchorTimeMs()
                    ui.focusChannelIndex -= 1
                    if (!ui.focusOnChannelColumn) {
                        ui.focusProgramIndex = alignProgramIndexToTime(ui.focusChannelIndex, anchorTime)
                        syncFocusedProgramScroll()
                    }
                    scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.UP)
                    true
                } else {
                    focusNavDrawerItem(GuideNavDrawerItem.LiveView)
                    true
                }
            }
            Key.DirectionRight -> {
                val channel = boundDeps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return false
                val progs = programsForChannelTimed(channel, "DirectionRight")
                if (ui.focusOnChannelColumn) {
                    ui.focusOnChannelColumn = false
                    ui.focusProgramIndex = alignProgramIndexToTime(ui.focusChannelIndex, viewportCenterTimeMs())
                    scrollToProgram(progs.getOrNull(ui.focusProgramIndex))
                } else if (ui.focusProgramIndex < progs.lastIndex) {
                    ui.focusProgramIndex += 1
                    scrollToProgram(progs[ui.focusProgramIndex])
                } else {
                    val last = progs.lastOrNull()
                    val windowEnd = boundDeps.windowStart + boundDeps.windowDurationMs
                    if (last != null && last.endTime >= windowEnd - 15 * 60 * 1000) {
                        boundDeps.viewModel.extendWindowForward()
                    }
                    scrollByTimeSlot(1)
                }
                true
            }
            Key.DirectionLeft -> {
                if (!ui.focusOnChannelColumn) {
                    val channel = boundDeps.displayChannels.getOrNull(ui.focusChannelIndex)
                    val progs = channel?.let { programsForChannelTimed(it, "DirectionLeft") }.orEmpty()
                    if (ui.focusProgramIndex > 0) {
                        ui.focusProgramIndex -= 1
                        scrollToProgram(progs[ui.focusProgramIndex])
                    } else {
                        ui.focusOnChannelColumn = true
                    }
                } else {
                    focusChannelGroupsFromGrid()
                }
                true
            }
            Key.Menu -> {
                focusNavDrawerItem(GuideNavDrawerItem.LiveView)
                true
            }
            Key.PageDown -> {
                val anchorTime = programAnchorTimeMs()
                ui.focusChannelIndex = (ui.focusChannelIndex + 10).coerceAtMost(boundDeps.displayChannels.lastIndex)
                if (!ui.focusOnChannelColumn) {
                    ui.focusProgramIndex = alignProgramIndexToTime(ui.focusChannelIndex, anchorTime)
                    syncFocusedProgramScroll()
                }
                scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.DOWN)
                true
            }
            Key.PageUp -> {
                val anchorTime = programAnchorTimeMs()
                ui.focusChannelIndex = (ui.focusChannelIndex - 10).coerceAtLeast(0)
                if (!ui.focusOnChannelColumn) {
                    ui.focusProgramIndex = alignProgramIndexToTime(ui.focusChannelIndex, anchorTime)
                    syncFocusedProgramScroll()
                }
                scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.UP)
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                val channel = boundDeps.displayChannels.getOrNull(ui.focusChannelIndex) ?: return true
                if (!ui.focusOnChannelColumn) {
                    val prog = programsForChannelTimed(channel, "Enter").getOrNull(ui.focusProgramIndex)
                    if (prog != null) {
                        val replay = boundDeps.viewModel.replayState(
                            prog,
                            boundDeps.channels.find { it.id == channel.id } ?: channel,
                            System.currentTimeMillis()
                        )
                        if (replay.action == EpgProgramAction.WATCH_REPLAY) {
                            playProgram(channel, prog, instant = true)
                        } else {
                            openPreviewForChannel(channel)
                        }
                        return true
                    }
                }
                openPreviewForChannel(channel)
                true
            }
            Key.Back, Key.Escape -> {
                if (ui.focusZone == EpgFocusZone.CHANNEL_GROUPS) {
                    focusEpgZone(EpgFocusZone.NAV_DRAWER)
                    return true
                }
                if (ui.focusZone == EpgFocusZone.NAV_DRAWER) {
                    focusEpgZone(EpgFocusZone.GRID)
                    return true
                }
                if (ui.detailExpanded) {
                    GuideNavigationLogger.backPressed(
                        zone = ui.focusZone.name,
                        channelIndex = ui.focusChannelIndex,
                        onChannelColumn = ui.focusOnChannelColumn,
                        detailExpanded = true,
                        branch = "collapse_detail"
                    )
                    ui.detailExpanded = false
                    focusEpgZone(EpgFocusZone.GRID)
                    true
                } else {
                    val playingChannelId = boundDeps.viewModel.lastPlayedChannel.value?.id
                        ?: boundDeps.viewModel.livePlayerManager.playbackUiState.value.activeChannelId
                    val playingIndex = playingChannelId?.let { id ->
                        boundDeps.displayChannels.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                    }
                    when {
                        playingIndex != null && ui.focusChannelIndex != playingIndex -> {
                            GuideNavigationLogger.backPressed(
                                zone = ui.focusZone.name,
                                channelIndex = ui.focusChannelIndex,
                                onChannelColumn = ui.focusOnChannelColumn,
                                detailExpanded = false,
                                branch = "restore_current_channel"
                            )
                            GuideNavigationLogger.focusRestoreCurrent(playingIndex)
                            GuideNavigationLogger.currentChannelIndex(playingIndex)
                            ui.focusChannelIndex = playingIndex
                            if (!ui.focusOnChannelColumn) {
                                ui.focusProgramIndex = clampProgramIndex(playingIndex, ui.focusProgramIndex)
                            }
                            scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.NEUTRAL)
                            true
                        }
                        ui.focusChannelIndex > 0 -> {
                            GuideNavigationLogger.backPressed(
                                zone = ui.focusZone.name,
                                channelIndex = ui.focusChannelIndex,
                                onChannelColumn = ui.focusOnChannelColumn,
                                detailExpanded = false,
                                branch = "restore_top_channel"
                            )
                            GuideNavigationLogger.focusRestoreTop()
                            GuideNavigationLogger.scrollToTop(0)
                            ui.focusChannelIndex = 0
                            if (!ui.focusOnChannelColumn) {
                                ui.focusProgramIndex = clampProgramIndex(0, ui.focusProgramIndex)
                            }
                            scrollFocusedChannelIntoView(TvLazyFocusScrollDirection.UP)
                            true
                        }
                        else -> {
                            GuideNavigationLogger.backPressed(
                                zone = ui.focusZone.name,
                                channelIndex = ui.focusChannelIndex,
                                onChannelColumn = ui.focusOnChannelColumn,
                                detailExpanded = false,
                                branch = "restore_top_filter"
                            )
                            GuideNavigationLogger.focusRestoreTop()
                            focusEpgZone(EpgFocusZone.NAV_DRAWER)
                            true
                        }
                    }
                }
            }
            else -> false
        }
    }
}
