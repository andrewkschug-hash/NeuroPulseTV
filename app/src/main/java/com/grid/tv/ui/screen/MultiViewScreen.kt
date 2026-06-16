package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grid.tv.ui.component.ScreenBackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.grid.tv.domain.model.Channel
import com.grid.tv.player.multiview.MultiViewLayout
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.MultiViewViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MultiViewScreen(
    seedChannelId: Long = 0L,
    onBack: () -> Unit,
    viewModel: MultiViewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(seedChannelId) {
        viewModel.initialize(seedChannelId)
    }

    LaunchedEffect(state.panels.map { it.channel?.id }) {
        viewModel.tuneAllPanels(context)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    val replacing = state.replacingPanelIndex != null
    val fullscreenIndex = state.fullscreenPanelIndex

    fun consumeMultiViewLocalBack(): Boolean {
        if (fullscreenIndex != null) {
            viewModel.exitFullscreen()
            return true
        }
        return false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeMultiViewLocalBack
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (replacing) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back -> consumeMultiViewLocalBack()
                    Key.DirectionLeft -> {
                        viewModel.moveFocus(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.moveFocus(1)
                        true
                    }
                    Key.DirectionUp -> {
                        if (state.layout == MultiViewLayout.FOUR && state.focusedPanelIndex >= 2) {
                            viewModel.moveFocus(-2)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (state.layout == MultiViewLayout.FOUR && state.focusedPanelIndex < 2) {
                            viewModel.moveFocus(2)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (fullscreenIndex != null) {
                            viewModel.exitFullscreen()
                        } else {
                            viewModel.selectActivePanel()
                            viewModel.enterFullscreen()
                        }
                        true
                    }
                    Key.Menu -> {
                        viewModel.startReplacePanel()
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MultiView", color = EpgColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowFocusButton(onClick = { viewModel.setLayout(MultiViewLayout.TWO) }) {
                        Text("2-Screen")
                    }
                    GlowFocusButton(onClick = { viewModel.setLayout(MultiViewLayout.FOUR) }) {
                        Text("4-Screen")
                    }
                    GlowFocusButton(onClick = onBack) { Text("Back") }
                }
            }

            if (fullscreenIndex != null) {
                MultiViewPanel(
                    panelIndex = fullscreenIndex,
                    channel = state.panels.getOrNull(fullscreenIndex)?.channel,
                    focused = true,
                    activeAudio = state.activeAudioPanelIndex == fullscreenIndex,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            } else {
                when (state.layout) {
                    MultiViewLayout.TWO -> Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        state.panels.take(2).forEach { panel ->
                            MultiViewPanel(
                                panelIndex = panel.index,
                                channel = panel.channel,
                                focused = state.focusedPanelIndex == panel.index,
                                activeAudio = state.activeAudioPanelIndex == panel.index,
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                    MultiViewLayout.FOUR -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            state.panels.take(2).forEach { panel ->
                                MultiViewPanel(
                                    panelIndex = panel.index,
                                    channel = panel.channel,
                                    focused = state.focusedPanelIndex == panel.index,
                                    activeAudio = state.activeAudioPanelIndex == panel.index,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            state.panels.drop(2).take(2).forEach { panel ->
                                MultiViewPanel(
                                    panelIndex = panel.index,
                                    channel = panel.channel,
                                    focused = state.focusedPanelIndex == panel.index,
                                    activeAudio = state.activeAudioPanelIndex == panel.index,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (replacing) {
            MultiViewChannelPicker(
                channels = channels,
                onSelect = { channel -> viewModel.replacePanel(context, channel) },
                onDismiss = { viewModel.cancelReplacePanel() }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MultiViewPanel(
    panelIndex: Int,
    channel: Channel?,
    focused: Boolean,
    activeAudio: Boolean,
    viewModel: MultiViewViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        activeAudio -> EpgColors.Accent.copy(alpha = 0.6f)
        else -> EpgColors.BorderSubtle
    }
    val player = remember(panelIndex) { viewModel.playerForPanel(context, panelIndex) }

    Box(
        modifier = modifier
            .padding(6.dp)
            .border(2.dp, borderColor, shape)
            .background(Color.Black, shape)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
            }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = channel?.name ?: "Empty panel — Menu to assign",
                color = EpgColors.TextPrimary
            )
            if (activeAudio) {
                Text("Audio", color = EpgColors.Accent)
            }
        }
    }
}

@Composable
private fun MultiViewChannelPicker(
    channels: List<Channel>,
    onSelect: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(24.dp)
    ) {
        Column {
            Text("Replace channel", color = EpgColors.TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(channels.filter { it.streamUrl.isNotBlank() }, key = { it.id }) { channel ->
                    GlowFocusButton(onClick = { onSelect(channel) }) {
                        Text(channel.name)
                    }
                }
            }
            GlowFocusButton(onClick = onDismiss, modifier = Modifier.padding(top = 12.dp)) {
                Text("Cancel")
            }
        }
    }
}
