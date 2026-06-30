package com.grid.tv.util

import android.os.SystemClock
import android.util.Log
import com.grid.tv.BuildConfig
import com.grid.tv.player.LowEndDeviceMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opt-in lightweight performance telemetry. Disabled in release and on low-end survival mode.
 */
object PerformanceTelemetryTracker {
    private const val TAG = "PerfTelemetry"
    private const val SAMPLE_EVERY_N = 5

    val enabled: Boolean
        get() = BuildConfig.DEBUG && !LowEndDeviceMode.current().active

    private val sampleCounter = AtomicInteger(0)
    private val activeSpans = ConcurrentHashMap<String, Long>()

    fun shouldSample(): Boolean = !enabled || sampleCounter.incrementAndGet() % SAMPLE_EVERY_N == 0

    fun markStart(operation: String): String {
        if (!enabled) return operation
        val token = "$operation#${System.nanoTime()}"
        activeSpans[token] = SystemClock.elapsedRealtime()
        return token
    }

    fun markEnd(token: String, detail: String? = null) {
        if (!enabled) return
        val started = activeSpans.remove(token) ?: return
        val elapsedMs = SystemClock.elapsedRealtime() - started
        if (!shouldSample() && elapsedMs < 50L) return
        Log.i(TAG, "${token.substringBefore('#')} ${elapsedMs}ms${detail?.let { " $it" } ?: ""}")
    }

    fun record(operation: String, elapsedMs: Long, detail: String? = null) {
        if (!enabled) return
        if (!shouldSample() && elapsedMs < 50L) return
        Log.i(TAG, "$operation ${elapsedMs}ms${detail?.let { " $it" } ?: ""}")
    }

    fun searchStarted(query: String, generation: Long): String =
        markStart("search gen=$generation q=${query.take(24)}")

    fun searchFirstResult(token: String, source: String) {
        if (!enabled) return
        val started = activeSpans[token] ?: return
        record("search_first_result", SystemClock.elapsedRealtime() - started, source)
    }

    fun searchCompleted(token: String, channels: Int, vod: Int, series: Int) {
        markEnd(token, "channels=$channels vod=$vod series=$series")
    }

    fun tmdbEnrichment(providerKey: String, elapsedMs: Long, hit: Boolean) {
        record("tmdb_enrichment", elapsedMs, "key=$providerKey hit=$hit")
    }

    fun epgRefresh(playlistId: Long, elapsedMs: Long, programCount: Int) {
        record("epg_refresh", elapsedMs, "playlist=$playlistId programs=$programCount")
    }

    fun vodCatalogLoad(playlistId: Long, elapsedMs: Long, itemCount: Int) {
        record("vod_catalog_load", elapsedMs, "playlist=$playlistId items=$itemCount")
    }
}
