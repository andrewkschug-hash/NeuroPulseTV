package com.grid.tv.feature.health.intelligence

enum class HealthTier(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor");

    companion object {
        fun fromScore(score: Int): HealthTier = when {
            score >= 95 -> EXCELLENT
            score >= 85 -> GOOD
            score >= 70 -> FAIR
            else -> POOR
        }
    }
}

enum class StreamSourceId(val storageKey: String) {
    PRIMARY("primary"),
    BACKUP_1("backup_1"),
    BACKUP_2("backup_2"),
    BACKUP_3("backup_3");

    companion object {
        fun fromIndex(index: Int): StreamSourceId = entries.getOrElse(index) { PRIMARY }
    }
}

data class PlaybackSessionRecord(
    val channelId: Long,
    val streamId: String,
    val providerId: Long,
    val sessionStart: Long,
    val sessionEnd: Long,
    val watchDurationMs: Long,
    val startupTimeMs: Long,
    val bufferingEventCount: Int,
    val bufferingDurationMs: Long,
    val playbackErrorCount: Int,
    val streamSwitchCount: Int,
    val reconnectAttempts: Int,
    val playbackSuccess: Boolean
)

data class HealthScoreSnapshot(
    val score: Int,
    val tier: HealthTier,
    val sessionCount: Int,
    val avgStartupTimeMs: Double = 0.0,
    val avgBufferingDurationMs: Double = 0.0,
    val failureRate: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class StreamHealthDetail(
    val channelId: Long,
    val streamId: String,
    val snapshot: HealthScoreSnapshot
)

data class ChannelHealthDetail(
    val channelId: Long,
    val snapshot: HealthScoreSnapshot,
    val streams: List<StreamHealthDetail>
)

data class ProviderHealthDetail(
    val providerId: Long,
    val snapshot: HealthScoreSnapshot,
    val channelCount: Int
)

data class StreamFailoverRanking(
    val channelId: Long,
    val orderedStreamIds: List<String>
)
