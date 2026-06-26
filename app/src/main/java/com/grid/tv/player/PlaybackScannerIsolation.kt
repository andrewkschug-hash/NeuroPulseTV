package com.grid.tv.player

import com.grid.tv.feature.scanner.ChannelScanner
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Provider

/**
 * Ref-counted, asynchronous channel-scanner suspension for playback sessions.
 * Registry updates stay on the caller thread; scanner suspend/resume runs off the hot path.
 *
 * [ChannelScanner] is resolved only on first acquire/release — not during construction.
 */
class PlaybackScannerIsolation(
    private val channelScannerProvider: Provider<ChannelScanner>
) {
    private val lock = Any()

    private var acquireCount = 0

    private val defaultExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "playback-scanner-isolation").apply { isDaemon = true }
    }

    @Volatile
    internal var executorOverride: Executor? = null

    private fun executor(): Executor = executorOverride ?: defaultExecutor

    /** Resolves the scanner on first playback isolation — never at graph construction time. */
    fun getScanner(): ChannelScanner = channelScannerProvider.get()

    internal fun runPendingIsolationTasksForTests() {
        val direct = executorOverride as? QueuedTestExecutor ?: return
        direct.runPending()
    }

    /** Schedule scanner suspend when the first playback session acquires isolation. */
    fun acquireAsync() {
        val scheduleSuspend: Boolean
        synchronized(lock) {
            acquireCount++
            scheduleSuspend = acquireCount == 1
        }
        if (scheduleSuspend) {
            executor().execute { getScanner().setPlaybackScanSuspended(true) }
        }
    }

    /** Schedule scanner resume when the last playback session releases isolation. */
    fun releaseAsync() {
        val scheduleResume: Boolean
        synchronized(lock) {
            if (acquireCount <= 0) return
            acquireCount--
            scheduleResume = acquireCount == 0
        }
        if (scheduleResume) {
            executor().execute { getScanner().setPlaybackScanSuspended(false) }
        }
    }

    internal fun acquireCountForTests(): Int = synchronized(lock) { acquireCount }

    internal fun awaitIdleForTests() {
        runPendingIsolationTasksForTests()
        if (executorOverride is QueuedTestExecutor) return
        val latch = java.util.concurrent.CountDownLatch(1)
        executor().execute { latch.countDown() }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
    }

    /** Test-only executor that runs tasks only when [runPending] is called. */
    internal class QueuedTestExecutor : Executor {
        private val pending = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            synchronized(pending) { pending.addLast(command) }
        }

        fun runPending() {
            while (true) {
                val task = synchronized(pending) {
                    if (pending.isEmpty()) return
                    pending.removeFirst()
                }
                task.run()
            }
        }
    }
}
