package com.grid.tv.feature.startup

import javax.inject.Inject
import javax.inject.Singleton

/** Global sequential disk queue — all startup DAO / Room work routes here. */
@Singleton
class StartupDiskQueue @Inject constructor(
    private val safety: StartupSafety
) {
    suspend fun <T> run(label: String, block: suspend () -> T): T =
        safety.runDiskExclusive(label, block)
}
