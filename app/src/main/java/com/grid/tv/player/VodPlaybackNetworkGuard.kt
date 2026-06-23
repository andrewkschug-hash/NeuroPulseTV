package com.grid.tv.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * VOD-only playback network isolation. URL registration is immediate; scanner suspension
 * is deferred so ExoPlayer can start its first request without waiting on scan teardown.
 */
@Singleton
class VodPlaybackNetworkGuard @Inject constructor(
    private val liveNetworkExclusivity: PlaybackNetworkExclusivity,
    private val playbackScannerIsolation: PlaybackScannerIsolation
) {
    private val lock = Any()

    @Volatile
    private var sessionActive: Boolean = false

    private var activeStreamUrl: String? = null

    private var holdsScannerIsolation: Boolean = false

    fun beginSession(streamUrl: String) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        val scheduleIsolation: Boolean
        synchronized(lock) {
            if (sessionActive && activeStreamUrl == trimmed) return
            activeStreamUrl = trimmed
            sessionActive = true
            scheduleIsolation = !holdsScannerIsolation
            holdsScannerIsolation = true
            if (!liveNetworkExclusivity.hasActivePlayback) {
                PlaybackActivityGate.suppressScannerMetrics = true
            }
        }
        if (scheduleIsolation) {
            playbackScannerIsolation.acquireAsync()
        }
    }

    fun endSession() {
        val scheduleRelease: Boolean
        synchronized(lock) {
            if (!sessionActive) return
            sessionActive = false
            activeStreamUrl = null
            scheduleRelease = holdsScannerIsolation
            holdsScannerIsolation = false
            if (!liveNetworkExclusivity.hasActivePlayback) {
                PlaybackActivityGate.suppressScannerMetrics = false
            }
        }
        if (scheduleRelease) {
            playbackScannerIsolation.releaseAsync()
        }
    }

    /** Skip HEAD/manifest preflight when VOD ExoPlayer is already loading this URL. */
    fun shouldSkipPreflightProbe(streamUrl: String): Boolean {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return false
        synchronized(lock) {
            return sessionActive && activeStreamUrl == trimmed
        }
    }

    fun hasActiveVodSession(): Boolean = sessionActive
}
