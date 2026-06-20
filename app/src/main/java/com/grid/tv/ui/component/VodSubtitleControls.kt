package com.grid.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.feature.subtitles.ActiveSubtitle
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private val GlassBorder = Color.White.copy(alpha = 0.2f)
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassFillSelected = Color.White.copy(alpha = 0.14f)
private val PillShape = RoundedCornerShape(24.dp)
private val SegmentShape = RoundedCornerShape(20.dp)
private val SectionShape = RoundedCornerShape(12.dp)

@Composable
fun VodPlayerSettingsOverlay(
    title: String,
    settings: AppSettings,
    activeSubtitle: ActiveSubtitle?,
    onToggle: () -> Unit,
    onLanguage: (String) -> Unit,
    onFontSize: (SubtitleFontSize) -> Unit,
    onPosition: (SubtitlePosition) -> Unit,
    onDelayAdjust: (Long) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSyncPanel by remember { mutableStateOf(false) }

    BackHandler(enabled = showSyncPanel) {
        showSyncPanel = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.45f),
                        Color(0xB3000000),
                        Color(0xCC000000)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                VodGlassIconButton(
                    label = "←",
                    contentDescription = "Back",
                    onClick = {
                        if (showSyncPanel) showSyncPanel = false else onBack()
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val status = when {
                        !settings.subtitlesEnabled -> "Subtitles off"
                        activeSubtitle != null -> "${activeSubtitle.label} · ${activeSubtitle.source.name.lowercase()}"
                        else -> "Searching for subtitles…"
                    }
                    Text(
                        text = status,
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            VodSubtitleControls(
                settings = settings,
                onToggle = onToggle,
                onLanguage = onLanguage,
                onFontSize = onFontSize,
                onPosition = onPosition,
                onOpenSyncSettings = { showSyncPanel = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                VodGlassPillButton(
                    label = "Done",
                    selected = false,
                    onClick = {
                        if (showSyncPanel) showSyncPanel = false else onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(48.dp)
                )
            }
        }

        if (showSyncPanel) {
            VodSyncSettingsPanel(
                delayMs = settings.subtitleDelayMs,
                onDelayAdjust = onDelayAdjust,
                onDismiss = { showSyncPanel = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VodSubtitleControls(
    settings: AppSettings,
    onToggle: () -> Unit,
    onLanguage: (String) -> Unit,
    onFontSize: (SubtitleFontSize) -> Unit,
    onPosition: (SubtitlePosition) -> Unit,
    onOpenSyncSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        VodSettingsSection(title = "Subtitles") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VodGlassPillButton(
                    label = if (settings.subtitlesEnabled) "Disable subs" else "Enable subs",
                    selected = settings.subtitlesEnabled,
                    onClick = onToggle
                )
                VodGlassPillButton(
                    label = "Language: ${settings.subtitleLanguage.uppercase()}",
                    selected = settings.subtitlesEnabled,
                    onClick = { onLanguage(if (settings.subtitleLanguage == "en") "es" else "en") },
                    enabled = settings.subtitlesEnabled
                )
            }
        }

        VodSettingsSection(title = "Text size") {
            VodSegmentedControl(
                options = SubtitleFontSize.entries.map {
                    it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                },
                selectedIndex = SubtitleFontSize.entries.indexOf(settings.subtitleFontSize),
                onSelect = { index -> onFontSize(SubtitleFontSize.entries[index]) }
            )
        }

        VodSettingsSection(title = "Position") {
            VodSegmentedControl(
                options = SubtitlePosition.entries.map {
                    it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                },
                selectedIndex = SubtitlePosition.entries.indexOf(settings.subtitlePosition),
                onSelect = { index -> onPosition(SubtitlePosition.entries[index]) }
            )
        }

        VodSettingsSection(title = "Advanced") {
            VodGlassPillButton(
                label = syncSettingsLabel(settings.subtitleDelayMs),
                selected = settings.subtitleDelayMs != 0L,
                onClick = onOpenSyncSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VodSyncSettingsPanel(
    delayMs: Long,
    onDelayAdjust: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .clip(SectionShape)
                .background(GlassFill)
                .border(1.dp, GlassBorder, SectionShape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Sync Settings",
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Adjust subtitle timing relative to audio",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VodGlassIconButton(
                    label = "−0.5s",
                    contentDescription = "Decrease subtitle delay",
                    onClick = { onDelayAdjust(delayMs - 500L) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                VodGlassPillButton(
                    label = formatSubtitleDelay(delayMs),
                    selected = true,
                    onClick = { onDelayAdjust(0L) },
                    modifier = Modifier.width(160.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                VodGlassIconButton(
                    label = "+0.5s",
                    contentDescription = "Increase subtitle delay",
                    onClick = { onDelayAdjust(delayMs + 500L) }
                )
            }
            VodGlassPillButton(
                label = "Done",
                selected = false,
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )
        }
    }
}

private fun syncSettingsLabel(delayMs: Long): String {
    val offset = when {
        delayMs == 0L -> null
        delayMs % 1000L == 0L -> {
            val seconds = delayMs / 1000L
            if (seconds > 0) "+${seconds}s" else "${seconds}s"
        }
        else -> "${delayMs}ms"
    }
    return if (offset != null) "⚙  Sync Settings · $offset" else "⚙  Sync Settings"
}

@Composable
private fun VodSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SectionShape)
                .background(GlassFill)
                .border(1.dp, GlassBorder, SectionShape)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun VodSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SegmentShape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, GlassBorder, SegmentShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            VodGlassPillButton(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VodGlassPillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        selected -> EpgColors.Accent.copy(alpha = 0.28f)
        focused -> GlassFillSelected
        else -> Color.Transparent
    }
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        selected -> EpgColors.Accent.copy(alpha = 0.85f)
        else -> Color.Transparent
    }
    val scale = if (focused) 1.04f else 1f

    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = focused,
                shape = PillShape,
                unfocusedColor = Color.Transparent
            )
            .border(
                width = if (selected && !focused) 1.dp else if (focused) 2.dp else 0.dp,
                color = borderColor,
                shape = PillShape
            ),
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background,
            pressedContainerColor = background.copy(alpha = 0.85f),
            disabledContainerColor = Color.White.copy(alpha = 0.04f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = when {
                    !enabled -> EpgColors.TextDimmed
                    selected || focused -> Color.White
                    else -> EpgColors.TextSecondary
                },
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VodGlassIconButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val background = if (focused) GlassFillSelected else GlassFill
    val scale = if (focused) 1.08f else 1f

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .size(if (label.length <= 2) 44.dp else 52.dp, 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = focused,
                shape = if (label.length <= 2) CircleShape else PillShape,
                unfocusedColor = GlassBorder,
                unfocusedWidth = 1.dp
            ),
        shape = ClickableSurfaceDefaults.shape(
            if (label.length <= 2) CircleShape else PillShape
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = EpgColors.Accent.copy(alpha = 0.22f),
            pressedContainerColor = background.copy(alpha = 0.85f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (focused) Color.White else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = if (label.length <= 2) 18.sp else 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatSubtitleDelay(delayMs: Long): String {
    return when {
        delayMs == 0L -> "Delay 0ms"
        delayMs % 1000L == 0L -> {
            val seconds = delayMs / 1000L
            if (seconds > 0) "Delay +${seconds}s" else "Delay ${seconds}s"
        }
        else -> "Delay ${delayMs}ms"
    }
}
