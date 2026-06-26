package com.grid.tv.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlowFocusButton(
            onClick = { onVote(com.grid.tv.feature.vod.personalization.RecommendationVote.UP) },
            externallyFocused = currentVote == com.grid.tv.feature.vod.personalization.RecommendationVote.UP
        ) {
            Text(
                text = "👍",
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        GlowFocusButton(
            onClick = { onVote(com.grid.tv.feature.vod.personalization.RecommendationVote.DOWN) },
            externallyFocused = currentVote == com.grid.tv.feature.vod.personalization.RecommendationVote.DOWN
        ) {
            Text(
                text = "👎",
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
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
