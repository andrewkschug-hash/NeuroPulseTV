package com.grid.tv.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grid.tv.domain.model.ChannelScanStatus

@Composable
fun StatusDot(
    status: ChannelScanStatus,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val pulse = rememberInfiniteTransition(label = "statusDotPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "statusDotPulseAlpha"
    )

    val baseColor = when (status) {
        ChannelScanStatus.LIVE -> Color(0xFF2ECC71)
        ChannelScanStatus.DEAD -> Color(0xFFFF3B3B)
        ChannelScanStatus.CHECKING -> Color(0xFFFFB020)
        ChannelScanStatus.UNKNOWN -> Color(0xFF6B6B7B)
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (status == ChannelScanStatus.CHECKING) pulseAlpha else 1f)
            .background(baseColor, CircleShape)
    )
}

/** Legacy playback-health dot; maps reliability score to scan status when scanner data is absent. */
@Composable
fun ChannelHealthDot(
    reliabilityScore: Int,
    sessions: Int = 0,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    scanStatus: ChannelScanStatus? = null
) {
    val status = scanStatus ?: when {
        sessions == 0 -> ChannelScanStatus.UNKNOWN
        reliabilityScore >= 70 -> ChannelScanStatus.LIVE
        reliabilityScore >= 40 -> ChannelScanStatus.CHECKING
        else -> ChannelScanStatus.DEAD
    }
    StatusDot(status = status, modifier = modifier, size = size)
}

fun formatLastChecked(lastCheckedAt: Long?, now: Long = System.currentTimeMillis()): String? {
    if (lastCheckedAt == null) return null
    val minutes = ((now - lastCheckedAt) / 60_000L).coerceAtLeast(0)
    return when {
        minutes == 0L -> "Last checked: just now"
        minutes == 1L -> "Last checked: 1 min ago"
        else -> "Last checked: $minutes mins ago"
    }
}
