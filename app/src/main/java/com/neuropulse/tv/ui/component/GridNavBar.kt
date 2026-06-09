package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    EpgNavTab.Home,
    EpgNavTab.Search,
    EpgNavTab.Recordings,
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
        selected -> EpgColors.Accent
        focused -> EpgColors.TextPrimary
        else -> EpgColors.TextSecondary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tab.glyph,
                fontSize = 24.sp,
                color = iconColor
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(24.dp)
                        .height(2.dp)
                        .background(EpgColors.Accent)
                )
            }
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
