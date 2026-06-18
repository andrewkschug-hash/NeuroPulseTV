package com.grid.tv.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.util.isTelevision
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChromecastController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val livePlayerManager: LivePlayerManager
) {
    private var castContext: CastContext? = null
    private var sessionListenerBound = false
    private var localPlaybackWasActive = false

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            loadCurrentStreamOnCast()
            pauseLocalPlayback()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            resumeLocalPlayback()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit

        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit

        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    fun isSupported(): Boolean = !appContext.isTelevision()

    fun ensureInitialized(): CastContext? {
        if (!isSupported()) return null
        castContext?.let { return it }
        return runCatching {
            CastContext.getSharedInstance(appContext).also { castContext = it }
        }.onFailure { error ->
            Log.w(TAG, "Cast SDK unavailable", error)
        }.getOrNull()
    }

    fun bindSessionListener() {
        val context = ensureInitialized() ?: return
        if (sessionListenerBound) return
        context.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        sessionListenerBound = true
    }

    fun unbindSessionListener() {
        val context = castContext ?: return
        if (!sessionListenerBound) return
        context.sessionManager.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        sessionListenerBound = false
    }

    fun isCasting(): Boolean =
        castContext?.sessionManager?.currentCastSession?.isConnected == true

    fun onActiveChannelChanged() {
        if (isCasting()) {
            loadCurrentStreamOnCast()
        }
    }

    private fun loadCurrentStreamOnCast() {
        val castSession = castContext?.sessionManager?.currentCastSession ?: return
        val streamUrl = livePlayerManager.activeStreamUrl()?.takeIf { it.isNotBlank() } ?: return
        val channel = livePlayerManager.activeChannel()
        val channelName = channel?.name?.takeIf { it.isNotBlank() } ?: "Live TV"
        val logoUrl = channel?.logoUrl?.takeIf { it.isNotBlank() }

        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, channelName)
            logoUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }

        val isHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
            streamUrl.contains("mpegurl", ignoreCase = true)
        val contentType = if (isHls) "application/x-mpegURL" else "video/mp4"
        val streamType = if (isHls) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(streamType)
            .setContentType(contentType)
            .setMetadata(mediaMetadata)
            .build()

        val remoteMediaClient = castSession.remoteMediaClient ?: return
        remoteMediaClient.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    private fun pauseLocalPlayback() {
        val player = livePlayerManager.activePlayer() ?: return
        localPlaybackWasActive = player.playWhenReady
        player.playWhenReady = false
        player.pause()
    }

    private fun resumeLocalPlayback() {
        if (!localPlaybackWasActive) return
        val player = livePlayerManager.activePlayer() ?: return
        player.playWhenReady = true
        localPlaybackWasActive = false
    }

    private companion object {
        const val TAG = "ChromecastController"
    }
}
