package com.grid.tv.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Instruments PlayerView surface attach/detach for decoder pressure tracking.
 */
object PlaybackSurfaceInstrument {

    fun resolveTracker(context: Context): DecoderPressureTracker? =
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                PlayerEntryPoint::class.java
            ).decoderPressureTracker()
        }.getOrNull()

    fun surfaceType(view: PlayerView): String = when {
        view.videoSurfaceView != null -> "SurfaceView"
        else -> "TextureView"
    }

    fun attach(owner: String, player: ExoPlayer?, view: PlayerView) {
        val tracker = resolveTracker(view.context) ?: return
        tracker.onSurfaceAttached(owner, player, surfaceType(view))
    }

    fun detach(owner: String, player: ExoPlayer?, view: PlayerView) {
        val tracker = resolveTracker(view.context) ?: return
        tracker.onSurfaceDetached(owner, player)
    }

    fun configurePlayerView(
        owner: String,
        player: ExoPlayer?,
        view: PlayerView,
        block: PlayerView.() -> Unit = {}
    ) {
        view.apply {
            block()
            this.player = player
        }
        attach(owner, player, view)
    }

    fun releasePlayerView(owner: String, player: ExoPlayer?, view: PlayerView) {
        detach(owner, player, view)
        view.player = null
    }
}
