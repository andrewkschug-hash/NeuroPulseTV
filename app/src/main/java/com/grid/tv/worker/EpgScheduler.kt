package com.grid.tv.worker

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun scheduleAtLaunch() {
        Log.i(TAG, "scheduleAtLaunch: periodic EPG refresh (6h, requires network) + resolver (7d)")
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            "epg_refresh_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        val resolverRequest = PeriodicWorkRequestBuilder<EpgResolverWorker>(7, TimeUnit.DAYS)
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "epg_resolver_weekly_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            resolverRequest
        )
    }

    /**
     * Immediate EPG refresh at app launch / after playlist import.
     * No network constraint — the worker must start so EpgFlow logs are visible; fetch fails if offline.
     */
    fun runEpgRefreshNow() {
        Log.i(TAG, "runEpgRefreshNow: enqueueing one-shot EpgRefreshWorker (no constraints)")
        val request = OneTimeWorkRequestBuilder<EpgRefreshWorker>().build()
        workManager.enqueueUniqueWork(
            "epg_refresh_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
        logUniqueWorkState("epg_refresh_now")
    }

    fun runResolverForNewChannels(createdAfter: Long) {
        Log.i(TAG, "runResolverForNewChannels: enqueueing EpgResolverWorker createdAfter=$createdAfter")
        val request = OneTimeWorkRequestBuilder<EpgResolverWorker>()
            .setInputData(workDataOf(EpgResolverWorker.KEY_CREATED_AFTER to createdAfter))
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniqueWork("epg_resolver_after_import", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        logUniqueWorkState("epg_resolver_after_import")
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun logUniqueWorkState(uniqueWorkName: String) {
        val infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        if (infos.isEmpty()) {
            Log.w(TAG, "Work '$uniqueWorkName' enqueued but no WorkInfo returned yet")
            return
        }
        infos.forEach { info ->
            Log.i(
                TAG,
                "Work '$uniqueWorkName' id=${info.id} state=${info.state} " +
                    "runAttempt=${info.runAttemptCount} tags=${info.tags}"
            )
            if (info.state == WorkInfo.State.FAILED) {
                Log.e(TAG, "Work '$uniqueWorkName' FAILED — check worker factory / Hilt wiring")
            }
            if (info.state == WorkInfo.State.ENQUEUED) {
                Log.i(TAG, "Work '$uniqueWorkName' is ENQUEUED (waiting for constraints or scheduler)")
            }
        }
    }

    companion object {
        private const val TAG = "EpgFlow"
    }
}
