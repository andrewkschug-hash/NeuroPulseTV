package com.grid.tv.ui.component

import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.feature.subtitles.ActiveSubtitle
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun VodSubtitleControls(
    settings: AppSettings,
    activeSubtitle: ActiveSubtitle?,
    onToggle: () -> Unit,
    onLanguage: (String) -> Unit,
    onFontSize: (SubtitleFontSize) -> Unit,
    onPosition: (SubtitlePosition) -> Unit,
    onDelayAdjust: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val status = when {
            !settings.subtitlesEnabled -> "Subtitles off"
            activeSubtitle != null -> "${activeSubtitle.label} (${activeSubtitle.source.name.lowercase()})"
            else -> "Searching for subtitles…"
        }
        Text(
            text = status,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowFocusButton(onClick = onToggle) {
                Text(if (settings.subtitlesEnabled) "Disable subs" else "Enable subs", color = Color.White, fontSize = 12.sp)
            }
            GlowFocusButton(onClick = { onLanguage(if (settings.subtitleLanguage == "en") "es" else "en") }) {
                Text("Lang: ${settings.subtitleLanguage}", color = Color.White, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleFontSize.entries.forEach { size ->
                GlowFocusButton(onClick = { onFontSize(size) }) {
                    Text(
                        size.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (settings.subtitleFontSize == size) Color.White else EpgColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitlePosition.entries.forEach { position ->
                GlowFocusButton(onClick = { onPosition(position) }) {
                    Text(
                        position.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (settings.subtitlePosition == position) Color.White else EpgColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowFocusButton(onClick = { onDelayAdjust(settings.subtitleDelayMs - 500L) }) {
                Text("-0.5s", color = Color.White, fontSize = 11.sp)
            }
            Text(
                text = "Delay ${settings.subtitleDelayMs}ms",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            GlowFocusButton(onClick = { onDelayAdjust(settings.subtitleDelayMs + 500L) }) {
                Text("+0.5s", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}
