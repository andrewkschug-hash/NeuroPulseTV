package com.grid.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
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
    Settings("Settings")
}

val GuideNavDrawerItems = GuideNavDrawerItem.entries

/** Collapsed rail width — keep in sync with [GuideNavDrawer] animation targets. */
val GuideNavDrawerCollapsedWidth = 48.dp

/** Expanded icon-rail width — keep in sync with [GuideNavDrawer] animation targets. */
val GuideNavDrawerExpandedWidth = 96.dp

private val DrawerButtonShape = RoundedCornerShape(8.dp)
private val DrawerIconSize = NavIconHitSize

private fun GuideNavDrawerItem.navTabIcon(): EpgNavTab? = when (this) {
    GuideNavDrawerItem.Search -> EpgNavTab.Search
    GuideNavDrawerItem.Vod -> EpgNavTab.Vod
    GuideNavDrawerItem.Favorites -> EpgNavTab.Favorites
    GuideNavDrawerItem.Recordings -> EpgNavTab.Recordings
    GuideNavDrawerItem.Settings -> EpgNavTab.Settings
    else -> null
}

private fun GuideNavDrawerItem.glyphIcon(): String = when (this) {
    GuideNavDrawerItem.ChannelGroups -> "☰"
    GuideNavDrawerItem.RecentChannels -> "◷"
    GuideNavDrawerItem.MultiView -> "⊞"
    else -> "•"
}

@Composable
private fun GuideNavDrawerIcon(
    item: GuideNavDrawerItem,
    tint: Color,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val navTab = item.navTabIcon()
    if (navTab != null) {
        GridNavTabIcon(
            tab = navTab,
            tint = tint,
            selected = selected && item == GuideNavDrawerItem.Recordings,
            modifier = modifier
        )
    } else {
        Text(
            text = item.glyphIcon(),
            color = tint,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            modifier = modifier
        )
    }
}

@Composable
private fun DrawerIconButton(
    item: GuideNavDrawerItem,
    focused: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val showFocused = isFocused || focused
    val iconTint = when {
        showFocused -> EpgColors.Accent
        selected -> EpgColors.TextPrimary
        else -> EpgColors.TextSecondary
    }

    Box(modifier = modifier) {
        GridFocusSurface(
            onClick = onClick,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .tvFocusBorder(
                    focused = showFocused,
                    shape = DrawerButtonShape,
                    unfocusedColor = Color.Transparent
                ),
            shape = ClickableSurfaceDefaults.shape(DrawerButtonShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = EpgColors.ChannelRowFocusBg.copy(alpha = 0.35f)
            )
        ) {
            Box(
                modifier = Modifier
                    .size(DrawerIconSize),
                contentAlignment = Alignment.Center
            ) {
                GuideNavDrawerIcon(
                    item = item,
                    tint = iconTint,
                    selected = selected
                )
            }
        }

        AnimatedVisibility(
            visible = showFocused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = DrawerIconSize + 4.dp)
                .zIndex(1f)
        ) {
            Text(
                text = item.label,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .background(Color(0xFF1A1A28), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun GuideNavDrawer(
    expanded: Boolean,
    focusedIndex: Int,
    drawerFocusRequester: FocusRequester,
    onItemFocused: (Int) -> Unit,
    onItemSelected: (GuideNavDrawerItem) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    onExpandRequest: () -> Unit = {},
    selectedItem: GuideNavDrawerItem? = null,
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
                .padding(vertical = 12.dp, horizontal = if (collapsed) 4.dp else 8.dp)
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
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
                    DrawerIconButton(
                        item = item,
                        focused = focused,
                        selected = selectedItem == item,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier
                            .focusRequester(requesterFor(index))
                            .onFocusChanged {
                                if (it.isFocused) onItemFocused(index)
                            }
                    )
                }
            }
        }
    }
}
