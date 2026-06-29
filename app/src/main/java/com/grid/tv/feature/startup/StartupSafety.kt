package com.grid.tv.feature.startup

import android.os.SystemClock
import android.util.Log
import com.grid.tv.data.io.DiskIoSerialExecutor
import com.grid.tv.domain.model.VodRefreshTrigger
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single active-startup lock: disk I/O, network scheduling, and UI-idle input gating.
 */
class StartupSafety(
    private val diskIoSerialExecutor: DiskIoSerialExecutor,
    private val uiIdleMonitor: UiIdleMonitor
) {
    private val globalLock = Mutex()

    private val _phase = MutableStateFlow(StartupPhase.BOOTING)
    val phase: StateFlow<StartupPhase> = _phase.asStateFlow()

    @Volatile
    private var activeWork: StartupWorkKind = StartupWorkKind.NONE

    @Volatile
    private var uiReady = false

    @Volatile
    private var uiIdle = false

    @Volatile
    private var uiIdleReason: String? = null

    @Volatile
    private var inputSafe = false

    @Volatile
    private var diskJobsInFlight = 0

    @Volatile
    private var diskLockDepth = 0

    @Volatile
    private var lastDiskReleaseMs = 0L

    private var coldStartOriginMs = 0L

    private val pendingNetwork = ConcurrentLinkedQueue<PendingNetworkJob>()
    private val pendingVodLoads = ConcurrentLinkedQueue<VodRefreshTrigger>()
    private var vodLoadFlusher: ((VodRefreshTrigger) -> Unit)? = null

    init {
        uiIdleMonitor.setOnActivityListener { source ->
            log("ui_activity_detected", source)
        }
        uiIdleMonitor.setOnIdleListener { reason -> markUiIdle(reason) }
    }

    fun markColdStart() {
        coldStartOriginMs = SystemClock.elapsedRealtime()
    }

    fun elapsedSinceColdStartMs(): Long = SystemClock.elapsedRealtime() - coldStartOriginMs

    fun setPhase(phase: StartupPhase) {
        _phase.value = phase
        StartupProfiler.mark("startup_phase", phase.name)
    }

    fun currentPhase(): StartupPhase = _phase.value

    fun isUiReady(): Boolean = uiReady

    fun isUiIdle(): Boolean = uiIdle

    /** @deprecated Use [isUiIdle] */
    fun isUiStable(): Boolean = uiIdle

    fun isInputSafe(): Boolean = inputSafe

    fun markUiReady() {
        if (uiReady) return
        uiReady = true
        setPhase(StartupPhase.UI_READY)
        log("ui_ready_entered")
        uiIdleMonitor.onUiReady()
    }

    fun signalUiActivity(source: String) {
        uiIdleMonitor.signalActivity(source)
    }

    private fun markUiIdle(reason: String) {
        if (uiIdle) return
        uiIdle = true
        uiIdleReason = reason
        log("ui_idle_entered", "reason=$reason")
    }

    fun registerVodLoadFlusher(flusher: (VodRefreshTrigger) -> Unit) {
        vodLoadFlusher = flusher
    }

    suspend fun awaitUiReady(timeoutMs: Long = 10_000L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!uiReady && SystemClock.elapsedRealtime() < deadline) {
            delay(50)
        }
        if (!uiReady) {
            log("input_blocked_reason", "ui_ready_timeout_using_fallback")
            markUiReady()
        }
    }

    suspend fun awaitUiIdle(timeoutMs: Long = StartupTierPolicy.uiIdleTimeoutMs()) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!uiIdle && SystemClock.elapsedRealtime() < deadline) {
            delay(50)
        }
        if (!uiIdle) {
            log("input_blocked_reason", "ui_idle_timeout")
            uiIdleMonitor.forceIdle("timeout_fallback")
        }
    }

    /** @deprecated Use [awaitUiIdle] */
    suspend fun awaitUiStable(timeoutMs: Long = StartupTierPolicy.uiIdleTimeoutMs()) {
        awaitUiIdle(timeoutMs)
    }

    suspend fun awaitDiskIdle(idleMs: Long) {
        while (diskJobsInFlight > 0) delay(50)
        val sinceRelease = SystemClock.elapsedRealtime() - lastDiskReleaseMs
        val remaining = idleMs - sinceRelease
        if (remaining > 0L) delay(remaining)
    }

    suspend fun enterInputSafe() {
        if (!uiReady) {
            awaitUiReady()
        }
        awaitUiIdle()
        awaitDiskIdle(StartupTierPolicy.networkIdleAfterDiskMs())
        while (activeWork != StartupWorkKind.NONE) delay(50)
        inputSafe = true
        setPhase(StartupPhase.INPUT_SAFE)
        val idlePart = uiIdleReason ?: "unknown"
        log(
            "input_enabled_true",
            "reason=ui_ready+ui_idle($idlePart)+disk_idle+network_idle"
        )
        flushPendingVodLoads()
    }

    suspend fun drainPendingNetworkJobs() {
        while (true) {
            val job = pendingNetwork.poll() ?: break
            runNetworkExclusive(job.label, job.block)
        }
    }

    fun allowNetwork(operation: String): Boolean {
        if (inputSafe) return true
        log("network_deferred", operation)
        return false
    }

    fun shouldDeferDbCountQueries(): Boolean = !inputSafe

    fun shouldDeferChannelPageWarm(): Boolean = !inputSafe

    fun shouldDeferHeavyRepositoryWork(trigger: VodRefreshTrigger): Boolean {
        if (trigger == VodRefreshTrigger.MANUAL_RETRY) return false
        if (inputSafe) return false
        return trigger == VodRefreshTrigger.REPOSITORY_INIT ||
            trigger == VodRefreshTrigger.VOD_HUB_MOUNT
    }

    fun enqueueDeferredVodLoad(trigger: VodRefreshTrigger) {
        if (!pendingVodLoads.contains(trigger)) {
            pendingVodLoads.add(trigger)
            log("network_deferred", "vod_load_$trigger")
        }
    }

    fun drainDeferredVodLoads(): List<VodRefreshTrigger> {
        val drained = mutableListOf<VodRefreshTrigger>()
        while (true) {
            drained += pendingVodLoads.poll() ?: break
        }
        return drained
    }

    suspend fun awaitInputSafe() {
        awaitInputSafe(timeoutMs = Long.MAX_VALUE)
    }

    /** Returns whether [inputSafe] was reached before [timeoutMs] elapsed. */
    suspend fun awaitInputSafe(timeoutMs: Long): Boolean {
        if (inputSafe) return true
        if (timeoutMs <= 0L) return inputSafe
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!inputSafe && SystemClock.elapsedRealtime() < deadline) {
            delay(50)
        }
        if (!inputSafe) {
            log("input_blocked_reason", "await_input_safe_timeout timeoutMs=$timeoutMs")
        }
        return inputSafe
    }

    suspend fun <T> runDiskExclusive(label: String, block: suspend () -> T): T {
        globalLock.withLock {
            while (activeWork == StartupWorkKind.NETWORK) {
                log("input_blocked_reason", "disk_waiting_network label=$label")
                delay(10)
            }
            if (diskLockDepth == 0) {
                activeWork = StartupWorkKind.DISK
            }
            diskLockDepth++
            diskJobsInFlight++
            log("disk_lock_acquired", label)
        }
        return try {
            withContext(diskIoSerialExecutor.dispatcher) {
                block()
            }
        } finally {
            globalLock.withLock {
                diskJobsInFlight--
                diskLockDepth--
                if (diskLockDepth == 0) {
                    lastDiskReleaseMs = SystemClock.elapsedRealtime()
                    activeWork = StartupWorkKind.NONE
                }
                log("disk_lock_released", label)
            }
        }
    }

    suspend fun runNetworkExclusive(label: String, block: suspend () -> Unit) {
        if (!inputSafe) {
            deferNetwork(label, block)
            return
        }
        globalLock.withLock {
            while (activeWork == StartupWorkKind.DISK) {
                log("input_blocked_reason", "network_waiting_disk label=$label")
                delay(10)
            }
            activeWork = StartupWorkKind.NETWORK
            log("network_lock_acquired", label)
            try {
                block()
            } finally {
                activeWork = StartupWorkKind.NONE
                log("network_lock_released", label)
            }
        }
    }

    fun deferNetwork(label: String, block: suspend () -> Unit) {
        pendingNetwork.add(PendingNetworkJob(label, block))
        log("network_deferred", label)
    }

    private fun flushPendingVodLoads() {
        val flusher = vodLoadFlusher ?: return
        drainDeferredVodLoads().forEach { trigger ->
            log("network_lock_acquired", "flush_vod_$trigger")
            flusher(trigger)
        }
    }

    private fun log(event: String, detail: String? = null) {
        val message = if (detail.isNullOrBlank()) event else "$event $detail"
        Log.i(TAG, message)
        StartupProfiler.mark(event, detail)
    }

    companion object {
        private const val TAG = "StartupSafety"
    }
}
