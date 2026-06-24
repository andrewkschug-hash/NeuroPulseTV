package com.grid.tv.worker

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Registers periodic WorkManager jobs for continuous VOD catalog hydration. */
@Singleton
class VodCatalogSyncScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun schedulePeriodicSync() {
        Log.i(TAG, "Scheduling periodic VOD catalog sync (every ${PERIOD_HOURS}h)")
        val request = PeriodicWorkRequestBuilder<VodCatalogSyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val TAG = "VodCatalogPipeline"
        private const val PERIOD_HOURS = 12L
        const val UNIQUE_WORK_NAME = "vod_catalog_periodic_sync"
    }
}
