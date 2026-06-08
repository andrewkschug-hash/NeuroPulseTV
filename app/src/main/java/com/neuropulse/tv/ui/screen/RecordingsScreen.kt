package com.neuropulse.tv.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.feature.recording.RecordingSort
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel

@Composable
fun RecordingsScreen(viewModel: RecordingViewModel = hiltViewModel()) {
    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(0) }
    var deleteScheduledId by remember { mutableStateOf<Long?>(null) }
    var deleteMedia by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var previewPath by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tab = 0 }) { Text(if (tab == 0) "[Scheduled]" else "Scheduled") }
            Button(onClick = { tab = 1 }) { Text(if (tab == 1) "[Recordings]" else "Recordings") }
        }

        if (tab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scheduled) { s ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .clickable { deleteScheduledId = s.id }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("${s.channelName} - ${s.programTitle}")
                            Text("${java.util.Date(s.startTime)} to ${java.util.Date(s.endTime)}")
                            Text("Status: ${s.status}")
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Button(onClick = { viewModel.setSort(RecordingSort.DATE) }) { Text(if (sort == RecordingSort.DATE) "[Date]" else "Date") }
                Button(onClick = { viewModel.setSort(RecordingSort.CHANNEL) }) { Text(if (sort == RecordingSort.CHANNEL) "[Channel]" else "Channel") }
                Button(onClick = { viewModel.setSort(RecordingSort.DURATION) }) { Text(if (sort == RecordingSort.DURATION) "[Duration]" else "Duration") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recorded) { r ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222222))
                            .clickable { previewPath = r.filePath }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("${r.channelName} - ${r.programTitle}")
                            Text("${(r.durationMs / 1000)}s | ${(r.fileSizeBytes / (1024 * 1024))}MB")
                            Text(r.filePath)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { previewPath = r.filePath }) { Text("Play") }
                                Button(onClick = { deleteMedia = r.id to r.filePath }) { Text("Delete") }
                            }
                        }
                    }
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
        AlertDialog(
            onDismissRequest = { deleteMedia = null },
            title = { Text("Delete recording?") },
            text = { Text("The media file will be removed from storage.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.deleteRecording(deleteMedia!!.first, deleteMedia!!.second)
                        deleteMedia = null
                    }) { Text("Delete") }
                    Button(onClick = { deleteMedia = null }) { Text("Close") }
                }
            }
        )
    }

    if (previewPath != null) {
        AlertDialog(
            onDismissRequest = { previewPath = null },
            title = { Text("Playback") },
            text = {
                LocalPlaybackPreview(path = previewPath!!)
            },
            confirmButton = { Button(onClick = { previewPath = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun LocalPlaybackPreview(path: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        factory = { PlayerView(it).apply { this.player = player; useController = true } }
    )
}
