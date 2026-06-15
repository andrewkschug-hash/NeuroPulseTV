package com.neuropulse.tv.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.component.SplitViewChannelPicker
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.SplitViewViewModel
import com.neuropulse.tv.util.MediaAttribution

private enum class SplitViewFocusZone { TOP_BAR, PANES }

private enum class SplitViewTopAction(val label: String) {
    AUDIO_LEFT("Audio Left"),
    AUDIO_RIGHT("Audio Right"),
    PICK_SECOND("Pick 2nd"),
    BACK("Back")
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SplitViewScreen(
    primaryChannelId: Long,
    onBack: () -> Unit,
    viewModel: SplitViewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val primary by viewModel.primaryChannel.collectAsStateWithLifecycle()
    val secondary by viewModel.secondaryChannel.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(secondary == null) }

    val playbackContext = remember {
        MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
    }
    val leftPlayer = remember(playbackContext) { ExoPlayer.Builder(playbackContext).build() }
    val rightPlayer = remember(playbackContext) { ExoPlayer.Builder(playbackContext).build() }

    var focusZone by remember { mutableStateOf(SplitViewFocusZone.TOP_BAR) }
    var topBarIndex by remember { mutableIntStateOf(0) }
    var paneIndex by remember { mutableIntStateOf(0) }
    var activeAudioPane by remember { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }

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
            activeAudioPane = 0
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

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocusSafelyAfterLayout()
    }

    fun setAudioLeft() {
        leftPlayer.volume = 1f
        rightPlayer.volume = 0f
        activeAudioPane = 0
    }

    fun setAudioRight() {
        leftPlayer.volume = 0f
        rightPlayer.volume = 1f
        activeAudioPane = 1
    }

    fun activateTopAction() {
        when (SplitViewTopAction.entries[topBarIndex]) {
            SplitViewTopAction.AUDIO_LEFT -> setAudioLeft()
            SplitViewTopAction.AUDIO_RIGHT -> setAudioRight()
            SplitViewTopAction.PICK_SECOND -> showPicker = true
            SplitViewTopAction.BACK -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showPicker) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.DirectionUp -> {
                        if (focusZone == SplitViewFocusZone.PANES) {
                            focusZone = SplitViewFocusZone.TOP_BAR
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (focusZone == SplitViewFocusZone.TOP_BAR) {
                            focusZone = SplitViewFocusZone.PANES
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        when (focusZone) {
                            SplitViewFocusZone.TOP_BAR -> {
                                topBarIndex = (topBarIndex - 1).coerceAtLeast(0)
                                true
                            }
                            SplitViewFocusZone.PANES -> {
                                paneIndex = 0
                                true
                            }
                        }
                    }
                    Key.DirectionRight -> {
                        when (focusZone) {
                            SplitViewFocusZone.TOP_BAR -> {
                                topBarIndex = (topBarIndex + 1)
                                    .coerceAtMost(SplitViewTopAction.entries.lastIndex)
                                true
                            }
                            SplitViewFocusZone.PANES -> {
                                paneIndex = 1
                                true
                            }
                        }
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        when (focusZone) {
                            SplitViewFocusZone.TOP_BAR -> activateTopAction()
                            SplitViewFocusZone.PANES -> {
                                if (paneIndex == 0) setAudioLeft() else setAudioRight()
                            }
                        }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Split View",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (focusZone) {
                            SplitViewFocusZone.TOP_BAR -> "↑↓ move between toolbar and streams"
                            SplitViewFocusZone.PANES -> "Enter selects audio for focused stream"
                        },
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitViewTopAction.entries.forEachIndexed { index, action ->
                        SplitViewToolbarButton(
                            label = action.label,
                            focused = focusZone == SplitViewFocusZone.TOP_BAR && topBarIndex == index
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SplitPane(
                    label = primary?.name ?: "Channel 1",
                    player = leftPlayer,
                    focused = focusZone == SplitViewFocusZone.PANES && paneIndex == 0,
                    activeAudio = activeAudioPane == 0,
                    modifier = Modifier.weight(1f)
                )
                SplitPane(
                    label = secondary?.name ?: "Pick a channel",
                    player = rightPlayer,
                    focused = focusZone == SplitViewFocusZone.PANES && paneIndex == 1,
                    activeAudio = activeAudioPane == 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showPicker) {
        SplitViewChannelPicker(
            favoriteChannels = favoriteChannels,
            recentChannels = recentChannels,
            allChannels = channels,
            excludeChannelId = primaryChannelId,
            onSelect = { channel ->
                viewModel.selectSecondary(channel.id)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun SplitViewToolbarButton(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) EpgColors.FocusBorder else EpgColors.BorderSubtle,
                shape = shape
            )
            .background(
                if (focused) EpgColors.ChannelRowFocusBg else Color(0xFF252530),
                shape
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplitPane(
    label: String,
    player: ExoPlayer,
    focused: Boolean,
    activeAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        activeAudio -> EpgColors.Accent.copy(alpha = 0.6f)
        else -> EpgColors.BorderSubtle
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .border(width = if (focused) 3.dp else 1.dp, color = borderColor, shape = shape)
            .background(Color.Black, shape)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
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
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                maxLines = 2
            )
            if (activeAudio) {
                Text(
                    text = "Audio",
                    color = EpgColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
