package com.grid.tv.feature.startup

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Lightweight startup timing — logs elapsed ms from process start for each stage.
 * Filter logcat with tag [TAG].
 */
object StartupProfiler {
    const val TAG = "StartupProfiler"

    private val originMs = SystemClock.elapsedRealtime()
    private val stages = ConcurrentLinkedQueue<Pair<String, Long>>()

    fun mark(stage: String, detail: String? = null) {
        val elapsed = SystemClock.elapsedRealtime() - originMs
        stages.add(stage to elapsed)
        val suffix = detail?.let { " ($it)" }.orEmpty()
        Log.i(TAG, "$stage$suffix +${elapsed}ms")
    }

    fun summary(): String =
        stages.joinToString(separator = " | ") { (stage, ms) -> "$stage=${ms}ms" }

    fun resetOriginForTests() {
        stages.clear()
    }
}
