package com.neuropulse.tv.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.feature.recording.RecordingCountdown
import com.neuropulse.tv.feature.recording.RecordingSort
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.feature.recording.StorageFormat
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsScreen(viewModel: RecordingViewModel = hiltViewModel()) {
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }

    var tab by remember { mutableStateOf(0) }
    var deleteScheduledId by remember { mutableStateOf<Long?>(null) }
    var deleteMedia by remember { mutableStateOf<RecordedMediaEntity?>(null) }
    var previewMedia by remember { mutableStateOf<RecordedMediaEntity?>(null) }

    LaunchedEffect(scheduled) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick = System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tab = 0 }) { Text(if (tab == 0) "[Scheduled]" else "Scheduled") }
            Button(onClick = { tab = 1 }) { Text(if (tab == 1) "[Recordings]" else "Recordings") }
        }

        if (tab == 0) {
            val upcoming = scheduled.filter {
                it.status == RecordingStatus.SCHEDULED.name || it.status == RecordingStatus.RECORDING.name
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(upcoming, key = { it.id }) { s ->
                    ScheduledRecordingRow(
                        item = s,
                        now = tick,
                        onLongPress = { deleteScheduledId = s.id }
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                SortButton("Date", RecordingSort.DATE, sort) { viewModel.setSort(it) }
                SortButton("Channel", RecordingSort.CHANNEL, sort) { viewModel.setSort(it) }
                SortButton("Duration", RecordingSort.DURATION, sort) { viewModel.setSort(it) }
                SortButton("Size", RecordingSort.FILE_SIZE, sort) { viewModel.setSort(it) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recorded, key = { it.id }) { r ->
                    RecordedMediaRow(
                        item = r,
                        onPlay = { previewMedia = r },
                        onDelete = { deleteMedia = r }
                    )
                }
            }
        }
    }

    if (deleteScheduledId != null) {
        AlertDialog(
            onDismissRequest = { deleteScheduledId = null },
            title = { Text("Cancel scheduled recording?") },
            text = { Text("This will remove the recording timer.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.deleteScheduled(deleteScheduledId!!)
                        deleteScheduledId = null
                    }) { Text("Delete") }
                    Button(onClick = { deleteScheduledId = null }) { Text("Close") }
                }
            }
        )
    }

    if (deleteMedia != null) {
        val media = deleteMedia!!
        AlertDialog(
            onDismissRequest = { deleteMedia = null },
            title = { Text("Delete recording?") },
            text = { Text("The media file will be removed from storage.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.deleteRecording(media.id, media.filePath, media.thumbnailPath)
                        deleteMedia = null
                    }) { Text("Delete") }
                    Button(onClick = { deleteMedia = null }) { Text("Close") }
                }
            }
        )
    }

    if (previewMedia != null) {
        val media = previewMedia!!
        AlertDialog(
            onDismissRequest = { previewMedia = null },
            title = { Text("${media.programTitle} (${media.channelName})") },
            text = {
                LocalPlaybackPreview(
                    media = media,
                    onPositionChange = { viewModel.savePlaybackPosition(media.id, it) }
                )
            },
            confirmButton = { Button(onClick = { previewMedia = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SortButton(
    label: String,
    value: RecordingSort,
    current: RecordingSort,
    onSelect: (RecordingSort) -> Unit
) {
    Button(onClick = { onSelect(value) }) {
        Text(if (current == value) "[$label]" else label)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduledRecordingRow(
    item: ScheduledRecordingEntity,
    now: Long,
    onLongPress: () -> Unit
) {
    val countdown = when (item.status) {
        RecordingStatus.RECORDING.name -> "● Recording now"
        else -> RecordingCountdown.formatUntilStart(item.startTime, now)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "${item.programTitle} — $countdown",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp
            )
            Text(
                text = "${item.channelName} · ${formatRecordedDate(item.startTime)}",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun RecordedMediaRow(
    item: RecordedMediaEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val durationMin = (item.durationMs / 60_000).coerceAtLeast(1)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF222222), RoundedCornerShape(6.dp))
            .clickable(onClick = onPlay)
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.thumbnailPath != null && File(item.thumbnailPath).exists()) {
                AsyncImage(
                    model = File(item.thumbnailPath),
                    contentDescription = item.programTitle,
                    modifier = Modifier
                        .size(80.dp, 48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp, 48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = EpgColors.TextSecondary, fontSize = 18.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.programTitle, color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 15.sp)
                Text(
                    "${item.channelName} · ${formatRecordedDate(item.recordedAt)} · ${durationMin} min · ${StorageFormat.formatFileSize(item.fileSizeBytes)}",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp
                )
            }
            Button(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun formatRecordedDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun LocalPlaybackPreview(
    media: RecordedMediaEntity,
    onPositionChange: (Long) -> Unit
) {
    val context = LocalContext.current
    val player = remember(media.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(media.filePath))))
            if (media.playbackPositionMs > 0) {
                seekTo(media.playbackPositionMs)
            }
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPositionChange(0)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            onPositionChange(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        factory = { PlayerView(it).apply { this.player = player; useController = true } }
    )
}
