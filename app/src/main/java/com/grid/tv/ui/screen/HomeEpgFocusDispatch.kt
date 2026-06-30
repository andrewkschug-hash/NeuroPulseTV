package com.grid.tv.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.focus.GuideNavDrawerFocusTargets
import com.grid.tv.ui.focus.TvFocusDispatcher
import com.grid.tv.ui.focus.dispatchFocus

internal data class HomeEpgFocusDispatchContext(
    val guideSubScreenOpen: Boolean,
    val channelGroupsPanelVisible: Boolean,
    val hasContinueWatching: Boolean,
    val showPreviewSection: Boolean,
    val pendingPreviewFocus: Boolean,
)

@Composable
internal fun HomeEpgFocusDispatcher(
    ui: HomeEpgUiState,
    navDrawerTargets: GuideNavDrawerFocusTargets,
    channelGroupsPanelFocusRequester: FocusRequester,
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
        context.pendingPreviewFocus,
    ) {
        when (ui.focusZone) {
            EpgFocusZone.NAV_DRAWER ->
                navDrawerTargets.forIndex(ui.navDrawerFocusIndex).dispatchFocus()
            EpgFocusZone.CHANNEL_GROUPS ->
                if (context.channelGroupsPanelVisible) {
                    channelGroupsPanelFocusRequester.dispatchFocus()
                }
            EpgFocusZone.CONTINUE_WATCHING ->
                if (context.hasContinueWatching) {
                    continueWatchingFocusRequester.dispatchFocus()
                }
            EpgFocusZone.PREVIEW ->
                previewFocusRequester.dispatchFocus()
            EpgFocusZone.GRID ->
                gridFocusRequester.dispatchFocus()
        }
    }
}
