package com.grid.tv.player

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.data.catalog.CatalogHydrationGuard
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.data.network.toNetworkPlaybackConfig
import com.grid.tv.domain.model.Channel
import com.grid.tv.feature.scanner.ChannelScanner
import com.grid.tv.util.PerformanceAudit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class TimeshiftUiState(
    val bufferStartMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val behindLiveMs: Long = 0L,
    val isTimeshifting: Boolean = false,
    val atLiveEdge: Boolean = true,
    val canTimeshift: Boolean = false,
    val canRewind: Boolean = false,
    val canFastForward: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = true,
    val playbackState: Int = Player.STATE_IDLE
) {
    val showPauseControl: Boolean
        get() = playWhenReady && playbackState != Player.STATE_ENDED
}

@Singleton
class LivePlayerManager @Inject constructor(
    private val playerFactory: PlayerFactory,
    private val playbackHttpDataSourceFactory: PlaybackHttpDataSourceFactory,
    private val streamFailover: StreamFailoverController,
    private val channelScanner: ChannelScanner,
    private val catalogHydrationGuard: CatalogHydrationGuard,
    private val decoderPressureTracker: DecoderPressureTracker
) {
    enum class Mode { IDLE, MINI, FULLSCREEN }

    private var player: ExoPlayer? = null
    private var currentChannelId: Long? = null
    private var currentStreamUrl: String? = null
    private var currentChannel: Channel? = null

    private val _lastChannel = MutableStateFlow<Channel?>(null)
    val lastChannelFlow: StateFlow<Channel?> = _lastChannel.asStateFlow()
    private var catchupDays: Int = 0
    private var miniAudioEnabled: Boolean = false
    private var bufferSize: BufferSize = BufferSize.MEDIUM
    private var preferHardwareDecoding: Boolean = true
    private var cachedNetworkSettings: AppSettings? = null

    private var hasDvrWindow: Boolean = false
    private var pendingJumpToLive: Boolean = false

    private val _activeChannelId = MutableStateFlow<Long?>(null)

    private val _activeStreamUrl = MutableStateFlow<String?>(null)

    /** Bumped only when the underlying ExoPlayer instance is destroyed and recreated. */
    private val _playerGeneration = MutableStateFlow(0)
    val playerGeneration: StateFlow<Int> = _playerGeneration.asStateFlow()

    private var pendingZapChannelId: Long? = null
    private var playbackReadySeen = false
    private var bufferStartupStartedAtMs = 0L

    private val _timeshiftState = MutableStateFlow(TimeshiftUiState())

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackMonitor = StreamPlaybackMonitor(monitorScope)

    /** Combined UI snapshot — prefer this over collecting individual flows in Compose. */
    val playbackUiState: StateFlow<LivePlaybackUiState> = combine(
        playbackMonitor.status,
        streamFailover.uiState,
        _timeshiftState,
        _activeChannelId,
        _activeStreamUrl
    ) { status, failover, timeshift, channelId, streamUrl ->
        LivePlaybackUiState(
            status = status,
            failover = failover,
            timeshift = timeshift,
            activeChannelId = channelId,
            activeStreamUrl = streamUrl
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = monitorScope,
            started = SharingStarted.Eagerly,
            initialValue = LivePlaybackUiState()
        )

    /** @deprecated Prefer [playbackUiState] — kept for non-UI side-effect wiring. */
    val playbackStatus: StateFlow<StreamPlaybackStatus> = playbackMonitor.status

    /** @deprecated Prefer [playbackUiState]. */
    val failoverUiState: StateFlow<StreamFailoverUiState> = streamFailover.uiState

    /** @deprecated Prefer [playbackUiState]. */
    val timeshiftStateFlow: StateFlow<TimeshiftUiState> = _timeshiftState.asStateFlow()

    /** @deprecated Prefer [playbackUiState]. */
    val activeChannelIdFlow: StateFlow<Long?> = _activeChannelId.asStateFlow()

    /** @deprecated Prefer [playbackUiState]. */
    val activeStreamUrlFlow: StateFlow<String?> = _activeStreamUrl.asStateFlow()

    private val failoverActions = object : StreamFailoverPlaybackActions {
        override fun reconnectSameStream() {
            val exo = player ?: return
            val url = currentStreamUrl ?: return
            if (url.isBlank()) return
            val resumeMs = exo.currentPosition.coerceAtLeast(0L)
            exo.setMediaItem(buildLiveMediaItem(url), resumeMs)
            exo.prepare()
            exo.playWhenReady = mode != Mode.IDLE
            applyVolume()
        }

        override fun switchToStream(url: String) {
            val contextChannel = currentChannel ?: return
            val context = lastContext ?: return
            switchToStreamUrl(context, contextChannel.id, url, contextChannel.catchupDays, contextChannel)
        }
    }

    private var lastContext: Context? = null

    var mode: Mode = Mode.IDLE
        private set

    private var fullscreenSessions: Int = 0
    private var volumeFadeMultiplier: Float = 1f
    private var streamTransitionJob: Job? = null
    private var playerReleasedForBackground = false
    private val tuneGuard = TunePipelineGuard()

    init {
        playbackMonitor.status
            .onEach { status ->
                PerformanceAudit.recordPlaybackStatusEmission(status)
                streamFailover.onPlaybackStatus(status)
            }
            .launchIn(monitorScope)
        monitorScope.launch {
            playbackUiState.collect { ui ->
                PerformanceAudit.recordPlaybackUiEmission(ui)
            }
        }
    }

    fun isFullscreenActive(): Boolean = fullscreenSessions > 0

    private val timeshiftListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            player?.let { refreshTimeshiftWindow(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                player?.let { exo ->
                    refreshTimeshiftWindow(exo)
                    if (bufferStartupStartedAtMs > 0L) {
                        PerformanceAudit.logBufferStartupReady(
                            elapsedMs = System.currentTimeMillis() - bufferStartupStartedAtMs,
                            profileName = TimeshiftManager.activeProfileName,
                            playerHash = System.identityHashCode(exo)
                        )
                        bufferStartupStartedAtMs = 0L
                    }
                    pendingZapChannelId?.let { channelId ->
                        PerformanceAudit.completeZap(System.identityHashCode(exo), channelId)
                        pendingZapChannelId = null
                    }
                    playbackReadySeen = true
                }
            } else if (playbackState == Player.STATE_BUFFERING && playbackReadySeen) {
                player?.let { exo ->
                    PerformanceAudit.logRebufferEvent(
                        profileName = TimeshiftManager.activeProfileName,
                        playerHash = System.identityHashCode(exo)
                    )
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            player?.let { refreshTimeshiftWindow(it) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            player?.let { refreshTimeshiftWindow(it) }
        }
    }

    @UnstableApi
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (player == null) {
            val caps = context.devicePlaybackCapabilities()
            val profile = IptvBufferProfiles.resolve(
                bufferSize = bufferSize,
                startupPriority = null,
                isLowEndDevice = caps.isLowEndDevice
            )
            TimeshiftManager.syncBufferProfile(profile)
            player = playerFactory.create(
                context.applicationContext,
                bufferSize,
                preferHardwareDecoding,
                networkSettings = cachedNetworkSettings,
                decoderOwner = "live_guide"
            ).also { exo ->
                exo.addListener(timeshiftListener)
                PerformanceAudit.logPlayerLifecycle("CREATE", exo, _playerGeneration.value)
            }
        }
        return player!!
    }

    @UnstableApi
    fun syncNetworkSettings(context: Context, settings: AppSettings) {
        val previous = cachedNetworkSettings
        cachedNetworkSettings = settings
        val changed = previous == null ||
            previous.useProxy != settings.useProxy ||
            previous.proxyUrl != settings.proxyUrl ||
            previous.connectionTimeoutSeconds != settings.connectionTimeoutSeconds
        if (!changed) return
        android.util.Log.i(
            TAG,
            "Network settings changed for playback — ${settings.toNetworkPlaybackConfig().toLogLine()}"
        )
        playbackHttpDataSourceFactory.syncNetworkSettings(settings)
        rebuildPlayerPreservingTune(context.applicationContext)
    }

    @UnstableApi
    fun syncPlaybackSettings(
        context: Context,
        bufferSize: BufferSize,
        preferHardwareDecoding: Boolean
    ) {
        if (this.bufferSize == bufferSize && this.preferHardwareDecoding == preferHardwareDecoding) {
            return
        }
        this.bufferSize = bufferSize
        this.preferHardwareDecoding = preferHardwareDecoding
        val profile = IptvBufferProfiles.resolve(
            bufferSize = bufferSize,
            startupPriority = null,
            isLowEndDevice = context.devicePlaybackCapabilities().isLowEndDevice
        )
        TimeshiftManager.syncBufferProfile(profile)
        rebuildPlayerPreservingTune(context.applicationContext)
    }

    @UnstableApi
    private fun rebuildPlayerPreservingTune(context: Context) {
        if (fullscreenSessions > 0) return
        if (player == null) return
        val channelId = currentChannelId
        val streamUrl = currentStreamUrl
        val days = catchupDays
        val channel = currentChannel
        val currentMode = mode
        val audio = miniAudioEnabled
        destroyPlayerInstance(context, clearCache = true)
        resetTimeshiftState()
        if (channelId != null && !streamUrl.isNullOrBlank()) {
            tuneChannel(context, channelId, streamUrl, days, channel, bypassDedupe = true)
            mode = currentMode
            miniAudioEnabled = audio
            applyVolume()
        }
    }

    private fun markPlayerReplaced() {
        _playerGeneration.value++
    }

    fun setMiniAudioEnabled(enabled: Boolean) {
        runOnPlayerThread {
            miniAudioEnabled = enabled
            if (mode == Mode.MINI) applyVolumeNow()
        }
    }

    fun setAutoReconnectOnDrop(enabled: Boolean) {
        streamFailover.setAutoReconnectEnabled(enabled)
    }

    fun setStreamRetries(count: Int) {
        streamFailover.setStreamRetries(count)
    }

    fun hasPlayerInstance(): Boolean = player != null

    fun isPlaybackActive(): Boolean {
        val exo = player ?: return false
        if (!exo.playWhenReady) return false
        return exo.playbackState == Player.STATE_READY || exo.playbackState == Player.STATE_BUFFERING
    }

    /**
     * Keep decoders allocated for fullscreen playback or any stream that is actively playing.
     */
    fun shouldRetainPlayerInBackground(): Boolean {
        val exo = player ?: return false
        if (mode == Mode.FULLSCREEN && isPlaybackActive()) return true
        if (mode == Mode.MINI && isPlaybackActive()) return true
        return false
    }

    /**
     * Releases the ExoPlayer instance and stream cache while preserving tune session metadata
     * so [restoreSessionAfterBackgroundRelease] can rebuild quickly on return.
     */
    fun releasePlayerResources(context: Context, reason: String) {
        if (player == null) return
        PerformanceAudit.logPlayerMemoryRelease(
            reason = reason,
            phase = "before",
            generation = _playerGeneration.value,
            channelId = currentChannelId
        )
        streamTransitionJob?.cancel()
        if (fullscreenSessions > 0) {
            channelScanner.setPlaybackScanSuspended(false)
        }
        destroyPlayerInstance(context.applicationContext, clearCache = true)
        playbackReadySeen = false
        bufferStartupStartedAtMs = 0L
        playerReleasedForBackground = true
        syncCatalogHydrationGuard()
        PerformanceAudit.logPlayerMemoryRelease(
            reason = reason,
            phase = "after",
            generation = _playerGeneration.value,
            channelId = currentChannelId
        )
        Log.i(TAG, "Released player resources ($reason); session preserved mode=$mode channelId=$currentChannelId")
    }

    fun restoreSessionAfterBackgroundRelease(context: Context) {
        if (!playerReleasedForBackground) return
        playerReleasedForBackground = false
        val appContext = context.applicationContext
        lastContext = appContext
        val channel = currentChannel
        val channelId = currentChannelId
        val streamUrl = currentStreamUrl
        if (channelId == null || streamUrl.isNullOrBlank()) return
        when (mode) {
            Mode.FULLSCREEN -> {
                if (channel != null) {
                    tuneChannel(appContext, channel, bypassDedupe = true)
                } else {
                    tuneChannel(appContext, channelId, streamUrl, catchupDays, null, bypassDedupe = true)
                }
                setMode(Mode.FULLSCREEN)
            }
            Mode.MINI -> {
                if (channel != null) {
                    tuneChannel(appContext, channel, bypassDedupe = true)
                } else {
                    tuneChannel(appContext, channelId, streamUrl, catchupDays, null, bypassDedupe = true)
                }
                resumeGuidePreview(appContext, withAudio = miniAudioEnabled)
            }
            Mode.IDLE -> Unit
        }
        Log.i(TAG, "Restored session after background release mode=$mode channelId=$channelId")
    }

    fun tuneChannel(context: Context, channel: Channel, bypassDedupe: Boolean = false) {
        tuneChannel(context, channel.id, channel.streamUrl, channel.catchupDays, channel, bypassDedupe)
    }

    fun tuneChannel(
        context: Context,
        channelId: Long,
        streamUrl: String,
        catchupDays: Int = 0,
        channelSnapshot: Channel? = null,
        bypassDedupe: Boolean = false
    ) {
        lastContext = context.applicationContext
        if (tryReuseActiveStream(context, channelId, streamUrl, channelSnapshot)) return
        scheduleTunePipeline(
            context = context,
            channelId = channelId,
            streamUrl = streamUrl,
            catchupDays = catchupDays,
            channelSnapshot = channelSnapshot,
            bypassDedupe = bypassDedupe,
            configureFailover = true,
            trackZapMetrics = true,
            playWhenReady = true
        )
    }

    /**
     * Fast-path when the requested stream is already loaded and ready — avoids flush/prepare.
     */
    private fun tryReuseActiveStream(
        context: Context,
        channelId: Long,
        streamUrl: String,
        channelSnapshot: Channel?
    ): Boolean {
        if (currentChannelId != channelId || currentStreamUrl != streamUrl || streamUrl.isBlank()) {
            return false
        }
        val exo = player ?: getOrCreatePlayer(context)
        channelSnapshot?.let { currentChannel = it }
        if (exo.playbackState != Player.STATE_READY) {
            return false
        }
        PerformanceAudit.logPlayerLifecycle("REUSE_SKIP_FLUSH", exo, _playerGeneration.value, channelId)
        PerformanceAudit.logTuneReuseSkipFlush(channelId, streamUrl)
        exo.playWhenReady = true
        applyVolume()
        refreshTimeshiftWindow(exo)
        playbackMonitor.onStreamContinued(exo)
        return true
    }

    private fun scheduleTunePipeline(
        context: Context,
        channelId: Long,
        streamUrl: String,
        catchupDays: Int,
        channelSnapshot: Channel?,
        bypassDedupe: Boolean,
        configureFailover: Boolean,
        trackZapMetrics: Boolean,
        playWhenReady: Boolean
    ) {
        val key = TunePipelineGuard.TuneKey(channelId, streamUrl)
        when (val admission = tuneGuard.evaluateAdmission(key, bypassDedupe)) {
            is TunePipelineGuard.Admission.Suppressed -> {
                PerformanceAudit.logTuneSuppressed(
                    reason = admission.reason.name,
                    channelId = channelId,
                    streamUrl = streamUrl
                )
                Log.i(TAG, "Tune suppressed reason=${admission.reason} channelId=$channelId")
                return
            }
            TunePipelineGuard.Admission.Accepted -> Unit
        }

        streamTransitionJob?.cancel()
        streamTransitionJob = monitorScope.launch {
            tuneGuard.runPipeline(key) {
                PerformanceAudit.logTunePipelineStart(channelId, streamUrl, configureFailover)
                resetPlayerForMediaSwap()
                delay(AUDIO_PIPELINE_FLUSH_DELAY_MS)
                loadStream(
                    context = context,
                    channelId = channelId,
                    streamUrl = streamUrl,
                    catchupDays = catchupDays,
                    channelSnapshot = channelSnapshot,
                    playWhenReady = playWhenReady,
                    configureFailover = configureFailover,
                    trackZapMetrics = trackZapMetrics
                )
                PerformanceAudit.logTunePipelineEnd(channelId, streamUrl)
            }
        }
    }

    fun switchToStreamUrl(
        context: Context,
        channelId: Long,
        streamUrl: String,
        catchupDays: Int = 0,
        channelSnapshot: Channel? = null
    ) {
        lastContext = context.applicationContext
        if (tryReuseActiveStream(context, channelId, streamUrl, channelSnapshot)) return
        scheduleTunePipeline(
            context = context,
            channelId = channelId,
            streamUrl = streamUrl,
            catchupDays = catchupDays,
            channelSnapshot = channelSnapshot,
            bypassDedupe = true,
            configureFailover = false,
            trackZapMetrics = true,
            playWhenReady = mode != Mode.IDLE
        )
    }

    private suspend fun loadStream(
        context: Context,
        channelId: Long,
        streamUrl: String,
        catchupDays: Int,
        channelSnapshot: Channel?,
        playWhenReady: Boolean,
        configureFailover: Boolean,
        trackZapMetrics: Boolean
    ) {
        val exo = getOrCreatePlayer(context)
        playbackMonitor.attach(exo)
        streamFailover.attach(exo, failoverActions)
        if (configureFailover) {
            val failoverChannel = channelSnapshot
                ?: currentChannel?.takeIf { it.id == channelId }
                ?: Channel(
                    id = channelId,
                    number = 0,
                    name = "",
                    group = "",
                    logoUrl = null,
                    epgId = null,
                    streamUrl = streamUrl,
                    playlistId = 0,
                    isFavorite = false
                )
            streamFailover.configure(failoverChannel, streamUrl)
        }

        playbackMonitor.onTuneStarted(streamUrl)
        this.catchupDays = catchupDays

        if (configureFailover) {
            currentChannel?.takeIf { it.id != channelId }?.let { previous ->
                _lastChannel.value = previous
            }
        }

        TimeshiftManager.reset()
        clearStreamCache(context)
        resetTimeshiftState()
        this.catchupDays = catchupDays

        if (streamUrl.isBlank()) {
            setActiveChannelId(channelId)
            setActiveStreamUrl(streamUrl)
            channelSnapshot?.let { currentChannel = it }
            pendingZapChannelId = null
            exo.stop()
            exo.clearMediaItems()
            syncCatalogHydrationGuard()
            return
        }

        setActiveChannelId(channelId)
        setActiveStreamUrl(streamUrl)
        channelSnapshot?.let { currentChannel = it }
        pendingJumpToLive = true
        Log.i(TAG, "loadStream id=$channelId url=$streamUrl playWhenReady=$playWhenReady")
        PerformanceAudit.logPlayerLifecycle("SWAP_MEDIA", exo, _playerGeneration.value, channelId)
        if (trackZapMetrics) {
            pendingZapChannelId = channelId
            PerformanceAudit.beginZap(channelId, System.identityHashCode(exo))
        }
        playbackReadySeen = false
        bufferStartupStartedAtMs = System.currentTimeMillis()
        PerformanceAudit.logBufferTuneStarted(
            channelId = channelId,
            profileName = TimeshiftManager.activeProfileName
        )
        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(buildLiveMediaItem(streamUrl))
        exo.prepare()
        exo.playWhenReady = playWhenReady
        applyVolume()
        streamFailover.onStreamSwitched(streamUrl)
        syncCatalogHydrationGuard()
    }

    /**
     * Stops current media on the existing player without releasing decoders or the instance.
     * Used for normal channel / backup URL switches.
     */
    private fun resetPlayerForMediaSwap() {
        val exo = player ?: return
        PerformanceAudit.logPlayerLifecycle("SWAP_RESET", exo, _playerGeneration.value, currentChannelId)
        streamFailover.detach()
        exo.playWhenReady = false
        exo.stop()
        exo.clearMediaItems()
    }

    /**
     * Fully destroys the ExoPlayer instance. Use only for settings-driven rebuilds,
     * explicit [release], or other lifecycle teardown — not for channel zaps.
     */
    private fun destroyPlayerInstance(context: Context? = null, clearCache: Boolean = false) {
        val exo = player ?: return
        PerformanceAudit.logPlayerLifecycle("RELEASE", exo, _playerGeneration.value, currentChannelId)
        playbackMonitor.detach()
        streamFailover.detach()
        exo.removeListener(timeshiftListener)
        exo.playWhenReady = false
        exo.stop()
        exo.clearVideoSurface()
        exo.clearMediaItems()
        decoderPressureTracker.unregisterPlayer(exo)
        exo.release()
        player = null
        pendingZapChannelId = null
        markPlayerReplaced()
        if (clearCache && context != null) {
            clearStreamCache(context)
        }
    }

    private fun buildLiveMediaItem(streamUrl: String): MediaItem =
        MediaItem.Builder().setUri(streamUrl).build()

    companion object {
        private const val TAG = "LivePlayerManager"
        private const val AUDIO_PIPELINE_FLUSH_DELAY_MS = 75L
    }

    fun lastChannel(): Channel? = _lastChannel.value

    fun switchChannel(context: Context): Boolean {
        val target = _lastChannel.value ?: return false
        tuneChannel(context, target)
        return true
    }

    fun canTimeshift(): Boolean = hasDvrWindow

    fun rewind(ms: Long = 30_000L) {
        val exo = player ?: return
        if (!hasDvrWindow || !TimeshiftManager.canRewind(exo)) return
        TimeshiftManager.rewind(exo, ms)
    }

    /** @deprecated Use [rewind] with millisecond amount. */
    fun rewindMinutes(mins: Int) {
        rewind(mins * 60_000L)
    }

    fun fastForward(ms: Long = 30_000L) {
        val exo = player ?: return
        if (!hasDvrWindow || !TimeshiftManager.canFastForward(exo)) return
        TimeshiftManager.fastForward(exo, ms)
    }

    fun skipBack(ms: Long) {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.skipBack(exo, ms)
    }

    fun skipForward(ms: Long) {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.skipForward(exo, ms)
    }

    fun instantReplay() {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.instantReplay(exo)
    }

    fun seekRelative(deltaMs: Long) {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.seekRelative(exo, deltaMs)
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.togglePlayPause(exo)
    }

    fun jumpToLive() {
        val exo = player ?: return
        TimeshiftManager.jumpToLive(exo)
    }

    fun isAtLiveEdge(): Boolean {
        val exo = player ?: return true
        return TimeshiftManager.isAtLiveEdge(exo)
    }

    fun refreshAtLiveEdge() {
        player?.let { refreshTimeshiftWindow(it) }
    }

    fun setMode(newMode: Mode) {
        mode = newMode
        applyVolume()
        player?.playWhenReady = newMode != Mode.IDLE
        syncCatalogHydrationGuard()
    }

    fun enterFullscreen() {
        fullscreenSessions++
        if (fullscreenSessions == 1) {
            channelScanner.setPlaybackScanSuspended(true)
        }
        resetVolumeFade()
        setMode(Mode.FULLSCREEN)
    }

    fun exitFullscreen(context: Context) {
        if (fullscreenSessions > 0) {
            fullscreenSessions--
        }
        if (fullscreenSessions > 0) return

        channelScanner.setPlaybackScanSuspended(false)
        TimeshiftManager.reset()
        resetVolumeFade()
        if (currentChannelId != null && !currentStreamUrl.isNullOrBlank()) {
            resumeGuidePreview(context, withAudio = true)
        } else {
            playbackMonitor.onPlaybackPaused()
            setMode(Mode.IDLE)
            player?.let { exo ->
                if (exo.isCurrentMediaItemDynamic) {
                    exo.seekToDefaultPosition()
                }
                exo.pause()
                refreshTimeshiftWindow(exo)
            } ?: resetTimeshiftState()
        }
    }

    fun setVolumeFade(multiplier: Float) {
        runOnPlayerThread {
            volumeFadeMultiplier = multiplier.coerceIn(0f, 1f)
            applyVolumeNow()
        }
    }

    fun resetVolumeFade() {
        runOnPlayerThread {
            volumeFadeMultiplier = 1f
            applyVolumeNow()
        }
    }

    private fun applyVolume() {
        runOnPlayerThread { applyVolumeNow() }
    }

    private fun applyVolumeNow() {
        val exo = player ?: return
        val baseVolume = when (mode) {
            Mode.FULLSCREEN -> 1f
            Mode.MINI -> if (miniAudioEnabled) 1f else 0f
            Mode.IDLE -> 0f
        }
        exo.volume = baseVolume * volumeFadeMultiplier
    }

    private fun runOnPlayerThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            monitorScope.launch { block() }
        }
    }

    fun activeChannelId(): Long? = currentChannelId

    fun activeChannel(): Channel? = currentChannel

    fun activeStreamUrl(): String? = currentStreamUrl

    private fun setActiveStreamUrl(url: String?) {
        currentStreamUrl = url
        if (_activeStreamUrl.value != url) {
            _activeStreamUrl.value = url
        }
    }

    private fun setActiveChannelId(channelId: Long?) {
        currentChannelId = channelId
        if (_activeChannelId.value != channelId) {
            _activeChannelId.value = channelId
        }
    }

    fun activePlayer(): ExoPlayer? = player

    /** Clears the video surface. Call only after every [PlayerView] has set `player = null`. */
    fun detachFromSurface() {
        player?.clearVideoSurface()
    }

    /** @deprecated Use [exitFullscreen]. */
    fun onFullscreenPlayerClosed(context: Context) {
        exitFullscreen(context)
    }

    fun ensureFullscreenPlayback(context: Context, channel: Channel) {
        resetVolumeFade()
        if (mode != Mode.FULLSCREEN) {
            setMode(Mode.FULLSCREEN)
        }
        if (tryReuseActiveStream(context, channel.id, channel.streamUrl, channel)) {
            return
        }
        tuneChannel(context, channel)
        player?.playWhenReady = true
        applyVolume()
    }

    /** Stop guide mini-player without releasing the tuned stream. */
    fun stopGuidePreview() {
        mode = Mode.IDLE
        playbackMonitor.onPlaybackPaused()
        streamFailover.onPlaybackPaused()
        player?.playWhenReady = false
        player?.pause()
        applyVolume()
        syncCatalogHydrationGuard()
    }

    /** Resume guide preview after leaving fullscreen or returning to the EPG. */
    fun resumeGuidePreview(context: Context, withAudio: Boolean = false) {
        mode = Mode.MINI
        val exo = player ?: return
        val url = currentStreamUrl
        if (url.isNullOrBlank()) {
            currentChannel?.let { tuneChannel(context, it) }
            if (withAudio) {
                exo.volume = 1f * volumeFadeMultiplier
            } else {
                applyVolume()
            }
            return
        }
        playbackMonitor.onPreviewResumed()
        streamFailover.onPlaybackResumed()
        exo.playWhenReady = true
        when (exo.playbackState) {
            Player.STATE_IDLE, Player.STATE_ENDED -> {
                if (exo.mediaItemCount > 0) {
                    exo.prepare()
                } else if (currentChannel != null &&
                    !tryReuseActiveStream(context, currentChannel!!.id, url, currentChannel)
                ) {
                    currentChannel?.let { tuneChannel(context, it) }
                }
            }
            else -> Unit
        }
        if (withAudio) {
            exo.volume = 1f * volumeFadeMultiplier
        } else {
            applyVolume()
        }
        syncCatalogHydrationGuard()
    }

    private fun syncCatalogHydrationGuard() {
        val suspendViewport = mode != Mode.IDLE && isPlaybackActive()
        catalogHydrationGuard.setViewportEpgSuspended(suspendViewport)
    }

    fun release(context: Context? = null) {
        streamTransitionJob?.cancel()
        channelScanner.setPlaybackScanSuspended(false)
        PerformanceAudit.logPlayerMemoryRelease(
            reason = "app_exit",
            phase = "before",
            generation = _playerGeneration.value,
            channelId = currentChannelId
        )
        destroyPlayerInstance(context, clearCache = true)
        PerformanceAudit.logPlayerMemoryRelease(
            reason = "app_exit",
            phase = "after",
            generation = _playerGeneration.value,
            channelId = null
        )
        setActiveChannelId(null)
        setActiveStreamUrl(null)
        currentChannel = null
        lastContext = null
        _lastChannel.value = null
        catchupDays = 0
        TimeshiftManager.reset()
        resetTimeshiftState()
        mode = Mode.IDLE
        fullscreenSessions = 0
        volumeFadeMultiplier = 1f
        playerReleasedForBackground = false
        playbackReadySeen = false
        bufferStartupStartedAtMs = 0L
    }

    private fun clearStreamCache(context: Context) {
        val appContext = context.applicationContext
        ioScope.launch {
            runCatching {
                appContext.cacheDir.listFiles()
                    ?.filter { it.name.startsWith("exo") }
                    ?.forEach { file ->
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
            }
        }
    }

    private fun resetTimeshiftState() {
        hasDvrWindow = false
        pendingJumpToLive = false
        TimeshiftManager.reset()
        publishTimeshiftState(TimeshiftUiState())
    }

    private fun publishTimeshiftState(state: TimeshiftUiState) {
        if (_timeshiftState.value == state) return
        _timeshiftState.value = state
    }

    private fun refreshTimeshiftWindow(exo: ExoPlayer) {
        val timeline = exo.currentTimeline
        if (timeline.isEmpty) {
            hasDvrWindow = false
            publishTimeshiftState(TimeshiftUiState(isPlaying = exo.isPlaying))
            return
        }

        val window = Timeline.Window()
        timeline.getWindow(exo.currentMediaItemIndex, window)
        val durationMs = window.durationMs
        hasDvrWindow = exo.isCurrentMediaItemDynamic &&
            durationMs > 0 &&
            durationMs != C.TIME_UNSET

        if (hasDvrWindow) {
            TimeshiftManager.updateLiveEdge(exo)
            if (pendingJumpToLive && exo.playbackState == Player.STATE_READY) {
                if (!TimeshiftManager.isAtLiveEdge(exo)) {
                    TimeshiftManager.jumpToLive(exo)
                } else {
                    pendingJumpToLive = false
                }
            }
            if (pendingJumpToLive && TimeshiftManager.isAtLiveEdge(exo)) {
                pendingJumpToLive = false
            }
        } else {
            TimeshiftManager.reset()
            pendingJumpToLive = false
        }

        val atEdge = pendingJumpToLive || TimeshiftManager.isAtLiveEdge(exo)
        TimeshiftManager.syncFromPlayer(exo, treatAsLiveEdge = pendingJumpToLive)
        val behindMs = if (atEdge) 0L else TimeshiftManager.behindLiveMs(exo)
        publishTimeshiftState(
            TimeshiftUiState(
                bufferStartMs = TimeshiftManager.bufferStartMs,
                liveEdgeMs = TimeshiftManager.liveEdgePositionMs,
                currentPositionMs = exo.currentPosition,
                behindLiveMs = behindMs,
                isTimeshifting = TimeshiftManager.isTimeshifting,
                atLiveEdge = atEdge,
                canTimeshift = canTimeshift(),
                canRewind = TimeshiftManager.canRewind(exo),
                canFastForward = TimeshiftManager.canFastForward(exo),
                isPlaying = exo.isPlaying,
                playWhenReady = exo.playWhenReady,
                playbackState = exo.playbackState
            )
        )
    }
}
