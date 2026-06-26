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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.util.Log
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grid.tv.ui.component.CatchupPlayerControlsOverlay
import com.grid.tv.ui.component.SkipIntroOverlay
import com.grid.tv.ui.component.VodInlineSubtitlePanel
import com.grid.tv.ui.component.VodNextUpOverlay
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
import com.grid.tv.domain.model.VodPlaybackMeta
import com.grid.tv.player.ExternalPlayerId
import com.grid.tv.player.ExternalPlayerLauncher
import com.grid.tv.player.devicePlaybackCapabilities
import com.grid.tv.player.playbackErrorMessage
import com.grid.tv.player.PlaybackHttpFailure
import com.grid.tv.player.PlaybackNetworkCoordinator
import com.grid.tv.player.PlaybackOrchestrator
import com.grid.tv.player.PlaybackSurfaceInstrument
import com.grid.tv.player.VodPlaybackRecoveryListener
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.PlaybackVideoFit
import com.grid.tv.ui.component.toPlaybackVideoFit
import com.grid.tv.ui.component.toResizeMode
import androidx.media3.ui.AspectRatioFrameLayout
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.media3.common.util.UnstableApi
import com.grid.tv.ui.viewmodel.DirectPlayerViewModel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DirectPlayerScreen(
    url: String,
    title: String,
    recordingId: Long = 0L,
    recordedAt: Long = 0L,
    resume: Boolean = false,
    resumePositionMs: Long = 0L,
    onBack: () -> Unit,
    onJumpToLive: (Long) -> Unit = {},
    viewModel: DirectPlayerViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val catchupSession = remember { CatchupPlaybackContext.consume() }
    val isCatchupPlayback = catchupSession != null && recordingId <= 0L
    val isRecordedPlayback = recordingId > 0L
    val pendingVodMeta = remember(url, isRecordedPlayback, isCatchupPlayback) {
        if (isRecordedPlayback || isCatchupPlayback) {
            VodPlaybackMeta()
        } else {
            VodPlaybackContext.consume()
        }
    }
    val streamId = pendingVodMeta.streamId ?: remember(url) {
        Regex("""/(\d+)\.\w+$""").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
    val vodEnrichmentKey = remember(pendingVodMeta, url, streamId) {
        pendingVodMeta.playlistId?.let { playlistId ->
            pendingVodMeta.streamId?.let { id ->
                com.grid.tv.feature.enrichment.TitleEnrichmentRepository.xtreamVodKey(playlistId, id)
            }
        } ?: "vod:$streamId:$url"
    }
    val resumeSeekState = remember(url) { ResumeSeekState() }
    val isVodPlayback = !isRecordedPlayback && !isCatchupPlayback
    var vodResumeMs by remember(url) { mutableLongStateOf(0L) }
    var vodResumeReady by remember(url, isRecordedPlayback, isCatchupPlayback) {
        mutableStateOf(isRecordedPlayback || isCatchupPlayback)
    }

    LaunchedEffect(url, isVodPlayback, title) {
        if (!isVodPlayback) return@LaunchedEffect
        val playerId = viewModel.preferredExternalPlayer()
        if (playerId != ExternalPlayerId.NONE &&
            ExternalPlayerLauncher.launch(context, playerId, url, title)
        ) {
            onBack()
        }
    }

    LaunchedEffect(url, pendingVodMeta, streamId, resumePositionMs, isRecordedPlayback, isCatchupPlayback) {
        if (!isVodPlayback) {
            vodResumeMs = 0L
            vodResumeReady = true
            resumeSeekState.pendingMs = 0L
            resumeSeekState.applied = true
            return@LaunchedEffect
        }
        viewModel.setVodMetadata(pendingVodMeta)
        val resolved = viewModel.resolveResumePositionMs(
            meta = pendingVodMeta,
            streamId = streamId,
            navigationResumeMs = resumePositionMs,
            stagedResumeMs = pendingVodMeta.resumePositionMs
        )
        vodResumeMs = resolved
        vodResumeReady = true
        resumeSeekState.pendingMs = resolved
        resumeSeekState.applied = resolved > 0L
        if (resolved > 0L) {
            Log.d(
                "DirectPlayer",
                "${DirectPlayerViewModel.RESUME_POSITION_MS_KEY}=$resolved (pre-playback resolve)"
            )
        }
    }

    LaunchedEffect(recordingId) {
        if (recordingId > 0L) viewModel.loadRecordedMedia(recordingId)
    }
    val recordedMedia by viewModel.recordedMedia.collectAsStateWithLifecycle()
    val subtitleSettings by viewModel.settings.collectAsStateWithLifecycle()
    val activeSubtitle by viewModel.activeSubtitle.collectAsStateWithLifecycle()
    val introWindow by viewModel.introWindow.collectAsStateWithLifecycle()
    val nextUpItem by viewModel.nextUpItem.collectAsStateWithLifecycle()
    var showNextUp by remember(url) { mutableStateOf(false) }
    var subtitlesAttached by remember(url) { mutableStateOf(false) }

    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }

    LaunchedEffect(positionMs, durationMs, isVodPlayback, showNextUp) {
        if (!isVodPlayback || showNextUp) return@LaunchedEffect
        if (viewModel.shouldOfferNextUp(positionMs, durationMs)) {
            showNextUp = true
        }
    }

    val skipIntroVisible = introWindow?.isInSkipWindow(positionMs / 1000.0) == true && isVodPlayback

    var isPlaying by remember(url) { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var hasSeekedToResume by remember(recordingId, resume, url) { mutableStateOf(false) }
    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    val isEmulator = remember(context) { context.devicePlaybackCapabilities().isEmulator }
    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }
    val playerEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlayerEntryPoint::class.java
        )
    }
    val playbackOrchestrator = remember { playerEntryPoint.playbackOrchestrator() }
    val playbackNetworkCoordinator = remember { playerEntryPoint.playbackNetworkCoordinator() }
    val playerFactory = remember { playerEntryPoint.playerFactory() }
    var vodSessionReady by remember(url) { mutableStateOf<Boolean?>(null) }

    DisposableEffect(url) {
        val result = playbackOrchestrator.requestSession(
            session = PlaybackOrchestrator.PlaybackSession.VOD,
            owner = "direct_player",
            context = context
        )
        vodSessionReady = result == PlaybackOrchestrator.SessionRequestResult.GRANTED ||
            result == PlaybackOrchestrator.SessionRequestResult.GRANTED_EVICTED_LOWER
        onDispose {
            playbackOrchestrator.releaseSession(PlaybackOrchestrator.PlaybackSession.VOD, context)
            playbackNetworkCoordinator.endVodSession()
        }
    }

    LaunchedEffect(vodSessionReady) {
        if (vodSessionReady == false) onBack()
    }

    if (vodSessionReady != true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val onDemandContentKind = remember(isCatchupPlayback, isRecordedPlayback, pendingVodMeta) {
        com.grid.tv.player.IptvOnDemandMediaItem.contentKindFor(
            isCatchupPlayback = isCatchupPlayback,
            isRecordedPlayback = isRecordedPlayback,
            vodMeta = pendingVodMeta
        )
    }

    var resolvedVodStream by remember(url, onDemandContentKind) {
        mutableStateOf<com.grid.tv.player.ResolvedVodStream?>(null)
    }
    var vodResolutionFailed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, onDemandContentKind) {
        vodResolutionFailed = false
        resolvedVodStream = null
        resolvedVodStream = viewModel.resolveVodStream(url, onDemandContentKind)
        if (resolvedVodStream == null) {
            vodResolutionFailed = true
        }
    }

    if (vodResolutionFailed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Unable to play stream — invalid or blocked response",
                color = Color.White,
                fontFamily = DmSansFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(48.dp)
            )
        }
        return
    }

    if (resolvedVodStream == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    if (!vodResumeReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val resolvedVod = resolvedVodStream!!

    val playbackStarted = remember { mutableStateOf(false) }
    val vodRecoveryRef = remember { arrayOfNulls<VodPlaybackRecoveryListener>(1) }
    val onFatalPlaybackError = rememberUpdatedState { error: PlaybackException ->
        com.grid.tv.util.PlaybackDiagnostics.logPlaybackError(
            owner = "vod_direct",
            error = error,
            streamUrl = url
        )
        PlaybackHttpFailure.logHttpFailure(error, url)
        playbackError = error.playbackErrorMessage(isEmulator)
    }

    val player = remember(url, vodResumeMs, onDemandContentKind, resolvedVod) {
        resumeSeekState.applied = vodResumeMs > 0L
        resumeSeekState.pendingMs = vodResumeMs
        playbackNetworkCoordinator.beginVodSession(url)
        viewModel.createPlayer(context).apply {
            val mediaItem = viewModel.buildOnDemandMediaItem(
                url,
                onDemandContentKind,
                resolvedStream = resolvedVod
            )
            if (vodResumeMs > 0L) {
                setMediaItem(mediaItem, vodResumeMs)
                Log.d(
                    "DirectPlayer",
                    "${DirectPlayerViewModel.RESUME_POSITION_MS_KEY} startPosition=$vodResumeMs"
                )
            } else {
                setMediaItem(mediaItem)
            }
            playbackNetworkCoordinator.markSingleRequestAllowed(url)
            VodPlaybackRecoveryListener(
                player = this,
                onFatalError = { error -> onFatalPlaybackError.value(error) }
            ).also { recovery ->
                recovery.attach()
                vodRecoveryRef[0] = recovery
                playerFactory.registerVodRecovery(this, recovery)
            }
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        if (durationMs <= 0L) durationMs = duration.coerceAtLeast(0L)
                    }
                    if (!isRecordedPlayback && !isCatchupPlayback) {
                        applyPendingResumeSeek(
                            player = this@apply,
                            playbackState = playbackState,
                            resumeSeekState = resumeSeekState
                        )
                        if (resumeSeekState.applied) {
                            hasSeekedToResume = true
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
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

    LaunchedEffect(
        resumeSeekState.pendingMs,
        player.playbackState,
        isVodPlayback,
        isRecordedPlayback,
        isCatchupPlayback
    ) {
        if (!isVodPlayback || isRecordedPlayback || isCatchupPlayback || resumeSeekState.applied) {
            return@LaunchedEffect
        }
        if (resumeSeekState.pendingMs <= 0L) return@LaunchedEffect
        applyPendingResumeSeek(player, player.playbackState, resumeSeekState)
        if (resumeSeekState.applied) {
            hasSeekedToResume = true
        }
    }

    LaunchedEffect(isRecordedPlayback, isCatchupPlayback) {
        if (isRecordedPlayback || isCatchupPlayback) return@LaunchedEffect
        viewModel.pipController.setPlaybackActive(true)
    }

    LaunchedEffect(recordedMedia, resume, isRecordedPlayback, player.playbackState) {
        if (!isRecordedPlayback || hasSeekedToResume) return@LaunchedEffect
        val media = recordedMedia ?: return@LaunchedEffect
        val startMs = if (resume && media.playbackPositionMs > 30_000) {
            media.playbackPositionMs
        } else {
            0L
        }
        if (startMs <= 0L) {
            hasSeekedToResume = true
            return@LaunchedEffect
        }
        if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
            player.seekTo(startMs)
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
            vodRecoveryRef[0]?.detach()
            vodRecoveryRef[0] = null
            viewModel.pipController.setPlaybackActive(false)
            positionMs = player.currentPosition
            if (durationMs <= 0L) durationMs = player.duration.coerceAtLeast(0L)
            if (isRecordedPlayback && recordingId > 0L) {
                viewModel.saveRecordingPosition(recordingId, positionMs)
            } else if (!isCatchupPlayback) {
                viewModel.persistProgress(streamId, positionMs, title, durationMs, url)
            }
            playerViewRef[0]?.let { view ->
                PlaybackSurfaceInstrument.releasePlayerView("vod_direct", player, view)
            }
            player.clearVideoSurface()
            viewModel.releasePlayer(player)
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
    var playbackVideoFit by remember(subtitleSettings.aspectRatio) {
        mutableStateOf(subtitleSettings.aspectRatio.toPlaybackVideoFit())
    }

    LaunchedEffect(subtitleSettings.aspectRatio) {
        playbackVideoFit = subtitleSettings.aspectRatio.toPlaybackVideoFit()
    }

    fun seekDeltaForScrub(): Long = if (seekRepeatCount > 3) 300_000L else 30_000L

    fun revealOverlay() {
        showOverlay = true
        overlayToken++
    }

    fun restoreTransportFocus() {
        if (isRecordedPlayback || isCatchupPlayback) {
            focusZone = RecordedPlayerFocusZone.TRANSPORT
        } else {
            vodFocusZone = VodPlayerFocusZone.TRANSPORT
        }
        transportFocusIndex = 2
    }

    fun revealOverlayOnly() {
        revealOverlay()
        restoreTransportFocus()
    }

    LaunchedEffect(showOverlay, showSubtitlePanel) {
        if (showOverlay && !showSubtitlePanel) {
            restoreTransportFocus()
        }
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
        3 -> PlaybackVideoFit.entries.size
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
            3 -> playbackVideoFit = PlaybackVideoFit.entries[subtitleFocusCol]
        }
    }

    fun revealOverlayFromHiddenKey(): Boolean {
        if (showOverlay) return false
        revealOverlayOnly()
        return true
    }

    fun handleVodPlaybackKey(key: Key): Boolean {
        if (key == Key.Back || key == Key.Escape) return consumeDirectPlayerLocalBack()

        val isRevealKey = key == Key.DirectionCenter ||
            key == Key.Enter ||
            key == Key.NumPadEnter ||
            key == Key.DirectionUp ||
            key == Key.DirectionDown ||
            key == Key.DirectionLeft ||
            key == Key.DirectionRight
        if (!showOverlay && isRevealKey) {
            return revealOverlayFromHiddenKey()
        }

        when (key) {
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (!showOverlay) return false
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
                    VodPlayerFocusZone.SEEK -> Unit
                    VodPlayerFocusZone.SUBTITLE_PANEL -> applySubtitlePanelSelection()
                }
                return true
            }
            Key.DirectionUp -> {
                if (!showOverlay) return false
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
                if (!showOverlay) return false
                revealOverlay()
                when (vodFocusZone) {
                    VodPlayerFocusZone.SUBTITLE_PANEL -> {
                        subtitleFocusRow = (subtitleFocusRow + 1).coerceAtMost(3)
                        subtitleFocusCol = subtitleFocusCol.coerceAtMost(subtitleRowColumnCount(subtitleFocusRow) - 1)
                        applySubtitlePanelSelection()
                    }
                    VodPlayerFocusZone.SEEK -> vodFocusZone = VodPlayerFocusZone.TRANSPORT
                    VodPlayerFocusZone.TRANSPORT -> Unit
                }
                return true
            }
            Key.DirectionLeft -> {
                if (!showOverlay) return false
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
                if (!showOverlay) return false
                revealOverlay()
                when (vodFocusZone) {
                    VodPlayerFocusZone.TRANSPORT -> {
                        transportFocusIndex = (transportFocusIndex + 1)
                            .coerceAtMost(5)
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
        if (key == Key.Back || key == Key.Escape) return consumeDirectPlayerLocalBack()

        val isRevealKey = key == Key.DirectionCenter ||
            key == Key.Enter ||
            key == Key.NumPadEnter ||
            key == Key.DirectionUp ||
            key == Key.DirectionDown ||
            key == Key.DirectionLeft ||
            key == Key.DirectionRight
        if (!showOverlay && isRevealKey) {
            return revealOverlayFromHiddenKey()
        }

        when (key) {
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (!showOverlay) return false
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
                    RecordedPlayerFocusZone.SEEK -> Unit
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
                if (!showOverlay) return false
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
                if (!showOverlay) return false
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
                if (!showOverlay) return false
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
                if (!showOverlay) return false
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
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    isRecordedPlayback || isCatchupPlayback -> handlePlaybackKey(keyEvent.key)
                    else -> handleVodPlaybackKey(keyEvent.key)
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(android.graphics.Color.BLACK)
                    playerViewRef[0] = this
                }.also { view ->
                    PlaybackSurfaceInstrument.attach("vod_direct", player, view)
                    if (!playbackStarted.value) {
                        player.prepare()
                        player.playWhenReady = true
                        playbackStarted.value = true
                    }
                }
            },
            update = { view ->
                view.resizeMode = playbackVideoFit.toResizeMode()
                if (view.player != player) view.player = player
            },
            onRelease = { view ->
                PlaybackSurfaceInstrument.detach("vod_direct", player, view)
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
                            videoFit = playbackVideoFit,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }

        SkipIntroOverlay(
            visible = skipIntroVisible && !showOverlay,
            label = if (introWindow?.recapStartSec != null &&
                positionMs / 1000.0 in (introWindow?.recapStartSec ?: 0.0)..(introWindow?.recapEndSec ?: 0.0)
            ) "Skip Recap" else "Skip Intro",
            onSkip = {
                val targetSec = introWindow?.skipTargetAt(positionMs / 1000.0) ?: return@SkipIntroOverlay
                player.seekTo((targetSec * 1000).toLong())
            },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        VodNextUpOverlay(
            visible = showNextUp && isVodPlayback,
            nextItem = nextUpItem,
            autoPlay = viewModel.nextUpAutoPlay,
            onPlayNext = {
                val next = nextUpItem ?: return@VodNextUpOverlay
                com.grid.tv.domain.model.VodPlaybackContext.stageSeriesEpisode(
                    posterUrl = next.posterUrl,
                    streamId = next.streamId,
                    seriesId = pendingVodMeta.seriesId ?: return@VodNextUpOverlay,
                    seasonNumber = next.seasonNumber,
                    episodeNumber = next.episodeNumber,
                    title = next.title,
                    playlistId = pendingVodMeta.playlistId
                )
                viewModel.clearNextUp()
                showNextUp = false
                leaveScreen()
            },
            onDismiss = {
                showNextUp = false
                viewModel.clearNextUp()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (playbackError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = playbackError.orEmpty(),
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                )
                GlowFocusButton(onClick = ::leaveScreen) {
                    Text("Go back", fontFamily = DmSansFamily)
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

private class ResumeSeekState {
    var pendingMs: Long = 0L
    var applied: Boolean = false
}

private fun applyPendingResumeSeek(
    player: ExoPlayer,
    playbackState: Int,
    resumeSeekState: ResumeSeekState
) {
    if (resumeSeekState.applied || resumeSeekState.pendingMs <= 0L) return
    val canSeek = playbackState == Player.STATE_READY ||
        (playbackState == Player.STATE_BUFFERING && player.currentPosition <= 1_000L)
    if (!canSeek) return
    player.seekTo(resumeSeekState.pendingMs)
    resumeSeekState.applied = true
    Log.d(
        "DirectPlayer",
        "${DirectPlayerViewModel.RESUME_POSITION_MS_KEY} seek applied at ${resumeSeekState.pendingMs}ms " +
            "(state=$playbackState)"
    )
}
