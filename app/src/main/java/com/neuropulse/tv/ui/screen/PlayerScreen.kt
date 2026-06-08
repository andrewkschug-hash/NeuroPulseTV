package com.neuropulse.tv.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.di.PlayerEntryPoint
import com.neuropulse.tv.feature.sports.SportsTickerService
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.player.LivePlayerManager
import com.neuropulse.tv.player.SeekThumbnailProvider
import dagger.hilt.android.EntryPointAccessors
import com.neuropulse.tv.ui.viewmodel.PlayerViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    tickerService: SportsTickerService = SportsTickerService(),
    seekThumbnailProvider: SeekThumbnailProvider = SeekThumbnailProvider()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val numberInput by viewModel.numberInput.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var streamInfo by remember { mutableStateOf("SD") }
    var bufferHealth by remember { mutableStateOf(Color.Green) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var failures by remember { mutableStateOf(0) }
    var gameLockEnabled by remember { mutableStateOf(false) }
    var sleepSeconds by remember { mutableStateOf(0) }
    var showStillWatching by remember { mutableStateOf(false) }
    var showStopRecordingDialog by remember { mutableStateOf(false) }
    var ticker by remember { mutableStateOf("Live scores loading...") }
    var seekThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showSeekThumb by remember { mutableStateOf(false) }

    val dimAlpha by animateFloatAsState(if (sleepSeconds in 1..120) 0.35f else 0f, label = "sleepDim")
    val pulse = rememberInfiniteTransition(label = "recPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recPulseAnim"
    )
    val scope = rememberCoroutineScope()
    val isRecordingThisChannel = scheduled.any { it.channelId == channelId && it.status == RecordingStatus.RECORDING.name }

    val livePlayerManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .livePlayerManager()
    }
    val player = remember { livePlayerManager.getOrCreatePlayer(context) }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.FULLSCREEN)
    }

    LaunchedEffect(channelId) {
        viewModel.load(channelId)
    }

    LaunchedEffect(channel?.id, channel?.streamUrl) {
        val ch = channel ?: return@LaunchedEffect
        val url = ch.streamUrl
        livePlayerManager.tuneChannel(context, ch.id, url)
        failures = 0
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val percent = if (player.duration > 0) ((player.bufferedPosition * 100) / player.duration).toInt() else 100
                bufferHealth = when {
                    percent > 70 -> Color.Green
                    percent > 30 -> Color.Yellow
                    else -> Color.Red
                }
                val bitrate = (player.videoFormat?.bitrate ?: 0) / 1000
                streamInfo = if (bitrate > 0) "${bitrate}kbps" else "Unknown"

                if (state == Player.STATE_READY) {
                    val loadMs = (System.currentTimeMillis() - player.currentPosition).coerceAtLeast(1)
                    scope.launch { viewModel.reportStreamHealth(loadMs, bufferEvents = 0, success = true) }
                }
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    if (failures < 3) {
                        failures++
                        player.prepare()
                        player.playWhenReady = true
                    } else {
                        val backup = channel?.backupStreamUrl
                        if (!backup.isNullOrBlank()) {
                            player.setMediaItem(MediaItem.fromUri(backup))
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }
                    scope.launch { viewModel.reportStreamHealth(3000, bufferEvents = 1, success = false) }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.savePosition(player.currentPosition)
            player.removeListener(listener)
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
        }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            delay(4000)
            showOverlay = false
        }
    }

    LaunchedEffect(channel?.name) {
        val name = channel?.name?.lowercase() ?: ""
        if (listOf("sport", "football", "nba", "nfl", "mlb", "nhl").any { it in name }) {
            ticker = tickerService.fetchTicker()
        }
    }

    LaunchedEffect(sleepSeconds) {
        while (sleepSeconds > 0) {
            delay(1000)
            sleepSeconds -= 1
        }
        if (sleepSeconds == 0) {
            player.pause()
            showStillWatching = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionUp -> {
                        if (gameLockEnabled) return@onPreviewKeyEvent true
                        viewModel.switchPrev()
                        showOverlay = true
                        true
                    }

                    Key.DirectionDown -> {
                        if (gameLockEnabled) return@onPreviewKeyEvent true
                        viewModel.switchNext()
                        showOverlay = true
                        true
                    }

                    Key.DirectionLeft -> {
                        player.seekBack()
                        val url = channel?.streamUrl
                        if (!url.isNullOrBlank()) {
                            seekThumb = seekThumbnailProvider.thumbnail(url, player.currentPosition)
                            showSeekThumb = true
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        player.seekForward()
                        val url = channel?.streamUrl
                        if (!url.isNullOrBlank()) {
                            seekThumb = seekThumbnailProvider.thumbnail(url, player.currentPosition)
                            showSeekThumb = true
                        }
                        true
                    }

                    Key.Enter, Key.DirectionCenter -> {
                        showOverlay = !showOverlay
                        true
                    }

                    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine -> {
                        val digit = when (it.key) {
                            Key.Zero -> 0
                            Key.One -> 1
                            Key.Two -> 2
                            Key.Three -> 3
                            Key.Four -> 4
                            Key.Five -> 5
                            Key.Six -> 6
                            Key.Seven -> 7
                            Key.Eight -> 8
                            else -> 9
                        }
                        viewModel.appendDigit(digit)
                        true
                    }

                    Key.Backspace -> {
                        viewModel.clearNumberInput()
                        true
                    }

                    Key.Back -> {
                        if (gameLockEnabled && (it.nativeKeyEvent?.repeatCount ?: 0) < 4) true else { onBack(); true }
                    }

                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { useController = false; this.player = player } }
        )

        if (dimAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        }

        if (showSeekThumb && seekThumb != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .background(Color(0xCC000000))
                    .padding(8.dp)
            ) {
                androidx.compose.foundation.Image(bitmap = seekThumb!!.asImageBitmap(), contentDescription = "Seek preview")
            }
            LaunchedEffect(seekThumb) {
                delay(1200)
                showSeekThumb = false
            }
        }

        AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CH ${channel?.number ?: 0} ${channel?.name ?: "Loading"}")
                    Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
                }

                Box(modifier = Modifier.weight(1f))

                Column(modifier = Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(16.dp)) {
                    Text(channel?.currentProgram ?: "No current program")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text("Quality: $streamInfo")
                        Box(modifier = Modifier.background(bufferHealth).padding(horizontal = 10.dp, vertical = 2.dp)) { Text("Buffer") }
                        if (isRecordingThisChannel) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Red.copy(alpha = pulse.value))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) { Text("REC") }
                        }
                        Text(if (numberInput.isBlank()) "" else "Jump: $numberInput")
                    }
                    Text(ticker, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Button(onClick = onBack) { Text("Back") }
                        Button(onClick = { showInfo = true }) { Text("Info") }
                        Button(onClick = { viewModel.jumpByNumber() }) { Text("Go") }
                        Button(onClick = { showSleepDialog = true }) { Text("Sleep") }
                        Button(onClick = {
                            if (isRecordingThisChannel) {
                                showStopRecordingDialog = true
                            } else {
                                val ch = channel ?: return@Button
                                recordingViewModel.startImmediateRecording(
                                    context = context,
                                    channel = ch,
                                    title = ch.currentProgram ?: ch.name
                                )
                            }
                        }) { Text(if (isRecordingThisChannel) "Stop REC" else "Record") }
                        Button(onClick = { gameLockEnabled = !gameLockEnabled }) { Text(if (gameLockEnabled) "Game Lock On" else "Game Lock Off") }
                    }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(channel?.name ?: "Program Info") },
            text = { Text(channel?.currentProgram ?: "No details") },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("Close") } }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { min ->
                        Button(onClick = { sleepSeconds = min * 60; showSleepDialog = false }) { Text("$min minutes") }
                    }
                    Button(onClick = { sleepSeconds = 120; showSleepDialog = false }) { Text("End of current program") }
                }
            },
            confirmButton = { Button(onClick = { showSleepDialog = false }) { Text("Close") } }
        )
    }

    if (showStillWatching) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Still watching?") },
            text = { Text("Playback paused by sleep timer") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { player.play(); showStillWatching = false }) { Text("Continue") }
                    Button(onClick = onBack) { Text("Exit") }
                }
            }
        )
    }

    if (showStopRecordingDialog) {
        AlertDialog(
            onDismissRequest = { showStopRecordingDialog = false },
            title = { Text("Stop recording?") },
            text = { Text("Current recording will be finalized and saved.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        recordingViewModel.stopActiveRecording(context)
                        showStopRecordingDialog = false
                    }) { Text("Stop") }
                    Button(onClick = { showStopRecordingDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}
