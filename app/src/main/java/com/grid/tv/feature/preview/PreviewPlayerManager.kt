package com.grid.tv.feature.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.player.PlaybackStartupPriority
import com.grid.tv.player.PlayerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewPlayerManager @Inject constructor(
    private val playerFactory: PlayerFactory
) {
    private var currentChannelId: Long? = null
    private var player: ExoPlayer? = null

    @UnstableApi
    fun startPreview(context: Context, channelId: Long, url: String): ExoPlayer {
        if (currentChannelId == channelId && player != null) return player!!
        stopPreview()
        currentChannelId = channelId
        player = playerFactory.create(
            context = context,
            startupPriority = PlaybackStartupPriority.FAST
        ).apply {
            volume = 0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
        return player!!
    }

    fun stopPreview() {
        player?.release()
        player = null
        currentChannelId = null
    }

    fun activeChannelId(): Long? = currentChannelId

    fun activePlayer(): ExoPlayer? = player
}
