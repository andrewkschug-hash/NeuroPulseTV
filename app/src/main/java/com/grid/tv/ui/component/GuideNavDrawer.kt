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
import androidx.compose.runtime.getValue
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
    modifier: Modifier = Modifier
) {
    val width by animateDpAsState(
        targetValue = if (expanded) GuideNavDrawerExpandedWidth else GuideNavDrawerCollapsedWidth,
        animationSpec = tween(durationMillis = 180),
        label = "guideNavDrawerWidth"
    )
    val collapsed = !expanded

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
                Text(
                    text = "☰",
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            } else {
                GuideNavDrawerItems.forEachIndexed { index, item ->
                    val focused = focusedIndex == index
                    GlowFocusButton(
                        onClick = { onItemSelected(item) },
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(drawerFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged {
                                if (it.isFocused) onItemFocused(index)
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (index < GuideNavDrawerItems.lastIndex) {
                                            onItemFocused(index + 1)
                                        }
                                        false
                                    }
                                    Key.DirectionUp -> {
                                        if (index > 0) onItemFocused(index - 1)
                                        false
                                    }
                                    else -> false
                                }
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
