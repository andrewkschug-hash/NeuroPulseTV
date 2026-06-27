package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR

enum class GuideNavDrawerItem(val label: String, val showLabel: Boolean = false) {
    Search("Search"),
    LiveView("Live"),
    Vod("VODs"),
    Favorites("Favourites"),
    Recordings("Recordings")
}

val GuideNavDrawerItems = GuideNavDrawerItem.entries

/** Profile sits above nav icons at focus index 0. */
const val GuideNavDrawerProfileFocusIndex = 0

fun guideNavDrawerItemFocusIndex(item: GuideNavDrawerItem): Int =
    GuideNavDrawerItems.indexOf(item) + 1

/** Icon rail width — keep in sync with layout padding. */
val GuideNavDrawerCollapsedWidth = 68.dp

/** @deprecated Expanded rail removed — icon-only sidebar is always used. */
val GuideNavDrawerExpandedWidth = GuideNavDrawerCollapsedWidth

private val DrawerButtonShape = RoundedCornerShape(8.dp)
private val DrawerIconSize = NavIconHitSize

private fun GuideNavDrawerItem.navTabIcon(): EpgNavTab? = when (this) {
    GuideNavDrawerItem.Search -> EpgNavTab.Search
    GuideNavDrawerItem.LiveView -> EpgNavTab.Guide
    GuideNavDrawerItem.Vod -> EpgNavTab.Vod
    GuideNavDrawerItem.Favorites -> EpgNavTab.Favorites
    GuideNavDrawerItem.Recordings -> EpgNavTab.Recordings
}

@Composable
private fun GuideNavDrawerIcon(
    item: GuideNavDrawerItem,
    tint: Color,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    GridNavTabIcon(
        tab = item.navTabIcon() ?: EpgNavTab.Guide,
        tint = tint,
        selected = selected && item == GuideNavDrawerItem.Recordings,
        modifier = modifier
    )
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
        item == GuideNavDrawerItem.Recordings && selected -> Color(0xFFFF5252)
        showFocused -> EpgColors.LiveGuideFocus
        selected -> EpgColors.TextPrimary
        else -> EpgColors.TextSecondary
    }

    val labelColor = when {
        showFocused -> EpgColors.LiveGuideFocus
        selected -> EpgColors.TextPrimary
        else -> EpgColors.TextSecondary
    }

    Column(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GridFocusSurface(
            onClick = onClick,
            modifier = Modifier
                .size(NavIconHitSize)
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GuideNavDrawerIcon(
                    item = item,
                    tint = iconTint,
                    selected = selected
                )
            }
        }
        if (item.showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.label,
                color = labelColor,
                fontFamily = DmSansFamily,
                fontSize = 10.sp,
                fontWeight = if (showFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun GuideNavDrawer(
    focusedIndex: Int,
    drawerActive: Boolean,
    drawerFocusRequester: FocusRequester,
    profileInitials: String,
    profileAvatarColor: String = DEFAULT_PROFILE_AVATAR_COLOR,
    profileFocused: Boolean = false,
    onProfileClick: () -> Unit,
    onItemFocused: (Int) -> Unit,
    onItemSelected: (GuideNavDrawerItem) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    selectedItem: GuideNavDrawerItem? = null,
    liveViewActive: Boolean = false,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") expanded: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onExpandRequest: () -> Unit = {},
) {
    val trailingRequesters = remember {
        List(GuideNavDrawerItems.size) { FocusRequester() }
    }

    fun requesterFor(index: Int): FocusRequester = when (index) {
        GuideNavDrawerProfileFocusIndex -> drawerFocusRequester
        else -> trailingRequesters.getOrElse(index - 1) { drawerFocusRequester }
    }

    LaunchedEffect(focusedIndex, drawerActive) {
        if (!drawerActive) return@LaunchedEffect
        requesterFor(focusedIndex.coerceAtLeast(GuideNavDrawerProfileFocusIndex))
            .requestFocusSafelyAfterLayout()
    }

    Box(
        modifier = modifier
            .width(GuideNavDrawerCollapsedWidth)
            .fillMaxHeight()
            .background(EpgColors.SidebarRailBg)
            .onPreviewKeyEvent(onPreviewKey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GridProfileAvatar(
                initials = profileInitials,
                focused = profileFocused || focusedIndex == GuideNavDrawerProfileFocusIndex,
                onClick = onProfileClick,
                avatarColorHex = profileAvatarColor,
                modifier = Modifier
                    .focusRequester(drawerFocusRequester)
                    .onFocusChanged {
                        if (it.isFocused) onItemFocused(GuideNavDrawerProfileFocusIndex)
                    }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    GuideNavDrawerItems.forEachIndexed { index, item ->
                        val focusIndex = index + 1
                        val focused = focusedIndex == focusIndex
                        val selected = when (item) {
                            GuideNavDrawerItem.LiveView -> liveViewActive
                            else -> selectedItem == item
                        }
                        DrawerIconButton(
                            item = item,
                            focused = focused,
                            selected = selected,
                            onClick = {
                        onItemFocused(focusIndex)
                        onItemSelected(item)
                    },
                            modifier = Modifier
                                .focusRequester(requesterFor(focusIndex))
                                .onFocusChanged {
                                    if (it.isFocused) onItemFocused(focusIndex)
                                }
                        )
                    }
                }
            }
        }
    }
}
