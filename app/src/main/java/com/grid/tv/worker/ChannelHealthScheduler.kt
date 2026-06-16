package com.grid.tv.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelHealthScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ChannelHealthProbeWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "channel_health_probe",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
