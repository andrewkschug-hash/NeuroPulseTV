package com.grid.tv.feature.startup

import android.app.Application
import android.util.Log
import com.grid.tv.data.db.AppDatabaseHolder
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.worker.ChannelHealthScheduler
import com.grid.tv.worker.EpgScheduler
import com.grid.tv.worker.VodCatalogSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Three-phase cold start. Phase 2B (SQLite COUNT) is fire-and-forget after the interactive window.
 */
@Singleton
class StartupOrchestrator @Inject constructor(
    private val pipeline: StartupPipeline,
    private val coordinator: StartupCoordinator,
    private val repository: IptvRepository,
    private val epgScheduler: EpgScheduler,
    private val channelHealthScheduler: ChannelHealthScheduler,
    private val vodCatalogSyncScheduler: VodCatalogSyncScheduler
) {
    suspend fun runColdStart(application: Application) {
        pipeline.markColdStart()
        pipeline.setPhase(StartupPhase.BOOTING)
        StartupProfiler.mark("database_prewarm_start")
        AppDatabaseHolder.prewarm(application)
        StartupProfiler.mark("database_prewarm_complete")

        pipeline.runDiskTask("phase1_minimal") {
            coordinator.runPhase1Minimal()
        }
        pipeline.setPhase(StartupPhase.PHASE1_READY)

        delay(StartupTierPolicy.phase2DelayMs())
        pipeline.setPhase(StartupPhase.PHASE2_RUNNING)
        coordinator.runPhase2CacheInstant()
        pipeline.setPhase(StartupPhase.PHASE2_CACHE_READY)
        flushDeferredVodLoads()

        pipeline.launchBackgroundAfterInteractiveDelay("phase2_db_counts") {
            pipeline.setPhase(StartupPhase.PHASE2_SAFE)
            coordinator.runPhase2BackgroundCounts()
            pipeline.setPhase(StartupPhase.PHASE2_COMPLETE)
            flushDeferredVodLoads()
        }

        val phase3Wait = (
            StartupTierPolicy.phase3DelayMs() - pipeline.elapsedSinceColdStartMs()
            ).coerceAtLeast(0L)
        if (phase3Wait > 0L) delay(phase3Wait)

        pipeline.setPhase(StartupPhase.PHASE3_RUNNING)
        epgScheduler.scheduleAtLaunch()
        if (!LowEndDeviceMode.current().deferChannelHealthProbe) {
            channelHealthScheduler.schedule()
        }
        vodCatalogSyncScheduler.schedulePeriodicSync()
        epgScheduler.scheduleStartupEpg()

        coordinator.runPhase3VodMaintenance()
        pipeline.setPhase(StartupPhase.READY)
        flushDeferredVodLoads()
        Log.i(TAG, "Cold start complete — ${StartupProfiler.summary()}")
    }

    private fun flushDeferredVodLoads() {
        pipeline.drainDeferredVodLoads().forEach { trigger ->
            Log.i(TAG, "Flushing deferred VOD load trigger=$trigger")
            repository.loadVodStreamed(trigger)
        }
    }

    companion object {
        private const val TAG = "StartupOrchestrator"
    }
}
