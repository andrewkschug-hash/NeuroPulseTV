package com.grid.tv.ui.screen

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Density
import com.grid.tv.domain.epg.ProgrammeIndex
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.GuideNavDrawerItem
import com.grid.tv.ui.component.guideNavDrawerItemFocusIndex
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEpgFocusNavigationTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun sampleChannel(id: Long = 1L) = Channel(
        id = id,
        number = 1,
        name = "News",
        group = "News",
        logoUrl = null,
        epgId = null,
        streamUrl = "http://example.com/stream",
        playlistId = 1L,
        isFavorite = false,
    )

    private fun setupController(
        channels: List<Channel> = listOf(sampleChannel()),
        channelGroups: List<String> = listOf("News", "Sports"),
        showPreviewSection: Boolean = true,
        hasContinueWatching: Boolean = false,
        continueWatchingItems: List<ContinueWatchingItem> = emptyList(),
        initialZone: EpgFocusZone = EpgFocusZone.GRID,
        channelGroupsPanelVisible: Boolean = false,
    ): Pair<HomeEpgUiState, HomeEpgGuideController> {
        val ui = HomeEpgUiState()
        ui.selectedTab = EpgNavTab.Guide
        ui.focusZone = initialZone
        ui.channelGroupsPanelVisible = channelGroupsPanelVisible
        val controller = HomeEpgGuideController(ui)

        val context = mockk<Context>(relaxed = true)
        val viewModel = mockk<HomeEpgViewModel>(relaxed = true)
        every { viewModel.hasCatalogChannels } returns MutableStateFlow(true)
        every { viewModel.favoriteChannelGroups } returns MutableStateFlow(emptyList())
        every { viewModel.isProfileAccessAllowed() } returns true

        val hScroll = mockk<ScrollState>(relaxed = true)
        every { hScroll.value } returns 0
        every { hScroll.maxValue } returns 0

        val listState = mockk<LazyListState>(relaxed = true)

        controller.bind(
            HomeEpgGuideDeps(
                context = context,
                scope = scope,
                listState = listState,
                hScroll = hScroll,
                channels = channels,
                displayChannels = channels,
                programmeIndex = ProgrammeIndex.EMPTY,
                viewModel = viewModel,
                recordingViewModel = mockk<RecordingViewModel>(relaxed = true),
                searchViewModel = mockk<SearchViewModel>(relaxed = true),
                windowStart = System.currentTimeMillis(),
                windowDurationMs = 3 * 60 * 60 * 1000L,
                density = Density(1f),
                gridFocusRequester = FocusRequester(),
                gridFilterFocusRequester = FocusRequester(),
                channelGroups = channelGroups,
                continueWatchingFocusRequester = FocusRequester(),
                previewFocusRequester = FocusRequester(),
                previewChannel = channels.firstOrNull(),
                focusedChannel = channels.firstOrNull(),
                focusedProgram = null,
                previewProgram = null,
                previewNextProgram = null,
                guideGroupCategories = emptyList(),
                guideFilter = GuideChannelFilter.All,
                committedGuideFilter = GuideChannelFilter.All,
                demoFavoriteIds = emptySet(),
                vodProgress = emptyMap(),
                continueWatchingItems = continueWatchingItems,
                showPreviewSection = showPreviewSection,
                hasContinueWatching = hasContinueWatching,
                usePlaceholder = false,
                onWatchChannel = {},
                onPlayCatchup = { _, _ -> },
                onNavigateRecordings = {},
                onNavigateSettings = {},
                onNavigateProfile = {},
                onNavigateMultiview = {},
                onNavigateVod = {},
                onNavigateSeries = { _, _ -> },
                onPlayVod = { _, _, _ -> },
                onResumeContinueWatching = {},
            )
        )
        return ui to controller
    }

    @Test
    fun epgZoneBelow_preview_movesToGrid() {
        assertEquals(
            EpgFocusZone.GRID,
            epgZoneBelow(EpgFocusZone.PREVIEW, showPreview = true, hasContinueWatching = false),
        )
    }

    @Test
    fun epgZoneAbove_grid_withPreview_movesToPreview() {
        assertEquals(
            EpgFocusZone.PREVIEW,
            epgZoneAbove(EpgFocusZone.GRID, showPreview = true, hasContinueWatching = false),
        )
    }

    @Test
    fun epgZoneAbove_grid_withContinueWatching_movesToContinueWatching() {
        assertEquals(
            EpgFocusZone.CONTINUE_WATCHING,
            epgZoneAbove(EpgFocusZone.GRID, showPreview = false, hasContinueWatching = true),
        )
    }

    @Test
    fun focusNavDrawerItem_transitionsToNavDrawerZone() {
        val (ui, controller) = setupController(initialZone = EpgFocusZone.GRID)
        controller.focusNavDrawerItem(GuideNavDrawerItem.Search)
        assertEquals(EpgFocusZone.NAV_DRAWER, ui.focusZone)
        assertEquals(guideNavDrawerItemFocusIndex(GuideNavDrawerItem.Search), ui.navDrawerFocusIndex)
    }

    @Test
    fun openChannelGroupsPanel_transitionsToChannelGroupsZone() {
        val (ui, controller) = setupController(initialZone = EpgFocusZone.GRID)
        controller.openChannelGroupsPanel()
        assertEquals(EpgFocusZone.CHANNEL_GROUPS, ui.focusZone)
        assertTrue(ui.channelGroupsPanelVisible)
    }

    @Test
    fun collapseChannelGroupsPanel_returnsToGrid() {
        val (ui, controller) = setupController(
            initialZone = EpgFocusZone.CHANNEL_GROUPS,
            channelGroupsPanelVisible = true,
        )
        controller.collapseChannelGroupsPanel(focusGrid = true)
        assertEquals(EpgFocusZone.GRID, ui.focusZone)
        assertFalse(ui.channelGroupsPanelVisible)
    }

    @Test
    fun focusChannelGroupsFromGrid_opensPanel() {
        val (ui, controller) = setupController(initialZone = EpgFocusZone.GRID)
        ui.focusOnChannelColumn = true
        controller.focusChannelGroupsFromGrid()
        assertEquals(EpgFocusZone.CHANNEL_GROUPS, ui.focusZone)
        assertTrue(ui.channelGroupsPanelVisible)
    }

    @Test
    fun openPreviewForChannel_transitionsToPreviewZone() {
        val (ui, controller) = setupController(initialZone = EpgFocusZone.GRID)
        controller.openPreviewForChannel(sampleChannel())
        assertEquals(EpgFocusZone.PREVIEW, ui.focusZone)
        assertTrue(ui.detailExpanded)
        assertEquals(0, ui.detailActionIndex)
    }

    @Test
    fun moveGuideFocusVertical_fromPreview_toGrid() {
        val (ui, controller) = setupController(
            initialZone = EpgFocusZone.PREVIEW,
            showPreviewSection = true,
        )
        controller.moveGuideFocusVertical(EpgFocusZone.PREVIEW, direction = 1)
        assertEquals(EpgFocusZone.GRID, ui.focusZone)
    }

    @Test
    fun moveGuideFocusVertical_fromGrid_toPreview() {
        val (ui, controller) = setupController(
            initialZone = EpgFocusZone.GRID,
            showPreviewSection = true,
        )
        controller.moveGuideFocusVertical(EpgFocusZone.GRID, direction = -1)
        assertEquals(EpgFocusZone.PREVIEW, ui.focusZone)
    }

    @Test
    fun transitionToZone_updatesFocusZone() {
        val (ui, controller) = setupController(initialZone = EpgFocusZone.GRID)
        controller.transitionToZone(EpgFocusZone.NAV_DRAWER, "test")
        assertEquals(EpgFocusZone.NAV_DRAWER, ui.focusZone)
    }

    @Test
    fun handleKey_ignoresKeysWhenGuideSubScreenOpen() {
        val (ui, controller) = setupController(initialZone = EpgFocusZone.GRID)
        ui.guideSubScreen = GuideSubScreen.Search
        assertFalse(controller.handleKey(mockk(relaxed = true)))
    }

    @Test
    fun applyChannelGroupFilter_withoutRefocusGrid_doesNotQueueGridFocus() {
        val (ui, controller) = setupController(
            initialZone = EpgFocusZone.CHANNEL_GROUPS,
            channelGroupsPanelVisible = true,
        )
        controller.applyChannelGroupFilter(GuideChannelFilter.All, refocusGrid = false)
        assertFalse(ui.focusChannelAfterGroupFilter)
        assertEquals(EpgFocusZone.CHANNEL_GROUPS, ui.focusZone)
    }
}
