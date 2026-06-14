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
import kotlin.math.abs

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
    private var liveEdgePositionMs: Long = 0L
    private var seekableStartMs: Long = 0L

    private val _activeChannelId = MutableStateFlow<Long?>(null)
    val activeChannelIdFlow: StateFlow<Long?> = _activeChannelId.asStateFlow()

    private val _canTimeshift = MutableStateFlow(false)
    val canTimeshiftFlow: StateFlow<Boolean> = _canTimeshift.asStateFlow()

    private val _atLiveEdge = MutableStateFlow(true)
    val atLiveEdgeFlow: StateFlow<Boolean> = _atLiveEdge.asStateFlow()

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
            if (isPlaying) {
                player?.let { refreshTimeshiftWindow(it) }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            player?.let { _atLiveEdge.value = isAtLiveEdgeInternal(it) }
        }
    }

    @UnstableApi
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (player == null) {
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
        val channelId = currentChannelId
        val streamUrl = currentStreamUrl
        val days = catchupDays
        val currentMode = mode
        val audio = miniAudioEnabled
        playbackMonitor.detach()
        player?.removeListener(timeshiftListener)
        player?.release()
        player = null
        resetTimeshiftState()
        if (channelId != null && !streamUrl.isNullOrBlank()) {
            tuneChannel(context, channelId, streamUrl, days)
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

        resetTimeshiftState()
        this.catchupDays = catchupDays

        if (streamUrl.isBlank()) {
            currentChannelId = channelId
            currentStreamUrl = streamUrl
            channelSnapshot?.let { currentChannel = it }
            _activeChannelId.value = channelId
            _canTimeshift.value = catchupDays > 0
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

    fun canTimeshift(): Boolean = hasDvrWindow || catchupDays > 0

    fun rewindMinutes(mins: Int) {
        val exo = player ?: return
        if (!hasDvrWindow) return
        refreshTimeshiftWindow(exo)
        val target = (liveEdgePositionMs - mins * 60_000L).coerceAtLeast(seekableStartMs)
        exo.seekTo(target)
        exo.playWhenReady = true
        _atLiveEdge.value = isAtLiveEdgeInternal(exo)
    }

    fun jumpToLive() {
        val exo = player ?: return
        exo.seekToDefaultPosition()
        exo.playWhenReady = true
        refreshTimeshiftWindow(exo)
        _atLiveEdge.value = true
    }

    fun isAtLiveEdge(): Boolean {
        val exo = player ?: return true
        return isAtLiveEdgeInternal(exo)
    }

    fun refreshAtLiveEdge() {
        player?.let {
            refreshTimeshiftWindow(it)
            _atLiveEdge.value = isAtLiveEdgeInternal(it)
        }
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

    fun release() {
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
        resetTimeshiftState()
        mode = Mode.IDLE
    }

    private fun resetTimeshiftState() {
        hasDvrWindow = false
        liveEdgePositionMs = 0L
        seekableStartMs = 0L
        _canTimeshift.value = false
        _atLiveEdge.value = true
    }

    private fun refreshTimeshiftWindow(exo: ExoPlayer) {
        val timeline = exo.currentTimeline
        if (timeline.isEmpty) {
            hasDvrWindow = false
            _canTimeshift.value = catchupDays > 0
            _atLiveEdge.value = true
            return
        }

        val window = Timeline.Window()
        timeline.getWindow(exo.currentMediaItemIndex, window)
        val durationMs = window.durationMs
        hasDvrWindow = exo.isCurrentMediaItemDynamic &&
            durationMs > 0 &&
            durationMs != C.TIME_UNSET

        if (hasDvrWindow) {
            seekableStartMs = window.positionInFirstPeriodMs
            liveEdgePositionMs = when {
                window.defaultPositionMs != C.TIME_UNSET -> window.defaultPositionMs
                durationMs != C.TIME_UNSET -> seekableStartMs + durationMs
                else -> exo.currentPosition
            }
        }

        _canTimeshift.value = canTimeshift()
        _atLiveEdge.value = isAtLiveEdgeInternal(exo)
    }

    private fun isAtLiveEdgeInternal(exo: ExoPlayer): Boolean {
        if (!hasDvrWindow) return true
        return abs(exo.currentPosition - liveEdgePositionMs) <= 10_000
    }
}
