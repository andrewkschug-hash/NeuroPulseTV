package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun CatchupPlayerControlsOverlay(
    programTitle: String,
    channelName: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    focusZone: RecordedPlayerFocusZone,
    transportFocusIndex: Int,
    jumpToLiveFocused: Boolean,
    seekTooltip: String?,
    onJumpToLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = programTitle,
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = channelName,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            Text(
                text = "⏪ REPLAY",
                color = Color(0xFF60A5FA),
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val transportLabels = listOf("◀◀ 10s", "⏮ 30s", if (isPlaying) "⏸" else "▶", "⏭ 30s", "▶▶ 10s")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            transportLabels.forEachIndexed { index, label ->
                EpgActionButton(
                    label = label,
                    isFocused = focusZone == RecordedPlayerFocusZone.TRANSPORT && transportFocusIndex == index,
                    onClick = {},
                    compact = true
                )
            }
        }

        seekTooltip?.let {
            Text(
                text = it,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            text = "${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs.coerceAtLeast(0L))}",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            EpgActionButton(
                label = "● Jump to Live",
                isFocused = jumpToLiveFocused,
                onClick = onJumpToLive,
                compact = true,
                labelColor = Color(0xFFEF4444)
            )
        }
    }
}
