package com.grid.tv.feature.startup

import android.util.Log
import com.grid.tv.domain.model.VodRefreshTrigger
import com.grid.tv.domain.repository.IptvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupCoordinator @Inject constructor(
    private val repository: IptvRepository
) {
    suspend fun runPhase1Minimal() {
        runCatching {
            StartupProfiler.mark("startup_phase1_start")
            repository.warmLocalUiCacheMinimal()
            StartupProfiler.mark("startup_phase1_complete")
        }.onFailure {
            Log.w(TAG, "runPhase1Minimal failed: ${it.message}", it)
        }
    }

    /** Phase 2A — cached counts only; must not touch SQLite COUNT. */
    fun runPhase2CacheInstant() {
        runCatching {
            StartupProfiler.mark("startup_phase2a_start")
            repository.applyCachedCatalogCountsAtStartup()
            StartupProfiler.mark("startup_phase2a_complete")
        }.onFailure {
            Log.w(TAG, "runPhase2CacheInstant failed: ${it.message}", it)
        }
    }

    /** Phase 2B — background COUNT queries + channel page (runs after interactive delay). */
    suspend fun runPhase2BackgroundCounts() {
        runCatching {
            StartupProfiler.mark("startup_phase2b_start")
            repository.updateCountsInBackground()
            StartupProfiler.mark("startup_phase2b_complete")
        }.onFailure {
            Log.w(TAG, "runPhase2BackgroundCounts failed: ${it.message}", it)
        }
    }

    suspend fun runPhase3VodMaintenance() {
        runCatching {
            StartupProfiler.mark("startup_phase3_start")
            repository.startDeferredVodMaintenance(VodRefreshTrigger.REPOSITORY_INIT)
            StartupProfiler.mark("startup_phase3_complete")
        }.onFailure {
            Log.w(TAG, "runPhase3VodMaintenance failed: ${it.message}", it)
        }
    }

    companion object {
        private const val TAG = "StartupCoordinator"
    }
}
