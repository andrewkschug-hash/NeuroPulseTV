package com.grid.tv.player

import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single orchestration layer for live guide, fullscreen, split, multiview, VOD, and preview.
 * Multi-pane modes release the shared [LivePlayerManager] before pane players start.
 *
 * Priority (highest first): LIVE_FULLSCREEN > VOD > SPLIT_VIEW / MULTIVIEW > PREVIEW > LIVE_GUIDE
 */
@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val livePlayerManager: LivePlayerManager,
    private val decoderPressureTracker: DecoderPressureTracker,
    private val multiPanePlaybackPool: MultiPanePlaybackPool
) {
    enum class PlaybackSession {
        NONE,
        LIVE_GUIDE,
        LIVE_FULLSCREEN,
        PREVIEW,
        SPLIT_VIEW,
        MULTIVIEW,
        VOD;

        val priority: Int
            get() = when (this) {
                LIVE_FULLSCREEN -> 100
                VOD -> 80
                SPLIT_VIEW, MULTIVIEW -> 60
                PREVIEW -> 40
                LIVE_GUIDE -> 30
                NONE -> 0
            }
    }

    /** @deprecated Use [PlaybackSession] */
    enum class Mode {
        NONE,
        GUIDE,
        FULLSCREEN,
        BROWSER_PREVIEW,
        SPLIT,
        MULTIVIEW,
        VOD
    }

    enum class SessionRequestResult {
        GRANTED,
        GRANTED_EVICTED_LOWER,
        DENIED_PRESSURE,
        DENIED_PRIORITY
    }

    @Volatile
    var activeSession: PlaybackSession = PlaybackSession.NONE
        private set

    private var liveSuspendedForExclusiveMode = false

    private val _blockedMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val blockedMessages: SharedFlow<String> = _blockedMessages.asSharedFlow()

    /**
     * Request ownership of a playback session. Evicts a lower-priority session when allowed.
     * Returns false when blocked by decoder pressure or a higher-priority active session.
     */
    fun requestSession(
        session: PlaybackSession,
        owner: String,
        context: Context
    ): SessionRequestResult {
        if (session == PlaybackSession.NONE) return SessionRequestResult.GRANTED

        val pressure = decoderPressureTracker.snapshot().pressureLevel
        val current = activeSession
        val incomingPriority = session.priority

        if (pressure == DecoderPressureLevel.CRITICAL &&
            incomingPriority < PlaybackSession.LIVE_FULLSCREEN.priority
        ) {
            emitBlocked("Too many video streams — close one to continue")
            Log.w(TAG, "requestSession DENIED_PRESSURE session=$session owner=$owner")
            return SessionRequestResult.DENIED_PRESSURE
        }

        if (current != PlaybackSession.NONE &&
            current != session &&
            current.priority > incomingPriority
        ) {
            emitBlocked("Stop ${current.label()} before starting ${session.label()}")
            Log.w(TAG, "requestSession DENIED_PRIORITY current=$current incoming=$session")
            return SessionRequestResult.DENIED_PRIORITY
        }

        val evicted = current != PlaybackSession.NONE && current != session &&
            current.priority <= incomingPriority

        if (evicted) {
            releaseSessionInternal(current, context.applicationContext, restoreLive = false)
        }

        applySessionEnter(session, context.applicationContext)
        activeSession = session
        Log.i(
            TAG,
            "requestSession GRANTED session=$session owner=$owner evicted=$evicted liveSuspended=$liveSuspendedForExclusiveMode"
        )
        return if (evicted) SessionRequestResult.GRANTED_EVICTED_LOWER else SessionRequestResult.GRANTED
    }

    fun releaseSession(session: PlaybackSession, context: Context) {
        if (activeSession != session) return
        releaseSessionInternal(session, context.applicationContext, restoreLive = true)
        activeSession = PlaybackSession.NONE
        Log.i(TAG, "releaseSession session=$session")
    }

    /** Legacy entry — maps to [requestSession]. */
    fun enter(mode: Mode, context: Context) {
        requestSession(mode.toSession(), owner = mode.name, context)
    }

    /** Legacy exit — maps to [releaseSession]. */
    fun exit(mode: Mode, context: Context) {
        releaseSession(mode.toSession(), context)
    }

    fun onGuidePreviewActive(context: Context) {
        if (activeSession == PlaybackSession.NONE || activeSession == PlaybackSession.LIVE_GUIDE) {
            requestSession(PlaybackSession.LIVE_GUIDE, owner = "guide_preview", context)
        }
    }

    fun onFullscreenActive() {
        activeSession = PlaybackSession.LIVE_FULLSCREEN
    }

    fun onFullscreenInactive() {
        if (activeSession == PlaybackSession.LIVE_FULLSCREEN) {
            activeSession = PlaybackSession.NONE
        }
    }

    /** Background / process teardown — releases all pane players and live decode. */
    fun releaseAllPlayback(context: Context) {
        val appContext = context.applicationContext
        multiPanePlaybackPool.releaseAll()
        previewStopGuide(appContext)
        if (livePlayerManager.hasPlayerInstance()) {
            livePlayerManager.releasePlayerResources(appContext, reason = "orchestrator_teardown")
        }
        liveSuspendedForExclusiveMode = false
        activeSession = PlaybackSession.NONE
        Log.i(TAG, "releaseAllPlayback")
    }

    private fun releaseSessionInternal(
        session: PlaybackSession,
        appContext: Context,
        restoreLive: Boolean
    ) {
        when (session) {
            PlaybackSession.SPLIT_VIEW, PlaybackSession.MULTIVIEW -> multiPanePlaybackPool.releaseAll()
            PlaybackSession.VOD, PlaybackSession.PREVIEW, PlaybackSession.LIVE_GUIDE -> Unit
            PlaybackSession.LIVE_FULLSCREEN -> Unit
            PlaybackSession.NONE -> Unit
        }
        if (restoreLive && session.suspendsLive()) {
            restoreLivePlaybackIfSuspended(appContext)
        }
    }

    private fun applySessionEnter(session: PlaybackSession, appContext: Context) {
        when (session) {
            PlaybackSession.SPLIT_VIEW, PlaybackSession.MULTIVIEW, PlaybackSession.VOD ->
                suspendLivePlayback(appContext, reason = session.name)
            PlaybackSession.PREVIEW, PlaybackSession.LIVE_GUIDE,
            PlaybackSession.LIVE_FULLSCREEN, PlaybackSession.NONE -> Unit
        }
    }

    private fun suspendLivePlayback(context: Context, reason: String) {
        if (!livePlayerManager.hasPlayerInstance()) return
        livePlayerManager.releasePlayerResources(context, reason = "exclusive_$reason")
        liveSuspendedForExclusiveMode = true
    }

    private fun restoreLivePlaybackIfSuspended(context: Context) {
        if (!liveSuspendedForExclusiveMode) return
        livePlayerManager.restoreSessionAfterBackgroundRelease(context)
        liveSuspendedForExclusiveMode = false
    }

    private fun previewStopGuide(appContext: Context) {
        livePlayerManager.stopGuidePreview()
    }

    private fun emitBlocked(message: String) {
        _blockedMessages.tryEmit(message)
    }

    private fun PlaybackSession.label(): String = when (this) {
        PlaybackSession.LIVE_FULLSCREEN -> "live playback"
        PlaybackSession.VOD -> "video playback"
        PlaybackSession.SPLIT_VIEW -> "split view"
        PlaybackSession.MULTIVIEW -> "multiview"
        PlaybackSession.PREVIEW -> "preview"
        PlaybackSession.LIVE_GUIDE -> "guide preview"
        PlaybackSession.NONE -> "playback"
    }

    private fun PlaybackSession.suspendsLive(): Boolean = when (this) {
        PlaybackSession.SPLIT_VIEW, PlaybackSession.MULTIVIEW, PlaybackSession.VOD -> true
        else -> false
    }

    private fun Mode.toSession(): PlaybackSession = when (this) {
        Mode.GUIDE -> PlaybackSession.LIVE_GUIDE
        Mode.FULLSCREEN -> PlaybackSession.LIVE_FULLSCREEN
        Mode.BROWSER_PREVIEW -> PlaybackSession.PREVIEW
        Mode.SPLIT -> PlaybackSession.SPLIT_VIEW
        Mode.MULTIVIEW -> PlaybackSession.MULTIVIEW
        Mode.VOD -> PlaybackSession.VOD
        Mode.NONE -> PlaybackSession.NONE
    }

    companion object {
        private const val TAG = "PlaybackOrchestrator"
    }
}
