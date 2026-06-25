package com.grid.tv.feature.startup

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI idle gate — debounce timer resets on activity; after 500ms quiet a Choreographer callback
 * plus main-looper idle handler confirms the main thread is free.
 */
@Singleton
class UiIdleMonitor @Inject constructor() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()

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
        monitoring = true
        lastActivityUptimeMs = SystemClock.uptimeMillis()
        scheduleIdleCheck()
    }

    fun signalActivity(source: String) {
        if (!monitoring || idle) return
        onActivity?.invoke(source)
        lastActivityUptimeMs = SystemClock.uptimeMillis()
        stopFrameWatch()
        scheduleIdleCheck()
    }

    fun forceIdle(reason: String) {
        if (idle) return
        completeIdle(reason)
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
        choreographer.postFrameCallback(callback)
    }

    private fun stopFrameWatch() {
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
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
}
