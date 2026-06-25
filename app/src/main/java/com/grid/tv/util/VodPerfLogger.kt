package com.grid.tv.util

import android.os.SystemClock
import android.util.Log

/** ANR/jank diagnostics for the VOD hub pipeline. Filter logcat with tag `VodPerf`. */
object VodPerfLogger {
    private const val TAG = "VodPerf"
    private const val SLOW_MS = 16L
    private const val ANR_RISK_MS = 100L

    fun logStage(stage: String, durationMs: Long, detail: String = "") {
        val suffix = if (detail.isBlank()) "" else " $detail"
        when {
            durationMs >= ANR_RISK_MS -> Log.w(TAG, "SLOW stage=$stage ms=$durationMs$suffix")
            durationMs >= SLOW_MS -> Log.i(TAG, "stage=$stage ms=$durationMs$suffix")
            else -> Log.d(TAG, "stage=$stage ms=$durationMs$suffix")
        }
    }

    inline fun <T> trace(stage: String, detail: String = "", block: () -> T): T {
        val started = System.nanoTime()
        return try {
            block()
        } finally {
            logStage(stage, (System.nanoTime() - started) / 1_000_000, detail)
        }
    }

    /** Marks the start of a user input → UI response window (language, genre, focus). */
    fun markInput(stage: String, detail: String = "") {
        Log.d(TAG, "input stage=$stage t=${SystemClock.elapsedRealtime()} $detail")
    }

    fun logEmission(source: String, detail: String) {
        Log.d(TAG, "emit source=$source t=${SystemClock.elapsedRealtime()} $detail")
    }
}
