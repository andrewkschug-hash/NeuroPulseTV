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
import com.neuropulse.tv.player.StreamPlaybackStatus
import com.neuropulse.tv.player.isHealthy
import com.neuropulse.tv.player.userLabel
import com.neuropulse.tv.ui.component.StreamStatusBadge
import dagger.hilt.android.EntryPointAccessors
import com.neuropulse.tv.ui.component.PlayerSideMenu
import com.neuropulse.tv.ui.component.PlayerSideMenuAction
import com.neuropulse.tv.ui.component.PlayerSideMenuSection
import com.neuropulse.tv.ui.component.sectionSize
import com.neuropulse.tv.ui.component.visiblePlayerSideMenuSections
import com.neuropulse.tv.ui.component.RecordingPrecheckDialog
import androidx.compose.runtime.mutableIntStateOf
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
    val sportsMenuItems by viewModel.sportsMenuItems.collectAsStateWithLifecycle()
    val newsChannels by viewModel.newsChannels.collectAsStateWithLifecycle()
    val hasPreviousChannel by viewModel.hasPreviousChannel.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val numberInput by viewModel.numberInput.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(true) }
    var showSideMenu by remember { mutableStateOf(false) }
    var sideMenuSection by remember { mutableStateOf(PlayerSideMenuSection.CHANNELS) }
    var sideMenuIndex by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    var streamInfo by remember { mutableStateOf("SD") }
    var bufferHealth by remember { mutableStateOf(Color.Green) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var failures by remember { mutableStateOf(0) }
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

    val sideMenuActions = remember(hasPreviousChannel, isRecordingThisChannel) {
        buildList {
            add(PlayerSideMenuAction("back", "Back", "←"))
            add(PlayerSideMenuAction("info", "Info"))
            if (hasPreviousChannel) {
                add(PlayerSideMenuAction("last", "Last Channel", "↩"))
            }
            add(PlayerSideMenuAction("split", "Split"))
            add(
                PlayerSideMenuAction(
                    id = "record",
                    label = if (isRecordingThisChannel) "Stop REC" else "Record"
                )
            )
            add(PlayerSideMenuAction("guide", "TV Guide"))
            add(PlayerSideMenuAction("recordings", "Recordings"))
            add(PlayerSideMenuAction("settings", "Settings"))
        }
    }

    fun menuSections() = visiblePlayerSideMenuSections(
        sportsCount = sportsMenuItems.size,
        newsCount = newsChannels.size
    )

    fun currentSectionSize(section: PlayerSideMenuSection = sideMenuSection): Int =
        sectionSize(
            section = section,
            channelCount = channels.size,
            sportsCount = sportsMenuItems.size,
            newsCount = newsChannels.size,
            actionCount = sideMenuActions.size
        )

    fun activatePlayerAction(actionId: String) {
        when (actionId) {
            "back" -> onBack()
            "info" -> showInfo = true
            "last" -> viewModel.switchToPreviousChannel()
            "split" -> channel?.id?.let { onOpenSplit(it) }
            "record" -> {
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
            "guide" -> onNavigateGuide()
            "recordings" -> onNavigateRecordings()
            "settings" -> onNavigateSettings()
        }
    }

    fun selectedMenuChannelId(): Long? = when (sideMenuSection) {
        PlayerSideMenuSection.CHANNELS -> channels.getOrNull(sideMenuIndex)?.id
        PlayerSideMenuSection.SPORTS -> sportsMenuItems.getOrNull(sideMenuIndex)?.channel?.id
        PlayerSideMenuSection.NEWS -> newsChannels.getOrNull(sideMenuIndex)?.id
        PlayerSideMenuSection.ACTIONS -> null
    }

    fun activateSideMenuSelection() {
        when (sideMenuSection) {
            PlayerSideMenuSection.ACTIONS -> {
                sideMenuActions.getOrNull(sideMenuIndex)?.let { action ->
                    showSideMenu = false
                    activatePlayerAction(action.id)
                }
            }
            else -> {
                selectedMenuChannelId()?.let { channelId ->
                    viewModel.tuneChannel(channelId)
                    showSideMenu = false
                }
            }
        }
    }

    fun moveSideMenuVertical(delta: Int) {
        val sections = menuSections()
        val sectionIdx = sections.indexOf(sideMenuSection).coerceAtLeast(0)
        val size = currentSectionSize()
        if (delta > 0) {
            if (sideMenuIndex < size - 1) {
                sideMenuIndex += 1
            } else if (sectionIdx < sections.lastIndex) {
                sideMenuSection = sections[sectionIdx + 1]
                sideMenuIndex = 0
            }
        } else {
            if (sideMenuIndex > 0) {
                sideMenuIndex -= 1
            } else if (sectionIdx > 0) {
                val prevSection = sections[sectionIdx - 1]
                sideMenuSection = prevSection
                sideMenuIndex = (sectionSize(
                    section = prevSection,
                    channelCount = channels.size,
                    sportsCount = sportsMenuItems.size,
                    newsCount = newsChannels.size,
                    actionCount = sideMenuActions.size
                ) - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(channel?.id, channels) {
        if (sideMenuSection == PlayerSideMenuSection.CHANNELS) {
            val idx = channels.indexOfFirst { it.id == channel?.id }
            if (idx >= 0) sideMenuIndex = idx
        }
    }

    LaunchedEffect(sideMenuSection, sideMenuActions.size, sportsMenuItems.size, newsChannels.size) {
        val maxIdx = (currentSectionSize() - 1).coerceAtLeast(0)
        if (sideMenuIndex > maxIdx) sideMenuIndex = maxIdx
    }

    val livePlayerManager = remember { entryPoint.livePlayerManager() }
    val player = remember { livePlayerManager.getOrCreatePlayer(context) }
    val playbackStatus by livePlayerManager.playbackStatus.collectAsStateWithLifecycle()

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

    LaunchedEffect(channel?.id, playbackStatus) {
        if (channel?.id == null) return@LaunchedEffect
        if (playbackStatus == StreamPlaybackStatus.IDLE || playbackStatus == StreamPlaybackStatus.LOADING) return@LaunchedEffect
        val success = playbackStatus == StreamPlaybackStatus.PLAYING ||
            playbackStatus == StreamPlaybackStatus.AUDIO_ONLY
        viewModel.reportStreamHealth(
            loadMs = if (success) 1200 else 5000,
            bufferEvents = when (playbackStatus) {
                StreamPlaybackStatus.STALLED, StreamPlaybackStatus.NO_SIGNAL -> 3
                StreamPlaybackStatus.ERROR, StreamPlaybackStatus.UNAVAILABLE -> 5
                else -> 0
            },
            success = success
        )
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
                        if (showSideMenu) {
                            moveSideMenuVertical(-1)
                        } else {
                            viewModel.switchPrev()
                            showOverlay = true
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (showSideMenu) {
                            moveSideMenuVertical(1)
                        } else {
                            viewModel.switchNext()
                            showOverlay = true
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (showSideMenu) {
                            showSideMenu = false
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
                        if (!showSideMenu) {
                            showSideMenu = true
                            showOverlay = false
                            sideMenuSection = PlayerSideMenuSection.CHANNELS
                            sideMenuIndex = channels.indexOfFirst { it.id == channel?.id }
                                .coerceAtLeast(0)
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        when {
                            showSideMenu -> activateSideMenuSelection()
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
                        if (showSideMenu) {
                            showSideMenu = false
                            true
                        } else {
                            onBack()
                            true
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

        if (!playbackStatus.isHealthy()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                StreamStatusBadge(status = playbackStatus)
            }
        }

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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Quality: $streamInfo")
                        Box(modifier = Modifier.background(bufferHealth).padding(horizontal = 10.dp, vertical = 2.dp)) { Text("Buffer") }
                        if (playbackStatus.userLabel().isNotBlank()) {
                            StreamStatusBadge(status = playbackStatus)
                        }
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
                }
            }
        }

        PlayerSideMenu(
            visible = showSideMenu,
            channels = channels,
            sportsItems = sportsMenuItems,
            newsChannels = newsChannels,
            actions = sideMenuActions,
            currentChannelId = channel?.id,
            focusedSection = sideMenuSection,
            focusedIndex = sideMenuIndex
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
