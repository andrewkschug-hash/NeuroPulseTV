package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.feature.recording.RecordingHealth
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

/** Minimal guide header — date/time and optional recording chip (profile lives in sidebar). */
@Composable
fun EpgGuideHeader(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    activeRecordingTitle: String? = null,
    recordingHealth: RecordingHealth = RecordingHealth.RECORDING,
    onRecordingIndicatorClick: () -> Unit = {},
) {
    val clockNow = rememberEpgNowMillis(EpgNowTicker.CLOCK_INTERVAL_MS)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(EpgColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatEpgDay(clockNow),
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = formatEpgClock(clockNow),
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
            if (isRecording) {
                RecordingIndicatorChip(
                    title = activeRecordingTitle,
                    focused = false,
                    onClick = onRecordingIndicatorClick,
                    health = recordingHealth
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
