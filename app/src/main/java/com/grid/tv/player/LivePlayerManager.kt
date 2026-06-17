package com.grid.tv.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.domain.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

data class TimeshiftUiState(
    val bufferStartMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val behindLiveMs: Long = 0L,
    val isTimeshifting: Boolean = false,
    val atLiveEdge: Boolean = true,
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
    private val streamFailover: StreamFailoverController
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

    private var hasDvrWindow: Boolean = false
    private var pendingJumpToLive: Boolean = false

    private val _activeChannelId = MutableStateFlow<Long?>(null)
    val activeChannelIdFlow: StateFlow<Long?> = _activeChannelId.asStateFlow()

    /** Bumped whenever the underlying ExoPlayer instance is replaced. */
    private val _playerGeneration = MutableStateFlow(0)
    val playerGeneration: StateFlow<Int> = _playerGeneration.asStateFlow()

    private val _canTimeshift = MutableStateFlow(false)
    val canTimeshiftFlow: StateFlow<Boolean> = _canTimeshift.asStateFlow()

    private val _atLiveEdge = MutableStateFlow(true)
    val atLiveEdgeFlow: StateFlow<Boolean> = _atLiveEdge.asStateFlow()

    private val _timeshiftState = MutableStateFlow(TimeshiftUiState())
    val timeshiftStateFlow: StateFlow<TimeshiftUiState> = _timeshiftState.asStateFlow()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackMonitor = StreamPlaybackMonitor(monitorScope)
    val playbackStatus: StateFlow<StreamPlaybackStatus> = playbackMonitor.status
    val failoverUiState: StateFlow<StreamFailoverUiState> = streamFailover.uiState

    private val failoverActions = object : StreamFailoverPlaybackActions {
        override fun reconnectSameStream() {
            val exo = player ?: return
            val url = currentStreamUrl ?: return
            playbackMonitor.onTuneStarted(url)
            exo.stop()
            exo.clearMediaItems()
            if (url.isNotBlank()) {
                exo.setMediaItem(buildLiveMediaItem(url))
                exo.prepare()
            }
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

    init {
        playbackMonitor.status.onEach { status ->
            streamFailover.onPlaybackStatus(status)
        }.launchIn(monitorScope)
    }

    fun isFullscreenActive(): Boolean = fullscreenSessions > 0

    private val timeshiftListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            player?.let { refreshTimeshiftWindow(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                player?.let { refreshTimeshiftWindow(it) }
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
            TimeshiftManager.maxBufferMs = TimeshiftManager.maxBufferMsFor(bufferSize)
            player = playerFactory.create(
                context.applicationContext,
                bufferSize,
                preferHardwareDecoding
            ).also { exo ->
                exo.addListener(timeshiftListener)
            }
        }
        return player!!
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
        TimeshiftManager.maxBufferMs = TimeshiftManager.maxBufferMsFor(bufferSize)
        if (fullscreenSessions > 0) {
            return
        }
        if (player == null) {
            return
        }
        val channelId = currentChannelId
        val streamUrl = currentStreamUrl
        val days = catchupDays
        val channel = currentChannel
        val currentMode = mode
        val audio = miniAudioEnabled
        playbackMonitor.detach()
        streamFailover.detach()
        player?.removeListener(timeshiftListener)
        player?.release()
        player = null
        clearStreamCache(context)
        resetTimeshiftState()
        if (channelId != null && !streamUrl.isNullOrBlank()) {
            tuneChannel(context, channelId, streamUrl, days, channel)
            mode = currentMode
            miniAudioEnabled = audio
            applyVolume()
        }
        markPlayerReplaced()
    }

    private fun markPlayerReplaced() {
        _playerGeneration.value++
    }

    fun setMiniAudioEnabled(enabled: Boolean) {
        miniAudioEnabled = enabled
        if (mode == Mode.MINI) applyVolume()
    }

    fun setAutoReconnectOnDrop(enabled: Boolean) {
        streamFailover.setAutoReconnectEnabled(enabled)
    }

    fun tuneChannel(context: Context, channel: Channel) {
        tuneChannel(context, channel.id, channel.streamUrl, channel.catchupDays, channel)
    }

    fun tuneChannel(
        context: Context,
        channelId: Long,
        streamUrl: String,
        catchupDays: Int = 0,
        channelSnapshot: Channel? = null
    ) {
        lastContext = context.applicationContext
        val exo = getOrCreatePlayer(context)
        playbackMonitor.attach(exo)
        streamFailover.attach(exo, failoverActions)
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

        if (currentChannelId == channelId && currentStreamUrl == streamUrl && streamUrl.isNotBlank()) {
            channelSnapshot?.let { currentChannel = it }
            val state = exo.playbackState
            if (state == Player.STATE_READY) {
                exo.playWhenReady = true
                applyVolume()
                refreshTimeshiftWindow(exo)
                playbackMonitor.onStreamContinued(exo)
                return
            }
        }

        playbackMonitor.onTuneStarted(streamUrl)
        this.catchupDays = catchupDays

        currentChannel?.takeIf { it.id != channelId }?.let { previous ->
            _lastChannel.value = previous
        }

        TimeshiftManager.reset()
        clearStreamCache(context)
        resetTimeshiftState()
        this.catchupDays = catchupDays

        if (streamUrl.isBlank()) {
            currentChannelId = channelId
            currentStreamUrl = streamUrl
            channelSnapshot?.let { currentChannel = it }
            _activeChannelId.value = channelId
            _canTimeshift.value = false
            exo.stop()
            exo.clearMediaItems()
            return
        }

        currentChannelId = channelId
        currentStreamUrl = streamUrl
        channelSnapshot?.let { currentChannel = it }
        _activeChannelId.value = channelId
        pendingJumpToLive = true
        Log.i(TAG, "tuneChannel id=$channelId url=$streamUrl")
        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(buildLiveMediaItem(streamUrl))
        exo.prepare()
        exo.playWhenReady = true
        applyVolume()
        streamFailover.onStreamSwitched(streamUrl)
    }

    fun switchToStreamUrl(
        context: Context,
        channelId: Long,
        streamUrl: String,
        catchupDays: Int = 0,
        channelSnapshot: Channel? = null
    ) {
        lastContext = context.applicationContext
        val exo = getOrCreatePlayer(context)
        playbackMonitor.attach(exo)
        streamFailover.attach(exo, failoverActions)

        playbackMonitor.onTuneStarted(streamUrl)
        this.catchupDays = catchupDays
        currentChannelId = channelId
        currentStreamUrl = streamUrl
        channelSnapshot?.let { currentChannel = it }
        _activeChannelId.value = channelId
        pendingJumpToLive = true
        Log.i(TAG, "switchToStreamUrl id=$channelId url=$streamUrl")
        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(buildLiveMediaItem(streamUrl))
        exo.prepare()
        exo.playWhenReady = mode != Mode.IDLE
        applyVolume()
        streamFailover.onStreamSwitched(streamUrl)
    }

    private fun buildLiveMediaItem(streamUrl: String): MediaItem =
        MediaItem.Builder().setUri(streamUrl).build()

    companion object {
        private const val TAG = "LivePlayerManager"
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
    }

    fun enterFullscreen() {
        fullscreenSessions++
        resetVolumeFade()
        setMode(Mode.FULLSCREEN)
    }

    fun exitFullscreen(context: Context) {
        if (fullscreenSessions > 0) {
            fullscreenSessions--
        }
        if (fullscreenSessions > 0) return

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
        volumeFadeMultiplier = multiplier.coerceIn(0f, 1f)
        applyVolume()
    }

    fun resetVolumeFade() {
        volumeFadeMultiplier = 1f
        applyVolume()
    }

    private fun applyVolume() {
        val exo = player ?: return
        val baseVolume = when (mode) {
            Mode.FULLSCREEN -> 1f
            Mode.MINI -> if (miniAudioEnabled) 1f else 0f
            Mode.IDLE -> 0f
        }
        exo.volume = baseVolume * volumeFadeMultiplier
    }

    fun activeChannelId(): Long? = currentChannelId

    fun activeChannel(): Channel? = currentChannel

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
                } else {
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
    }

    fun release(context: Context? = null) {
        playbackMonitor.detach()
        streamFailover.detach()
        player?.removeListener(timeshiftListener)
        player?.release()
        player = null
        currentChannelId = null
        currentStreamUrl = null
        currentChannel = null
        lastContext = null
        _lastChannel.value = null
        catchupDays = 0
        _activeChannelId.value = null
        TimeshiftManager.reset()
        resetTimeshiftState()
        mode = Mode.IDLE
        fullscreenSessions = 0
        volumeFadeMultiplier = 1f
        markPlayerReplaced()
        context?.let { clearStreamCache(it) }
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
        _canTimeshift.value = false
        _atLiveEdge.value = true
        _timeshiftState.value = TimeshiftUiState()
    }

    private fun refreshTimeshiftWindow(exo: ExoPlayer) {
        val timeline = exo.currentTimeline
        if (timeline.isEmpty) {
            hasDvrWindow = false
            _canTimeshift.value = false
            _atLiveEdge.value = true
            _timeshiftState.value = TimeshiftUiState(isPlaying = exo.isPlaying)
            return
        }

        val window = Timeline.Window()
        timeline.getWindow(exo.currentMediaItemIndex, window)
        val durationMs = window.durationMs
        Log.d(
            "LIVE_DEBUG",
            "pos=${exo.currentPosition} " +
                "dur=${exo.duration} " +
                "offset=${exo.currentLiveOffset} " +
                "dynamic=${exo.isCurrentMediaItemDynamic} " +
                "defaultPos=${window.defaultPositionMs}"
        )
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
        Log.d(
            "TIMESHIFT_DEBUG",
            """
            atEdge=${TimeshiftManager.isAtLiveEdge(exo)}
            behind=${TimeshiftManager.behindLiveMs(exo)}
            offset=${exo.currentLiveOffset}
            position=${exo.currentPosition}
            duration=${exo.duration}
            windowBehind=${window.defaultPositionMs - exo.currentPosition}
            defaultPos=${window.defaultPositionMs}
            """.trimIndent()
        )
        _canTimeshift.value = canTimeshift()
        _atLiveEdge.value = atEdge
        _timeshiftState.value = TimeshiftUiState(
            bufferStartMs = TimeshiftManager.bufferStartMs,
            liveEdgeMs = TimeshiftManager.liveEdgePositionMs,
            currentPositionMs = exo.currentPosition,
            behindLiveMs = behindMs,
            isTimeshifting = TimeshiftManager.isTimeshifting,
            atLiveEdge = atEdge,
            canRewind = TimeshiftManager.canRewind(exo),
            canFastForward = TimeshiftManager.canFastForward(exo),
            isPlaying = exo.isPlaying,
            playWhenReady = exo.playWhenReady,
            playbackState = exo.playbackState
        )
    }
}
