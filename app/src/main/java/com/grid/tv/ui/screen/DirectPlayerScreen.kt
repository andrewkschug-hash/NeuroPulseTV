package com.grid.tv.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grid.tv.ui.component.CatchupPlayerControlsOverlay
import com.grid.tv.ui.component.VodInlineSubtitlePanel
import com.grid.tv.ui.component.VodPlayerFocusZone
import com.grid.tv.ui.component.VodPlayerHudOverlay
import com.grid.tv.ui.component.RecordedPlayerControlsOverlay
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.RecordedPlayerFocusZone
import com.grid.tv.ui.component.RecordingDeleteDialog
import com.grid.tv.ui.component.formatPlayerTime
import com.grid.tv.ui.component.formatRecordedPlayerOverlayDate
import com.grid.tv.domain.model.CatchupPlaybackContext
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.domain.model.VodPlaybackContext
import com.grid.tv.player.PictureInPictureController
import com.grid.tv.ui.viewmodel.DirectPlayerViewModel
import kotlinx.coroutines.delay
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DirectPlayerScreen(
    url: String,
    title: String,
    recordingId: Long = 0L,
    recordedAt: Long = 0L,
    resume: Boolean = false,
    onBack: () -> Unit,
    onJumpToLive: (Long) -> Unit = {},
    viewModel: DirectPlayerViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val catchupSession = remember { CatchupPlaybackContext.consume() }
    val isCatchupPlayback = catchupSession != null && recordingId <= 0L
    val isRecordedPlayback = recordingId > 0L
    val streamId = remember(url) {
        if (isRecordedPlayback) null
        else Regex("""/(\d+)\.\w+$""").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    LaunchedEffect(recordingId) {
        if (recordingId > 0L) viewModel.loadRecordedMedia(recordingId)
    }
    val recordedMedia by viewModel.recordedMedia.collectAsStateWithLifecycle()
    val subtitleSettings by viewModel.settings.collectAsStateWithLifecycle()
    val activeSubtitle by viewModel.activeSubtitle.collectAsStateWithLifecycle()
    var subtitlesAttached by remember(url) { mutableStateOf(false) }

    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    var isPlaying by remember(url) { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var hasSeekedToResume by remember(recordingId, resume) { mutableStateOf(false) }
    val playbackRetryCount = remember(url) { intArrayOf(0) }
    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }

    val player = remember(url) {
        viewModel.createPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        if (durationMs <= 0L) durationMs = duration.coerceAtLeast(0L)
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (playbackRetryCount[0] < 2) {
                        playbackRetryCount[0] += 1
                        prepare()
                        playWhenReady = true
                    }
                }
            })
        }
    }

    LaunchedEffect(url) {
        if (!isRecordedPlayback && !isCatchupPlayback) {
            viewModel.setVodMetadata(VodPlaybackContext.consume())
        }
    }

    LaunchedEffect(player.playbackState, url, isRecordedPlayback, isCatchupPlayback, subtitlesAttached) {
        if (isRecordedPlayback || isCatchupPlayback || subtitlesAttached) return@LaunchedEffect
        if (player.playbackState != Player.STATE_READY) return@LaunchedEffect
        viewModel.attachAutoSubtitles(player, playerViewRef[0], url, title)
        subtitlesAttached = true
    }

    LaunchedEffect(subtitleSettings, playerViewRef[0]) {
        if (isRecordedPlayback || isCatchupPlayback) return@LaunchedEffect
        viewModel.applySubtitleStyle(playerViewRef[0], subtitleSettings)
    }

    LaunchedEffect(url, resume, isRecordedPlayback, isCatchupPlayback, streamId, player.playbackState) {
        if (isRecordedPlayback || isCatchupPlayback || hasSeekedToResume) return@LaunchedEffect
        if (player.playbackState != Player.STATE_READY) return@LaunchedEffect
        val startMs = viewModel.resumePositionMs(streamId, url, resume)
        if (startMs > 0L) player.seekTo(startMs)
        hasSeekedToResume = true
    }

    LaunchedEffect(isRecordedPlayback, isCatchupPlayback) {
        if (isRecordedPlayback || isCatchupPlayback) return@LaunchedEffect
        viewModel.pipController.setPlaybackActive(true)
    }

    LaunchedEffect(recordedMedia, resume, isRecordedPlayback) {
        if (!isRecordedPlayback || hasSeekedToResume) return@LaunchedEffect
        val media = recordedMedia ?: return@LaunchedEffect
        if (player.playbackState == Player.STATE_READY) {
            val startMs = if (resume && media.playbackPositionMs > 30_000) {
                media.playbackPositionMs
            } else {
                0L
            }
            if (startMs > 0L) player.seekTo(startMs)
            hasSeekedToResume = true
        }
    }

    LaunchedEffect(isRecordedPlayback) {
        if (!isRecordedPlayback) return@LaunchedEffect
        playbackSpeed = viewModel.loadPlaybackSpeed()
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isRecordedPlayback, recordingId) {
        if (!isRecordedPlayback || recordingId <= 0L) return@LaunchedEffect
        while (true) {
            delay(5_000)
            viewModel.saveRecordingPosition(recordingId, player.currentPosition)
        }
    }

    LaunchedEffect(url) {
        while (true) {
            delay(500)
            positionMs = player.currentPosition
            if (durationMs <= 0L && player.duration > 0) durationMs = player.duration
        }
    }

    LaunchedEffect(isRecordedPlayback, isCatchupPlayback, url, streamId, title) {
        if (isRecordedPlayback || isCatchupPlayback) return@LaunchedEffect
        while (true) {
            delay(10_000)
            val pos = player.currentPosition
            val dur = player.duration.coerceAtLeast(0L)
            if (pos > 0L) {
                viewModel.persistProgress(streamId, pos, title, dur, url)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            viewModel.pipController.setPlaybackActive(false)
            positionMs = player.currentPosition
            if (durationMs <= 0L) durationMs = player.duration.coerceAtLeast(0L)
            if (isRecordedPlayback && recordingId > 0L) {
                viewModel.saveRecordingPosition(recordingId, positionMs)
            } else {
                viewModel.persistProgress(streamId, positionMs, title, durationMs, url)
            }
            playerViewRef[0]?.player = null
            player.clearVideoSurface()
            player.release()
        }
    }

    var showOverlay by remember { mutableStateOf(false) }
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var overlayToken by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(RecordedPlayerFocusZone.TRANSPORT) }
    var vodFocusZone by remember { mutableStateOf(VodPlayerFocusZone.TRANSPORT) }
    var transportFocusIndex by remember { mutableIntStateOf(2) }
    var bottomFocusIndex by remember { mutableIntStateOf(1) }
    var subtitleFocusRow by remember { mutableIntStateOf(0) }
    var subtitleFocusCol by remember { mutableIntStateOf(0) }
    var seekRepeatCount by remember { mutableIntStateOf(0) }
    var seekTooltip by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun seekDeltaForScrub(): Long = if (seekRepeatCount > 3) 300_000L else 30_000L

    fun revealOverlay() {
        showOverlay = true
        overlayToken++
    }

    LaunchedEffect(showOverlay, overlayToken, showSubtitlePanel) {
        if (showOverlay && !showSubtitlePanel) {
            delay(4_000)
            showOverlay = false
            seekTooltip = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
        seekTooltip = formatPlayerTime(target)
        revealOverlay()
    }

    fun leaveScreen() {
        playerViewRef[0]?.player = null
        onBack()
    }

    fun consumeDirectPlayerLocalBack(): Boolean {
        if (showDeleteDialog) {
            showDeleteDialog = false
            return true
        }
        if (showSubtitlePanel) {
            showSubtitlePanel = false
            vodFocusZone = VodPlayerFocusZone.TRANSPORT
            transportFocusIndex = 5
            return true
        }
        if (showOverlay && !isRecordedPlayback && !isCatchupPlayback) {
            showOverlay = false
            return true
        }
        return false
    }

    fun openSubtitlePanel() {
        showSubtitlePanel = true
        showOverlay = true
        overlayToken++
        vodFocusZone = VodPlayerFocusZone.SUBTITLE_PANEL
        subtitleFocusRow = 0
        subtitleFocusCol = if (subtitleSettings.subtitlesEnabled) 1 else 0
    }

    fun subtitleRowColumnCount(row: Int): Int = when (row) {
        0 -> 2
        else -> 3
    }

    fun applySubtitlePanelSelection() {
        when (subtitleFocusRow) {
            0 -> {
                val wantEnabled = subtitleFocusCol == 1
                if (wantEnabled != subtitleSettings.subtitlesEnabled) {
                    viewModel.updateSubtitleSettings(
                        enabled = wantEnabled,
                        player = player,
                        playerView = playerViewRef[0],
                        url = url,
                        title = title
                    )
                }
            }
            1 -> viewModel.updateSubtitleSettings(
                fontSize = SubtitleFontSize.entries[subtitleFocusCol],
                playerView = playerViewRef[0]
            )
            2 -> viewModel.updateSubtitleSettings(
                position = SubtitlePosition.entries[subtitleFocusCol],
                playerView = playerViewRef[0]
            )
        }
    }

    fun handleVodPlaybackKey(key: Key): Boolean {
        when (key) {
            Key.Back, Key.Escape -> return consumeDirectPlayerLocalBack()
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                revealOverlay()
                when (vodFocusZone) {
                    VodPlayerFocusZone.TRANSPORT -> when (transportFocusIndex) {
                        0 -> seekBy(-10_000)
                        1 -> seekBy(-30_000)
                        2 -> {
                            player.playWhenReady = !player.isPlaying
                            isPlaying = player.isPlaying
                        }
                        3 -> seekBy(30_000)
                        4 -> seekBy(10_000)
                        5 -> openSubtitlePanel()
                    }
                    VodPlayerFocusZone.SEEK -> seekBy(-30_000)
                    VodPlayerFocusZone.SUBTITLE_PANEL -> applySubtitlePanelSelection()
                }
                return true
            }
            Key.DirectionUp -> {
                revealOverlay()
                seekRepeatCount = 0
                when (vodFocusZone) {
                    VodPlayerFocusZone.SUBTITLE_PANEL -> {
                        subtitleFocusRow = (subtitleFocusRow - 1).coerceAtLeast(0)
                        subtitleFocusCol = subtitleFocusCol.coerceAtMost(subtitleRowColumnCount(subtitleFocusRow) - 1)
                        applySubtitlePanelSelection()
                    }
                    VodPlayerFocusZone.TRANSPORT -> vodFocusZone = VodPlayerFocusZone.SEEK
                    VodPlayerFocusZone.SEEK -> Unit
                }
                return true
            }
            Key.DirectionDown -> {
                revealOverlay()
                when (vodFocusZone) {
                    VodPlayerFocusZone.SUBTITLE_PANEL -> {
                        subtitleFocusRow = (subtitleFocusRow + 1).coerceAtMost(2)
                        subtitleFocusCol = subtitleFocusCol.coerceAtMost(subtitleRowColumnCount(subtitleFocusRow) - 1)
                        applySubtitlePanelSelection()
                    }
                    VodPlayerFocusZone.SEEK -> vodFocusZone = VodPlayerFocusZone.TRANSPORT
                    VodPlayerFocusZone.TRANSPORT -> Unit
                }
                return true
            }
            Key.DirectionLeft -> {
                revealOverlay()
                when (vodFocusZone) {
                    VodPlayerFocusZone.TRANSPORT -> {
                        transportFocusIndex = (transportFocusIndex - 1).coerceAtLeast(0)
                    }
                    VodPlayerFocusZone.SEEK -> {
                        seekRepeatCount++
                        seekBy(-seekDeltaForScrub())
                    }
                    VodPlayerFocusZone.SUBTITLE_PANEL -> {
                        if (subtitleFocusRow == 0 && subtitleFocusCol == 0) {
                            showSubtitlePanel = false
                            vodFocusZone = VodPlayerFocusZone.TRANSPORT
                            transportFocusIndex = 5
                        } else {
                            subtitleFocusCol = (subtitleFocusCol - 1).coerceAtLeast(0)
                            applySubtitlePanelSelection()
                        }
                    }
                }
                return true
            }
            Key.DirectionRight -> {
                revealOverlay()
                when (vodFocusZone) {
                    VodPlayerFocusZone.TRANSPORT -> {
                        if (transportFocusIndex >= 5) {
                            openSubtitlePanel()
                        } else {
                            transportFocusIndex = (transportFocusIndex + 1).coerceAtMost(5)
                        }
                    }
                    VodPlayerFocusZone.SEEK -> {
                        seekRepeatCount++
                        seekBy(seekDeltaForScrub())
                    }
                    VodPlayerFocusZone.SUBTITLE_PANEL -> {
                        subtitleFocusCol = (subtitleFocusCol + 1)
                            .coerceAtMost(subtitleRowColumnCount(subtitleFocusRow) - 1)
                        applySubtitlePanelSelection()
                    }
                }
                return true
            }
            else -> return false
        }
    }

    ScreenBackHandler(
        onNavigateBack = ::leaveScreen,
        onBackPressed = ::consumeDirectPlayerLocalBack
    )

    var jumpToLiveFocused by remember { mutableStateOf(false) }

    fun handlePlaybackKey(key: Key): Boolean {
        when (key) {
            Key.Back, Key.Escape -> return consumeDirectPlayerLocalBack()
            Key.Enter, Key.DirectionCenter -> {
                revealOverlay()
                when (focusZone) {
                    RecordedPlayerFocusZone.TRANSPORT -> when (transportFocusIndex) {
                        0 -> seekBy(-10_000)
                        1 -> seekBy(-30_000)
                        2 -> {
                            player.playWhenReady = !player.isPlaying
                            isPlaying = player.isPlaying
                        }
                        3 -> seekBy(30_000)
                        4 -> seekBy(10_000)
                    }
                    RecordedPlayerFocusZone.SEEK -> seekBy(-30_000)
                    RecordedPlayerFocusZone.BOTTOM -> {
                        if (isCatchupPlayback && catchupSession != null) {
                            playerViewRef[0]?.player = null
                            onJumpToLive(catchupSession.channelId)
                        } else when (bottomFocusIndex) {
                            0 -> showDeleteDialog = true
                            1 -> {
                                playbackSpeed = 1f
                                player.setPlaybackSpeed(1f)
                                viewModel.updatePlaybackSpeed(1f)
                            }
                            2 -> {
                                playbackSpeed = 1.5f
                                player.setPlaybackSpeed(1.5f)
                                viewModel.updatePlaybackSpeed(1.5f)
                            }
                            3 -> {
                                playbackSpeed = 2f
                                player.setPlaybackSpeed(2f)
                                viewModel.updatePlaybackSpeed(2f)
                            }
                        }
                    }
                }
                return true
            }
            Key.DirectionUp -> {
                revealOverlay()
                seekRepeatCount = 0
                jumpToLiveFocused = false
                focusZone = when (focusZone) {
                    RecordedPlayerFocusZone.BOTTOM -> RecordedPlayerFocusZone.TRANSPORT
                    RecordedPlayerFocusZone.TRANSPORT -> RecordedPlayerFocusZone.SEEK
                    RecordedPlayerFocusZone.SEEK -> RecordedPlayerFocusZone.SEEK
                }
                return true
            }
            Key.DirectionDown -> {
                revealOverlay()
                jumpToLiveFocused = isCatchupPlayback
                focusZone = when (focusZone) {
                    RecordedPlayerFocusZone.SEEK -> RecordedPlayerFocusZone.TRANSPORT
                    RecordedPlayerFocusZone.TRANSPORT -> RecordedPlayerFocusZone.BOTTOM
                    RecordedPlayerFocusZone.BOTTOM -> RecordedPlayerFocusZone.BOTTOM
                }
                return true
            }
            Key.DirectionLeft -> {
                revealOverlay()
                when (focusZone) {
                    RecordedPlayerFocusZone.TRANSPORT -> {
                        transportFocusIndex = (transportFocusIndex - 1).coerceAtLeast(0)
                    }
                    RecordedPlayerFocusZone.SEEK -> {
                        seekRepeatCount++
                        seekBy(-seekDeltaForScrub())
                    }
                    RecordedPlayerFocusZone.BOTTOM -> {
                        bottomFocusIndex = (bottomFocusIndex - 1).coerceAtLeast(0)
                    }
                }
                return true
            }
            Key.DirectionRight -> {
                revealOverlay()
                when (focusZone) {
                    RecordedPlayerFocusZone.TRANSPORT -> {
                        transportFocusIndex = (transportFocusIndex + 1).coerceAtMost(4)
                    }
                    RecordedPlayerFocusZone.SEEK -> {
                        seekRepeatCount++
                        seekBy(seekDeltaForScrub())
                    }
                    RecordedPlayerFocusZone.BOTTOM -> {
                        bottomFocusIndex = (bottomFocusIndex + 1).coerceAtMost(3)
                    }
                }
                return true
            }
            else -> return false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    isRecordedPlayback || isCatchupPlayback -> handlePlaybackKey(it.key)
                    else -> {
                        if (it.key !in setOf(Key.Back, Key.Escape)) {
                            revealOverlay()
                        }
                        handleVodPlaybackKey(it.key)
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    playerViewRef[0] = this
                }
            },
            onRelease = { view ->
                view.player = null
                if (playerViewRef[0] === view) playerViewRef[0] = null
            }
        )

        if (isRecordedPlayback) {
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RecordedPlayerControlsOverlay(
                    title = title,
                    recordedAtLabel = formatRecordedPlayerOverlayDate(recordedAt),
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed,
                    focusZone = focusZone,
                    transportFocusIndex = transportFocusIndex,
                    bottomFocusIndex = bottomFocusIndex,
                    seekTooltip = seekTooltip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (isCatchupPlayback && catchupSession != null) {
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                CatchupPlayerControlsOverlay(
                    programTitle = catchupSession.programTitle,
                    channelName = catchupSession.channelName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    focusZone = focusZone,
                    transportFocusIndex = transportFocusIndex,
                    jumpToLiveFocused = jumpToLiveFocused && focusZone == RecordedPlayerFocusZone.BOTTOM,
                    seekTooltip = seekTooltip,
                    onJumpToLive = {
                        playerViewRef[0]?.player = null
                        onJumpToLive(catchupSession.channelId)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VodPlayerHudOverlay(
                        title = title,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        focusZone = vodFocusZone,
                        transportFocusIndex = transportFocusIndex,
                        seekTooltip = seekTooltip,
                        subtitlesEnabled = subtitleSettings.subtitlesEnabled,
                        showSubtitlePanel = showSubtitlePanel,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    if (showSubtitlePanel) {
                        VodInlineSubtitlePanel(
                            settings = subtitleSettings,
                            focusRow = subtitleFocusRow,
                            focusCol = subtitleFocusCol,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }

        if (showDeleteDialog) {
            val media = recordedMedia
            RecordingDeleteDialog(
                title = title,
                fileSizeBytes = media?.fileSizeBytes ?: 0L,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.deleteRecording(recordingId, onBack)
                }
            )
        }
    }
}
