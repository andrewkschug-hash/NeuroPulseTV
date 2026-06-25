package com.grid.tv.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

enum class GuideNavDrawerItem(val label: String) {
    Search("Search"),
    ChannelGroups("Channel Groups"),
    Vod("On Demand"),
    Favorites("Favorites"),
    RecentChannels("Recent Channels"),
    Recordings("Recordings"),
    MultiView("MultiView"),
    Profile("Profile"),
    Settings("Settings")
}

val GuideNavDrawerItems = GuideNavDrawerItem.entries

/** Collapsed rail width — keep in sync with [GuideNavDrawer] animation targets. */
val GuideNavDrawerCollapsedWidth = 48.dp

/** Expanded drawer width — keep in sync with [GuideNavDrawer] animation targets. */
val GuideNavDrawerExpandedWidth = 340.dp

@Composable
fun GuideNavDrawer(
    expanded: Boolean,
    focusedIndex: Int,
    drawerFocusRequester: FocusRequester,
    onItemFocused: (Int) -> Unit,
    onItemSelected: (GuideNavDrawerItem) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    onExpandRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val width by animateDpAsState(
        targetValue = if (expanded) GuideNavDrawerExpandedWidth else GuideNavDrawerCollapsedWidth,
        animationSpec = tween(durationMillis = 180),
        label = "guideNavDrawerWidth"
    )
    val collapsed = !expanded
    val trailingRequesters = remember {
        List((GuideNavDrawerItems.size - 1).coerceAtLeast(0)) { FocusRequester() }
    }

    fun requesterFor(index: Int): FocusRequester = when {
        index == 0 -> drawerFocusRequester
        index - 1 in trailingRequesters.indices -> trailingRequesters[index - 1]
        else -> drawerFocusRequester
    }

    LaunchedEffect(focusedIndex, expanded) {
        if (expanded && GuideNavDrawerItems.isNotEmpty()) {
            val index = focusedIndex.coerceIn(0, GuideNavDrawerItems.lastIndex)
            requesterFor(index).requestFocusSafelyAfterLayout()
        }
    }

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(EpgColors.DetailPanelBg.copy(alpha = 0.98f))
            .onPreviewKeyEvent(onPreviewKey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = if (collapsed) 6.dp else 16.dp)
                .focusGroup()
        ) {
            if (collapsed) {
                GlowFocusButton(
                    onClick = onExpandRequest,
                    modifier = Modifier
                        .focusRequester(drawerFocusRequester)
                        .onFocusChanged { if (it.isFocused) onItemFocused(0) },
                    contentDescription = "Open menu"
                ) {
                    Text(
                        text = "☰",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 20.sp
                    )
                }
            } else {
                GuideNavDrawerItems.forEachIndexed { index, item ->
                    val focused = focusedIndex == index
                    GlowFocusButton(
                        onClick = { onItemSelected(item) },
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .focusRequester(requesterFor(index))
                            .onFocusChanged {
                                if (it.isFocused) onItemFocused(index)
                            },
                        externallyFocused = focused
                    ) {
                        Text(
                            text = item.label,
                            color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
