package com.grid.tv.player

import android.net.Uri
import android.util.Log
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryCollector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playback resilience metrics — complements [com.grid.tv.feature.scanner.ScanMetricsLogger]
 * for live ExoPlayer sessions (buffering, retries, watchdog recoveries).
 */
@Singleton
class PlaybackMetricsLogger @Inject constructor(
    private val telemetryCollector: PlaybackTelemetryCollector
) {

    private var activeChannelId: Long? = null
    private var activeHost: String? = null
    private var bufferingStartedAtMs: Long = 0L
    private var rebufferCount: Int = 0
    private var totalBufferingMs: Long = 0L
    private var hasCompletedStartup = false
    private var lastBufferLogMs = 0L
    private var lastLoadRetryLogMs = 0L

    fun onTuneStarted(channelId: Long?, streamUrl: String?) {
        activeChannelId = channelId
        activeHost = streamUrl?.let(::hostFromUrl)
        bufferingStartedAtMs = 0L
        rebufferCount = 0
        totalBufferingMs = 0L
        hasCompletedStartup = false
        lastBufferLogMs = 0L
        lastLoadRetryLogMs = 0L
        Log.i(
            TAG,
            "PLAYBACK_TUNE channelId=$channelId host=$activeHost"
        )
    }

    fun onBufferingStarted() {
        if (bufferingStartedAtMs == 0L) {
            bufferingStartedAtMs = System.currentTimeMillis()
            if (hasCompletedStartup) {
                rebufferCount++
            }
            if (shouldEmitBufferLog()) {
                Log.d(
                    TAG,
                    "BUFFER_START channelId=$activeChannelId host=$activeHost rebufferCount=$rebufferCount"
                )
            }
        }
    }

    fun onBufferingEnded() {
        if (bufferingStartedAtMs > 0L) {
            val duration = System.currentTimeMillis() - bufferingStartedAtMs
            totalBufferingMs += duration
            hasCompletedStartup = true
            if (shouldEmitBufferLog()) {
                Log.d(
                    TAG,
                    "BUFFER_END channelId=$activeChannelId host=$activeHost durationMs=$duration " +
                        "totalBufferingMs=$totalBufferingMs rebufferCount=$rebufferCount"
                )
            }
            bufferingStartedAtMs = 0L
        }
    }

    fun logLoadRetry(
        dataType: Int,
        errorCount: Int,
        httpStatus: Int?,
        delayMs: Long,
        accepted: Boolean,
        uri: String?
    ) {
        if (accepted) {
            telemetryCollector.onLoadRetry()
        }
        if (!shouldEmitLoadRetryLog()) return
        Log.w(
            TAG,
            "LOAD_RETRY channelId=$activeChannelId host=$activeHost dataType=$dataType " +
                "attempt=$errorCount http=$httpStatus delayMs=$delayMs accepted=$accepted " +
                "url=${uri?.take(120)}"
        )
    }

    fun logWatchdogRecovery(reason: String, stallMs: Long) {
        Log.w(
            TAG,
            "WATCHDOG_RECOVERY channelId=$activeChannelId host=$activeHost reason=$reason stallMs=$stallMs"
        )
    }

    fun logCumulativeBufferingBudget(totalMs: Long, windowMs: Long) {
        if (!shouldEmitBufferLog(force = true)) return
        Log.w(
            TAG,
            "CUMULATIVE_BUFFERING_BUDGET channelId=$activeChannelId host=$activeHost " +
                "totalMs=$totalMs windowMs=$windowMs"
        )
    }

    fun rebufferCount(): Int = rebufferCount

    fun totalBufferingMs(): Long = totalBufferingMs

    fun logBehindLiveWindowRecovery() {
        Log.i(
            TAG,
            "BEHIND_LIVE_WINDOW channelId=$activeChannelId host=$activeHost seekToLiveEdge"
        )
    }

    fun logPlaybackError(errorCode: Int, recoverable: Boolean, message: String?) {
        Log.w(
            TAG,
            "PLAYBACK_ERROR channelId=$activeChannelId host=$activeHost code=$errorCode " +
                "recoverable=$recoverable msg=$message"
        )
    }

    private fun shouldEmitBufferLog(force: Boolean = false): Boolean {
        val intervalMs = bufferLogIntervalMs()
        if (intervalMs <= 0L || force) return true
        val now = System.currentTimeMillis()
        if (now - lastBufferLogMs < intervalMs) return false
        lastBufferLogMs = now
        return true
    }

    private fun shouldEmitLoadRetryLog(): Boolean {
        val intervalMs = bufferLogIntervalMs()
        if (intervalMs <= 0L) return true
        val now = System.currentTimeMillis()
        if (now - lastLoadRetryLogMs < intervalMs) return false
        lastLoadRetryLogMs = now
        return true
    }

    private fun bufferLogIntervalMs(): Long =
        if (LowEndDeviceMode.current().active) LOW_END_LOG_INTERVAL_MS else 0L

    private fun hostFromUrl(url: String): String? = runCatching {
        Uri.parse(url).host
    }.getOrNull()

    companion object {
        private const val TAG = "PlaybackMetrics"
        private const val LOW_END_LOG_INTERVAL_MS = 5_000L
    }
}
