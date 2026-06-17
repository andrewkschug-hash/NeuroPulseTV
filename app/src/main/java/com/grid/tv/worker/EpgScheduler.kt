package com.grid.tv.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun scheduleAtLaunch() {
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            "epg_refresh_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        val resolverRequest = PeriodicWorkRequestBuilder<EpgResolverWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "epg_resolver_weekly_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            resolverRequest
        )
    }

    fun runEpgRefreshNow() {
        val request = OneTimeWorkRequestBuilder<EpgRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(
            "epg_refresh_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun runResolverForNewChannels(createdAfter: Long) {
        val request = OneTimeWorkRequestBuilder<EpgResolverWorker>()
            .setInputData(workDataOf(EpgResolverWorker.KEY_CREATED_AFTER to createdAfter))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork("epg_resolver_after_import", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
