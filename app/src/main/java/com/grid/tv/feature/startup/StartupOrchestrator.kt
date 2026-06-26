package com.grid.tv.feature.startup

import android.app.Application
import android.util.Log
import com.grid.tv.data.db.AppDatabaseHolder
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.worker.ChannelHealthScheduler
import com.grid.tv.worker.EpgScheduler
import com.grid.tv.worker.VodCatalogSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Strictly serialized cold start — input-safe opens before COUNT queries and network scheduling.
 */
@Singleton
class StartupOrchestrator @Inject constructor(
    private val safety: StartupSafety,
    private val diskQueue: StartupDiskQueue,
    private val coordinator: StartupCoordinator,
    private val epgScheduler: EpgScheduler,
    private val channelHealthScheduler: ChannelHealthScheduler,
    private val vodCatalogSyncScheduler: VodCatalogSyncScheduler
) {
    suspend fun runColdStart(application: Application) {
        safety.markColdStart()
        safety.setPhase(StartupPhase.BOOTING)

        diskQueue.run("database_prewarm") {
            StartupProfiler.mark("database_prewarm_start")
            AppDatabaseHolder.prewarm(application)
            StartupProfiler.mark("database_prewarm_complete")
        }

        diskQueue.run("phase1_minimal") {
            coordinator.runPhase1Minimal()
        }

        safety.awaitUiReady()

        val phase2Wait = StartupTierPolicy.phase2DelayMs() - safety.elapsedSinceColdStartMs()
        if (phase2Wait > 0L) delay(phase2Wait)
        coordinator.runPhase2CacheInstant()

        safety.awaitUiIdle()

        safety.enterInputSafe()

        safety.runNetworkExclusive("schedule_epg_workers") {
            epgScheduler.scheduleAtLaunch()
            if (!LowEndDeviceMode.current().deferStartupEpg) {
                epgScheduler.scheduleStartupEpg()
            } else {
                Log.i(TAG, "Skipping startup EPG — low-end survival mode (guide-open refresh)")
            }
        }
        if (!LowEndDeviceMode.current().deferChannelHealthProbe) {
            safety.runNetworkExclusive("schedule_channel_health") {
                channelHealthScheduler.schedule()
            }
        }
        safety.runNetworkExclusive("schedule_vod_sync") {
            vodCatalogSyncScheduler.schedulePeriodicSync()
        }
        safety.runNetworkExclusive("phase3_vod_maintenance") {
            if (!LowEndDeviceMode.current().deferStartupVod) {
                coordinator.runPhase3VodMaintenance()
            } else {
                Log.i(TAG, "Skipping phase-3 VOD maintenance — low-end survival mode")
            }
        }

        diskQueue.run("phase2b_counts") {
            coordinator.runPhase2BackgroundCounts()
        }
        safety.setPhase(StartupPhase.DISK_WARM_COMPLETE)

        safety.drainPendingNetworkJobs()
        safety.setPhase(StartupPhase.READY)
        Log.i(TAG, "Cold start complete — ${StartupProfiler.summary()}")
    }

    companion object {
        private const val TAG = "StartupOrchestrator"
    }
}
