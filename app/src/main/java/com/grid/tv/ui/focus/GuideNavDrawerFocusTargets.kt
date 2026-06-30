package com.grid.tv.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.component.GuideNavDrawerItems
import com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex

/** Hoisted nav-rail focus targets — owned by the screen, wired into [GuideNavDrawer]. */
@Stable
class GuideNavDrawerFocusTargets {
    val profileFocusRequester = FocusRequester()
    private val itemFocusRequesters = List(GuideNavDrawerItems.size) { FocusRequester() }

    fun forIndex(focusIndex: Int): FocusRequester = when (focusIndex) {
        GuideNavDrawerProfileFocusIndex -> profileFocusRequester
        else -> itemFocusRequesters.getOrElse(focusIndex - 1) { profileFocusRequester }
    }
}

@Composable
fun rememberGuideNavDrawerFocusTargets(): GuideNavDrawerFocusTargets =
    remember { GuideNavDrawerFocusTargets() }
