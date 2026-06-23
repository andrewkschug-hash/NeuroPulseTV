package com.grid.tv.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures live playback is not competing with channel scanning, HEAD preflight probes,
 * or duplicate manifest fetches for the same stream URL.
 *
 * Stream registration is synchronous and lightweight; scanner suspension is deferred.
 */
@Singleton
class PlaybackNetworkExclusivity @Inject constructor(
    private val playbackScannerIsolation: PlaybackScannerIsolation
) {
    private val lock = Any()

    private val activeStreamUrls = LinkedHashSet<String>()

    @Volatile
    var hasActivePlayback: Boolean = false
        private set

    fun registerStream(streamUrl: String) {
        val trimmed = streamUrl.trim()
        if (trimmed.isEmpty()) return
        val scheduleIsolation: Boolean
        synchronized(lock) {
            scheduleIsolation = activeStreamUrls.isEmpty()
            activeStreamUrls.add(trimmed)
            hasActivePlayback = true
            if (scheduleIsolation) {
                PlaybackActivityGate.suppressScannerMetrics = true
            }
        }
        if (scheduleIsolation) {
            playbackScannerIsolation.acquireAsync()
        }
    }

    fun unregisterStream(streamUrl: String?) {
        val trimmed = streamUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        val scheduleRelease: Boolean
        synchronized(lock) {
            activeStreamUrls.remove(trimmed)
            scheduleRelease = activeStreamUrls.isEmpty()
            if (scheduleRelease) {
                hasActivePlayback = false
                PlaybackActivityGate.suppressScannerMetrics = false
            }
        }
        if (scheduleRelease) {
            playbackScannerIsolation.releaseAsync()
        }
    }

    fun clearAll() {
        val scheduleRelease: Boolean
        synchronized(lock) {
            if (activeStreamUrls.isEmpty()) return
            activeStreamUrls.clear()
            hasActivePlayback = false
            PlaybackActivityGate.suppressScannerMetrics = false
            scheduleRelease = true
        }
        if (scheduleRelease) {
            playbackScannerIsolation.releaseAsync()
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
