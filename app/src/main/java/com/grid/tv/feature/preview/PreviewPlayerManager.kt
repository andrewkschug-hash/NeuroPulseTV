package com.grid.tv.feature.preview

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.PlaybackOrchestrator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Channel-browser preview facade — delegates to [LivePlayerManager] MINI mode (single ExoPlayer stack).
 */
@Singleton
class PreviewPlayerManager @Inject constructor(
    private val livePlayerManager: LivePlayerManager,
    private val playbackOrchestrator: PlaybackOrchestrator
) {
    private var currentChannelId: Long? = null

    @UnstableApi
    fun startPreview(context: Context, channelId: Long, url: String): ExoPlayer? {
        val appContext = context.applicationContext
        if (currentChannelId == channelId && livePlayerManager.hasPlayerInstance()) {
            return livePlayerManager.getOrCreatePlayer(appContext)
        }
        stopPreview(appContext)
        val granted = playbackOrchestrator.requestSession(
            session = PlaybackOrchestrator.PlaybackSession.PREVIEW,
            owner = "channel_browser",
            context = appContext
        )
        if (granted != PlaybackOrchestrator.SessionRequestResult.GRANTED &&
            granted != PlaybackOrchestrator.SessionRequestResult.GRANTED_EVICTED_LOWER
        ) {
            return null
        }
        currentChannelId = channelId
        livePlayerManager.tuneChannel(
            context = appContext,
            channelId = channelId,
            streamUrl = url,
            catchupDays = 0,
            channelSnapshot = null
        )
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
        return livePlayerManager.getOrCreatePlayer(appContext)
    }

    fun stopPreview(context: Context? = null) {
        currentChannelId = null
        livePlayerManager.stopGuidePreview()
        context?.applicationContext?.let {
            playbackOrchestrator.releaseSession(PlaybackOrchestrator.PlaybackSession.PREVIEW, it)
        }
    }

    fun activeChannelId(): Long? = currentChannelId ?: livePlayerManager.activeChannelId()

    fun activePlayer(): ExoPlayer? = livePlayerManager.activePlayer()
}
