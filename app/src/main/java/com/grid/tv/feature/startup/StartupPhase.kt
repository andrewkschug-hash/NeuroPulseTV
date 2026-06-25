package com.grid.tv.feature.startup

/**
 * Cold-start phases — strictly sequential; no overlap between disk and network work.
 */
enum class StartupPhase {
    BOOTING,
    UI_READY,
    DISK_WARM_COMPLETE,
    INPUT_SAFE,
    READY
}
