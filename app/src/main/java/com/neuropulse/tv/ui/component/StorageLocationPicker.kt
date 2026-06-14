package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.RecordQuality
import com.neuropulse.tv.feature.recording.RecordingPrecheck
import com.neuropulse.tv.feature.recording.StorageOption
import com.neuropulse.tv.feature.recording.label
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

private val DialogBgTop = Color(0xFF222838)
private val DialogBgBottom = Color(0xFF12141C)
private val DialogInsetBg = Color(0xFF0A0C12)
private val DialogTextSecondary = Color(0xFF9CA3AF)
private val DialogTextMuted = Color(0xFF6B7280)
private val RecordAccent = Color(0xFFE53935)
private val PremiumDialogShape = RoundedCornerShape(16.dp)

private enum class PrecheckFocusZone { QUALITY, BUTTONS }

@Composable
fun StorageLocationPicker(
    options: List<StorageOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Where should GRID save recordings?"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBgBottom,
        titleContentColor = Color.White,
        textContentColor = DialogTextSecondary,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { option ->
                    Surface(
                        onClick = { onSelect(option.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp)),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = EpgColors.ChannelColumnBg,
                            focusedContainerColor = EpgColors.ChannelRowFocusBg,
                            pressedContainerColor = EpgColors.ChannelRowFocusBg
                        )
                    ) {
                        Text(
                            text = option.displayLine(),
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent
                )
            ) {
                Text(
                    text = "Cancel",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    )
}

@Composable
fun RecordingPrecheckDialog(
    precheck: RecordingPrecheck,
    onQualitySelected: (RecordQuality) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val showQuality = precheck.availableQualities.size > 1
    val qualities = precheck.availableQualities
    var focusZone by remember { mutableStateOf(PrecheckFocusZone.BUTTONS) }
    var qualityIndex by remember(precheck.selectedQuality, qualities) {
        mutableIntStateOf(
            qualities.indexOf(precheck.selectedQuality).coerceAtLeast(0)
        )
    }
    var buttonIndex by remember { mutableIntStateOf(0) }

    val recordFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val qualityFocusRequesters = remember(qualities.size) {
        List(qualities.size) { FocusRequester() }
    }

    LaunchedEffect(precheck.selectedQuality) {
        val idx = qualities.indexOf(precheck.selectedQuality)
        if (idx >= 0) qualityIndex = idx
    }

    LaunchedEffect(Unit) {
        buttonIndex = 0
        focusZone = PrecheckFocusZone.BUTTONS
        recordFocusRequester.requestFocusSafelyAfterLayout()
    }

    LaunchedEffect(focusZone, buttonIndex, qualityIndex, showQuality) {
        when (focusZone) {
            PrecheckFocusZone.BUTTONS ->
                if (buttonIndex == 0) recordFocusRequester.requestFocusSafelyAfterLayout() else cancelFocusRequester.requestFocusSafelyAfterLayout()
            PrecheckFocusZone.QUALITY ->
                if (showQuality) qualityFocusRequesters.getOrNull(qualityIndex)?.requestFocusSafelyAfterLayout()
        }
    }

    fun activateSelection() {
        when (focusZone) {
            PrecheckFocusZone.QUALITY -> {
                qualities.getOrNull(qualityIndex)?.let(onQualitySelected)
            }
            PrecheckFocusZone.BUTTONS -> when (buttonIndex) {
                0 -> if (precheck.insufficientSpaceWarning == null) onConfirm()
                1 -> onDismiss()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.82f)
                    )
                )
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onDismiss()
                        true
                    }
                    Key.DirectionUp -> {
                        if (showQuality && focusZone == PrecheckFocusZone.BUTTONS) {
                            focusZone = PrecheckFocusZone.QUALITY
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (showQuality && focusZone == PrecheckFocusZone.QUALITY) {
                            focusZone = PrecheckFocusZone.BUTTONS
                            buttonIndex = 0
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        when (focusZone) {
                            PrecheckFocusZone.BUTTONS -> {
                                buttonIndex = 1
                                true
                            }
                            PrecheckFocusZone.QUALITY -> {
                                if (qualityIndex > 0) {
                                    qualityIndex -= 1
                                    qualities.getOrNull(qualityIndex)?.let(onQualitySelected)
                                }
                                true
                            }
                        }
                    }
                    Key.DirectionRight -> {
                        when (focusZone) {
                            PrecheckFocusZone.BUTTONS -> {
                                buttonIndex = 0
                                true
                            }
                            PrecheckFocusZone.QUALITY -> {
                                if (qualityIndex < qualities.lastIndex) {
                                    qualityIndex += 1
                                    qualities.getOrNull(qualityIndex)?.let(onQualitySelected)
                                }
                                true
                            }
                        }
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        activateSelection()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(PremiumDialogShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DialogBgTop, DialogBgBottom)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), PremiumDialogShape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(RecordAccent.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, RecordAccent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(RecordAccent, CircleShape)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "LIVE RECORDING",
                        color = RecordAccent.copy(alpha = 0.85f),
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Start Recording",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DialogInsetBg.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = precheck.estimateText,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = precheck.freeStorageText,
                    color = DialogTextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp
                )
                precheck.lowStorageWarning?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFFB020),
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp
                    )
                }
                precheck.insufficientSpaceWarning?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF5252),
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (showQuality) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RECORD QUALITY",
                        color = DialogTextMuted,
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        qualities.forEachIndexed { index, quality ->
                            val selected = precheck.selectedQuality == quality
                            val focused = focusZone == PrecheckFocusZone.QUALITY && qualityIndex == index
                            PrecheckQualityPill(
                                label = quality.label,
                                selected = selected,
                                focused = focused,
                                modifier = Modifier
                                    .focusRequester(qualityFocusRequesters[index])
                                    .focusable()
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrecheckActionButton(
                    label = "Cancel",
                    focused = focusZone == PrecheckFocusZone.BUTTONS && buttonIndex == 1,
                    destructive = false,
                    enabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(cancelFocusRequester)
                        .focusable()
                )
                PrecheckActionButton(
                    label = "Record",
                    focused = focusZone == PrecheckFocusZone.BUTTONS && buttonIndex == 0,
                    destructive = true,
                    enabled = precheck.insufficientSpaceWarning == null,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(recordFocusRequester)
                        .focusable()
                )
            }
        }
    }
}

@Composable
private fun PrecheckQualityPill(
    label: String,
    selected: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = when {
        focused -> EpgColors.Accent
        selected -> EpgColors.Accent.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val background = when {
        focused -> EpgColors.Accent.copy(alpha = 0.2f)
        selected -> EpgColors.Accent.copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    Box(
        modifier = modifier
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .background(background, shape)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (focused || selected) EpgColors.TextPrimary else DialogTextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PrecheckActionButton(
    label: String,
    focused: Boolean,
    destructive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        focused && destructive -> RecordAccent
        focused -> EpgColors.Accent
        destructive -> RecordAccent.copy(alpha = 0.45f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.03f)
        focused && destructive -> RecordAccent.copy(alpha = 0.22f)
        focused && !destructive -> EpgColors.Accent.copy(alpha = 0.14f)
        destructive -> RecordAccent.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val textColor = when {
        !enabled -> DialogTextMuted
        focused -> Color.White
        destructive -> RecordAccent.copy(alpha = 0.9f)
        else -> DialogTextSecondary
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .background(background, shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
