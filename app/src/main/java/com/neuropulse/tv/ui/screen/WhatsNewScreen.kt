package com.neuropulse.tv.ui.screen

import com.neuropulse.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

data class ChangelogEntry(
    val version: String,
    val highlights: List<String>
)

object GridChangelog {
    val entries = listOf(
        ChangelogEntry(
            version = "2.1.0",
            highlights = listOf(
                "Sleep timer with volume fade-out",
                "Custom favorite groups with EPG filtering",
                "Continue Watching row on the guide",
                "Parental controls with viewing hours",
                "Picture-in-Picture on Android/Fire TV",
                "Channel health dots in the EPG",
                "Catch-up playback from the guide",
                "Two-channel split view",
                "One-tap .grid backup export",
                "What's New screen after updates"
            )
        ),
        ChangelogEntry(
            version = "2.0.0",
            highlights = listOf(
                "GRID rebrand with TV-optimized guide",
                "Profile picker and onboarding flow",
                "Recording to USB/SD with scheduled recordings",
                "Smart EPG auto-matching"
            )
        )
    )

    fun forVersion(version: String): ChangelogEntry? =
        entries.firstOrNull { it.version == version }
}

@Composable
fun WhatsNewScreen(
    version: String,
    onDismiss: () -> Unit
) {
    val entry = GridChangelog.forVersion(version) ?: GridChangelog.entries.first()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What's New in GRID v${entry.version}",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 26.sp
        )
        entry.highlights.forEach { line ->
            Text(
                text = "• $line",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = "Like the update? Buy us a coffee ☕",
            color = Color(0xFFFFB020),
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        GlowFocusButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Continue", fontFamily = DmSansFamily)
        }
    }
}
