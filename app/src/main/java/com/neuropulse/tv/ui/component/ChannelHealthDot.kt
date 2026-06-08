package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ChannelHealthDot(
    reliabilityScore: Int,
    sessions: Int = 0,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val color = when {
        sessions == 0 -> Color(0xFFFFB020)
        reliabilityScore >= 70 -> Color(0xFF2ECC71)
        reliabilityScore >= 40 -> Color(0xFFFFB020)
        else -> Color(0xFFFF3B3B)
    }
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
    )
}
