package com.grid.tv.feature.startup

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Hilt dependency-construction probe for MainActivity field injection.
 * Filter logcat: `adb logcat -s STARTUP_TRACE`
 *
 * Each [traceCreate] span records inclusive time (nested creations stack as children).
 */
object StartupDependencyProbe {

    const val TAG = StartupTrace.TAG

    private data class Frame(
        val name: String,
        val startNs: Long,
        val thread: String,
        val mainThread: Boolean
    )

    data class CreationRecord(
        val name: String,
        val parent: String?,
        val startNs: Long,
        val endNs: Long,
        val durationMs: Long,
        val thread: String,
        val mainThread: Boolean,
        val startOffsetMs: Long,
        val endOffsetMs: Long,
        val blockingHints: Set<String> = emptySet(),
        val phase: String? = null
    )

    private val stack = ThreadLocal.withInitial { ArrayDeque<Frame>() }
    private val records = mutableListOf<CreationRecord>()
    private val recordsLock = Any()

    @Volatile
    private var injectionActive = false

    @Volatile
    private var injectionStartNs: Long = 0L

    @Volatile
    private var injectionEndNs: Long = 0L

    @Volatile
    private var injectionDurationMs: Long = 0L

    private val treeDumped = AtomicBoolean(false)
    private val playbackTreeDumped = AtomicBoolean(false)
    private val spanId = AtomicLong(0L)

    @Volatile
    private var playbackGraphActive = false

    @Volatile
    private var playbackGraphStartNs: Long = 0L

    @Volatile
    private var playbackGraphWallMs: Long = 0L

    private val playbackRecords = mutableListOf<CreationRecord>()

    private fun isMainThread(): Boolean =
        runCatching { Looper.getMainLooper().thread == Thread.currentThread() }.getOrDefault(false)

    private fun elapsedSinceProcessStartMs(): Long = StartupTiming.elapsedSinceProcessStartMs()

    private fun log(message: String) {
        Log.i(
            TAG,
            "STARTUP_TRACE: $message " +
                "[thread=${Thread.currentThread().name} main=${isMainThread()} t+${elapsedSinceProcessStartMs()}ms]"
        )
    }

    fun beginActivityInjection() {
        injectionStartNs = System.nanoTime()
        injectionActive = true
        stack.get().clear()
        log("Hilt Activity injection START (MainActivity.super.onCreate)")
    }

    fun endActivityInjection() {
        injectionEndNs = System.nanoTime()
        injectionDurationMs = (injectionEndNs - injectionStartNs) / 1_000_000L
        injectionActive = false
        log("Hilt Activity injection END — wall ${injectionDurationMs}ms")
        dumpDependencyTreeOnce()
    }

    fun beginPlaybackGraph(trigger: String) {
        playbackGraphStartNs = System.nanoTime()
        playbackGraphActive = true
        playbackTreeDumped.set(false)
        synchronized(recordsLock) {
            playbackRecords.clear()
        }
        stack.get().clear()
        log("Playback graph resolution START ($trigger)")
    }

    fun endPlaybackGraph() {
        playbackGraphEndNs = System.nanoTime()
        playbackGraphWallMs = (playbackGraphEndNs - playbackGraphStartNs) / 1_000_000L
        playbackGraphActive = false
        log("Playback graph resolution END — wall ${playbackGraphWallMs}ms")
        dumpPlaybackGraphTreeOnce()
    }

    @Volatile
    private var playbackGraphEndNs: Long = 0L

    fun <T> traceCreate(
        name: String,
        blockingHints: Set<String> = emptySet(),
        phase: String? = null,
        block: () -> T
    ): T {
        val thread = Thread.currentThread().name
        val main = isMainThread()
        val parent = stack.get().peekLast()?.name
        val startNs = System.nanoTime()
        val startOffsetMs = elapsedSinceProcessStartMs()
        val id = spanId.incrementAndGet()
        stack.get().addLast(Frame(name, startNs, thread, main))
        log(">>> ctor start #$id $name parent=${parent ?: "MainActivity"} thread=$thread main=$main")
        return try {
            block()
        } finally {
            val endNs = System.nanoTime()
            val endOffsetMs = elapsedSinceProcessStartMs()
            val durationMs = (endNs - startNs) / 1_000_000L
            stack.get().pollLast()
            val record = CreationRecord(
                name = name,
                parent = parent,
                startNs = startNs,
                endNs = endNs,
                durationMs = durationMs,
                thread = thread,
                mainThread = main,
                startOffsetMs = startOffsetMs,
                endOffsetMs = endOffsetMs,
                blockingHints = blockingHints,
                phase = phase
            )
            synchronized(recordsLock) {
                when {
                    injectionActive -> records += record
                    playbackGraphActive -> playbackRecords += record
                }
            }
            log(
                "<<< ctor end #$id $name ${durationMs}ms " +
                    "parent=${parent ?: "MainActivity"} thread=$thread main=$main"
            )
        }
    }

    /** Marks @Inject singleton init when no @Provides wrapper exists. */
    fun traceInjectedInit(
        name: String,
        blockingHints: Set<String> = emptySet(),
        phase: String? = "ctor-init",
        block: () -> Unit = {}
    ) {
        traceCreate(name, blockingHints = blockingHints, phase = phase) {
            block()
        }
    }

    fun <T> traceInitPhase(
        owner: String,
        phase: String,
        blockingHints: Set<String> = emptySet(),
        block: () -> T
    ): T = traceCreate("$owner.$phase", blockingHints = blockingHints, phase = phase, block = block)

    fun dumpDependencyTreeOnce() {
        if (!treeDumped.compareAndSet(false, true)) return
        val allRecords = synchronized(recordsLock) { records.toList() }

        log("=== HILT DEPENDENCY TREE (MainActivity injection) ===")
        log("Injection wall time: ${injectionDurationMs}ms")
        log("Traced construction spans: ${allRecords.size}")

        val childrenByParent = linkedMapOf<String?, MutableList<CreationRecord>>()
        allRecords.forEach { record ->
            childrenByParent.getOrPut(record.parent) { mutableListOf() }.add(record)
        }
        childrenByParent.values.forEach { list ->
            list.sortBy { it.startNs }
        }

        fun printNode(parentLabel: String?, depth: Int) {
            val children = childrenByParent[parentLabel].orEmpty()
            children.forEach { record ->
                val indent = "│   ".repeat(depth.coerceAtLeast(0))
                val branch = if (depth == 0) "├── " else "│   ├── "
                val prefix = if (depth == 0) "├── " else indent.dropLast(4) + "├── "
                val line = buildString {
                    append(prefix)
                    append(record.name)
                    append(" (")
                    append(record.durationMs)
                    append(" ms, thread=")
                    append(record.thread)
                    append(if (record.mainThread) ", main" else "")
                    append(")")
                }
                log(line)
                printNode(record.name, depth + 1)
            }
        }

        log("MainActivity")
        printNode(null, 0)

        val top = allRecords.sortedByDescending { it.durationMs }.take(3)
        log("TOP_3_SLOWEST_INJECTED_DEPENDENCIES:")
        top.forEachIndexed { index, record ->
            log(
                "  ${index + 1}. ${record.name} — ${record.durationMs}ms " +
                    "(parent=${record.parent ?: "MainActivity"}, thread=${record.thread}, main=${record.mainThread})"
            )
        }

        val tracedInclusiveRootMs = childrenByParent[null]?.maxOfOrNull { it.durationMs } ?: 0L
        val tracedSumExclusiveMs = allRecords.sumOf { it.durationMs }
        log(
            "ACCOUNTING: injection_wall=${injectionDurationMs}ms " +
                "root_spans=${childrenByParent[null]?.size ?: 0} " +
                "sum_all_span_durations=${tracedSumExclusiveMs}ms " +
                "(nested spans overlap; wall time ~= max root inclusive path)"
        )
        log("=== END HILT DEPENDENCY TREE ===")
    }

    fun dumpPlaybackGraphTreeOnce() {
        if (!playbackTreeDumped.compareAndSet(false, true)) return
        val allRecords = synchronized(recordsLock) { playbackRecords.toList() }
        if (allRecords.isEmpty()) {
            log("=== PLAYBACK GRAPH TREE (empty — no traced spans) ===")
            return
        }

        log("=== PLAYBACK GRAPH TREE (onStart Lazy.get) ===")
        log("Playback graph wall time: ${playbackGraphWallMs}ms")
        log("Traced construction spans: ${allRecords.size}")

        val childrenByParent = linkedMapOf<String?, MutableList<CreationRecord>>()
        allRecords.forEach { record ->
            childrenByParent.getOrPut(record.parent) { mutableListOf() }.add(record)
        }
        childrenByParent.values.forEach { list -> list.sortBy { it.startNs } }

        data class NodeStats(
            val record: CreationRecord,
            val inclusiveMs: Long,
            val exclusiveMs: Long
        )

        fun inclusiveMs(name: String): Long =
            allRecords.filter { it.name == name }.maxOfOrNull { it.durationMs } ?: 0L

        fun exclusiveMs(record: CreationRecord): Long {
            val childSum = childrenByParent[record.name].orEmpty().sumOf { it.durationMs }
            return (record.durationMs - childSum).coerceAtLeast(0L)
        }

        fun printNode(parentLabel: String?, depth: Int) {
            childrenByParent[parentLabel].orEmpty().forEach { record ->
                val indent = "│   ".repeat(depth.coerceAtLeast(0))
                val prefix = if (depth == 0) "├── " else indent.dropLast(4) + "├── "
                val excl = exclusiveMs(record)
                val incl = inclusiveMs(record.name)
                val hints = if (record.blockingHints.isEmpty()) "" else " hints=${record.blockingHints.joinToString(",")}"
                val phase = record.phase?.let { " phase=$it" } ?: ""
                log(
                    "$prefix${record.name} (excl=${excl}ms incl=${incl}ms, " +
                        "thread=${record.thread}${if (record.mainThread) ", main" else ""}$phase$hints)"
                )
                printNode(record.name, depth + 1)
            }
        }

        log("AppPlayerLifecycleCoordinator (root)")
        printNode(null, 0)

        val ranked = allRecords
            .map { NodeStats(it, inclusiveMs(it.name), exclusiveMs(it)) }
            .distinctBy { it.record.name }
            .sortedByDescending { it.inclusiveMs }

        log("PLAYBACK_GRAPH_RANKED_BY_INCLUSIVE:")
        ranked.take(15).forEachIndexed { index, stats ->
            log(
                "  ${index + 1}. ${stats.record.name} — incl=${stats.inclusiveMs}ms excl=${stats.exclusiveMs}ms " +
                    "(parent=${stats.record.parent ?: "root"}, main=${stats.record.mainThread})"
            )
        }

        val mainThreadExcl = allRecords.filter { it.mainThread }.sumOf { exclusiveMs(it) }
        log(
            "ACCOUNTING: playback_wall=${playbackGraphWallMs}ms " +
                "sum_exclusive_main=${mainThreadExcl}ms " +
                "sum_all_span_durations=${allRecords.sumOf { it.durationMs }}ms"
        )
        log("=== END PLAYBACK GRAPH TREE ===")
    }
}
