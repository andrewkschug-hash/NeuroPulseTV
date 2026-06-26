package com.grid.tv.feature.startup

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer

/**
 * UI idle gate — debounce timer resets on activity; after 500ms quiet a Choreographer callback
 * plus main-looper idle handler confirms the main thread is free.
 *
 * Choreographer is only accessed on the main looper — never during construction or from background threads.
 */
class UiIdleMonitor {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var monitoring = false

    @Volatile
    private var idle = false

    @Volatile
    private var lastActivityUptimeMs = 0L

    private var onActivity: ((String) -> Unit)? = null
    private var onIdle: ((String) -> Unit)? = null
    private var idleCheck: Runnable? = null
    private var frameCallback: Choreographer.FrameCallback? = null

    fun setOnActivityListener(listener: (source: String) -> Unit) {
        onActivity = listener
    }

    fun setOnIdleListener(listener: (reason: String) -> Unit) {
        onIdle = listener
    }

    fun isIdle(): Boolean = idle

    fun onUiReady() {
        runOnMain { startMonitoring() }
    }

    fun signalActivity(source: String) {
        runOnMain { recordActivity(source) }
    }

    fun forceIdle(reason: String) {
        runOnMain {
            if (idle) return@runOnMain
            completeIdle(reason)
        }
    }

    private fun startMonitoring() {
        monitoring = true
        lastActivityUptimeMs = SystemClock.uptimeMillis()
        scheduleIdleCheck()
    }

    private fun recordActivity(source: String) {
        if (!monitoring || idle) return
        onActivity?.invoke(source)
        lastActivityUptimeMs = SystemClock.uptimeMillis()
        stopFrameWatch()
        scheduleIdleCheck()
    }

    private fun scheduleIdleCheck() {
        idleCheck?.let { mainHandler.removeCallbacks(it) }
        val quietMs = StartupTierPolicy.uiIdleQuietMs()
        val runnable = Runnable { confirmIdleAfterQuietWindow(quietMs) }
        idleCheck = runnable
        mainHandler.postDelayed(runnable, quietMs)
    }

    private fun confirmIdleAfterQuietWindow(quietMs: Long) {
        if (!monitoring || idle) return
        val quietFor = SystemClock.uptimeMillis() - lastActivityUptimeMs
        if (quietFor < quietMs) {
            mainHandler.postDelayed({ confirmIdleAfterQuietWindow(quietMs) }, quietMs - quietFor)
            return
        }
        stopFrameWatch()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!monitoring || idle) return
                val stillQuiet = SystemClock.uptimeMillis() - lastActivityUptimeMs >= quietMs
                if (!stillQuiet) {
                    scheduleIdleCheck()
                    return
                }
                Looper.getMainLooper().queue.addIdleHandler {
                    if (!monitoring || idle) return@addIdleHandler false
                    val quietOnIdle = SystemClock.uptimeMillis() - lastActivityUptimeMs >= quietMs
                    if (quietOnIdle) {
                        completeIdle("quiet_window")
                    } else {
                        scheduleIdleCheck()
                    }
                    false
                }
            }
        }
        frameCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun stopFrameWatch() {
        val callback = frameCallback ?: return
        frameCallback = null
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    private fun completeIdle(reason: String) {
        if (idle) return
        idle = true
        monitoring = false
        idleCheck?.let { mainHandler.removeCallbacks(it) }
        idleCheck = null
        stopFrameWatch()
        onIdle?.invoke(reason)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
