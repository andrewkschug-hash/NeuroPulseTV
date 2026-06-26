package com.grid.tv.player

import android.net.Uri
import android.util.Log
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryCollector
import com.grid.tv.feature.startup.StartupDependencyProbe
import javax.inject.Inject
import javax.inject.Singleton

/** Session health tier for live playback telemetry (does not affect playback decisions). */
enum class PlaybackSessionHealthTier(val label: String) {
    HEALTHY("Healthy"),
    DEGRADED("Degraded"),
    UNSTABLE("Unstable");

    companion object {
        fun fromScore(score: Int): PlaybackSessionHealthTier = when {
            score >= 90 -> HEALTHY
            score >= 70 -> DEGRADED
            else -> UNSTABLE
        }
    }
}

data class PlaybackHealthSnapshot(
    val score: Int,
    val tier: PlaybackSessionHealthTier,
    val channelId: Long?,
    val host: String?,
    val streamUrl: String?,
    val startupTimeMs: Long,
    val rebufferCount: Int,
    val totalBufferingMs: Long,
    val retryCount: Int,
    val failoverCount: Int,
    val playbackInterruptions: Int
)

/**
 * Computes a rolling 0–100 playback health score from session telemetry.
 * Logs score changes; does not alter playback behavior.
 */
@Singleton
class PlaybackHealthMonitor @Inject constructor(
    private val metrics: PlaybackMetricsLogger,
    private val telemetry: PlaybackTelemetryCollector
) {
    init {
        StartupDependencyProbe.traceInjectedInit("PlaybackHealthMonitor")
    }

    @Volatile
    private var lastSnapshot: PlaybackHealthSnapshot? = null

    fun evaluateAndLog(
        channelId: Long?,
        streamUrl: String?,
        startupTimeMs: Long = 0L,
        failoverCount: Int = 0
    ): PlaybackHealthSnapshot {
        val session = telemetry.peekActive()
        val rebufferCount = metrics.rebufferCount()
        val totalBufferingMs = metrics.totalBufferingMs()
        val retryCount = session?.reconnectAttempts ?: 0
        val errors = session?.playbackErrorCount ?: 0
        val streamSwitches = session?.streamSwitchCount ?: 0
        val interruptions = errors + streamSwitches + failoverCount

        var score = 100
        score -= (startupTimeMs / 200).toInt().coerceAtMost(20)
        score -= rebufferCount * 4
        score -= (totalBufferingMs / 2_000).toInt().coerceAtMost(25)
        score -= retryCount * 5
        score -= failoverCount * 6
        score -= interruptions * 3
        score = score.coerceIn(0, 100)

        val snapshot = PlaybackHealthSnapshot(
            score = score,
            tier = PlaybackSessionHealthTier.fromScore(score),
            channelId = channelId,
            host = streamUrl?.let(::hostFromUrl),
            streamUrl = streamUrl,
            startupTimeMs = startupTimeMs,
            rebufferCount = rebufferCount,
            totalBufferingMs = totalBufferingMs,
            retryCount = retryCount,
            failoverCount = failoverCount,
            playbackInterruptions = interruptions
        )

        val previous = lastSnapshot
        if (previous == null ||
            previous.score != snapshot.score ||
            previous.tier != snapshot.tier ||
            previous.streamUrl != snapshot.streamUrl
        ) {
            if (LowEndDeviceMode.current().telemetryVerbose) {
                Log.i(
                    TAG,
                    "PLAYBACK_HEALTH score=${snapshot.score} tier=${snapshot.tier.label} " +
                        "channelId=${snapshot.channelId} host=${snapshot.host} " +
                        "rebuffer=${snapshot.rebufferCount} bufferingMs=${snapshot.totalBufferingMs} " +
                        "retries=${snapshot.retryCount} failovers=${snapshot.failoverCount} " +
                        "url=${snapshot.streamUrl?.take(96)}"
                )
            }
            lastSnapshot = snapshot
        }
        return snapshot
    }

    fun reset() {
        lastSnapshot = null
    }

    private fun hostFromUrl(url: String): String? = runCatching {
        Uri.parse(url).host
    }.getOrNull()

    companion object {
        private const val TAG = "PlaybackHealth"
    }
}
