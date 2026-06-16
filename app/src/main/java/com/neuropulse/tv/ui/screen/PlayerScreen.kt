package com.neuropulse.tv.ui.screen

import com.neuropulse.tv.ui.component.GlowFocusButton
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.neuropulse.tv.ui.component.PlayerSideMenuFocusTarget
import com.neuropulse.tv.ui.component.PlayerSideMenuMaxFavorites
import com.neuropulse.tv.ui.component.PlayerSideMenuSection
import com.neuropulse.tv.ui.component.buildPlayerSideMenuFocusOrder
import com.neuropulse.tv.ui.component.playerSideMenuFocusSection
import com.neuropulse.tv.ui.component.playerSideMenuFocusState
import com.neuropulse.tv.ui.component.RecordingPrecheckDialog
import com.neuropulse.tv.ui.component.ScreenBackHandler
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.component.playerPlaybackStatus
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
    val livePlayerManager = remember { entryPoint.livePlayerManager() }
    val lastChannel by livePlayerManager.lastChannelFlow.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val isRecordingActive by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecording by recordingViewModel.activeRecording.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val numberInput by viewModel.numberInput.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val settingsReady by settingsViewModel.settingsReady.collectAsStateWithLifecycle()

    var showOverlay by remember { mutableStateOf(true) }
    var showSideMenu by remember { mutableStateOf(false) }
    var channelsExpanded by remember { mutableStateOf(false) }
    var favoritesExpanded by remember { mutableStateOf(false) }
    var focusTargetIndex by remember { mutableIntStateOf(0) }
    var flashingZone by remember { mutableStateOf<PlayerSideMenuSection?>(null) }
    val playerFocusRequester = remember { FocusRequester() }
    val sideMenuFocusRequester = remember { FocusRequester() }
    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }
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
    val favoritePanelChannels = remember(favoriteChannels) {
        favoriteChannels.take(PlayerSideMenuMaxFavorites)
    }

    val sideMenuActions = remember(lastChannel, isRecordingActive) {
        buildList {
            add(PlayerSideMenuAction("info", "Info", "ℹ"))
            if (lastChannel != null) {
                add(PlayerSideMenuAction("last_channel", "Last Channel", "⇄"))
            }
            add(
                PlayerSideMenuAction(
                    id = "record",
                    label = if (isRecordingActive) "■ Stop" else "Record",
                    glyph = if (isRecordingActive) "■" else "⏺",
                    highlightStop = isRecordingActive
                )
            )
            add(PlayerSideMenuAction("split_view", "Split View", "▥"))
            add(PlayerSideMenuAction("guide", "TV Guide", "📺"))
            add(PlayerSideMenuAction("settings", "Settings", "⚙"))
        }
    }

    val sideMenuFocusOrder = remember(
        nearbyChannels.size,
        favoritePanelChannels.size,
        sideMenuActions.size,
        channelsExpanded,
        favoritesExpanded
    ) {
        buildPlayerSideMenuFocusOrder(
            nearbyChannelCount = nearbyChannels.size,
            favoriteChannelCount = favoritePanelChannels.size,
            actionCount = sideMenuActions.size,
            channelsExpanded = channelsExpanded,
            favoritesExpanded = favoritesExpanded
        )
    }
    val currentFocusTarget = sideMenuFocusOrder.getOrNull(focusTargetIndex)
    val sideMenuFocusState = playerSideMenuFocusState(currentFocusTarget)

    fun applyFocusTargetIndex(index: Int, flash: Boolean = true) {
        if (sideMenuFocusOrder.isEmpty()) return
        focusTargetIndex = index.coerceIn(0, sideMenuFocusOrder.lastIndex)
        if (flash) {
            sideMenuFocusOrder.getOrNull(focusTargetIndex)?.let {
                flashingZone = playerSideMenuFocusSection(it)
            }
        }
    }

    fun activatePlayerAction(actionId: String) {
        when (actionId) {
            "info" -> showInfo = true
            "last_channel" -> {
                if (livePlayerManager.switchChannel(context)) {
                    livePlayerManager.activeChannelId()?.let { viewModel.tuneChannel(it) }
                }
            }
            "record" -> {
                if (isRecordingActive) {
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
            "split_view" -> onOpenSplit(channel?.id ?: channelId)
            "guide" -> onNavigateGuide()
            "settings" -> onNavigateSettings()
        }
    }

    fun focusChannelsHeader() {
        val headerIndex = sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.ChannelsHeader)
        if (headerIndex >= 0) applyFocusTargetIndex(headerIndex, flash = false)
    }

    fun focusFavoritesHeader() {
        val headerIndex = sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.FavoritesHeader)
        if (headerIndex >= 0) applyFocusTargetIndex(headerIndex, flash = false)
    }

    fun collapseChannelsSection(): Boolean {
        if (!channelsExpanded) return false
        channelsExpanded = false
        focusChannelsHeader()
        return true
    }

    fun collapseFavoritesSection(): Boolean {
        if (!favoritesExpanded) return false
        favoritesExpanded = false
        focusFavoritesHeader()
        return true
    }

    fun activateSideMenuSelection() {
        when (val target = sideMenuFocusOrder.getOrNull(focusTargetIndex)) {
            PlayerSideMenuFocusTarget.ChannelsHeader -> {
                channelsExpanded = !channelsExpanded
            }
            is PlayerSideMenuFocusTarget.NearbyChannel -> {
                nearbyChannels.getOrNull(target.index)?.id?.let { id ->
                    viewModel.tuneChannel(id)
                    showSideMenu = false
                }
            }
            PlayerSideMenuFocusTarget.BrowseAll -> {
                showSideMenu = false
                onNavigateGuide()
            }
            PlayerSideMenuFocusTarget.FavoritesHeader -> {
                favoritesExpanded = !favoritesExpanded
            }
            is PlayerSideMenuFocusTarget.FavoriteChannel -> {
                favoritePanelChannels.getOrNull(target.index)?.id?.let { id ->
                    viewModel.tuneChannel(id)
                    showSideMenu = false
                }
            }
            is PlayerSideMenuFocusTarget.Action -> {
                sideMenuActions.getOrNull(target.index)?.let { action ->
                    showSideMenu = false
                    activatePlayerAction(action.id)
                }
            }
            null -> Unit
        }
    }

    fun moveSideMenuVertical(delta: Int) {
        if (sideMenuFocusOrder.isEmpty()) return
        applyFocusTargetIndex(focusTargetIndex + delta, flash = false)
    }

    fun openSideMenu() {
        showSideMenu = true
        showOverlay = false
        val startIndex = if (!channelsExpanded) {
            sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.ChannelsHeader).coerceAtLeast(0)
        } else {
            val channelIdx = nearbyChannels.indexOfFirst { it.id == channel?.id }.coerceAtLeast(0)
            sideMenuFocusOrder.indexOfFirst {
                it is PlayerSideMenuFocusTarget.NearbyChannel && it.index == channelIdx
            }.takeIf { idx -> idx >= 0 }
                ?: sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.BrowseAll).coerceAtLeast(0)
        }
        applyFocusTargetIndex(startIndex, flash = false)
    }

    fun revealOverlay() {
        showOverlay = true
        overlayInteractionToken++
    }

    fun leavePlayer() {
        playerViewRef[0]?.player = null
        onBack()
    }

    fun handleSideMenuKey(key: Key): Boolean {
        when (key) {
            Key.DirectionUp -> moveSideMenuVertical(-1)
            Key.DirectionDown -> moveSideMenuVertical(1)
            Key.Enter, Key.DirectionCenter -> activateSideMenuSelection()
            Key.DirectionRight -> when (currentFocusTarget) {
                PlayerSideMenuFocusTarget.ChannelsHeader -> {
                    if (!channelsExpanded) channelsExpanded = true
                }
                PlayerSideMenuFocusTarget.FavoritesHeader -> {
                    if (!favoritesExpanded) favoritesExpanded = true
                }
                else -> return false
            }
            Key.DirectionLeft -> when (currentFocusTarget) {
                PlayerSideMenuFocusTarget.ChannelsHeader -> {
                    if (collapseChannelsSection()) return true
                }
                PlayerSideMenuFocusTarget.FavoritesHeader -> {
                    if (collapseFavoritesSection()) return true
                }
                is PlayerSideMenuFocusTarget.NearbyChannel,
                PlayerSideMenuFocusTarget.BrowseAll -> {
                    if (collapseChannelsSection()) return true
                }
                is PlayerSideMenuFocusTarget.FavoriteChannel -> {
                    if (collapseFavoritesSection()) return true
                }
                else -> Unit
            }
            Key.Back, Key.Escape -> {
                when (currentFocusTarget) {
                    PlayerSideMenuFocusTarget.ChannelsHeader -> {
                        if (collapseChannelsSection()) return true
                    }
                    PlayerSideMenuFocusTarget.FavoritesHeader -> {
                        if (collapseFavoritesSection()) return true
                    }
                    is PlayerSideMenuFocusTarget.NearbyChannel,
                    PlayerSideMenuFocusTarget.BrowseAll -> {
                        if (collapseChannelsSection()) return true
                    }
                    is PlayerSideMenuFocusTarget.FavoriteChannel -> {
                        if (collapseFavoritesSection()) return true
                    }
                    else -> Unit
                }
                showSideMenu = false
                revealOverlay()
                return true
            }
            else -> return false
        }
        if (key == Key.DirectionLeft) {
            showSideMenu = false
            revealOverlay()
        }
        return true
    }

    fun consumePlayerLocalBack(): Boolean {
        if (showStopRecordingDialog) {
            showStopRecordingDialog = false
            return true
        }
        if (showInfo) {
            showInfo = false
            return true
        }
        if (showStoragePicker) {
            recordingViewModel.dismissStoragePicker()
            return true
        }
        if (precheck != null) {
            recordingViewModel.dismissPrecheck()
            return true
        }
        if (showSideMenu) {
            handleSideMenuKey(Key.Back)
            return true
        }
        return false
    }

    ScreenBackHandler(
        onNavigateBack = ::leavePlayer,
        onBackPressed = ::consumePlayerLocalBack
    )

    LaunchedEffect(showSideMenu) {
        if (showSideMenu) {
            sideMenuFocusRequester.requestFocusSafelyAfterLayout()
            val startIndex = if (!channelsExpanded) {
                sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.ChannelsHeader).coerceAtLeast(0)
            } else {
                val channelIdx = nearbyChannels.indexOfFirst { it.id == channel?.id }.coerceAtLeast(0)
                sideMenuFocusOrder.indexOfFirst {
                    it is PlayerSideMenuFocusTarget.NearbyChannel && it.index == channelIdx
                }.takeIf { idx -> idx >= 0 }
                    ?: sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.BrowseAll).coerceAtLeast(0)
            }
            applyFocusTargetIndex(startIndex, flash = false)
        } else {
            playerFocusRequester.requestFocusSafelyAfterLayout()
            channelsExpanded = false
            favoritesExpanded = false
        }
    }

    LaunchedEffect(channelsExpanded, favoritesExpanded, sideMenuFocusOrder) {
        if (!showSideMenu) return@LaunchedEffect
        val target = sideMenuFocusOrder.getOrNull(focusTargetIndex) ?: return@LaunchedEffect
        when (target) {
            is PlayerSideMenuFocusTarget.NearbyChannel, PlayerSideMenuFocusTarget.BrowseAll ->
                if (!channelsExpanded) {
                    val headerIndex = sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.ChannelsHeader)
                    if (headerIndex >= 0) applyFocusTargetIndex(headerIndex, flash = false)
                }
            is PlayerSideMenuFocusTarget.FavoriteChannel ->
                if (!favoritesExpanded) {
                    val headerIndex = sideMenuFocusOrder.indexOf(PlayerSideMenuFocusTarget.FavoritesHeader)
                    if (headerIndex >= 0) applyFocusTargetIndex(headerIndex, flash = false)
                }
            else -> Unit
        }
    }

    LaunchedEffect(flashingZone) {
        if (flashingZone != null) {
            delay(300)
            flashingZone = null
        }
    }

    LaunchedEffect(sideMenuFocusOrder.size, focusTargetIndex) {
        if (focusTargetIndex > sideMenuFocusOrder.lastIndex) {
            focusTargetIndex = sideMenuFocusOrder.lastIndex.coerceAtLeast(0)
        }
    }

    var playbackSettingsSynced by remember { mutableStateOf(false) }

    LaunchedEffect(settings.bufferSize, settings.preferHardwareDecoding, settingsReady) {
        if (!settingsReady) return@LaunchedEffect
        livePlayerManager.syncPlaybackSettings(
            context,
            settings.bufferSize,
            settings.preferHardwareDecoding
        )
        playbackSettingsSynced = true
    }

    if (!playbackSettingsSynced) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val playerGeneration by livePlayerManager.playerGeneration.collectAsStateWithLifecycle()
    val player = remember(playerGeneration, context) {
        livePlayerManager.getOrCreatePlayer(context)
    }
    val playbackStatus by livePlayerManager.playbackStatus.collectAsStateWithLifecycle()
    val canTimeshift by livePlayerManager.canTimeshiftFlow.collectAsStateWithLifecycle()
    val timeshiftState by livePlayerManager.timeshiftStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(settings.pictureInPictureEnabled) {
        viewModel.pipController.pictureInPictureEnabled = settings.pictureInPictureEnabled
    }

    LaunchedEffect(player) {
        sleepTimer.onVolumeFade = { level -> livePlayerManager.setVolumeFade(level) }
        sleepTimer.onExpired = {
            player.pause()
            showStillWatching = true
        }
    }

    LaunchedEffect(Unit) {
        livePlayerManager.enterFullscreen()
        viewModel.pipController.setPlaybackActive(true)
    }

    LaunchedEffect(channelId) {
        viewModel.load(channelId)
    }

    LaunchedEffect(channel?.id, channel?.streamUrl, playbackSettingsSynced) {
        if (!playbackSettingsSynced) return@LaunchedEffect
        val ch = channel ?: return@LaunchedEffect
        livePlayerManager.ensureFullscreenPlayback(context, ch)
        failures = 0
    }

    LaunchedEffect(channel?.id, channel?.streamUrl, settings.sleepTimerAutoEnabled, settings.sleepTimerMinutes) {
        val ch = channel ?: return@LaunchedEffect
        if (settings.sleepTimerAutoEnabled) {
            sleepTimer.start(settings.sleepTimerMinutes)
        }
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
        onDispose {
            playerViewRef[0]?.player = null
            sleepTimer.cancel()
            sleepTimer.onVolumeFade = null
            sleepTimer.onExpired = null
            livePlayerManager.resetVolumeFade()
            livePlayerManager.exitFullscreen(context)
            viewModel.pipController.setPlaybackActive(false)
            livePlayerManager.activePlayer()?.let { exo ->
                viewModel.savePosition(exo.currentPosition)
            }
        }
    }

    DisposableEffect(player) {
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
            player.removeListener(listener)
        }
    }

    LaunchedEffect(showOverlay, overlayInteractionToken) {
        if (showOverlay) {
            delay(4000)
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
                        revealOverlay()
                        viewModel.switchPrev()
                        true
                    }
                    Key.DirectionDown -> {
                        revealOverlay()
                        viewModel.switchNext()
                        true
                    }
                    Key.DirectionLeft -> {
                        revealOverlay()
                        true
                    }
                    Key.DirectionRight -> {
                        revealOverlay()
                        if (!showSideMenu) {
                            openSideMenu()
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
                    Key.Back, Key.Escape -> consumePlayerLocalBack()
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
                    playerViewRef[0] = this
                }
            },
            update = { view ->
                view.isFocusable = !showSideMenu
                view.isFocusableInTouchMode = !showSideMenu
                if (view.player != player) view.player = player
            },
            onRelease = { view ->
                view.player = null
                if (playerViewRef[0] === view) playerViewRef[0] = null
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
                            val playbackStatusLabel = playerPlaybackStatus(
                                playbackStatus = playbackStatus,
                                canTimeshift = canTimeshift,
                                timeshiftState = timeshiftState
                            )
                            if (playbackStatusLabel != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (playbackStatusLabel.showLiveDot) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(EpgColors.LiveBadge, CircleShape)
                                        )
                                    }
                                    Text(
                                        text = playbackStatusLabel.label,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontFamily = DmSansFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(
                                            start = if (playbackStatusLabel.showLiveDot) 5.dp else 0.dp
                                        )
                                    )
                                }
                            } else when (playbackStatus) {
                                com.neuropulse.tv.player.StreamPlaybackStatus.NO_SIGNAL,
                                com.neuropulse.tv.player.StreamPlaybackStatus.STALLED,
                                com.neuropulse.tv.player.StreamPlaybackStatus.ERROR,
                                com.neuropulse.tv.player.StreamPlaybackStatus.UNAVAILABLE -> {
                                    Text(
                                        text = playbackStatus.userLabel(),
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontFamily = DmSansFamily,
                                        fontSize = 11.sp
                                    )
                                }
                                else -> Unit
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
            favoriteChannels = favoritePanelChannels,
            actions = sideMenuActions,
            currentChannelId = channel?.id,
            focusState = sideMenuFocusState,
            channelsExpanded = channelsExpanded,
            favoritesExpanded = favoritesExpanded,
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
            confirmButton = { GlowFocusButton(onClick = { showInfo = false }) { Text("Close") } }
        )
    }

    if (showStillWatching) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Still watching?") },
            text = { Text("Playback paused by sleep timer") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowFocusButton(onClick = {
                        livePlayerManager.resetVolumeFade()
                        player.play()
                        showStillWatching = false
                    }) { Text("Continue") }
                    GlowFocusButton(onClick = ::leavePlayer) { Text("Exit") }
                }
            }
        )
    }

    if (showStopRecordingDialog) {
        val recTitle = activeRecording?.programTitle ?: "this program"
        AlertDialog(
            onDismissRequest = { showStopRecordingDialog = false },
            title = { Text("Stop recording?") },
            text = { Text("Stop recording $recTitle?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowFocusButton(onClick = {
                        recordingViewModel.stopActiveRecording(context)
                        showStopRecordingDialog = false
                    }) { Text("Stop") }
                    GlowFocusButton(onClick = { showStopRecordingDialog = false }) { Text("Cancel") }
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
            precheck = check,
            onQualitySelected = recordingViewModel::updatePrecheckQuality,
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
