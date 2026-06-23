package com.grid.tv.player

import com.grid.tv.feature.scanner.ChannelScanner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VOD-only playback network isolation. Suspends channel scanning and blocks duplicate
 * preflight probes for the active on-demand stream without touching live playback wiring.
 */
@Singleton
class VodPlaybackNetworkGuard @Inject constructor(
    private val channelScanner: ChannelScanner,
    private val liveNetworkExclusivity: PlaybackNetworkExclusivity
) {
    private val lock = Any()

    @Volatile
    private var sessionActive: Boolean = false

    private var activeStreamUrl: String? = null

    /** True when this guard suspended the scanner (live may already hold suspend). */
    private var vodSuspendedScanner: Boolean = false

    fun beginSession(streamUrl: String) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            if (sessionActive && activeStreamUrl == trimmed) return
            activeStreamUrl = trimmed
            sessionActive = true
            if (!channelScanner.isPlaybackScanSuspended) {
                channelScanner.setPlaybackScanSuspended(true)
                vodSuspendedScanner = true
            } else {
                vodSuspendedScanner = false
            }
            if (!liveNetworkExclusivity.hasActivePlayback) {
                PlaybackActivityGate.suppressScannerMetrics = true
            }
        }
    }

    fun endSession() {
        synchronized(lock) {
            if (!sessionActive) return
            sessionActive = false
            activeStreamUrl = null
            if (vodSuspendedScanner && !liveNetworkExclusivity.hasActivePlayback) {
                channelScanner.setPlaybackScanSuspended(false)
                vodSuspendedScanner = false
            }
            if (!liveNetworkExclusivity.hasActivePlayback) {
                PlaybackActivityGate.suppressScannerMetrics = false
            }
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
