package com.neuropulse.tv.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

enum class TimeshiftControlFocus {
    REWIND,
    PLAY_PAUSE,
    FAST_FORWARD,
    SEEK_BAR,
    LIVE_BADGE
}

@Composable
fun LiveTimeshiftControls(
    focusedTarget: TimeshiftControlFocus,
    timeshiftState: com.neuropulse.tv.player.TimeshiftUiState,
    modifier: Modifier = Modifier
) {
    val atLiveEdge = timeshiftState.atLiveEdge
    val showPauseIcon = timeshiftState.showPauseControl
    val canRewind = timeshiftState.canRewind
    val canFastForward = timeshiftState.canFastForward

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeshiftTransportButton(
                glyph = "<<",
                caption = "Rewind",
                focused = focusedTarget == TimeshiftControlFocus.REWIND,
                enabled = canRewind
            )
            TimeshiftTransportButton(
                glyph = if (showPauseIcon) "||" else ">",
                caption = if (showPauseIcon) "Pause" else "Play",
                focused = focusedTarget == TimeshiftControlFocus.PLAY_PAUSE
            )
            TimeshiftTransportButton(
                glyph = ">>",
                caption = "Forward",
                focused = focusedTarget == TimeshiftControlFocus.FAST_FORWARD,
                enabled = canFastForward
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TimeshiftSeekBar(
                bufferStartMs = timeshiftState.bufferStartMs,
                liveEdgeMs = timeshiftState.liveEdgeMs,
                currentPositionMs = timeshiftState.currentPositionMs,
                focused = focusedTarget == TimeshiftControlFocus.SEEK_BAR,
                modifier = Modifier.weight(1f)
            )
            TimeshiftLiveBadge(
                atLiveEdge = atLiveEdge,
                focused = focusedTarget == TimeshiftControlFocus.LIVE_BADGE
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBehindLive(timeshiftState.behindLiveMs),
                color = if (atLiveEdge) EpgColors.TextDimmed else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 11.sp
            )
            Text(
                text = "LIVE",
                color = if (atLiveEdge) EpgColors.LiveBadge else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                fontWeight = if (atLiveEdge) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun TimeshiftStatusBadge(
    timeshiftState: com.neuropulse.tv.player.TimeshiftUiState,
    modifier: Modifier = Modifier
) {
    if (!timeshiftState.isTimeshifting || timeshiftState.atLiveEdge) return
    val label = if (timeshiftState.showPauseControl) ">" else "||"
    Text(
        text = "$label ${formatBehindLive(timeshiftState.behindLiveMs)} behind live",
        color = EpgColors.TextSecondary,
        fontFamily = DmSansFamily,
        fontSize = 11.sp,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun PausedCornerIndicator(modifier: Modifier = Modifier) {
    Text(
        text = "||",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 18.sp,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun TimeshiftTransportButton(
    glyph: String,
    caption: String,
    focused: Boolean,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(8.dp)
        val glyphColor = when {
            !enabled -> EpgColors.TextDimmed.copy(alpha = 0.45f)
            focused -> EpgColors.TextPrimary
            else -> EpgColors.TextSecondary
        }
        val captionColor = when {
            !enabled -> EpgColors.TextDimmed.copy(alpha = 0.45f)
            focused -> EpgColors.TextPrimary
            else -> EpgColors.TextDimmed
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(
                    if (focused && enabled) EpgColors.ChannelRowFocusBg
                    else EpgColors.GridBg.copy(alpha = if (enabled) 0.8f else 0.45f),
                    shape
                )
                .border(
                    width = if (focused && enabled) 2.dp else 1.dp,
                    color = when {
                        !enabled -> EpgColors.BorderSubtle.copy(alpha = 0.35f)
                        focused -> EpgColors.FocusBorder
                        else -> EpgColors.BorderSubtle
                    },
                    shape = shape
                )
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = glyph,
                color = glyphColor,
                fontFamily = DmSansFamily,
                fontSize = 16.sp,
                fontWeight = if (focused && enabled) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Text(
            text = caption,
            color = captionColor,
            fontFamily = DmSansFamily,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TimeshiftSeekBar(
    bufferStartMs: Long,
    liveEdgeMs: Long,
    currentPositionMs: Long,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val windowMs = (liveEdgeMs - bufferStartMs).coerceAtLeast(1L)
    val playedFraction = ((currentPositionMs - bufferStartMs).toFloat() / windowMs).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .height(if (focused) 10.dp else 6.dp)
            .clip(shape)
            .background(Color(0xFF1A1A24))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) EpgColors.FocusBorder else Color.Transparent,
                shape = shape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 6.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(playedFraction)
                .height(if (focused) 10.dp else 6.dp)
                .background(EpgColors.Accent)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(((currentPositionMs - bufferStartMs).toFloat() / windowMs).coerceIn(0.02f, 1f))
                .height(if (focused) 10.dp else 6.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(if (focused) 12.dp else 8.dp)
                    .background(
                        if (focused) Color.White else EpgColors.TextPrimary,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun TimeshiftLiveBadge(
    atLiveEdge: Boolean,
    focused: Boolean
) {
    val pulse = rememberInfiniteTransition(label = "livePulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "livePulseAnim"
    )
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .background(
                when {
                    focused -> EpgColors.ChannelRowFocusBg
                    atLiveEdge -> EpgColors.LiveBadge.copy(alpha = 0.18f + pulse.value * 0.12f)
                    else -> EpgColors.GridBg.copy(alpha = 0.8f)
                },
                shape
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> EpgColors.FocusBorder
                    atLiveEdge -> EpgColors.LiveBadge.copy(alpha = 0.6f)
                    else -> EpgColors.BorderSubtle
                },
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (atLiveEdge) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(EpgColors.LiveBadge.copy(alpha = pulse.value), CircleShape)
            )
        }
        Text(
            text = "LIVE",
            color = if (atLiveEdge) EpgColors.LiveBadge else EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatBehindLive(behindMs: Long): String {
    if (behindMs < 1_000L) return "LIVE"
    val totalSec = behindMs / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "-%d:%02d".format(minutes, seconds)
}
