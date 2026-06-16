package com.grid.tv.domain.model

enum class ChannelScanStatus {
    LIVE,
    DEAD,
    CHECKING,
    UNKNOWN;

    companion object {
        fun fromStored(value: String?): ChannelScanStatus =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

data class ChannelScanSnapshot(
    val status: ChannelScanStatus,
    val lastCheckedAt: Long? = null
)

data class ScannerSettings(
    val autoScanEnabled: Boolean = true,
    val scanIntervalMinutes: Int = 5,
    val concurrentChecks: Int = 10,
    val scanOnMetered: Boolean = false
)

data class ScannerRuntimeState(
    val isScanning: Boolean = false,
    val liveCount: Int = 0,
    val totalCount: Int = 0,
    val lastFullScanAt: Long? = null
)
