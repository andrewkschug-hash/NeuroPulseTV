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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.di.PlayerEntryPoint
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.player.LivePlayerManager
import com.neuropulse.tv.player.SeekThumbnailProvider
import com.neuropulse.tv.player.StreamPlaybackStatus
import com.neuropulse.tv.player.userLabel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import dagger.hilt.android.EntryPointAccessors
import com.neuropulse.tv.ui.component.PlayerSideMenu
import com.neuropulse.tv.ui.component.PlayerSideMenuAction
import com.neuropulse.tv.ui.component.PlayerSideMenuMaxSports
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
    seekThumbnailProvider: SeekThumbnailProvider = SeekThumbnailProvider()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
    }
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val sportsNowChannels by viewModel.sportsNowChannels.collectAsStateWithLifecycle()
    val lastWatchedChannel by viewModel.lastWatchedChannel.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val numberInput by viewModel.numberInput.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(true) }
    var showSideMenu by remember { mutableStateOf(false) }
    var focusZone by remember { mutableStateOf(PlayerSideMenuSection.CHANNELS) }
    var focusZoneIndex by remember { mutableIntStateOf(0) }
    var flashingZone by remember { mutableStateOf<PlayerSideMenuSection?>(null) }
    val playerFocusRequester = remember { FocusRequester() }
    val sideMenuFocusRequester = remember { FocusRequester() }
    var showInfo by remember { mutableStateOf(false) }
    var videoQuality by remember { mutableStateOf("") }
    var isBuffering by remember { mutableStateOf(false) }
    var overlayInteractionToken by remember { mutableIntStateOf(0) }
    var failures by remember { mutableStateOf(0) }
    var showStillWatching by remember { mutableStateOf(false) }
    var showStopRecordingDialog by remember { mutableStateOf(false) }
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

    val nearbyChannels = remember(channels, channel?.id) {
        nearbyChannelsFor(channels, channel)
    }
    val sportsPanelChannels = remember(sportsNowChannels) {
        sportsNowChannels.take(PlayerSideMenuMaxSports)
    }

    val sideMenuActions = remember(lastWatchedChannel, isRecordingThisChannel) {
        buildList {
            lastWatchedChannel?.let { prev ->
                add(
                    PlayerSideMenuAction(
                        id = "last",
                        label = "Last: CH ${prev.number} • ${prev.name}",
                        glyph = "↩"
                    )
                )
            }
            add(PlayerSideMenuAction("info", "Info", "ℹ"))
            add(
                PlayerSideMenuAction(
                    id = "record",
                    label = if (isRecordingThisChannel) "Stop REC" else "Record",
                    glyph = "⏺"
                )
            )
            add(PlayerSideMenuAction("guide", "TV Guide", "📺"))
            add(PlayerSideMenuAction("settings", "Settings", "⚙"))
        }
    }

    val infoActionIndex = remember(sideMenuActions) {
        sideMenuActions.indexOfFirst { it.id == "info" }.coerceAtLeast(0)
    }
    val lastNearbyIndex = (nearbyChannels.size - 1).coerceAtLeast(0)
    fun menuSections() = visiblePlayerSideMenuSections(sportsCount = sportsPanelChannels.size)

    fun zoneItemCount(section: PlayerSideMenuSection = focusZone): Int =
        sectionSize(
            section = section,
            channelCount = nearbyChannels.size,
            sportsCount = sportsPanelChannels.size,
            actionCount = sideMenuActions.size
        )

    fun enterFocusZone(zone: PlayerSideMenuSection, index: Int = 0, flash: Boolean = true) {
        focusZone = zone
        focusZoneIndex = index.coerceIn(0, (zoneItemCount(zone) - 1).coerceAtLeast(0))
        if (flash) flashingZone = zone
    }

    fun activatePlayerAction(actionId: String) {
        when (actionId) {
            "info" -> showInfo = true
            "last" -> viewModel.switchToPreviousChannel()
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
            "settings" -> onNavigateSettings()
        }
    }

    fun selectedMenuChannelId(): Long? = when (focusZone) {
        PlayerSideMenuSection.CHANNELS -> nearbyChannels.getOrNull(focusZoneIndex)?.id
        PlayerSideMenuSection.SPORTS -> sportsPanelChannels.getOrNull(focusZoneIndex)?.id
        PlayerSideMenuSection.ACTIONS -> null
    }

    fun activateSideMenuSelection() {
        when {
            focusZone == PlayerSideMenuSection.ACTIONS -> {
                sideMenuActions.getOrNull(focusZoneIndex)?.let { action ->
                    showSideMenu = false
                    activatePlayerAction(action.id)
                }
            }
            else -> {
                selectedMenuChannelId()?.let { id ->
                    viewModel.tuneChannel(id)
                    showSideMenu = false
                }
            }
        }
    }

    fun moveSideMenuVertical(delta: Int) {
        val zones = menuSections()
        val zoneIdx = zones.indexOf(focusZone).coerceAtLeast(0)

        if (delta > 0) {
            when (focusZone) {
                PlayerSideMenuSection.CHANNELS -> when {
                    nearbyChannels.isEmpty() -> enterFocusZone(
                        PlayerSideMenuSection.ACTIONS,
                        infoActionIndex,
                        flash = false
                    )
                    focusZoneIndex < lastNearbyIndex -> focusZoneIndex++
                    else -> enterFocusZone(
                        PlayerSideMenuSection.ACTIONS,
                        infoActionIndex,
                        flash = false
                    )
                }
                PlayerSideMenuSection.SPORTS -> when {
                    focusZoneIndex < sportsPanelChannels.size - 1 -> focusZoneIndex++
                    else -> enterFocusZone(PlayerSideMenuSection.ACTIONS, infoActionIndex, flash = false)
                }
                PlayerSideMenuSection.ACTIONS -> {
                    if (focusZoneIndex < sideMenuActions.size - 1) focusZoneIndex++
                }
            }
        } else {
            when (focusZone) {
                PlayerSideMenuSection.CHANNELS -> {
                    if (focusZoneIndex > 0) focusZoneIndex--
                }
                PlayerSideMenuSection.SPORTS -> when {
                    focusZoneIndex > 0 -> focusZoneIndex--
                    else -> enterFocusZone(PlayerSideMenuSection.CHANNELS, lastNearbyIndex, flash = false)
                }
                PlayerSideMenuSection.ACTIONS -> when {
                    focusZoneIndex == infoActionIndex -> enterFocusZone(
                        PlayerSideMenuSection.CHANNELS,
                        lastNearbyIndex,
                        flash = false
                    )
                    focusZoneIndex > 0 -> focusZoneIndex--
                    zoneIdx > 0 -> {
                        val prevZone = zones[zoneIdx - 1]
                        val prevCount = zoneItemCount(prevZone)
                        if (prevCount > 0) {
                            enterFocusZone(prevZone, prevCount - 1, flash = false)
                        }
                    }
                }
            }
        }
    }

    fun revealOverlay() {
        showOverlay = true
        overlayInteractionToken++
    }

    fun handleSideMenuKey(key: Key): Boolean {
        when (key) {
            Key.DirectionUp -> moveSideMenuVertical(-1)
            Key.DirectionDown -> moveSideMenuVertical(1)
            Key.Enter, Key.DirectionCenter -> activateSideMenuSelection()
            Key.DirectionLeft, Key.Back, Key.Escape -> {
                showSideMenu = false
                revealOverlay()
            }
            else -> return false
        }
        return true
    }

    LaunchedEffect(showSideMenu) {
        if (showSideMenu) {
            sideMenuFocusRequester.requestFocus()
            val idx = nearbyChannels.indexOfFirst { it.id == channel?.id }.coerceAtLeast(0)
            enterFocusZone(PlayerSideMenuSection.CHANNELS, idx, flash = false)
        } else {
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(flashingZone) {
        if (flashingZone != null) {
            delay(300)
            flashingZone = null
        }
    }

    LaunchedEffect(focusZone, sideMenuActions.size, sportsPanelChannels.size, nearbyChannels.size) {
        val maxIdx = (zoneItemCount() - 1).coerceAtLeast(0)
        if (focusZoneIndex > maxIdx) focusZoneIndex = maxIdx
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
                isBuffering = state == Player.STATE_BUFFERING
                videoQuality = videoQualityLabel(player)

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

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoQuality = videoQualityLabel(player)
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.savePosition(player.currentPosition)
            player.removeListener(listener)
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
        }
    }

    LaunchedEffect(showOverlay, overlayInteractionToken) {
        if (showOverlay) {
            delay(3000)
            showOverlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (showSideMenu && handleSideMenuKey(it.key)) return@onPreviewKeyEvent true
                when (it.key) {
                    Key.DirectionUp -> {
                        viewModel.switchPrev()
                        revealOverlay()
                        true
                    }
                    Key.DirectionDown -> {
                        viewModel.switchNext()
                        revealOverlay()
                        true
                    }
                    Key.DirectionLeft -> {
                        revealOverlay()
                        player.seekBack()
                        val url = channel?.streamUrl
                        if (!url.isNullOrBlank()) {
                            seekThumb = seekThumbnailProvider.thumbnail(url, player.currentPosition)
                            showSeekThumb = true
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (!showSideMenu) {
                            showSideMenu = true
                            showOverlay = false
                            enterFocusZone(
                                zone = PlayerSideMenuSection.CHANNELS,
                                index = nearbyChannels.indexOfFirst { it.id == channel?.id }.coerceAtLeast(0),
                                flash = false
                            )
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        revealOverlay()
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
                        revealOverlay()
                        true
                    }
                    Key.Backspace -> {
                        viewModel.clearNumberInput()
                        true
                    }
                    Key.Back, Key.Escape -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                showSideMenu && handleSideMenuKey(it.key)
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    controllerHideOnTouch = true
                    isFocusable = true
                    this.player = player
                }
            },
            update = { view ->
                view.isFocusable = !showSideMenu
                view.isFocusableInTouchMode = !showSideMenu
                if (!showSideMenu) view.player = player
            }
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

        val showBuffering = isBuffering || playbackStatus == StreamPlaybackStatus.LOADING
        val clockText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

        AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTopBarChannel(
                                number = channel?.number ?: 0,
                                name = channel?.name ?: "Loading",
                                quality = videoQuality
                            ),
                            color = Color.White,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = clockText,
                            color = Color.White,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = channel?.name ?: "",
                            color = Color.White,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channel?.currentProgram ?: "No program info",
                            color = Color.White.copy(alpha = 0.72f),
                            fontFamily = DmSansFamily,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            when {
                                playbackStatus == StreamPlaybackStatus.PLAYING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(EpgColors.LiveBadge, CircleShape)
                                        )
                                        Text(
                                            text = "Live",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontFamily = DmSansFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 5.dp)
                                        )
                                    }
                                }
                                playbackStatus == StreamPlaybackStatus.AUDIO_ONLY -> {
                                    Text(
                                        text = "Audio only",
                                        color = EpgColors.TextSecondary,
                                        fontFamily = DmSansFamily,
                                        fontSize = 11.sp
                                    )
                                }
                                playbackStatus == StreamPlaybackStatus.NO_SIGNAL ||
                                    playbackStatus == StreamPlaybackStatus.STALLED ||
                                    playbackStatus == StreamPlaybackStatus.ERROR ||
                                    playbackStatus == StreamPlaybackStatus.UNAVAILABLE -> {
                                    Text(
                                        text = playbackStatus.userLabel(),
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontFamily = DmSansFamily,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (showBuffering) {
                                Text(
                                    text = "Buffer",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontFamily = DmSansFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                                Text(
                                    text = "Loading…",
                                    color = EpgColors.TextSecondary,
                                    fontFamily = DmSansFamily,
                                    fontSize = 11.sp
                                )
                            }
                            if (isRecordingThisChannel) {
                                Text(
                                    text = "REC",
                                    color = Color.White,
                                    fontFamily = DmSansFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .background(
                                            Color.Red.copy(alpha = 0.35f + pulse.value * 0.35f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (numberInput.isNotBlank()) {
                                Text(
                                    text = "Jump: $numberInput",
                                    color = EpgColors.TextSecondary,
                                    fontFamily = DmSansFamily,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        PlayerSideMenu(
            visible = showSideMenu,
            channels = nearbyChannels,
            sportsChannels = sportsPanelChannels,
            actions = sideMenuActions,
            currentChannelId = channel?.id,
            focusedSection = focusZone,
            focusedIndex = focusZoneIndex,
            flashingSection = flashingZone
        )

        if (showSideMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(sideMenuFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        handleSideMenuKey(event.key)
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        handleSideMenuKey(event.key)
                    }
            )
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

private fun videoQualityLabel(player: Player): String {
    val height = (player as? androidx.media3.exoplayer.ExoPlayer)?.videoFormat?.height ?: 0
    return when {
        height >= 2160 -> "4K"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        height > 0 -> "${height}p"
        else -> ""
    }
}

private fun nearbyChannelsFor(allChannels: List<Channel>, current: Channel?): List<Channel> {
    if (allChannels.isEmpty()) return emptyList()
    val sorted = allChannels.sortedBy { it.number }
    if (current == null) return sorted.take(5)
    val index = sorted.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    return sorted.subList(
        maxOf(0, index - 2),
        minOf(sorted.size, index + 3)
    )
}

private fun formatTopBarChannel(number: Int, name: String, quality: String): String {
    val parts = buildList {
        add("CH $number")
        if (name.isNotBlank()) add(name)
        if (quality.isNotBlank()) add(quality)
    }
    return parts.joinToString("  •  ")
}
