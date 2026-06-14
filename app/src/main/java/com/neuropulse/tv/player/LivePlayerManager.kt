package com.neuropulse.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.neuropulse.tv.domain.model.BufferSize
import com.neuropulse.tv.domain.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val playerFactory: PlayerFactory
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

    private val _activeChannelId = MutableStateFlow<Long?>(null)
    val activeChannelIdFlow: StateFlow<Long?> = _activeChannelId.asStateFlow()

    private val _canTimeshift = MutableStateFlow(false)
    val canTimeshiftFlow: StateFlow<Boolean> = _canTimeshift.asStateFlow()

    private val _atLiveEdge = MutableStateFlow(true)
    val atLiveEdgeFlow: StateFlow<Boolean> = _atLiveEdge.asStateFlow()

    private val _timeshiftState = MutableStateFlow(TimeshiftUiState())
    val timeshiftStateFlow: StateFlow<TimeshiftUiState> = _timeshiftState.asStateFlow()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playbackMonitor = StreamPlaybackMonitor(monitorScope)
    val playbackStatus: StateFlow<StreamPlaybackStatus> = playbackMonitor.status

    var mode: Mode = Mode.IDLE
        private set

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
    fun applyPlaybackSettings(
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
        val channelId = currentChannelId
        val streamUrl = currentStreamUrl
        val days = catchupDays
        val channel = currentChannel
        val currentMode = mode
        val audio = miniAudioEnabled
        playbackMonitor.detach()
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
    }

    fun setMiniAudioEnabled(enabled: Boolean) {
        miniAudioEnabled = enabled
        if (mode == Mode.MINI) applyVolume()
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
        val exo = getOrCreatePlayer(context)
        playbackMonitor.attach(exo)
        playbackMonitor.onTuneStarted(streamUrl)
        this.catchupDays = catchupDays

        if (currentChannelId == channelId && currentStreamUrl == streamUrl) {
            channelSnapshot?.let { currentChannel = it }
            exo.playWhenReady = true
            applyVolume()
            refreshTimeshiftWindow(exo)
            return
        }

        currentChannel?.takeIf { it.id != channelId }?.let { previous ->
            _lastChannel.value = previous
        }

        TimeshiftManager.reset()
        exo.stop()
        exo.clearMediaItems()
        clearStreamCache(context)
        resetTimeshiftState()
        this.catchupDays = catchupDays

        if (streamUrl.isBlank()) {
            currentChannelId = channelId
            currentStreamUrl = streamUrl
            channelSnapshot?.let { currentChannel = it }
            _activeChannelId.value = channelId
            _canTimeshift.value = false
            return
        }

        currentChannelId = channelId
        currentStreamUrl = streamUrl
        channelSnapshot?.let { currentChannel = it }
        _activeChannelId.value = channelId
        exo.setMediaItem(MediaItem.fromUri(streamUrl))
        exo.prepare()
        exo.playWhenReady = true
        applyVolume()
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
        refreshTimeshiftWindow(exo)
    }

    /** @deprecated Use [rewind] with millisecond amount. */
    fun rewindMinutes(mins: Int) {
        rewind(mins * 60_000L)
    }

    fun fastForward(ms: Long = 30_000L) {
        val exo = player ?: return
        if (!hasDvrWindow || !TimeshiftManager.canFastForward(exo)) return
        TimeshiftManager.fastForward(exo, ms)
        refreshTimeshiftWindow(exo)
    }

    fun seekRelative(deltaMs: Long) {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.seekRelative(exo, deltaMs)
        refreshTimeshiftWindow(exo)
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (!hasDvrWindow) return
        TimeshiftManager.togglePlayPause(exo)
        refreshTimeshiftWindow(exo)
    }

    fun jumpToLive() {
        val exo = player ?: return
        TimeshiftManager.jumpToLive(exo)
        refreshTimeshiftWindow(exo)
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
        player?.playWhenReady = true
    }

    private fun applyVolume() {
        val exo = player ?: return
        exo.volume = when (mode) {
            Mode.FULLSCREEN -> 1f
            Mode.MINI -> if (miniAudioEnabled) 1f else 0f
            Mode.IDLE -> 0f
        }
    }

    fun activeChannelId(): Long? = currentChannelId

    fun activePlayer(): ExoPlayer? = player

    fun detachFromSurface() {
        // PlayerView should set player = null before another view attaches.
    }

    fun onFullscreenPlayerClosed(context: Context) {
        TimeshiftManager.reset()
        clearStreamCache(context)
        player?.let { exo ->
            if (exo.isCurrentMediaItemDynamic) {
                exo.seekToDefaultPosition()
            }
            refreshTimeshiftWindow(exo)
        } ?: resetTimeshiftState()
    }

    fun release(context: Context? = null) {
        playbackMonitor.detach()
        player?.removeListener(timeshiftListener)
        player?.release()
        player = null
        currentChannelId = null
        currentStreamUrl = null
        currentChannel = null
        _lastChannel.value = null
        catchupDays = 0
        _activeChannelId.value = null
        TimeshiftManager.reset()
        resetTimeshiftState()
        mode = Mode.IDLE
        context?.let { clearStreamCache(it) }
    }

    private fun clearStreamCache(context: Context) {
        context.applicationContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith("exo") }
            ?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
    }

    private fun resetTimeshiftState() {
        hasDvrWindow = false
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
        hasDvrWindow = exo.isCurrentMediaItemDynamic &&
            durationMs > 0 &&
            durationMs != C.TIME_UNSET

        if (hasDvrWindow) {
            TimeshiftManager.updateLiveEdge(exo)
        } else {
            TimeshiftManager.reset()
        }

        val atEdge = TimeshiftManager.isAtLiveEdge(exo)
        _canTimeshift.value = canTimeshift()
        _atLiveEdge.value = atEdge
        _timeshiftState.value = TimeshiftUiState(
            bufferStartMs = TimeshiftManager.bufferStartMs,
            liveEdgeMs = TimeshiftManager.liveEdgePositionMs,
            currentPositionMs = exo.currentPosition,
            behindLiveMs = TimeshiftManager.behindLiveMs(exo),
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
