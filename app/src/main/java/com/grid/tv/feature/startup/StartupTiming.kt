package com.grid.tv.feature.startup

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Millisecond-granularity startup instrumentation for ANR root-cause proof.
 * Filter logcat: `adb logcat -s STARTUP_TRACE`
 */
object StartupTiming {

    const val TAG = StartupTrace.TAG

    @Volatile
    private var originElapsedMs: Long = 0L

    private val spans = ConcurrentLinkedQueue<TimingSpan>()
    private val reportDumped = AtomicBoolean(false)

    data class TimingSpan(
        val component: String,
        val startMs: Long,
        val endMs: Long,
        val durationMs: Long,
        val thread: String,
        val mainThread: Boolean
    )

    fun markProcessStart() {
        if (originElapsedMs == 0L) {
            originElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    fun elapsedSinceProcessStartMs(): Long =
        if (originElapsedMs == 0L) 0L else SystemClock.elapsedRealtime() - originElapsedMs

    fun isMainThread(): Boolean =
        runCatching { Looper.getMainLooper().thread == Thread.currentThread() }.getOrDefault(false)

    fun log(message: String) {
        Log.i(
            TAG,
            "STARTUP_TRACE: $message " +
                "[thread=${Thread.currentThread().name} main=${isMainThread()} t+${elapsedSinceProcessStartMs()}ms]"
        )
    }

    fun <T> trace(component: String, block: () -> T): T {
        log("$component start")
        val thread = Thread.currentThread().name
        val main = isMainThread()
        val startMs = elapsedSinceProcessStartMs()
        val startNs = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            val endMs = elapsedSinceProcessStartMs()
            spans.add(
                TimingSpan(
                    component = component,
                    startMs = startMs,
                    endMs = endMs,
                    durationMs = durationMs,
                    thread = thread,
                    mainThread = main
                )
            )
            log("$component took ${durationMs}ms")
        }
    }

    fun recordSpan(component: String, durationMs: Long, thread: String = Thread.currentThread().name) {
        val endMs = elapsedSinceProcessStartMs()
        val startMs = (endMs - durationMs).coerceAtLeast(0L)
        spans.add(
            TimingSpan(
                component = component,
                startMs = startMs,
                endMs = endMs,
                durationMs = durationMs,
                thread = thread,
                mainThread = thread == Looper.getMainLooper().thread.name
            )
        )
    }

    fun dumpReportOnce(trigger: String) {
        if (!reportDumped.compareAndSet(false, true)) return
        log("=== STARTUP TIMING REPORT ($trigger) ===")
        log(
            String.format(
                "%-44s %8s %8s %8s %6s %s",
                "Component",
                "Start",
                "End",
                "Dur",
                "Main",
                "Thread"
            )
        )
        spans.forEach { span ->
            log(
                String.format(
                    "%-44s %8d %8d %8d %6s %s",
                    span.component.take(44),
                    span.startMs,
                    span.endMs,
                    span.durationMs,
                    span.mainThread.toString(),
                    span.thread
                )
            )
        }
        val longest = spans.maxByOrNull { it.durationMs }
        if (longest != null) {
            log(
                "LONGEST_BLOCKING_OPERATION: ${longest.component} " +
                    "${longest.durationMs}ms on ${longest.thread} main=${longest.mainThread}"
            )
        }
        log("=== END STARTUP TIMING REPORT ===")
    }
}
