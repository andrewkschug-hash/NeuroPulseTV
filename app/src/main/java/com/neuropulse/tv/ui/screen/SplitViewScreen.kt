package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.viewmodel.SplitViewViewModel
import com.neuropulse.tv.util.MediaAttribution

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SplitViewScreen(
    primaryChannelId: Long,
    onBack: () -> Unit,
    viewModel: SplitViewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val primary by viewModel.primaryChannel.collectAsStateWithLifecycle()
    val secondary by viewModel.secondaryChannel.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(secondary == null) }

    val playbackContext = remember {
        MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
    }
    val leftPlayer = remember(playbackContext) { ExoPlayer.Builder(playbackContext).build() }
    val rightPlayer = remember(playbackContext) { ExoPlayer.Builder(playbackContext).build() }

    DisposableEffect(Unit) {
        onDispose {
            leftPlayer.release()
            rightPlayer.release()
        }
    }

    LaunchedEffect(primaryChannelId) {
        viewModel.loadPrimary(primaryChannelId)
    }

    LaunchedEffect(primary?.streamUrl) {
        primary?.streamUrl?.let { url ->
            leftPlayer.setMediaItem(MediaItem.fromUri(url))
            leftPlayer.prepare()
            leftPlayer.playWhenReady = true
            leftPlayer.volume = 1f
        }
    }

    LaunchedEffect(secondary?.streamUrl) {
        secondary?.streamUrl?.let { url ->
            rightPlayer.setMediaItem(MediaItem.fromUri(url))
            rightPlayer.prepare()
            rightPlayer.playWhenReady = true
            rightPlayer.volume = 0f
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Split View", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    leftPlayer.volume = 1f
                    rightPlayer.volume = 0f
                }) { Text("Audio Left") }
                Button(onClick = {
                    leftPlayer.volume = 0f
                    rightPlayer.volume = 1f
                }) { Text("Audio Right") }
                Button(onClick = { showPicker = true }) { Text("Pick 2nd") }
                Button(onClick = onBack) { Text("Back") }
            }
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SplitPane(
                label = primary?.name ?: "Channel 1",
                player = leftPlayer,
                modifier = Modifier.weight(1f)
            )
            SplitPane(
                label = secondary?.name ?: "Pick a channel",
                player = rightPlayer,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Open second stream") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.5f)) {
                    items(channels.filter { it.id != primaryChannelId }) { ch ->
                        Button(
                            onClick = {
                                viewModel.selectSecondary(ch.id)
                                showPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) { Text(ch.name) }
                    }
                }
            },
            confirmButton = { Button(onClick = { showPicker = false }) { Text("Close") } }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplitPane(label: String, player: ExoPlayer, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxHeight().padding(4.dp)) {
        Text(label, color = Color.White, modifier = Modifier.padding(6.dp))
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111111)),
            factory = { PlayerView(it).apply { this.player = player; useController = true } }
        )
    }
}
