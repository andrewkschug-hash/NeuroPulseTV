package com.grid.tv.feature.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.util.MediaAttribution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewPlayerManager @Inject constructor() {
    private var currentChannelId: Long? = null
    private var player: ExoPlayer? = null

    fun startPreview(context: Context, channelId: Long, url: String): ExoPlayer {
        if (currentChannelId == channelId && player != null) return player!!
        stopPreview()
        currentChannelId = channelId
        player = ExoPlayer.Builder(
            MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
        ).build().apply {
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
