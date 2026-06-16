package com.grid.tv.player.multiview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.domain.model.Channel
import com.grid.tv.player.PlayerFactory
import com.grid.tv.util.MediaAttribution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiViewManager @Inject constructor(
    private val playerFactory: PlayerFactory
) {
    private val players = mutableMapOf<Int, ExoPlayer>()
    private var activeAudioPanelIndex: Int = 0

    @UnstableApi
    fun getOrCreatePlayer(context: Context, panelIndex: Int): ExoPlayer {
        return players.getOrPut(panelIndex) {
            val appContext = MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
            playerFactory.create(appContext)
        }
    }

    fun tunePanel(context: Context, panelIndex: Int, channel: Channel) {
        val player = getOrCreatePlayer(context, panelIndex)
        val url = channel.streamUrl
        if (url.isBlank()) return
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        applyAudioFocus(panelIndex)
    }

    fun replacePanel(context: Context, panelIndex: Int, channel: Channel) {
        tunePanel(context, panelIndex, channel)
    }

    fun setActiveAudioPanel(panelIndex: Int) {
        activeAudioPanelIndex = panelIndex
        players.forEach { (index, player) ->
            player.volume = if (index == activeAudioPanelIndex) 1f else 0f
        }
    }

    fun pauseAll() {
        players.values.forEach { it.playWhenReady = false }
    }

    fun resumeAll() {
        players.values.forEach { it.playWhenReady = true }
    }

    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
        activeAudioPanelIndex = 0
    }

    fun activePlayer(): ExoPlayer? = players[activeAudioPanelIndex]

    private fun applyAudioFocus(panelIndex: Int) {
        setActiveAudioPanel(panelIndex)
    }
}
