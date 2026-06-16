package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

enum class RecordedPlayerFocusZone {
    TRANSPORT, SEEK, BOTTOM
}

@Composable
fun RecordedPlayerControlsOverlay(
    title: String,
    recordedAtLabel: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    focusZone: RecordedPlayerFocusZone,
    transportFocusIndex: Int,
    bottomFocusIndex: Int,
    seekTooltip: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = recordedAtLabel,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp
            )
        }

        val transportLabels = listOf("◀◀ 10s", "⏮ 30s", if (isPlaying) "⏸" else "▶", "⏭ 30s", "▶▶ 10s")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            transportLabels.forEachIndexed { index, label ->
                val focused = focusZone == RecordedPlayerFocusZone.TRANSPORT && transportFocusIndex == index
                PlayerControlChip(label = label, focused = focused)
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp)) {
            if (seekTooltip != null) {
                Text(
                    text = seekTooltip,
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatPlayerTime(positionMs),
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.width(72.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .then(
                            if (focusZone == RecordedPlayerFocusZone.SEEK) {
                                Modifier.border(2.dp, EpgColors.Accent, RoundedCornerShape(3.dp))
                            } else {
                                Modifier
                            }
                        )
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                ) {
                    val progress = if (durationMs > 0) {
                        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(EpgColors.Accent, RoundedCornerShape(3.dp))
                    )
                }
                Text(
                    text = formatPlayerTime(durationMs),
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.width(72.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerControlChip(
                label = "🗑 Delete",
                focused = focusZone == RecordedPlayerFocusZone.BOTTOM && bottomFocusIndex == 0,
                destructive = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 1.5f, 2f).forEachIndexed { index, speed ->
                    val label = when (speed) {
                        1f -> "1×"
                        1.5f -> "1.5×"
                        else -> "2×"
                    }
                    val selected = playbackSpeed == speed
                    PlayerControlChip(
                        label = label,
                        focused = focusZone == RecordedPlayerFocusZone.BOTTOM && bottomFocusIndex == index + 1,
                        selected = selected
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlChip(
    label: String,
    focused: Boolean,
    selected: Boolean = false,
    destructive: Boolean = false
) {
    val borderColor = when {
        focused && destructive -> Color(0xFFE53935)
        focused -> EpgColors.Accent
        selected -> EpgColors.Accent.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val bg = when {
        selected -> EpgColors.Accent.copy(alpha = 0.2f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Text(
        text = label,
        color = when {
            destructive && focused -> Color(0xFFE53935)
            focused -> Color.White
            selected -> Color.White
            else -> EpgColors.TextSecondary
        },
        fontFamily = DmSansFamily,
        fontSize = 13.sp,
        fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = if (borderColor == Color.Transparent) Color.White.copy(alpha = 0.15f) else borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "0:00:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun formatRecordedPlayerOverlayDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return "Recorded ${formatRecordingPlayerDate(epochMs)}"
}
