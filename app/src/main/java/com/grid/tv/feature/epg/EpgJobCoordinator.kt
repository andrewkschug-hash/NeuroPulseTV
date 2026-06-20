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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry point for scheduling EPG refresh/resolver work.
 * Ensures only one immediate EPG download runs at a time and import jobs supersede startup jobs.
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
    private val startupEpgSuppressed = AtomicBoolean(false)

    fun schedulePeriodicWorkers() {
        Log.i(TAG, "EPG_JOB_SCHEDULED source=PERIODIC action=register_periodic_workers")
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
            ExistingPeriodicWorkPolicy.UPDATE,
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
            ExistingPeriodicWorkPolicy.UPDATE,
            resolverRequest
        )
    }

    /** Cold-start EPG — skipped if an import-triggered job is already queued. */
    fun scheduleStartupEpg() {
        if (importEpgScheduled.get()) {
            startupEpgSuppressed.set(true)
            Log.i(
                TAG,
                "EPG_JOB_CANCELLED source=STARTUP reason=import_epg_pending"
            )
            cancelUniqueWork(IMMEDIATE_REFRESH_WORK, "startup_superseded_by_import")
            return
        }
        scheduleDelayedRefresh(EpgJobSource.STARTUP, STARTUP_EPG_INITIAL_DELAY_SEC)
    }

    /** After playlist import — priority validation complete; supersedes any startup EPG. */
    fun scheduleImportEpg() {
        importEpgScheduled.set(true)
        if (startupEpgSuppressed.compareAndSet(true, false)) {
            Log.i(TAG, "EPG_JOB_CANCELLED source=STARTUP reason=replaced_by_import")
        }
        cancelUniqueWork(IMMEDIATE_REFRESH_WORK, "replaced_by_import")
        scheduleImmediateRefresh(EpgJobSource.IMPORT)
    }

    fun scheduleManualEpg() {
        scheduleImmediateRefresh(EpgJobSource.MANUAL)
    }

    fun onImportEpgWorkerFinished() {
        importEpgScheduled.set(false)
    }

    fun scheduleResolverAfterImport(createdAfter: Long = 0L) {
        Log.i(TAG, "EPG_JOB_SCHEDULED source=IMPORT action=resolver createdAfter=$createdAfter")
        val request = OneTimeWorkRequestBuilder<EpgResolverWorker>()
            .setInputData(workDataOf(EpgResolverWorker.KEY_CREATED_AFTER to createdAfter))
            .build()
        workManager.enqueueUniqueWork(
            RESOLVER_AFTER_REFRESH_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        logUniqueWorkState(RESOLVER_AFTER_REFRESH_WORK)
    }

    private fun scheduleImmediateRefresh(source: EpgJobSource) {
        enqueueRefresh(source, initialDelaySec = 0L)
    }

    private fun scheduleDelayedRefresh(source: EpgJobSource, initialDelaySec: Long) {
        enqueueRefresh(source, initialDelaySec = initialDelaySec)
    }

    private fun enqueueRefresh(source: EpgJobSource, initialDelaySec: Long) {
        Log.i(
            TAG,
            "EPG_JOB_SCHEDULED source=$source work=$IMMEDIATE_REFRESH_WORK policy=REPLACE " +
                "initialDelaySec=$initialDelaySec"
        )
        val builder = OneTimeWorkRequestBuilder<EpgRefreshWorker>()
            .setInputData(workDataOf(EpgRefreshWorker.KEY_SOURCE to source.name))
        if (initialDelaySec > 0L) {
            builder.setInitialDelay(initialDelaySec, TimeUnit.SECONDS)
        }
        workManager.enqueueUniqueWork(
            IMMEDIATE_REFRESH_WORK,
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
        logUniqueWorkState(IMMEDIATE_REFRESH_WORK)
    }

    private fun cancelUniqueWork(uniqueName: String, reason: String) {
        scope.launch {
            runCatching {
                workManager.cancelUniqueWork(uniqueName)
                Log.i(TAG, "EPG_JOB_CANCELLED work=$uniqueName reason=$reason")
            }.onFailure {
                Log.w(TAG, "EPG_JOB_CANCEL_FAILED work=$uniqueName reason=$reason", it)
            }
        }
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
