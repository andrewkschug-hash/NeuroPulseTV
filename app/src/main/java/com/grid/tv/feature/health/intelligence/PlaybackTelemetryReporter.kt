package com.grid.tv.feature.health.intelligence

import android.util.Log
import com.grid.tv.player.LowEndDeviceMode

/**
 * Structured logcat output when playback sessions complete.
 */
class PlaybackTelemetryReporter(
    private val scoringEngine: StreamHealthScoringEngine
) {
    fun logSessionCompleted(record: PlaybackSessionRecord) {
        if (!LowEndDeviceMode.current().telemetryVerbose) {
            if (!record.playbackSuccess || record.playbackErrorCount > 0 || record.streamSwitchCount > 0) {
                Log.i(
                    TAG,
                    "SESSION_END channel=${record.channelId} success=${record.playbackSuccess} " +
                        "errors=${record.playbackErrorCount} failovers=${record.streamSwitchCount}"
                )
            }
            return
        }
        val score = scoringEngine.scoreSession(record)
        val tier = HealthTier.fromScore(score)
        Log.i(
            TAG,
            "SESSION_END channel=${record.channelId} stream=${record.streamId} provider=${record.providerId} " +
                "score=$score tier=${tier.label} success=${record.playbackSuccess} " +
                "startupMs=${record.startupTimeMs} bufferMs=${record.bufferingDurationMs} " +
                "bufferEvents=${record.bufferingEventCount} failovers=${record.streamSwitchCount} " +
                "reconnects=${record.reconnectAttempts} loadRetries=${record.loadRetryCount} " +
                "errors=${record.playbackErrorCount} watchMs=${record.watchDurationMs}"
        )
    }

    fun logDiagnostics(diagnostics: StreamHealthDiagnostics) {
        if (!LowEndDeviceMode.current().telemetryVerbose) return
        Log.i(TAG, "DIAGNOSTICS scope=${diagnostics.scope} id=${diagnostics.entityId} ${diagnostics.summaryLine}")
        diagnostics.unstableStreamIds.forEach { streamId ->
            Log.w(TAG, "UNSTABLE_STREAM channel=${diagnostics.entityId} stream=$streamId")
        }
    }

    companion object {
        private const val TAG = "PlaybackTelemetry"
    }
}
