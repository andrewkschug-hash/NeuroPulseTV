package com.grid.tv.feature.startup

import android.util.Log
import com.grid.tv.domain.model.VodRefreshTrigger

/** Only one startup work class may hold the global lock at a time. */
enum class StartupWorkKind {
    NONE,
    DISK,
    NETWORK
}

internal data class PendingNetworkJob(
    val label: String,
    val block: suspend () -> Unit
)
