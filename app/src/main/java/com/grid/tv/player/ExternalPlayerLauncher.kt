package com.grid.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

enum class ExternalPlayerId(val label: String) {
    NONE("Built-in player"),
    VLC("VLC"),
    MX_PLAYER("MX Player")
}

object ExternalPlayerLauncher {
    private const val TAG = "ExternalPlayer"

    fun launch(context: Context, playerId: ExternalPlayerId, streamUrl: String, title: String): Boolean {
        if (playerId == ExternalPlayerId.NONE) return false
        val uri = Uri.parse(streamUrl)
        val intent = when (playerId) {
            ExternalPlayerId.VLC -> Intent(Intent.ACTION_VIEW).apply {
                setPackage("org.videolan.vlc")
                setDataAndType(uri, "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ExternalPlayerId.MX_PLAYER -> Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.mxtech.videoplayer.ad")
                setDataAndType(uri, "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ExternalPlayerId.NONE -> return false
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "external player unavailable player=$playerId")
            false
        }
    }

    fun isInstalled(context: Context, playerId: ExternalPlayerId): Boolean {
        if (playerId == ExternalPlayerId.NONE) return true
        val pkg = when (playerId) {
            ExternalPlayerId.VLC -> "org.videolan.vlc"
            ExternalPlayerId.MX_PLAYER -> "com.mxtech.videoplayer.ad"
            ExternalPlayerId.NONE -> return true
        }
        return context.packageManager.getLaunchIntentForPackage(pkg) != null
    }
}
