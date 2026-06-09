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
import com.neuropulse.tv.ui.component.PlayerSideMenu
import com.neuropulse.tv.ui.component.RecordingPrecheckDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.component.StorageLocationPicker
import com.neuropulse.tv.ui.viewmodel.PlayerViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    onOpenSplit: (Long) -> Unit = {},
    onNavigateGuide: () -> Unit = onBack,
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    tickerService: SportsTickerService = SportsTickerService(),
    seekThumbnailProvider: SeekThumbnailProvider = SeekThumbnailProvider()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
    }
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val numberInput by viewModel.numberInput.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(true) }
    var showSideMenu by remember { mutableStateOf(false) }
    var sideMenuChannelIndex by remember { mutableIntStateOf(0) }
    var sideMenuActionIndex by remember { mutableIntStateOf(-1) }
    var overlayButtonIndex by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var streamInfo by remember { mutableStateOf("SD") }
    var bufferHealth by remember { mutableStateOf(Color.Green) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var failures by remember { mutableStateOf(0) }
    var gameLockEnabled by remember { mutableStateOf(false) }
    var showStillWatching by remember { mutableStateOf(false) }
    var showStopRecordingDialog by remember { mutableStateOf(false) }
    var ticker by remember { mutableStateOf("Live scores loading...") }
    var seekThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showSeekThumb by remember { mutableStateOf(false) }

    val sleepTimer = remember { entryPoint.sleepTimerController() }
    val sleepRemaining by sleepTimer.remainingSec.collectAsStateWithLifecycle()
    val dimAlpha by animateFloatAsState(if (sleepRemaining in 1..120) 0.35f else 0f, label = "sleepDim")
    val pulse = rememberInfiniteTransition(label = "recPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recPulseAnim"
    )
    val scope = rememberCoroutineScope()
    val isRecordingThisChannel = scheduled.any {
        it.channelId == (channel?.id ?: channelId) && it.status == RecordingStatus.RECORDING.name
    }

    val sideMenuActions = listOf(
        { onNavigateGuide() },
        { onNavigateRecordings() },
        { onNavigateSettings() }
    )

    LaunchedEffect(channel?.id, channels) {
        val idx = channels.indexOfFirst { it.id == channel?.id }
        if (idx >= 0) sideMenuChannelIndex = idx
    }

    fun activateOverlayButton(index: Int) {
        when (index) {
            0 -> onBack()
            1 -> showInfo = true
            2 -> viewModel.jumpByNumber()
            3 -> showSleepDialog = true
            4 -> channel?.id?.let { onOpenSplit(it) }
            5 -> {
                if (isRecordingThisChannel) {
                    showStopRecordingDialog = true
                } else {
                    val ch = channel ?: return
                    recordingViewModel.startImmediateRecording(
                        context = context,
                        channel = ch,
                        title = ch.currentProgram ?: ch.name
                    )
                }
            }
            6 -> gameLockEnabled = !gameLockEnabled
        }
    }

    fun activateSideMenuSelection() {
        if (sideMenuActionIndex >= 0) {
            showSideMenu = false
            sideMenuActions.getOrNull(sideMenuActionIndex)?.invoke()
            sideMenuActionIndex = -1
        } else {
            channels.getOrNull(sideMenuChannelIndex)?.let { selected ->
                viewModel.tuneChannel(selected.id)
                showSideMenu = false
            }
        }
    }

    val overlayButtonLabels = listOf(
        "Back", "Info", "Go", "Sleep", "Split",
        if (isRecordingThisChannel) "Stop REC" else "Record",
        if (gameLockEnabled) "Game Lock On" else "Game Lock Off"
    )

    val livePlayerManager = remember { entryPoint.livePlayerManager() }
    val player = remember { livePlayerManager.getOrCreatePlayer(context) }

    LaunchedEffect(player) {
        sleepTimer.onVolumeFade = { level -> player.volume = level }
        sleepTimer.onExpired = {
            player.pause()
            showStillWatching = true
        }
    }

    LaunchedEffect(channelId) {
        val resume = viewModel.lastPosition()
        if (resume > 0) player.seekTo(resume)
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.FULLSCREEN)
    }

    LaunchedEffect(channelId) {
        viewModel.load(channelId)
    }

    LaunchedEffect(channel?.id, channel?.streamUrl, settings.sleepTimerAutoEnabled, settings.sleepTimerMinutes) {
        val ch = channel ?: return@LaunchedEffect
        if (settings.sleepTimerAutoEnabled) {
            sleepTimer.start(settings.sleepTimerMinutes)
        }
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


    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionUp -> {
                        if (gameLockEnabled) return@onPreviewKeyEvent true
                        if (showSideMenu) {
                            if (sideMenuActionIndex >= 0) {
                                sideMenuActionIndex = (sideMenuActionIndex - 1).coerceAtLeast(-1)
                            } else {
                                sideMenuChannelIndex = (sideMenuChannelIndex - 1).coerceAtLeast(0)
                            }
                        } else if (showOverlay) {
                            overlayButtonIndex = (overlayButtonIndex - 1).coerceAtLeast(0)
                        } else {
                            viewModel.switchPrev()
                            showOverlay = true
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (gameLockEnabled) return@onPreviewKeyEvent true
                        if (showSideMenu) {
                            if (sideMenuActionIndex >= 0) {
                                sideMenuActionIndex = (sideMenuActionIndex + 1)
                                    .coerceAtMost(sideMenuActions.lastIndex)
                            } else if (sideMenuChannelIndex >= channels.lastIndex) {
                                sideMenuActionIndex = 0
                            } else {
                                sideMenuChannelIndex = (sideMenuChannelIndex + 1)
                                    .coerceAtMost(channels.lastIndex)
                            }
                        } else if (showOverlay) {
                            overlayButtonIndex = (overlayButtonIndex + 1)
                                .coerceAtMost(overlayButtonLabels.lastIndex)
                        } else {
                            viewModel.switchNext()
                            showOverlay = true
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (showSideMenu) {
                            showSideMenu = false
                            sideMenuActionIndex = -1
                        } else if (showOverlay) {
                            overlayButtonIndex = (overlayButtonIndex - 1).coerceAtLeast(0)
                        } else {
                            player.seekBack()
                            val url = channel?.streamUrl
                            if (!url.isNullOrBlank()) {
                                seekThumb = seekThumbnailProvider.thumbnail(url, player.currentPosition)
                                showSeekThumb = true
                            }
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (showSideMenu) {
                            if (sideMenuActionIndex < 0 && sideMenuChannelIndex < channels.lastIndex) {
                                sideMenuChannelIndex += 1
                            }
                        } else if (showOverlay) {
                            overlayButtonIndex = (overlayButtonIndex + 1)
                                .coerceAtMost(overlayButtonLabels.lastIndex)
                        } else {
                            showSideMenu = true
                            showOverlay = false
                            sideMenuActionIndex = -1
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        when {
                            showSideMenu -> activateSideMenuSelection()
                            showOverlay -> activateOverlayButton(overlayButtonIndex)
                            else -> showOverlay = true
                        }
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
                        showOverlay = true
                        true
                    }
                    Key.Backspace -> {
                        viewModel.clearNumberInput()
                        true
                    }
                    Key.Back, Key.Escape -> {
                        when {
                            showSideMenu -> {
                                showSideMenu = false
                                sideMenuActionIndex = -1
                                true
                            }
                            gameLockEnabled && (it.nativeKeyEvent?.repeatCount ?: 0) < 4 -> true
                            else -> {
                                onBack()
                                true
                            }
                        }
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
                        overlayButtonLabels.forEachIndexed { index, label ->
                            val displayLabel = when (index) {
                                3 -> if (sleepRemaining > 0) "Sleep ${sleepTimer.formatCountdown()}" else "Sleep"
                                else -> label
                            }
                            val focused = index == overlayButtonIndex
                            Button(
                                onClick = { activateOverlayButton(index) },
                                modifier = if (focused) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                }
                            ) {
                                Text(
                                    text = displayLabel,
                                    fontFamily = DmSansFamily,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        PlayerSideMenu(
            visible = showSideMenu,
            channels = channels,
            currentChannelId = channel?.id,
            focusedChannelIndex = sideMenuChannelIndex,
            focusedActionIndex = sideMenuActionIndex,
            onDismiss = { showSideMenu = false },
            onChannelSelected = { viewModel.tuneChannel(it.id) },
            onGuide = onNavigateGuide,
            onRecordings = onNavigateRecordings,
            onSettings = onNavigateSettings
        )
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
                        Button(onClick = { sleepTimer.start(min); showSleepDialog = false }) { Text("$min minutes") }
                    }
                    Button(onClick = { sleepTimer.cancel(); showSleepDialog = false }) { Text("Cancel timer") }
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

    if (showStoragePicker) {
        StorageLocationPicker(
            options = storageOptions,
            onSelect = { recordingViewModel.onStorageSelected(it, context) },
            onDismiss = { recordingViewModel.dismissStoragePicker() }
        )
    }

    precheck?.let { check ->
        RecordingPrecheckDialog(
            estimateText = check.estimateText,
            lowStorageWarning = check.lowStorageWarning,
            insufficientSpaceWarning = check.insufficientSpaceWarning,
            onConfirm = { recordingViewModel.confirmImmediateRecording(context) },
            onDismiss = { recordingViewModel.dismissPrecheck() }
        )
    }
}
