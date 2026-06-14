package com.neuropulse.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

/** Nav bar layout — shared with [EpgTopBar]. */
val NavIconHitSize = 44.dp
val NavBarMinHeight = 64.dp
private val NavIconSlotWidth = 56.dp

val GridNavTabs = listOf(
    EpgNavTab.Search,
    EpgNavTab.Guide,
    EpgNavTab.Vod,
    EpgNavTab.Favorites,
    EpgNavTab.Recordings
)

@Composable
fun GridNavIcon(
    tab: EpgNavTab,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = when {
        focused -> EpgColors.Accent
        selected -> EpgColors.TextPrimary
        else -> EpgColors.TextSecondary
    }

    Box(
        modifier = modifier
            .size(NavIconHitSize)
            .padding(0.dp),
        contentAlignment = Alignment.Center
    ) {
        // Focus glow — drawn first, behind everything, NOT inside Surface
        if (focused) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(EpgColors.Accent.copy(alpha = 0.18f))
            )
        }

        // Icon text — drawn directly, no Surface wrapper causing clip issues
        Text(
            text = tab.glyph,
            fontSize = 20.sp,
            color = iconColor,
            modifier = Modifier
                .clickable { onClick() }
                .padding(8.dp)
        )

        // Underline indicator at bottom
        if (focused || selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(if (focused) 18.dp else 14.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (focused) EpgColors.Accent
                        else EpgColors.Accent.copy(alpha = 0.6f)
                    )
            )
        }
    }
}

@Composable
private fun GridNavFocusTooltip(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        color = EpgColors.TextPrimary,
        fontFamily = DmSansFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .wrapContentWidth(unbounded = true)
            .background(Color(0xFF1A1A28), RoundedCornerShape(12.dp))
            .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun GridNavTooltipRow(
    visible: Boolean,
    focusedIndex: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && focusedIndex in GridNavTabs.indices,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                GridNavTabs.forEachIndexed { index, tab ->
                    Box(
                        modifier = Modifier.width(NavIconSlotWidth),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (index == focusedIndex) {
                            GridNavFocusTooltip(
                                label = tab.label,
                                modifier = Modifier.wrapContentWidth(unbounded = true)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(NavIconHitSize))
        }
    }
}

@Composable
fun GridProfileAvatar(
    initials: String,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(NavIconHitSize),
        contentAlignment = Alignment.Center
    ) {
        // Focus glow behind avatar
        if (focused) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(EpgColors.Accent.copy(alpha = 0.18f))
            )
        }

        // Avatar circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C3A6B))
                .border(
                    width = if (focused) 2.dp else 1.5.dp,
                    color = if (focused) EpgColors.Accent else Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GridNavIconRow(
    selectedTab: EpgNavTab,
    focusedIndex: Int,
    navFocused: Boolean,
    profileInitials: String,
    profileFocused: Boolean,
    onTabSelected: (EpgNavTab) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarMinHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GridNavTabs.forEachIndexed { index, tab ->
                GridNavIcon(
                    tab = tab,
                    selected = tab == selectedTab,
                    focused = navFocused && index == focusedIndex,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
        GridProfileAvatar(
            initials = profileInitials,
            focused = profileFocused,
            onClick = onProfileClick
        )
        Box(
            modifier = Modifier.padding(start = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trailing()
            }
        }
    }
}
