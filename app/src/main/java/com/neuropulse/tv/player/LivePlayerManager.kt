package com.neuropulse.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LivePlayerManager @Inject constructor(
    private val playerFactory: PlayerFactory
) {
    enum class Mode { IDLE, MINI, FULLSCREEN }

    private var player: ExoPlayer? = null
    private var currentChannelId: Long? = null
    private var currentStreamUrl: String? = null
    private var miniAudioEnabled: Boolean = false
    private val _activeChannelId = MutableStateFlow<Long?>(null)
    val activeChannelIdFlow: StateFlow<Long?> = _activeChannelId.asStateFlow()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playbackMonitor = StreamPlaybackMonitor(monitorScope)
    val playbackStatus: StateFlow<StreamPlaybackStatus> = playbackMonitor.status

    var mode: Mode = Mode.IDLE
        private set

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (player == null) {
            player = playerFactory.create(context.applicationContext)
        }
        return player!!
    }

    fun setMiniAudioEnabled(enabled: Boolean) {
        miniAudioEnabled = enabled
        if (mode == Mode.MINI) applyVolume()
    }

    fun tuneChannel(context: Context, channelId: Long, streamUrl: String) {
        val exo = getOrCreatePlayer(context)
        playbackMonitor.attach(exo)
        playbackMonitor.onTuneStarted(streamUrl)
        if (currentChannelId == channelId && currentStreamUrl == streamUrl) {
            exo.playWhenReady = true
            applyVolume()
            return
        }
        if (streamUrl.isBlank()) {
            currentChannelId = channelId
            currentStreamUrl = streamUrl
            _activeChannelId.value = channelId
            return
        }
        currentChannelId = channelId
        currentStreamUrl = streamUrl
        _activeChannelId.value = channelId
        exo.setMediaItem(MediaItem.fromUri(streamUrl))
        exo.prepare()
        exo.playWhenReady = true
        applyVolume()
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
        player?.release()
        player = null
        currentChannelId = null
        currentStreamUrl = null
        _activeChannelId.value = null
        mode = Mode.IDLE
    }
}
