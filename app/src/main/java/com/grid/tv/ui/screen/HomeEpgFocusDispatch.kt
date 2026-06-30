package com.grid.tv.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.focus.GuideChannelGroupsFocusRegistry
import com.grid.tv.ui.focus.GuideNavDrawerFocusTargets
import com.grid.tv.ui.focus.TvFocusDispatcher
import com.grid.tv.ui.focus.dispatchFocus

internal data class HomeEpgFocusDispatchContext(
    val guideSubScreenOpen: Boolean,
    val channelGroupsPanelVisible: Boolean,
    val hasContinueWatching: Boolean,
    val showPreviewSection: Boolean,
    val visibleChannelGroupRows: List<GuideGroupVisibleRow>,
    val channelGroupsFocusRegistry: GuideChannelGroupsFocusRegistry,
)

@Composable
internal fun HomeEpgFocusDispatcher(
    ui: HomeEpgUiState,
    navDrawerTargets: GuideNavDrawerFocusTargets,
    continueWatchingFocusRequester: FocusRequester,
    previewFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    context: HomeEpgFocusDispatchContext,
) {
    TvFocusDispatcher(
        enabled = !context.guideSubScreenOpen,
        ui.focusZone,
        ui.navDrawerFocusIndex,
        ui.channelGroupsFocusIndex,
        context.channelGroupsPanelVisible,
        context.hasContinueWatching,
        context.showPreviewSection,
        context.visibleChannelGroupRows.size,
    ) {
        when (ui.focusZone) {
            EpgFocusZone.NAV_DRAWER ->
                navDrawerTargets.forIndex(ui.navDrawerFocusIndex).dispatchFocus()
            EpgFocusZone.CHANNEL_GROUPS ->
                if (context.channelGroupsPanelVisible) {
                    context.channelGroupsFocusRegistry
                        .requesterForIndex(context.visibleChannelGroupRows, ui.channelGroupsFocusIndex)
                        ?.dispatchFocus()
                }
            EpgFocusZone.CONTINUE_WATCHING ->
                if (context.hasContinueWatching) {
                    continueWatchingFocusRequester.dispatchFocus()
                }
            EpgFocusZone.PREVIEW ->
                if (context.showPreviewSection) {
                    previewFocusRequester.dispatchFocus()
                }
            EpgFocusZone.GRID ->
                gridFocusRequester.dispatchFocus()
        }
    }
}
