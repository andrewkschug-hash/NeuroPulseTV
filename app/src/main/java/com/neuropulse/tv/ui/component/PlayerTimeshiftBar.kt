package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

@Composable
fun PlayerTimeshiftBar(
    focusedIndex: Int,
    atLiveEdge: Boolean,
    showGoLive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        PlayerTimeshiftChip(
            label = "−10 min",
            focused = focusedIndex == 0
        )
        PlayerTimeshiftChip(
            label = "−30 min",
            focused = focusedIndex == 1
        )
        PlayerTimeshiftChip(
            label = "● LIVE",
            focused = focusedIndex == 2,
            accent = atLiveEdge,
            liveBadge = true
        )
        if (showGoLive) {
            PlayerTimeshiftChip(
                label = "Go Live",
                focused = focusedIndex == 3
            )
        }
    }
}

@Composable
private fun PlayerTimeshiftChip(
    label: String,
    focused: Boolean,
    accent: Boolean = false,
    liveBadge: Boolean = false
) {
    val shape = RoundedCornerShape(6.dp)
    val backgroundColor = when {
        focused -> EpgColors.ChannelRowFocusBg
        liveBadge && accent -> Color.Red.copy(alpha = 0.22f)
        liveBadge && !accent -> Color(0xFF3A3A4A)
        else -> EpgColors.GridBg
    }
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        liveBadge && accent -> EpgColors.LiveBadge
        else -> EpgColors.BorderSubtle
    }
    val textColor = when {
        focused -> EpgColors.TextPrimary
        liveBadge && accent -> EpgColors.LiveBadge
        liveBadge && !accent -> EpgColors.TextSecondary
        else -> EpgColors.TextPrimary
    }
    Text(
        text = label,
        color = textColor,
        fontFamily = DmSansFamily,
        fontSize = 11.sp,
        fontWeight = if (focused || (liveBadge && accent)) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(backgroundColor, shape)
            .border(width = if (focused) 2.dp else 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
