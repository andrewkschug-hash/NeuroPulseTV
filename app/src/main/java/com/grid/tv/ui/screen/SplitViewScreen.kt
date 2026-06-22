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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
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
import com.grid.tv.player.MultiPanePlaybackPolicy
import com.grid.tv.player.PlaybackOrchestrator
import com.grid.tv.player.PlaybackSurfaceInstrument
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

    val maxPanes = remember(context) { MultiPanePlaybackPolicy.maxPaneCount(context) }
    val decodeOnlyAudio = remember(context) { MultiPanePlaybackPolicy.decodeOnlyActiveAudioPane(context) }

    var showControls by remember { mutableStateOf(true) }
    var controlsInteractionToken by remember { mutableIntStateOf(0) }
    var paneIndex by remember { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }
    var sessionGranted by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(Unit) {
        val result = viewModel.requestSplitSession(context)
        sessionGranted = result == PlaybackOrchestrator.SessionRequestResult.GRANTED ||
            result == PlaybackOrchestrator.SessionRequestResult.GRANTED_EVICTED_LOWER
        onDispose {
            viewModel.releaseSplitSession(context)
        }
    }

    LaunchedEffect(sessionGranted) {
        if (sessionGranted == false) onBack()
    }

    LaunchedEffect(primaryChannelId) {
        viewModel.loadPrimary(primaryChannelId)
    }

    LaunchedEffect(loadFailed) {
        if (loadFailed) onBack()
    }

    LaunchedEffect(paneChannels, audioPaneIndex, decodeOnlyAudio) {
        viewModel.syncPanePlayback(context, paneChannels, audioPaneIndex, decodeOnlyAudio)
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
        if (paneChannels.size < maxPanes) add(SplitPaneMenuAction.ADD_SCREEN)
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
            viewModel = viewModel,
            context = context,
            paneIndex = paneIndex,
            audioPaneIndex = audioPaneIndex,
            decodeOnlyAudio = decodeOnlyAudio
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
                        text = "${paneChannels.size} of $maxPanes streams",
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
                viewModel.addPane(channel.id, maxPanes)
                showPicker = false
                paneIndex = newPaneIndex.coerceAtMost(maxPanes - 1)
            },
            onDismiss = { showPicker = false },
            onNearEnd = { viewModel.loadMoreChannels() }
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
    viewModel: SplitViewViewModel,
    context: Context,
    paneIndex: Int,
    audioPaneIndex: Int,
    decodeOnlyAudio: Boolean
) {
    when {
        paneChannels.isEmpty() -> Box(
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
        paneChannels.size <= 2 -> Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            key(0) {
                SplitPaneSlot(
                    paneIndex = 0,
                    channel = paneChannels.getOrNull(0),
                    viewModel = viewModel,
                    context = context,
                    focused = paneIndex == 0,
                    activeAudio = audioPaneIndex == 0,
                    decodeOnlyAudio = decodeOnlyAudio,
                    modifier = if (paneChannels.size == 1) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.weight(1f)
                    }
                )
            }
            if (paneChannels.size >= 2) {
                key(1) {
                    SplitPaneSlot(
                        paneIndex = 1,
                        channel = paneChannels.getOrNull(1),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 1,
                        activeAudio = audioPaneIndex == 1,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        paneChannels.size == 3 -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                key(0) {
                    SplitPaneSlot(
                        paneIndex = 0,
                        channel = paneChannels.getOrNull(0),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 0,
                        activeAudio = audioPaneIndex == 0,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
                key(1) {
                    SplitPaneSlot(
                        paneIndex = 1,
                        channel = paneChannels.getOrNull(1),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 1,
                        activeAudio = audioPaneIndex == 1,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            key(2) {
                SplitPaneSlot(
                    paneIndex = 2,
                    channel = paneChannels.getOrNull(2),
                    viewModel = viewModel,
                    context = context,
                    focused = paneIndex == 2,
                    activeAudio = audioPaneIndex == 2,
                    decodeOnlyAudio = decodeOnlyAudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                key(0) {
                    SplitPaneSlot(
                        paneIndex = 0,
                        channel = paneChannels.getOrNull(0),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 0,
                        activeAudio = audioPaneIndex == 0,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
                key(1) {
                    SplitPaneSlot(
                        paneIndex = 1,
                        channel = paneChannels.getOrNull(1),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 1,
                        activeAudio = audioPaneIndex == 1,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                key(2) {
                    SplitPaneSlot(
                        paneIndex = 2,
                        channel = paneChannels.getOrNull(2),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 2,
                        activeAudio = audioPaneIndex == 2,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
                key(3) {
                    SplitPaneSlot(
                        paneIndex = 3,
                        channel = paneChannels.getOrNull(3),
                        viewModel = viewModel,
                    context = context,
                        focused = paneIndex == 3,
                        activeAudio = audioPaneIndex == 3,
                        decodeOnlyAudio = decodeOnlyAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplitPaneSlot(
    paneIndex: Int,
    channel: Channel?,
    viewModel: SplitViewViewModel,
    context: Context,
    focused: Boolean,
    activeAudio: Boolean,
    decodeOnlyAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val ch = channel ?: return
    val showVideo = !decodeOnlyAudio || activeAudio
    if (showVideo) {
        SplitPaneVideoSlot(
            paneIndex = paneIndex,
            label = ch.name,
            viewModel = viewModel,
            context = context,
            focused = focused,
            activeAudio = activeAudio,
            modifier = modifier
        )
    } else {
        SplitPanePlaceholderSlot(
            label = ch.name,
            focused = focused,
            activeAudio = activeAudio,
            modifier = modifier
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplitPaneVideoSlot(
    paneIndex: Int,
    label: String,
    viewModel: SplitViewViewModel,
    context: Context,
    focused: Boolean,
    activeAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val player = remember(paneIndex) { viewModel.playerForPane(context, paneIndex) }
    SplitPane(
        paneIndex = paneIndex,
        label = label,
        player = player,
        showVideo = true,
        focused = focused,
        activeAudio = activeAudio,
        modifier = modifier
    )
}

@Composable
private fun SplitPanePlaceholderSlot(
    label: String,
    focused: Boolean,
    activeAudio: Boolean,
    modifier: Modifier = Modifier
) {
    SplitPane(
        paneIndex = -1,
        label = label,
        player = null,
        showVideo = false,
        focused = focused,
        activeAudio = activeAudio,
        modifier = modifier
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SplitPane(
    paneIndex: Int,
    label: String,
    player: ExoPlayer?,
    showVideo: Boolean,
    focused: Boolean,
    activeAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerView = remember(paneIndex) {
        PlayerView(context).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setKeepContentOnPlayerReset(true)
        }
    }
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
        if (showVideo && player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { playerView.also { PlaybackSurfaceInstrument.attach("split_view_pane_$paneIndex", player, it) } },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                },
                onRelease = { view ->
                    PlaybackSurfaceInstrument.detach("split_view_pane_$paneIndex", player, view)
                    view.player = null
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    maxLines = 3
                )
            }
        }
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
