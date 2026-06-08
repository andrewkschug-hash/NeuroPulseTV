package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.feature.preview.PreviewPlayerManager
import com.neuropulse.tv.ui.component.FocusCard
import com.neuropulse.tv.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.delay

@Composable
fun ChannelBrowserScreen(
    onPlayChannel: (Long) -> Unit,
    onMultiview: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
    previewManager: PreviewPlayerManager = PreviewPlayerManager()
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var focusedChannel by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.selectAll() }) { Text("All Channels") }
            Button(onClick = { viewModel.selectFavorites() }) { Text("Favorites") }
            Button(onClick = { viewModel.selectSports() }) { Text("Sports") }
            groups.forEach { group ->
                Button(onClick = { viewModel.selectGroup(group) }) { Text(group) }
            }
            Button(onClick = onMultiview) { Text("Multiview") }
        }

        Text("Total channels: ${channels.size}")

        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(channels) { channel ->
                LaunchedEffect(focusedChannel) {
                    if (focusedChannel == channel.id) {
                        delay(2000)
                        if (focusedChannel == channel.id) {
                            previewManager.startPreview(context, channel.id, channel.streamUrl)
                        }
                    }
                }

                FocusCard(onClick = { onPlayChannel(channel.id) }, onFocusChanged = { isFocused ->
                    focusedChannel = if (isFocused) channel.id else null
                    if (!isFocused && previewManager.activeChannelId() == channel.id) previewManager.stopPreview()
                }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previewManager.activeChannelId() == channel.id && previewManager.activePlayer() != null) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(110.dp),
                                factory = { PlayerView(it).apply { useController = false; player = previewManager.activePlayer() } }
                            )
                        } else {
                            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(110.dp))
                        }
                        Text(channel.name)
                        Text(channel.currentProgram ?: "No current program")
                        val badge = when {
                            channel.reliabilityScore >= 75 -> "● Green"
                            channel.reliabilityScore >= 45 -> "● Yellow"
                            else -> "● Red"
                        }
                        Text("Reliability: $badge")
                    }
                }
            }
        }
    }
}
