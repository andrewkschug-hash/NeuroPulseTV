package com.grid.tv.feature.startup

import android.util.Log
import com.grid.tv.util.PlaybackDiagnostics
import kotlin.system.measureNanoTime

/**
 * Structured startup / heavy-operation tracing for LMKD investigations.
 * Filter logcat: `adb logcat -s STARTUP_TRACE`
 */
object StartupTrace {

    const val TAG = "STARTUP_TRACE"

    fun log(operation: String, durationMs: Long, thread: String = Thread.currentThread().name) {
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapFree = runtime.freeMemory()
        val heapMax = runtime.maxMemory()
        Log.i(
            TAG,
            "[STARTUP_TRACE]\n" +
                "timestamp=${System.currentTimeMillis()}\n" +
                "thread=$thread\n" +
                "operation=$operation\n" +
                "durationMs=$durationMs\n" +
                "heapUsed=$heapUsed\n" +
                "heapFree=$heapFree\n" +
                "heapMax=$heapMax\n" +
                "heapUsedMb=${heapUsed / (1024 * 1024)}\n" +
                "heapMaxMb=${heapMax / (1024 * 1024)}\n" +
                "heapPct=${PlaybackDiagnostics.memorySnapshotForLog().usedPercent}"
        )
    }

    inline fun <T> trace(operation: String, block: () -> T): T {
        var result: T
        val durationMs = measureNanoTime {
            result = block()
        } / 1_000_000L
        log(operation, durationMs)
        return result
    }

    suspend inline fun <T> traceSuspend(operation: String, crossinline block: suspend () -> T): T {
        val startNs = System.nanoTime()
        val result = block()
        val durationMs = (System.nanoTime() - startNs) / 1_000_000L
        log(operation, durationMs)
        return result
    }
}
