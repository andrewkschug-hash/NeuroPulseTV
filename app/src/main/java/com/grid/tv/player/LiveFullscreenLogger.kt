package com.grid.tv.player

import android.util.Log
import androidx.media3.exoplayer.ExoPlayer

object LiveFullscreenLogger {
    private const val TAG = "LiveFullscreen"

    fun enterFullscreen(player: ExoPlayer?) {
        Log.i(TAG, "ENTER_FULLSCREEN ${playerInstance(player)}")
    }

    fun exitFullscreen(player: ExoPlayer?) {
        Log.i(TAG, "EXIT_FULLSCREEN ${playerInstance(player)}")
    }

    fun attachPlayerToFullscreen(player: ExoPlayer?, surfaceOwner: String) {
        Log.i(TAG, "ATTACH_PLAYER_TO_FULLSCREEN owner=$surfaceOwner ${playerInstance(player)}")
    }

    fun detachPlayerFromFullscreen(player: ExoPlayer?, surfaceOwner: String) {
        Log.i(TAG, "DETACH_PLAYER_FROM_FULLSCREEN owner=$surfaceOwner ${playerInstance(player)}")
    }

    fun surfaceCreated(owner: String, player: ExoPlayer?) {
        Log.i(TAG, "SURFACE_CREATED owner=$owner ${playerInstance(player)}")
    }

    fun surfaceDestroyed(owner: String, player: ExoPlayer?) {
        Log.i(TAG, "SURFACE_DESTROYED owner=$owner ${playerInstance(player)}")
    }

    fun playerInstanceId(player: ExoPlayer?): String = playerInstance(player)

    private fun playerInstance(player: ExoPlayer?): String =
        "PLAYER_INSTANCE_ID=${player?.let { System.identityHashCode(it) } ?: "null"}"
}
