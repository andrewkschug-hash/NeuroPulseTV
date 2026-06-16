package com.grid.tv.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.grid.tv.domain.model.Channel
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.SplitViewChannelPicker
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.SplitViewViewModel
import com.grid.tv.util.MediaAttribution
import kotlinx.coroutines.delay

private enum class SplitPaneMenuAction(val label: String) {
    MAKE_PRIMARY("Make primary screen"),
    AUDIO_ON("Audio on"),
    REMOVE_SCREEN("Remove screen"),
    ADD_SCREEN("Add another screen")
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
    val paneChannels by viewModel.paneChannels.collectAsStateWithLifecycle()
    val audioPaneIndex by viewModel.audioPaneIndex.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    var showPaneMenu by remember { mutableStateOf(false) }
    var menuPaneIndex by remember { mutableIntStateOf(0) }

    val playbackContext = remember {
        MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
    }
    val players = remember(playbackContext) {
        List(SplitViewViewModel.MAX_PANES) { ExoPlayer.Builder(playbackContext).build() }
    }

    var showControls by remember { mutableStateOf(true) }
    var controlsInteractionToken by remember { mutableIntStateOf(0) }
    var paneIndex by remember { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose { players.forEach { it.release() } }
    }

    LaunchedEffect(primaryChannelId) {
        viewModel.loadPrimary(primaryChannelId)
    }

    LaunchedEffect(loadFailed) {
        if (loadFailed) onBack()
    }

    LaunchedEffect(paneChannels.map { it?.id to it?.streamUrl }) {
        paneChannels.forEachIndexed { index, channel ->
            val player = players[index]
            val url = channel?.streamUrl
            if (!url.isNullOrBlank()) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }
        }
        for (index in paneChannels.size until SplitViewViewModel.MAX_PANES) {
            players[index].stop()
            players[index].clearMediaItems()
        }
    }

    LaunchedEffect(audioPaneIndex, paneChannels.size) {
        players.forEachIndexed { index, player ->
            player.volume = if (index == audioPaneIndex) 1f else 0f
        }
    }

    LaunchedEffect(paneChannels.size) {
        paneIndex = paneIndex.coerceIn(0, (paneChannels.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocusSafelyAfterLayout()
    }

    LaunchedEffect(showControls, controlsInteractionToken) {
        if (showControls && !showPaneMenu && !showPicker) {
            delay(5_000)
            showControls = false
        }
    }

    fun revealControls() {
        showControls = true
        controlsInteractionToken++
    }

    fun menuActionsForPane(index: Int): List<SplitPaneMenuAction> = buildList {
        if (index != 0) add(SplitPaneMenuAction.MAKE_PRIMARY)
        add(SplitPaneMenuAction.AUDIO_ON)
        if (paneChannels.size > 1) add(SplitPaneMenuAction.REMOVE_SCREEN)
        if (paneChannels.size < SplitViewViewModel.MAX_PANES) add(SplitPaneMenuAction.ADD_SCREEN)
    }

    fun openPaneMenu(index: Int) {
        menuPaneIndex = index
        showPaneMenu = true
        revealControls()
    }

    fun activateMenuAction(action: SplitPaneMenuAction) {
        when (action) {
            SplitPaneMenuAction.MAKE_PRIMARY -> viewModel.makePrimary(menuPaneIndex)
            SplitPaneMenuAction.AUDIO_ON -> viewModel.setAudioPane(menuPaneIndex)
            SplitPaneMenuAction.REMOVE_SCREEN -> {
                val newSize = paneChannels.size - 1
                viewModel.removePane(menuPaneIndex)
                paneIndex = paneIndex.coerceIn(0, (newSize - 1).coerceAtLeast(0))
            }
            SplitPaneMenuAction.ADD_SCREEN -> {
                showPicker = true
            }
        }
        showPaneMenu = false
    }

    fun movePaneFocus(direction: Key): Boolean {
        val count = paneChannels.size
        if (count <= 1) return false
        paneIndex = when (count) {
            2 -> when (direction) {
                Key.DirectionLeft -> 0
                Key.DirectionRight -> 1
                else -> paneIndex
            }
            3 -> when (direction) {
                Key.DirectionLeft -> if (paneIndex == 1) 0 else paneIndex
                Key.DirectionRight -> if (paneIndex == 0) 1 else paneIndex
                Key.DirectionDown -> if (paneIndex in 0..1) 2 else paneIndex
                Key.DirectionUp -> if (paneIndex == 2) 0 else paneIndex
                else -> paneIndex
            }
            else -> when (direction) {
                Key.DirectionLeft -> when (paneIndex) {
                    1, 3 -> paneIndex - 1
                    else -> paneIndex
                }
                Key.DirectionRight -> when (paneIndex) {
                    0, 2 -> paneIndex + 1
                    else -> paneIndex
                }
                Key.DirectionDown -> when (paneIndex) {
                    0, 1 -> paneIndex + 2
                    else -> paneIndex
                }
                Key.DirectionUp -> when (paneIndex) {
                    2, 3 -> paneIndex - 2
                    else -> paneIndex
                }
                else -> paneIndex
            }
        }
        return true
    }

    fun consumeSplitViewLocalBack(): Boolean {
        when {
            showPicker -> {
                showPicker = false
                return true
            }
            showPaneMenu -> {
                showPaneMenu = false
                return true
            }
            showControls -> {
                showControls = false
                return true
            }
        }
        return false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeSplitViewLocalBack
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showPicker) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                revealControls()

                if (showPaneMenu) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.Back, Key.Escape -> consumeSplitViewLocalBack()
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown ->
                        movePaneFocus(event.key)
                    Key.Enter, Key.DirectionCenter -> {
                        openPaneMenu(paneIndex)
                        true
                    }
                    else -> false
                }
            }
    ) {
        SplitPaneGrid(
            paneChannels = paneChannels,
            players = players,
            paneIndex = paneIndex,
            audioPaneIndex = audioPaneIndex
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.88f),
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Split View",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "← → ↑ ↓ move focus  ·  Enter for stream options  ·  Back to exit",
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${paneChannels.size} of ${SplitViewViewModel.MAX_PANES} streams",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showPaneMenu) {
        val actions = menuActionsForPane(menuPaneIndex)
        SplitPaneActionMenu(
            paneLabel = paneChannels.getOrNull(menuPaneIndex)?.name ?: "Stream ${menuPaneIndex + 1}",
            actions = actions,
            onAction = ::activateMenuAction,
            onDismiss = { showPaneMenu = false }
        )
    }

    if (showPicker) {
        SplitViewChannelPicker(
            favoriteChannels = favoriteChannels,
            recentChannels = recentChannels,
            allChannels = channels,
            excludeChannelIds = paneChannels.mapNotNull { it?.id }.toSet(),
            onSelect = { channel ->
                val newPaneIndex = paneChannels.size
                viewModel.addPane(channel.id)
                showPicker = false
                paneIndex = newPaneIndex.coerceAtMost(SplitViewViewModel.MAX_PANES - 1)
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun SplitPaneActionMenu(
    paneLabel: String,
    actions: List<SplitPaneMenuAction>,
    onAction: (SplitPaneMenuAction) -> Unit,
    onDismiss: () -> Unit
) {
    val firstActionFocus = remember { FocusRequester() }
    LaunchedEffect(actions) {
        firstActionFocus.requestFocusSafelyAfterLayout()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A24))
                .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = paneLabel,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Stream options",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp
                )
                actions.forEachIndexed { index, action ->
                    GlowFocusButton(
                        onClick = { onAction(action) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == 0) Modifier.focusRequester(firstActionFocus) else Modifier
                            )
                    ) {
                        Text(
                            text = action.label,
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplitPaneGrid(
    paneChannels: List<Channel?>,
    players: List<ExoPlayer>,
    paneIndex: Int,
    audioPaneIndex: Int
) {
    when (paneChannels.size) {
        0 -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading…",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 16.sp
            )
        }
        1 -> SplitPane(
            label = paneChannels[0]?.name ?: "Channel",
            player = players[0],
            focused = paneIndex == 0,
            activeAudio = audioPaneIndex == 0,
            modifier = Modifier.fillMaxSize()
        )
        2 -> Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            paneChannels.forEachIndexed { index, channel ->
                SplitPane(
                    label = channel?.name ?: "Stream ${index + 1}",
                    player = players[index],
                    focused = paneIndex == index,
                    activeAudio = audioPaneIndex == index,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        3 -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SplitPane(
                    label = paneChannels[0]?.name ?: "Stream 1",
                    player = players[0],
                    focused = paneIndex == 0,
                    activeAudio = audioPaneIndex == 0,
                    modifier = Modifier.weight(1f)
                )
                SplitPane(
                    label = paneChannels[1]?.name ?: "Stream 2",
                    player = players[1],
                    focused = paneIndex == 1,
                    activeAudio = audioPaneIndex == 1,
                    modifier = Modifier.weight(1f)
                )
            }
            SplitPane(
                label = paneChannels[2]?.name ?: "Stream 3",
                player = players[2],
                focused = paneIndex == 2,
                activeAudio = audioPaneIndex == 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        4 -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SplitPane(
                    label = paneChannels[0]?.name ?: "Stream 1",
                    player = players[0],
                    focused = paneIndex == 0,
                    activeAudio = audioPaneIndex == 0,
                    modifier = Modifier.weight(1f)
                )
                SplitPane(
                    label = paneChannels[1]?.name ?: "Stream 2",
                    player = players[1],
                    focused = paneIndex == 1,
                    activeAudio = audioPaneIndex == 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SplitPane(
                    label = paneChannels[2]?.name ?: "Stream 3",
                    player = players[2],
                    focused = paneIndex == 2,
                    activeAudio = audioPaneIndex == 2,
                    modifier = Modifier.weight(1f)
                )
                SplitPane(
                    label = paneChannels[3]?.name ?: "Stream 4",
                    player = players[3],
                    focused = paneIndex == 3,
                    activeAudio = audioPaneIndex == 3,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
    val borderColor = when {
        focused -> EpgColors.Accent
        activeAudio -> EpgColors.Accent.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (focused || activeAudio) {
                    Modifier.border(width = 2.dp, color = borderColor)
                } else {
                    Modifier
                }
            )
            .background(Color.Black)
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
                if (view.player !== player) view.player = player
            }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
