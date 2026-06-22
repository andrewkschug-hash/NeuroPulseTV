package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.OutlinedTextField
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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.grid.tv.ui.component.FocusCard
import com.grid.tv.util.TvImageSizing
import com.grid.tv.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.delay

@Composable
fun ChannelBrowserScreen(
    onPlayChannel: (Long) -> Unit,
    onMultiview: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val previewManager = viewModel.previewManager
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val channelPagingItems = viewModel.pagedChannels.collectAsLazyPagingItems()
    val filteredTotalCount by viewModel.filteredTotalCount.collectAsStateWithLifecycle()
    var searchInput by remember { mutableStateOf("") }
    var focusedChannel by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(searchInput) {
        delay(250)
        viewModel.setSearchQuery(searchInput)
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowFocusButton(onClick = { viewModel.selectAll() }) { Text("All Channels") }
            GlowFocusButton(onClick = { viewModel.selectFavorites() }) { Text("Favorites") }
            GlowFocusButton(onClick = { viewModel.selectSports() }) { Text("Sports") }
            groups.forEach { group ->
                GlowFocusButton(onClick = { viewModel.selectGroup(group) }) { Text(group) }
            }
            GlowFocusButton(onClick = onMultiview) { Text("Multiview") }
        }

        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search channels") },
            singleLine = true
        )

        Text("Total channels: $filteredTotalCount (loaded ${channelPagingItems.itemCount})")

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                count = channelPagingItems.itemCount,
                key = channelPagingItems.itemKey { it.id }
            ) { index ->
                val channel = channelPagingItems[index] ?: return@items
                LaunchedEffect(focusedChannel, channel.id) {
                    if (focusedChannel == channel.id) {
                        delay(2000)
                        if (focusedChannel == channel.id) {
                            previewManager.startPreview(context, channel.id, channel.streamUrl)
                        }
                    }
                }

                FocusCard(onClick = { onPlayChannel(channel.id) }, onFocusChanged = { isFocused ->
                    focusedChannel = if (isFocused) channel.id else null
                    if (!isFocused && previewManager.activeChannelId() == channel.id) {
                        previewManager.stopPreview(context)
                    }
                }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previewManager.activeChannelId() == channel.id && previewManager.activePlayer() != null) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(110.dp),
                                factory = { PlayerView(it).apply { useController = false; player = previewManager.activePlayer() } }
                            )
                        } else {
                            val (logoW, logoH) = TvImageSizing.channelBrowserLogoPx(context)
                            AsyncImage(
                                model = TvImageSizing.sizedRequest(
                                    context = context,
                                    data = channel.logoUrl,
                                    widthPx = logoW,
                                    heightPx = logoH
                                ),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(110.dp)
                            )
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
