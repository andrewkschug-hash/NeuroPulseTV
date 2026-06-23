package com.grid.tv.player

import android.util.Log
import com.grid.tv.data.network.AppHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global playback network pipeline: one active stream session, immediate HTTP cancellation on
 * tune/switch, tune-lock window blocking probes/scanner/failover overlap, and diagnostics.
 */
@Singleton
class PlaybackNetworkCoordinator @Inject constructor(
    private val appHttpClient: AppHttpClient,
    private val playbackNetworkExclusivity: PlaybackNetworkExclusivity,
    private val vodPlaybackNetworkGuard: VodPlaybackNetworkGuard
) {
    enum class SessionKind {
        LIVE,
        VOD,
        MULTI_PANE
    }

    private val lock = Any()

    @Volatile
    private var activeSessionKind: SessionKind? = null

    @Volatile
    private var activeStreamUrl: String? = null

    @Volatile
    private var activeTuneGeneration: Int = 0

    @Volatile
    private var networkGeneration: Int = 0

    @Volatile
    private var tuneLockUntilMs: Long = 0L

    /** Begin a live or multi-pane tune — cancels stale HTTP and registers the stream immediately. */
    fun beginLiveTune(
        streamUrl: String,
        tuneGeneration: Int,
        previousUrl: String? = null,
        sessionKind: SessionKind = SessionKind.LIVE
    ) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        openTuneSession(sessionKind, trimmed, tuneGeneration)
        previousUrl?.trim()?.takeIf { it.isNotEmpty() && it != trimmed }?.let {
            playbackNetworkExclusivity.unregisterStream(it)
        }
        val cancelledProbe = appHttpClient.cancelInFlightProbeRequests()
        val cancelledPlayback = appHttpClient.cancelInFlightPlaybackRequests()
        playbackNetworkExclusivity.registerStream(trimmed)
        logSessionActive(
            sessionKind = sessionKind,
            streamUrl = trimmed,
            tuneGeneration = tuneGeneration,
            cancelledProbe = cancelledProbe,
            cancelledPlayback = cancelledPlayback
        )
    }

    /** Begin VOD playback network isolation (non-blocking for ExoPlayer prepare). */
    fun beginVodSession(streamUrl: String) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        val generation = openTuneSession(SessionKind.VOD, trimmed, tuneGeneration = 0)
        val cancelledProbe = appHttpClient.cancelInFlightProbeRequests()
        val cancelledPlayback = appHttpClient.cancelInFlightPlaybackRequests()
        vodPlaybackNetworkGuard.beginSession(trimmed)
        logSessionActive(
            sessionKind = SessionKind.VOD,
            streamUrl = trimmed,
            tuneGeneration = generation,
            cancelledProbe = cancelledProbe,
            cancelledPlayback = cancelledPlayback
        )
    }

    fun endVodSession() {
        synchronized(lock) {
            if (activeSessionKind == SessionKind.VOD) {
                activeSessionKind = null
                activeStreamUrl = null
            }
        }
        vodPlaybackNetworkGuard.endSession()
        Log.i(TAG, "SESSION_END kind=VOD")
    }

    /** Call immediately before ExoPlayer.prepare() for the active stream. */
    fun markSingleRequestAllowed(streamUrl: String, tuneGeneration: Int = activeTuneGeneration) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        if (tuneGeneration != activeTuneGeneration && tuneGeneration > 0) return
        Log.i(
            TAG,
            "SINGLE_REQUEST_ALLOWED kind=${activeSessionKind?.name ?: "NONE"} " +
                "gen=$tuneGeneration urlHash=${trimmed.hashCode()} " +
                "parallelBlocked=${isTuneLockActive()}"
        )
    }

    fun isProbeBlocked(streamUrl: String): Boolean {
        if (isTuneLockActive()) return true
        return playbackNetworkExclusivity.shouldSkipPreflightProbe(streamUrl) ||
            vodPlaybackNetworkGuard.shouldSkipPreflightProbe(streamUrl)
    }

    fun isFailoverBlocked(): Boolean = isTuneLockActive()

    fun shouldBlockScanner(): Boolean =
        isTuneLockActive() ||
            playbackNetworkExclusivity.hasActivePlayback ||
            vodPlaybackNetworkGuard.hasActiveVodSession()

    fun isTuneLockActive(): Boolean =
        System.currentTimeMillis() < tuneLockUntilMs || PlaybackActivityGate.isNetworkTuneLocked()

    fun activeUrlsSnapshot(): Set<String> = playbackNetworkExclusivity.activeUrlsSnapshot()

    internal fun tuneLockUntilMsForTests(): Long = tuneLockUntilMs

    private fun openTuneSession(kind: SessionKind, streamUrl: String, tuneGeneration: Int): Int {
        synchronized(lock) {
            networkGeneration++
            val generation = if (tuneGeneration > 0) tuneGeneration else networkGeneration
            activeSessionKind = kind
            activeStreamUrl = streamUrl
            activeTuneGeneration = generation
            tuneLockUntilMs = System.currentTimeMillis() + TUNE_LOCK_MS
            PlaybackActivityGate.openNetworkTuneLock(TUNE_LOCK_MS)
            return generation
        }
    }

    private fun logSessionActive(
        sessionKind: SessionKind,
        streamUrl: String,
        tuneGeneration: Int,
        cancelledProbe: Int,
        cancelledPlayback: Int
    ) {
        val parallelActive = cancelledProbe + cancelledPlayback
        Log.i(
            TAG,
            "SESSION_ACTIVE kind=$sessionKind gen=$tuneGeneration urlHash=${streamUrl.hashCode()} " +
                "SCANNER_SUSPENDED=${shouldBlockScanner()} tuneLockMs=$TUNE_LOCK_MS " +
                "cancelledProbe=$cancelledProbe cancelledPlayback=$cancelledPlayback " +
                if (parallelActive == 0) "NO_PARALLEL_REQUESTS_ACTIVE" else "PARALLEL_CANCELLED=$parallelActive"
        )
    }

    companion object {
        private const val TAG = "PlaybackNetwork"
        const val TUNE_LOCK_MS = 750L
    }
}
