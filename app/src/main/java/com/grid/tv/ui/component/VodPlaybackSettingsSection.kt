package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.player.ExternalPlayerId
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun RecommendationFeedbackButtons(
    contentKey: String,
    currentVote: com.grid.tv.feature.vod.personalization.RecommendationVote?,
    onVote: (com.grid.tv.feature.vod.personalization.RecommendationVote) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetflixFeedbackThumb(
            emoji = "👍",
            selected = currentVote == com.grid.tv.feature.vod.personalization.RecommendationVote.UP,
            onClick = { onVote(com.grid.tv.feature.vod.personalization.RecommendationVote.UP) },
            contentDescription = "Thumbs up"
        )
        NetflixFeedbackThumb(
            emoji = "👎",
            selected = currentVote == com.grid.tv.feature.vod.personalization.RecommendationVote.DOWN,
            onClick = { onVote(com.grid.tv.feature.vod.personalization.RecommendationVote.DOWN) },
            contentDescription = "Thumbs down"
        )
    }
}

@Composable
private fun NetflixFeedbackThumb(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val showRing = focused || selected
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = showRing,
                shape = CircleShape,
                unfocusedColor = Color.Transparent
            ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0x66000000),
            focusedContainerColor = Color(0x99000000),
            pressedContainerColor = Color(0x55000000)
        )
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier
                            .background(Color(0x33FFFFFF), CircleShape)
                            .border(1.dp, Color(0x66FFFFFF), CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 18.sp)
        }
    }
}

@Composable
fun VodPlaybackSettingsSection(
    externalPlayer: ExternalPlayerId,
    nextUpAutoPlay: Boolean,
    vodSyncIntervalHours: Int,
    onExternalPlayerChange: (ExternalPlayerId) -> Unit,
    onNextUpAutoPlayChange: (Boolean) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Playback & sync",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SettingsOptionRow(
            label = "External player",
            value = externalPlayer.label,
            onClick = {
                val next = when (externalPlayer) {
                    ExternalPlayerId.NONE -> ExternalPlayerId.VLC
                    ExternalPlayerId.VLC -> ExternalPlayerId.MX_PLAYER
                    ExternalPlayerId.MX_PLAYER -> ExternalPlayerId.NONE
                }
                onExternalPlayerChange(next)
            }
        )
        SettingsToggleRow(
            label = "Auto-play next episode",
            checked = nextUpAutoPlay,
            onCheckedChange = onNextUpAutoPlayChange
        )
        SettingsOptionRow(
            label = "VOD catalog sync",
            value = syncIntervalLabel(vodSyncIntervalHours),
            onClick = {
                val options = listOf(6, 24, 72, 168)
                val idx = options.indexOf(vodSyncIntervalHours).let { if (it < 0) 0 else (it + 1) % options.size }
                onSyncIntervalChange(options[idx])
            }
        )
    }
}

private fun syncIntervalLabel(hours: Int): String = when (hours) {
    6 -> "Every 6 hours"
    24 -> "Daily"
    72 -> "Every 3 days"
    168 -> "Weekly"
    else -> "Every ${hours}h"
}

@Composable
private fun SettingsOptionRow(label: String, value: String, onClick: () -> Unit) {
    GlowFocusButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 15.sp)
            Text(value, color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlowFocusButton(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 15.sp)
            Text(if (checked) "On" else "Off", color = EpgColors.Accent, fontFamily = DmSansFamily, fontSize = 14.sp)
        }
    }
}
