package com.grid.tv.feature.epg

import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.grid.tv.worker.EpgRefreshWorker
import com.grid.tv.worker.EpgResolverWorker
import com.grid.tv.player.LowEndDeviceMode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Single entry point for scheduling EPG refresh/resolver work.
 * Uses unique work names with KEEP for startup so cold-start jobs are not cancelled by duplicate enqueues.
 */
@Singleton
class EpgJobCoordinator @Inject constructor(
    private val workManager: WorkManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "epg-job-log").apply { isDaemon = true }
    }

    private val importEpgScheduled = AtomicBoolean(false)

    fun schedulePeriodicWorkers() {
        Log.i(TAG, "EPG_JOB_SCHEDULED source=PERIODIC action=register_periodic_workers policy=KEEP")
        val refreshRequest = androidx.work.PeriodicWorkRequestBuilder<EpgRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(EpgRefreshWorker.KEY_SOURCE to EpgJobSource.PERIODIC.name))
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest
        )

        val resolverRequest = androidx.work.PeriodicWorkRequestBuilder<EpgResolverWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_RESOLVER_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            resolverRequest
        )
    }

    /** Cold-start EPG — deferred until [StartupOrchestrator] reaches READY + initial WorkManager delay. */
    fun scheduleStartupEpg() {
        if (importEpgScheduled.get()) {
            Log.i(TAG, "EPG startup skipped — import EPG already queued")
            return
        }
        enqueueRefresh(
            source = EpgJobSource.STARTUP,
            initialDelaySec = LowEndDeviceMode.current().epgStartupDelaySec,
            policy = ExistingWorkPolicy.KEEP
        )
    }

    /** After playlist import — supersedes any queued startup EPG refresh. */
    fun scheduleImportEpg() {
        importEpgScheduled.set(true)
        enqueueRefresh(
            source = EpgJobSource.IMPORT,
            initialDelaySec = 0L,
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    fun scheduleManualEpg() {
        enqueueRefresh(
            source = EpgJobSource.MANUAL,
            initialDelaySec = 0L,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE
        )
    }

    fun onImportEpgWorkerFinished() {
        importEpgScheduled.set(false)
    }

    fun scheduleResolverAfterImport(createdAfter: Long = 0L) {
        Log.i(TAG, "EPG_JOB_SCHEDULED source=IMPORT action=resolver createdAfter=$createdAfter policy=KEEP")
        val request = OneTimeWorkRequestBuilder<EpgResolverWorker>()
            .setInputData(workDataOf(EpgResolverWorker.KEY_CREATED_AFTER to createdAfter))
            .build()
        workManager.enqueueUniqueWork(
            RESOLVER_AFTER_REFRESH_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
        logUniqueWorkState(RESOLVER_AFTER_REFRESH_WORK)
    }

    private fun enqueueRefresh(source: EpgJobSource, initialDelaySec: Long, policy: ExistingWorkPolicy) {
        Log.i(
            TAG,
            "EPG_JOB_SCHEDULED source=$source work=$IMMEDIATE_REFRESH_WORK policy=$policy " +
                "initialDelaySec=$initialDelaySec"
        )
        val builder = OneTimeWorkRequestBuilder<EpgRefreshWorker>()
            .setInputData(workDataOf(EpgRefreshWorker.KEY_SOURCE to source.name))
        if (initialDelaySec > 0L) {
            builder.setInitialDelay(initialDelaySec, TimeUnit.SECONDS)
        }
        workManager.enqueueUniqueWork(
            IMMEDIATE_REFRESH_WORK,
            policy,
            builder.build()
        )
        logUniqueWorkState(IMMEDIATE_REFRESH_WORK)
    }

    private fun logUniqueWorkState(uniqueWorkName: String) {
        val future = workManager.getWorkInfosForUniqueWork(uniqueWorkName)
        future.addListener(
            {
                try {
                    val infos = future.get()
                    if (infos.isEmpty()) {
                        Log.w(TAG, "EPG_JOB_STATE work=$uniqueWorkName status=no_work_info")
                        return@addListener
                    }
                    infos.forEach { info ->
                        Log.i(
                            TAG,
                            "EPG_JOB_STATE work=$uniqueWorkName id=${info.id} state=${info.state} " +
                                "runAttempt=${info.runAttemptCount}"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "EPG_JOB_STATE work=$uniqueWorkName failed", e)
                }
            },
            logExecutor
        )
    }

    companion object {
        private const val TAG = "EpgFlow"
        const val IMMEDIATE_REFRESH_WORK = "epg_refresh_now"
        private const val PERIODIC_REFRESH_WORK = "epg_refresh_work"
        private const val PERIODIC_RESOLVER_WORK = "epg_resolver_weekly_work"
        const val RESOLVER_AFTER_REFRESH_WORK = "epg_resolver_after_refresh"
        private const val STARTUP_EPG_INITIAL_DELAY_SEC = 5L
    }
}
