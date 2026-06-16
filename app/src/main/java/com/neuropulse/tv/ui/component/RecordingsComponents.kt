package com.neuropulse.tv.ui.component

import com.neuropulse.tv.ui.component.GlowFocusButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import com.neuropulse.tv.feature.recording.RecordingHealth
import com.neuropulse.tv.feature.recording.StorageFormat
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DialogBg = Color(0xFF1A1F2E)
private val DialogBody = Color(0xFFB0B0C0)
private val RecordingAccent = Color(0xFFE53935)

@Composable
fun RecordingsHubHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
        Text(
            text = "Browse the TV Guide and tap Record on any program",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun RecordingsChipFilterBar(
    labels: List<String>,
    activeIndex: Int,
    focusedIndex: Int,
    barFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == activeIndex
            val chipFocused = barFocused && index == focusedIndex
            val bg = when {
                chipFocused -> EpgColors.Accent.copy(alpha = 0.22f)
                selected -> EpgColors.ChannelRowFocusBg
                else -> Color.Transparent
            }
            val borderColor = when {
                chipFocused -> EpgColors.Accent
                selected -> EpgColors.Accent.copy(alpha = 0.45f)
                else -> EpgColors.BorderSubtle.copy(alpha = 0.65f)
            }
            Text(
                text = label,
                color = when {
                    chipFocused || selected -> EpgColors.TextPrimary
                    else -> EpgColors.TextSecondary
                },
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                fontWeight = if (chipFocused || selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .background(bg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun RecordingsEmptyState(
    title: String,
    message: String,
    icon: String = "●",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            color = if (icon == "●") RecordingAccent.copy(alpha = 0.55f) else EpgColors.TextDimmed.copy(alpha = 0.45f),
            fontSize = 36.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = message,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

fun formatRecordingRelativeDate(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val dayMs = TimeUnit.DAYS.toMillis(1)
    val todayStart = nowMs - (nowMs % dayMs) - timezoneOffset(nowMs)
    val targetStart = epochMs - (epochMs % dayMs) - timezoneOffset(epochMs)
    val diffDays = ((todayStart - targetStart) / dayMs).toInt()
    return when (diffDays) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(epochMs))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}

private fun timezoneOffset(ms: Long): Long {
    val offset = Calendar.getInstance().apply { timeInMillis = ms }.timeZone.getOffset(ms)
    return offset.toLong()
}

fun formatRecordingDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000).coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "$minutes min"
    }
}

fun formatRecordingFullDateTime(epochMs: Long): String {
    return SimpleDateFormat("EEEE MMMM d 'at' h:mm a", Locale.getDefault()).format(Date(epochMs))
}

fun formatRecordingPlayerDate(epochMs: Long): String {
    return SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
}

fun isRecordingWatched(item: RecordedMediaEntity): Boolean {
    if (item.durationMs <= 0) return false
    return item.playbackPositionMs >= item.durationMs * 0.9
}

fun canResumeRecording(item: RecordedMediaEntity): Boolean {
    if (item.durationMs <= 0) return false
    return item.playbackPositionMs > 30_000 && item.playbackPositionMs < item.durationMs * 0.9
}

@Composable
fun RecordingGridCard(
    title: String,
    channelName: String,
    recordedAt: Long,
    durationMs: Long,
    fileSizeBytes: Long,
    thumbnailPath: String?,
    playbackPositionMs: Long,
    integrityStatus: String,
    isFocused: Boolean,
    nowMs: Long,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isFocused -> EpgColors.Accent
        else -> EpgColors.BorderSubtle.copy(alpha = 0.65f)
    }
    val cardBg = if (isFocused) EpgColors.ChannelRowFocusBg else Color(0xFF13131A)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(cardBg, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A22))
        ) {
            if (thumbnailPath != null && File(thumbnailPath).exists()) {
                AsyncImage(
                    model = File(thumbnailPath),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 18.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White.copy(alpha = 0.85f), fontSize = 24.sp)
            }
            if (playbackPositionMs > 0 && durationMs > 0) {
                val progress = (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(EpgColors.Accent)
                    )
                }
            }
            val watched = durationMs > 0 && playbackPositionMs >= durationMs * 0.9
            if (watched) {
                Text(
                    text = "WATCHED",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = channelName,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatRecordingRelativeDate(recordedAt, nowMs),
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 11.sp
            )
            Text(
                text = formatRecordingDuration(durationMs),
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 11.sp
            )
        }
        Text(
            text = StorageFormat.formatFileSize(fileSizeBytes),
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (integrityStatus != "OK") {
            val (label, color) = when (integrityStatus) {
                "CORRUPT" -> "CORRUPT FILE" to Color(0xFFE53935)
                else -> "INCOMPLETE RECORDING" to Color(0xFFFFA726)
            }
            Text(
                text = label,
                color = color,
                fontFamily = DmSansFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun RecordingsBottomSheetPanel(
    title: String,
    channelName: String,
    recordedAt: Long,
    durationMs: Long,
    fileSizeBytes: Long,
    thumbnailPath: String?,
    integrityStatus: String = "OK",
    actions: List<String>,
    detailActionFocused: Int,
    visible: Boolean,
    onAction: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EpgLayout.DetailPanelHeight)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF161622), EpgColors.DetailPanelBg)
                    )
                )
                .border(width = 0.5.dp, color = EpgColors.BorderSubtle.copy(alpha = 0.6f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EpgColors.ChannelColumnBg),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailPath != null && File(thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(thumbnailPath),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("▶", color = EpgColors.TextSecondary, fontSize = 24.sp)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(channelName)
                        append(" · ")
                        append(formatRecordingRelativeDate(recordedAt))
                        append(" · ")
                        append(formatRecordingDuration(durationMs))
                        append(" · ")
                        append(StorageFormat.formatFileSize(fileSizeBytes))
                    },
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Recorded ${formatRecordingFullDateTime(recordedAt)}",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (integrityStatus != "OK") {
                    val issueLabel = if (integrityStatus == "CORRUPT") {
                        "Integrity: Corrupt transport stream data detected"
                    } else {
                        "Integrity: Incomplete recording (stream dropouts detected)"
                    }
                    Text(
                        text = issueLabel,
                        color = if (integrityStatus == "CORRUPT") Color(0xFFE53935) else Color(0xFFFFA726),
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    actions.forEachIndexed { index, label ->
                        EpgActionButton(
                            label = label,
                            isFocused = detailActionFocused == index,
                            onClick = { onAction(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsSimpleDetailPanel(
    title: String,
    subtitle: String,
    meta: String,
    actions: List<String>,
    detailActionFocused: Int,
    visible: Boolean,
    onAction: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EpgLayout.DetailPanelHeight)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF161622), EpgColors.DetailPanelBg)
                    )
                )
                .border(width = 0.5.dp, color = EpgColors.BorderSubtle.copy(alpha = 0.6f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = meta,
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { index, label ->
                    EpgActionButton(
                        label = label,
                        isFocused = detailActionFocused == index,
                        onClick = { onAction(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingDeleteDialog(
    title: String,
    fileSizeBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var focusIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val cancelFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val deleteFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(focusIndex) {
        if (focusIndex == 0) cancelFocusRequester.requestFocusSafelyAfterLayout() else deleteFocusRequester.requestFocusSafelyAfterLayout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> {
                        focusIndex = 0
                        true
                    }
                    androidx.compose.ui.input.key.Key.DirectionRight -> {
                        focusIndex = 1
                        true
                    }
                    androidx.compose.ui.input.key.Key.Enter,
                    androidx.compose.ui.input.key.Key.DirectionCenter -> {
                        if (focusIndex == 0) onDismiss() else onConfirm()
                        true
                    }
                    else -> false
                }
            }
    ) {
        AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Delete $title?",
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This will permanently delete the file. This cannot be undone.",
                    color = DialogBody,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
                Text(
                    text = "Frees up ${StorageFormat.formatFileSize(fileSizeBytes)}",
                    color = EpgColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            GlowFocusButton(
                onClick = onConfirm,
                modifier = Modifier
                    .focusRequester(deleteFocusRequester)
                    .focusable()
                    .then(
                        if (focusIndex == 1) {
                            Modifier.border(2.dp, Color(0xFFE53935), RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text("Delete", color = Color(0xFFE53935))
            }
        },
        dismissButton = {
            GlowFocusButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(cancelFocusRequester)
                    .focusable()
                    .then(
                        if (focusIndex == 0) {
                            Modifier.border(2.dp, EpgColors.Accent, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text("Cancel")
            }
        }
    )
    }
}

@Composable
fun RecordingIndicatorChip(
    title: String?,
    focused: Boolean,
    onClick: () -> Unit,
    health: RecordingHealth = RecordingHealth.RECORDING,
    modifier: Modifier = Modifier
) {
    val dotColor = when (health) {
        RecordingHealth.RECONNECTING -> Color(0xFFFFA726)
        RecordingHealth.STORAGE_PAUSED -> Color(0xFFFFC107)
        RecordingHealth.SIGNAL_LOST  -> Color(0xFF9E9E9E)
        else                         -> Color.Red
    }
    val chipBg = when (health) {
        RecordingHealth.RECONNECTING -> dotColor.copy(alpha = if (focused) 0.22f else 0.10f)
        RecordingHealth.STORAGE_PAUSED -> dotColor.copy(alpha = if (focused) 0.20f else 0.09f)
        RecordingHealth.SIGNAL_LOST  -> dotColor.copy(alpha = if (focused) 0.20f else 0.08f)
        else                         -> Color.Red.copy(alpha = if (focused) 0.25f else 0.12f)
    }
    val badgeText = when (health) {
        RecordingHealth.RECONNECTING -> "⟳ RECONNECTING"
        RecordingHealth.STORAGE_PAUSED -> "⏸ STORAGE PAUSED"
        RecordingHealth.SIGNAL_LOST  -> "✕ SIGNAL LOST"
        else                         -> "REC"
    }

    val pulseTransition = rememberInfiniteTransition(label = "recPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "recPulseScale"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipBg, RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) EpgColors.FocusBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (health == RecordingHealth.RECORDING) {
            Box(
                modifier = Modifier
                    .size(8.dp * pulse)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )
        }
        Text(
            text = badgeText,
            color = dotColor,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (health == RecordingHealth.RECORDING) {
            title?.take(18)?.let {
                Text(
                    text = it,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
