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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class EpgScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "epg-work-log").apply { isDaemon = true }
    }

    fun scheduleAtLaunch() {
        ioScope.launch { scheduleAtLaunchInternal() }
    }

    /**
     * Immediate EPG refresh at app launch / after playlist import.
     * No network constraint — the worker must start so EpgFlow logs are visible; fetch fails if offline.
     */
    fun runEpgRefreshNow() {
        ioScope.launch { runEpgRefreshNowInternal() }
    }

    fun runResolverForNewChannels(createdAfter: Long) {
        ioScope.launch { runResolverForNewChannelsInternal(createdAfter) }
    }

    /** Run resolver immediately for all unresolved channels (e.g. right after EPG import). */
    fun runResolverNow() {
        ioScope.launch { runResolverNowInternal() }
    }

    private fun scheduleAtLaunchInternal() {
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

    private fun runEpgRefreshNowInternal() {
        Log.i(TAG, "runEpgRefreshNow: enqueueing one-shot EpgRefreshWorker (no constraints)")
        val request = OneTimeWorkRequestBuilder<EpgRefreshWorker>().build()
        workManager.enqueueUniqueWork(
            "epg_refresh_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
        logUniqueWorkState("epg_refresh_now")
    }

    private fun runResolverForNewChannelsInternal(createdAfter: Long) {
        Log.i(TAG, "runResolverForNewChannels: enqueueing EpgResolverWorker createdAfter=$createdAfter")
        val request = OneTimeWorkRequestBuilder<EpgResolverWorker>()
            .setInputData(workDataOf(EpgResolverWorker.KEY_CREATED_AFTER to createdAfter))
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniqueWork("epg_resolver_after_import", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        logUniqueWorkState("epg_resolver_after_import")
    }

    private fun runResolverNowInternal() {
        Log.i(TAG, "runResolverNow: enqueueing EpgResolverWorker for all unresolved channels")
        val request = OneTimeWorkRequestBuilder<EpgResolverWorker>()
            .setInputData(workDataOf(EpgResolverWorker.KEY_CREATED_AFTER to 0L))
            .build()
        workManager.enqueueUniqueWork("epg_resolver_after_refresh", ExistingWorkPolicy.REPLACE, request)
        logUniqueWorkState("epg_resolver_after_refresh")
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun logUniqueWorkState(uniqueWorkName: String) {
        val future = workManager.getWorkInfosForUniqueWork(uniqueWorkName)
        future.addListener(
            {
                try {
                    val infos = future.get()
                    if (infos.isEmpty()) {
                        Log.w(TAG, "Work '$uniqueWorkName' enqueued but no WorkInfo returned yet")
                        return@addListener
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
                } catch (e: Exception) {
                    Log.w(TAG, "Work '$uniqueWorkName' state check failed", e)
                }
            },
            logExecutor
        )
    }

    companion object {
        private const val TAG = "EpgFlow"
    }
}
