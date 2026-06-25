package com.grid.tv.feature.startup

import android.os.SystemClock
import com.grid.tv.data.io.DiskIoSerialExecutor
import com.grid.tv.domain.model.VodRefreshTrigger
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Startup phase tracking and short disk tasks. Long COUNT queries run in [launchBackgroundAfterInteractiveDelay].
 */
@Singleton
class StartupPipeline @Inject constructor(
    private val diskIoSerialExecutor: DiskIoSerialExecutor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _phase = MutableStateFlow(StartupPhase.BOOTING)
    val phase: StateFlow<StartupPhase> = _phase.asStateFlow()

    private val diskGate = Mutex()
    private val pendingVodLoads = ConcurrentLinkedQueue<VodRefreshTrigger>()
    private var coldStartOriginMs: Long = SystemClock.elapsedRealtime()

    fun markColdStart() {
        coldStartOriginMs = SystemClock.elapsedRealtime()
    }

    fun elapsedSinceColdStartMs(): Long = SystemClock.elapsedRealtime() - coldStartOriginMs

    fun setPhase(phase: StartupPhase) {
        _phase.value = phase
        StartupProfiler.mark("startup_phase", phase.name)
    }

    fun currentPhase(): StartupPhase = _phase.value

    /** True until the interactive window — blocks SQLite COUNT and heavy DAO warm. */
    fun shouldDeferDbCountQueries(): Boolean =
        _phase.value.ordinal < StartupPhase.PHASE2_SAFE.ordinal

    fun shouldDeferChannelPageWarm(): Boolean = shouldDeferDbCountQueries()

    fun shouldDeferHeavyRepositoryWork(trigger: VodRefreshTrigger): Boolean {
        if (trigger == VodRefreshTrigger.MANUAL_RETRY) return false
        return when (_phase.value) {
            StartupPhase.READY -> false
            StartupPhase.PHASE3_RUNNING,
            StartupPhase.PHASE2_COMPLETE,
            StartupPhase.PHASE2_SAFE -> trigger == VodRefreshTrigger.REPOSITORY_INIT
            else -> trigger == VodRefreshTrigger.REPOSITORY_INIT ||
                trigger == VodRefreshTrigger.VOD_HUB_MOUNT
        }
    }

    fun enqueueDeferredVodLoad(trigger: VodRefreshTrigger) {
        if (!pendingVodLoads.contains(trigger)) {
            pendingVodLoads.add(trigger)
            StartupProfiler.mark("startup_vod_deferred", trigger.name)
        }
    }

    fun drainDeferredVodLoads(): List<VodRefreshTrigger> {
        val drained = mutableListOf<VodRefreshTrigger>()
        while (true) {
            val next = pendingVodLoads.poll() ?: break
            drained += next
        }
        return drained
    }

    /** Short startup disk work only (profile/settings) — must finish quickly. */
    suspend fun <T> runDiskTask(label: String, block: suspend () -> T): T =
        withContext(diskIoSerialExecutor.dispatcher) {
            diskGate.withLock {
                StartupProfiler.mark("startup_disk_begin", label)
                try {
                    block()
                } finally {
                    StartupProfiler.mark("startup_disk_end", label)
                }
            }
        }

    /**
     * Fire-and-forget background work after [StartupTierPolicy.phase2InteractiveDelayMs].
     * Does not block [StartupOrchestrator.runColdStart].
     */
    fun launchBackgroundAfterInteractiveDelay(label: String, block: suspend () -> Unit) {
        scope.launch {
            val waitMs = StartupTierPolicy.phase2InteractiveDelayMs() - elapsedSinceColdStartMs()
            if (waitMs > 0L) {
                StartupProfiler.mark("phase2_background_deferred", "wait=${waitMs}ms label=$label")
                delay(waitMs)
            }
            StartupProfiler.mark("phase2_background_start", label)
            try {
                block()
            } catch (error: Throwable) {
                StartupProfiler.mark("phase2_background_failed", "${label}:${error.message}")
            } finally {
                StartupProfiler.mark("phase2_background_end", label)
            }
        }
    }
}
