package com.neuropulse.tv.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.component.RecordedPlayerControlsOverlay
import com.neuropulse.tv.ui.component.ScreenBackHandler
import com.neuropulse.tv.ui.component.RecordedPlayerFocusZone
import com.neuropulse.tv.ui.component.RecordingDeleteDialog
import com.neuropulse.tv.ui.component.formatPlayerTime
import com.neuropulse.tv.ui.component.formatRecordedPlayerOverlayDate
import com.neuropulse.tv.domain.model.VodPlaybackContext
import com.neuropulse.tv.player.PictureInPictureController
import com.neuropulse.tv.ui.viewmodel.DirectPlayerViewModel
import com.neuropulse.tv.util.MediaAttribution
import kotlinx.coroutines.delay

@Composable
fun DirectPlayerScreen(
    url: String,
    title: String,
    recordingId: Long = 0L,
    recordedAt: Long = 0L,
    resume: Boolean = false,
    onBack: () -> Unit,
    viewModel: DirectPlayerViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isRecordedPlayback = recordingId > 0L
    val streamId = remember(url) {
        if (isRecordedPlayback) null
        else Regex("""/(\d+)\.\w+$""").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    LaunchedEffect(recordingId) {
        if (recordingId > 0L) viewModel.loadRecordedMedia(recordingId)
    }
    val recordedMedia by viewModel.recordedMedia.collectAsStateWithLifecycle()

    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    var isPlaying by remember(url) { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var hasSeekedToResume by remember(recordingId, resume) { mutableStateOf(false) }
    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }

    val player = remember(url) {
        ExoPlayer.Builder(
            MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
        ).build().apply {
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
            })
        }
    }

    LaunchedEffect(url) {
        if (!isRecordedPlayback) {
            viewModel.setVodMetadata(VodPlaybackContext.consume())
        }
    }

    LaunchedEffect(url, resume, isRecordedPlayback, streamId, player.playbackState) {
        if (isRecordedPlayback || hasSeekedToResume) return@LaunchedEffect
        if (player.playbackState != Player.STATE_READY) return@LaunchedEffect
        val startMs = viewModel.resumePositionMs(streamId, url, resume)
        if (startMs > 0L) player.seekTo(startMs)
        hasSeekedToResume = true
    }

    LaunchedEffect(isRecordedPlayback) {
        if (isRecordedPlayback) return@LaunchedEffect
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

    LaunchedEffect(isRecordedPlayback) {
        if (!isRecordedPlayback) return@LaunchedEffect
        while (true) {
            delay(500)
            positionMs = player.currentPosition
            if (durationMs <= 0L && player.duration > 0) durationMs = player.duration
        }
    }

    LaunchedEffect(isRecordedPlayback, url, streamId, title) {
        if (isRecordedPlayback) return@LaunchedEffect
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

    var showOverlay by remember { mutableStateOf(true) }
    var overlayToken by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(RecordedPlayerFocusZone.TRANSPORT) }
    var transportFocusIndex by remember { mutableIntStateOf(2) }
    var bottomFocusIndex by remember { mutableIntStateOf(1) }
    var seekRepeatCount by remember { mutableIntStateOf(0) }
    var seekTooltip by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun seekDeltaForScrub(): Long = if (seekRepeatCount > 3) 300_000L else 30_000L

    fun revealOverlay() {
        showOverlay = true
        overlayToken++
    }

    LaunchedEffect(showOverlay, overlayToken) {
        if (showOverlay && isRecordedPlayback) {
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
        return false
    }

    ScreenBackHandler(
        onNavigateBack = ::leaveScreen,
        onBackPressed = ::consumeDirectPlayerLocalBack
    )

    fun handleRecordedKey(key: Key): Boolean {
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
                    RecordedPlayerFocusZone.BOTTOM -> when (bottomFocusIndex) {
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
                return true
            }
            Key.DirectionUp -> {
                revealOverlay()
                seekRepeatCount = 0
                focusZone = when (focusZone) {
                    RecordedPlayerFocusZone.BOTTOM -> RecordedPlayerFocusZone.TRANSPORT
                    RecordedPlayerFocusZone.TRANSPORT -> RecordedPlayerFocusZone.SEEK
                    RecordedPlayerFocusZone.SEEK -> RecordedPlayerFocusZone.SEEK
                }
                return true
            }
            Key.DirectionDown -> {
                revealOverlay()
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
                if (isRecordedPlayback) handleRecordedKey(it.key)
                else when (it.key) {
                    Key.Back, Key.Escape -> consumeDirectPlayerLocalBack()
                    Key.Enter, Key.DirectionCenter -> {
                        showOverlay = !showOverlay
                        true
                    }
                    else -> false
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
        } else if (showOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(title, color = Color.White)
                Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back") }
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
