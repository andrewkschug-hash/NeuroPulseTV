package com.grid.tv.player.multiview

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.domain.model.Channel
import com.grid.tv.player.MultiPanePlaybackPool
import com.grid.tv.feature.startup.StartupDependencyProbe
import javax.inject.Inject
import javax.inject.Singleton

/** MultiView facade — delegates to shared [MultiPanePlaybackPool]. */
@Singleton
class MultiViewManager @Inject constructor(
    private val multiPanePlaybackPool: MultiPanePlaybackPool
) {
    init {
        StartupDependencyProbe.traceInjectedInit("MultiViewManager")
    }

    private companion object {
        const val OWNER = "multiview"
    }

    @UnstableApi
    fun getOrCreatePlayer(context: Context, panelIndex: Int): ExoPlayer =
        multiPanePlaybackPool.getOrCreatePlayer(context, panelIndex, OWNER)

    fun tunePanel(context: Context, panelIndex: Int, channel: Channel) {
        tunePanel(context, panelIndex, channel.streamUrl)
        setActiveAudioPanel(panelIndex)
    }

    private fun tunePanel(context: Context, panelIndex: Int, streamUrl: String) {
        multiPanePlaybackPool.tunePane(context, panelIndex, streamUrl, OWNER)
    }

    fun replacePanel(context: Context, panelIndex: Int, channel: Channel) {
        tunePanel(context, panelIndex, channel)
    }

    fun setActiveAudioPanel(panelIndex: Int) {
        multiPanePlaybackPool.setActiveAudioPane(panelIndex)
    }

    fun syncDecodePolicy(decodeOnlyAudioPane: Boolean, activeAudioPanelIndex: Int) {
        multiPanePlaybackPool.syncDecodePolicy(decodeOnlyAudioPane, activeAudioPanelIndex)
    }

    fun pauseAll() = multiPanePlaybackPool.pauseAll()

    fun resumeAll() = multiPanePlaybackPool.resumeAll()

    fun releaseAll() {
        multiPanePlaybackPool.releaseAll()
    }

    fun activePlayer(): ExoPlayer? = multiPanePlaybackPool.activePlayer()
}
