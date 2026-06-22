package com.grid.tv.feature.health.intelligence

/**
 * Read-model for identifying unstable IPTV sources at channel, stream, and provider levels.
 */
enum class HealthDiagnosticsScope {
    CHANNEL,
    STREAM,
    PROVIDER
}

data class SessionDiagnosticSummary(
    val sessionStart: Long,
    val streamId: String,
    val startupTimeMs: Long,
    val bufferingDurationMs: Long,
    val bufferingEventCount: Int,
    val streamSwitchCount: Int,
    val reconnectAttempts: Int,
    val loadRetryCount: Int,
    val playbackErrorCount: Int,
    val watchDurationMs: Long,
    val playbackSuccess: Boolean,
    val sessionScore: Int
)

data class StreamHealthDiagnostics(
    val scope: HealthDiagnosticsScope,
    val entityId: Long,
    val entityLabel: String?,
    val streamId: String? = null,
    val healthScore: Int,
    val tier: HealthTier,
    val sessionCount: Int,
    val completionRate: Double,
    val failureRate: Double,
    val avgStartupTimeMs: Double,
    val avgBufferingDurationMs: Double,
    val avgBufferingEventsPerSession: Double,
    val avgFailoversPerSession: Double,
    val avgReconnectAttemptsPerSession: Double,
    val avgLoadRetriesPerSession: Double,
    val avgErrorsPerSession: Double,
    val avgWatchDurationMs: Double,
    val recentSessions: List<SessionDiagnosticSummary>,
    val unstableStreamIds: List<String> = emptyList(),
    val summaryLine: String
) {
    val isUnstable: Boolean
        get() = tier == HealthTier.POOR || failureRate >= 0.25 || avgFailoversPerSession >= 1.0
}
