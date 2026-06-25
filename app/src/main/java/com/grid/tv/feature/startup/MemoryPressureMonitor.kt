package com.grid.tv.feature.startup

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodic JVM heap sampling for LMKD / memory-spike correlation.
 * Filter logcat: `adb logcat -s MEMORY_PRESSURE`
 */
object MemoryPressureMonitor {

    const val TAG = "MEMORY_PRESSURE"
    private const val INTERVAL_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    @Volatile
    private var lastUsedMb: Long = -1L

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                logSnapshot()
                delay(INTERVAL_MS)
            }
        }
    }

    private fun logSnapshot() {
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapFree = runtime.freeMemory()
        val heapMax = runtime.maxMemory()
        val usedMb = heapUsed / (1024 * 1024)
        val freeMb = heapFree / (1024 * 1024)
        val maxMb = heapMax / (1024 * 1024)
        val pct = ((heapUsed.toDouble() / heapMax.coerceAtLeast(1L).toDouble()) * 100).toInt()
        val deltaMb = if (lastUsedMb >= 0) usedMb - lastUsedMb else 0L
        val growthFlag = when {
            deltaMb >= 32 -> "SPIKE"
            deltaMb >= 8 -> "GROWTH"
            else -> "STABLE"
        }
        lastUsedMb = usedMb
        Log.i(
            TAG,
            "[MEMORY_PRESSURE]\n" +
                "timestamp=${System.currentTimeMillis()}\n" +
                "usedMb=$usedMb\n" +
                "freeMb=$freeMb\n" +
                "maxMb=$maxMb\n" +
                "percentUsed=$pct\n" +
                "deltaMb=$deltaMb\n" +
                "trend=$growthFlag\n" +
                "heapUsed=$heapUsed\n" +
                "heapFree=$heapFree\n" +
                "heapMax=$heapMax"
        )
    }
}
