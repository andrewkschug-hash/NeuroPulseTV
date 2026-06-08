package com.neuropulse.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
        if (currentChannelId == channelId && currentStreamUrl == streamUrl) {
            exo.playWhenReady = true
            applyVolume()
            return
        }
        currentChannelId = channelId
        currentStreamUrl = streamUrl
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
        player?.release()
        player = null
        currentChannelId = null
        currentStreamUrl = null
        mode = Mode.IDLE
    }
}
