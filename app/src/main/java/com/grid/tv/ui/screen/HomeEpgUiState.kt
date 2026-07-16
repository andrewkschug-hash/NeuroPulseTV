package com.grid.tv.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grid.tv.ui.component.EpgNavTab

@Stable
internal class HomeEpgUiState {
    var selectedTab by mutableStateOf(EpgNavTab.Guide)
    var profileMenuOpen by mutableStateOf(false)
    var profileMenuFocusIndex by mutableIntStateOf(0)
    var focusZone by mutableStateOf(EpgFocusZone.GRID)
    var topBarFocusIndex by mutableIntStateOf(0)
    var focusedContinueIndex by mutableIntStateOf(0)
    var focusChannelIndex by mutableIntStateOf(0)
    var focusProgramIndex by mutableIntStateOf(0)
    var focusOnChannelColumn by mutableStateOf(true)
    var detailExpanded by mutableStateOf(false)
    var detailActionIndex by mutableIntStateOf(0)
    var showFavoritePicker by mutableStateOf(false)
    var showCreateGroup by mutableStateOf(false)
    var newGroupName by mutableStateOf("")
    var showGuideGroupPicker by mutableStateOf(false)
    var navDrawerOpen by mutableStateOf(false)
    var navDrawerFocusIndex by mutableIntStateOf(
        com.grid.tv.ui.component.guideNavDrawerItemFocusIndex(
            com.grid.tv.ui.component.GuideNavDrawerItem.LiveView
        )
    )
    var guideSubScreen by mutableStateOf<GuideSubScreen?>(null)
    var showCategoryFilterMenu by mutableStateOf(false)
    var categoryMenuFocusIndex by mutableIntStateOf(0)
    var categoryMenuExpandedCategories by mutableStateOf(setOf<Int>())
    var showSearchOverlay by mutableStateOf(false)
    var didInitialScroll by mutableStateOf(false)
    var didRestoreGuide by mutableStateOf(false)
    var hasRequestedInitialGridFocus by mutableStateOf(false)
    /** True while opening preview; blocks spurious filter focus during grid→preview transition. */
    /** Short-press Enter on channel groups: defer until KeyUp so long-press can toggle favourites. */
    var channelGroupsActivatePending by mutableStateOf(false)
    var channelGroupsFocusIndex by mutableIntStateOf(0)
    /** Last highlighted group row — restored when reopening the panel from the live grid. */
    var lastChannelGroupsFocusIndex by mutableIntStateOf(0)
    /** Stable row identity (see [guideGroupVisibleRowKey]) — survives list regroup/reorder. */
    var lastChannelGroupRowKey by mutableStateOf<String?>(null)
    var channelGroupsExpandedCategories by mutableStateOf(setOf<Int>())
    /**
     * Channel groups overlay beside the icon rail. Opened only by explicit confirm
     * (OK/Select on the Live sidebar icon) — never by D-pad focus alone.
     */
    var channelGroupsPanelVisible by mutableStateOf(false)
    /**
     * When closing the channel-groups overlay (or returning Right from the sidebar),
     * restore channel-list vs program-grid focus from before the overlay/sidebar visit.
     */
    var restoreFocusOnChannelColumn by mutableStateOf(true)
    /** After committing a group filter, land on channel 0 once the filtered list reloads. */
    var focusChannelAfterGroupFilter by mutableStateOf(false)
}
