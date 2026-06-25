package com.grid.tv.worker

import com.grid.tv.feature.epg.EpgJobCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade for EPG WorkManager scheduling. All scheduling decisions are delegated to [EpgJobCoordinator].
 */
@Singleton
class EpgScheduler @Inject constructor(
    private val epgJobCoordinator: EpgJobCoordinator
) {
    fun scheduleAtLaunch() {
        epgJobCoordinator.schedulePeriodicWorkers()
    }

    fun scheduleStartupEpg() {
        epgJobCoordinator.scheduleStartupEpg()
    }

    fun scheduleEpgOnGuideOpen() {
        epgJobCoordinator.scheduleEpgOnGuideOpen()
    }

    /** @deprecated Prefer [scheduleStartupEpg] or [scheduleManualEpg] via coordinator sources. */
    fun runEpgRefreshNow() {
        epgJobCoordinator.scheduleManualEpg()
    }

    fun scheduleManualEpg() {
        epgJobCoordinator.scheduleManualEpg()
    }

    fun runResolverForNewChannels(createdAfter: Long) {
        epgJobCoordinator.scheduleResolverAfterImport(createdAfter)
    }

    fun runResolverNow() {
        epgJobCoordinator.scheduleResolverAfterImport(createdAfter = 0L)
    }
}
