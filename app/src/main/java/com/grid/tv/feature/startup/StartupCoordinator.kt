package com.grid.tv.feature.startup

import android.util.Log
import com.grid.tv.domain.repository.IptvRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads UI-critical local data (VOD catalog from DB, channel list cache) before background maintenance work.
 */
@Singleton
class StartupCoordinator @Inject constructor(
    private val repository: IptvRepository
) {
    suspend fun warmCriticalLocalData() {
        runCatching {
            StartupProfiler.mark("startup_coordinator_warm_start")
            repository.warmLocalUiCache()
            StartupProfiler.mark("startup_coordinator_warm_complete")
            Log.i(TAG, "Local VOD cache and channel lists warmed")
        }.onFailure {
            Log.w(TAG, "warmCriticalLocalData failed: ${it.message}", it)
        }
    }

    companion object {
        private const val TAG = "StartupCoordinator"
    }
}
