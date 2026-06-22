package com.grid.tv.util

import android.os.Looper
import android.util.Log

/**
 * Tracks JSON/XML parse work and warns when parsing runs on the main thread.
 */
object JsonParseMetrics {
    private const val TAG = "JsonParseMetrics"

    fun <T> onIoThread(label: String, itemCount: Int = -1, block: () -> T): T {
        val onMain = Looper.getMainLooper().thread == Thread.currentThread()
        if (onMain) {
            Log.e(TAG, "JSON parse on MAIN thread blocked label=$label — move to Dispatchers.IO")
            PerformanceAudit.logJsonParseMainThreadViolation(label)
        }
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            PerformanceAudit.logJsonParse(label, elapsedMs, itemCount)
        }
    }
}
