package com.grid.tv.player

import com.grid.tv.feature.scanner.ChannelScanner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures live playback is not competing with channel scanning, HEAD preflight probes,
 * or duplicate manifest fetches for the same stream URL.
 */
@Singleton
class PlaybackNetworkExclusivity @Inject constructor(
    private val channelScanner: ChannelScanner
) {
    private val lock = Any()

    private val activeStreamUrls = LinkedHashSet<String>()

    @Volatile
    var hasActivePlayback: Boolean = false
        private set

    fun registerStream(streamUrl: String) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            val wasInactive = activeStreamUrls.isEmpty()
            activeStreamUrls.add(trimmed)
            hasActivePlayback = true
            if (wasInactive) {
                PlaybackActivityGate.suppressScannerMetrics = true
                channelScanner.setPlaybackScanSuspended(true)
            }
        }
    }

    fun unregisterStream(streamUrl: String?) {
        val trimmed = streamUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            activeStreamUrls.remove(trimmed)
            if (activeStreamUrls.isEmpty()) {
                hasActivePlayback = false
                PlaybackActivityGate.suppressScannerMetrics = false
                channelScanner.setPlaybackScanSuspended(false)
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            if (activeStreamUrls.isEmpty()) return
            activeStreamUrls.clear()
            hasActivePlayback = false
            PlaybackActivityGate.suppressScannerMetrics = false
            channelScanner.setPlaybackScanSuspended(false)
        }
    }

    /** Skip HEAD/manifest preflight when ExoPlayer is already loading this URL. */
    fun shouldSkipPreflightProbe(streamUrl: String): Boolean {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return true
        synchronized(lock) {
            return activeStreamUrls.contains(trimmed)
        }
    }

    fun activeUrlsSnapshot(): Set<String> = synchronized(lock) { activeStreamUrls.toSet() }
}
