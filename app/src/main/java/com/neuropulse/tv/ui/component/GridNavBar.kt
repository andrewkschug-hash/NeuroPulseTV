package com.neuropulse.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.neuropulse.tv.ui.theme.DmSansFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.EpgColors

val GridNavTabs = listOf(
    EpgNavTab.Search,
    EpgNavTab.Guide,
    EpgNavTab.Favorites
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
            .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
            .wrapContentSize(unbounded = true),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (focused) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EpgColors.Accent.copy(alpha = 0.18f))
                    )
                }
                Surface(
                    onClick = onClick,
                    modifier = Modifier.size(40.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        pressedContainerColor = Color.Transparent
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab.glyph,
                            fontSize = 22.sp,
                            color = iconColor
                        )
                    }
                }
                if (focused || selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 2.dp)
                            .width(if (focused) 20.dp else 16.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (focused) EpgColors.Accent else EpgColors.Accent.copy(alpha = 0.6f)
                            )
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = focused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 46.dp)
        ) {
            Text(
                text = tab.label,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color(0xFF1A1A28), RoundedCornerShape(12.dp))
                    .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
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
    val borderColor = if (focused) EpgColors.Accent else Color.White.copy(alpha = 0.15f)
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C3A6B))
                .border(1.5.dp, borderColor, CircleShape),
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
        modifier = modifier,
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
        trailing()
    }
}
