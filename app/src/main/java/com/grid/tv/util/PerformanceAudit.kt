package com.grid.tv.util

import android.os.Debug
import android.os.Looper
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer

/**
 * Temporary instrumentation for performance root-cause verification.
 * Filter logcat with tag `PerfAudit` or `VodCatalogPipeline`.
 * Set [ENABLED] = false to disable after audit completes.
 */
object PerformanceAudit {
    const val TAG = "PerfAudit"
    const val ENABLED = true

    private inline fun auditLog(block: () -> Unit) {
        if (!ENABLED) return
        runCatching { block() }
    }

    fun isMainThread(): Boolean =
        runCatching { Looper.getMainLooper().thread == Thread.currentThread() }.getOrDefault(false)

    fun logPlayerLifecycle(
        event: String,
        player: ExoPlayer?,
        generation: Int,
        channelId: Long? = null
    ) {
        if (!ENABLED) return
        val hash = player?.let { System.identityHashCode(it) } ?: -1
        auditLog {
            Log.i(
                TAG,
                "player $event gen=$generation hash=$hash channelId=$channelId thread=main:${isMainThread()} " +
                    memorySnapshotSuffix()
            )
        }
    }

    fun logPlayerMemoryRelease(
        reason: String,
        phase: String,
        generation: Int,
        channelId: Long? = null
    ) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "playerRelease reason=$reason phase=$phase gen=$generation channelId=$channelId " +
                    memorySnapshotSuffix()
            )
        }
    }

    private fun memorySnapshotSuffix(): String {
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return "heapUsedMb=$heapUsedMb nativeMb=$nativeMb"
    }

    fun logPlayerBufferSnapshot(
        label: String,
        player: ExoPlayer,
        maxBufferMs: Long,
        minBufferMs: Long,
        profileName: String = "unknown"
    ) {
        if (!ENABLED) return
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val bufferAheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
        auditLog {
            Log.i(
                TAG,
                "bufferSnapshot label=$label profile=$profileName hash=${System.identityHashCode(player)} " +
                    "state=${player.playbackState} playing=${player.isPlaying} " +
                    "bufferAheadMs=$bufferAheadMs " +
                    "configuredMinMs=$minBufferMs configuredMaxMs=$maxBufferMs " +
                    "heapUsedMb=$heapUsedMb nativeMb=$nativeMb"
            )
        }
    }

    fun logBufferProfileApplied(
        profile: com.grid.tv.player.IptvBufferProfiles.Durations,
        legacyBaseline: com.grid.tv.player.IptvBufferProfiles.Durations
    ) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "bufferProfile APPLIED ${profile.toLogString()} " +
                    "legacyBaseline=${legacyBaseline.toLogString()}"
            )
        }
    }

    fun logBufferTuneStarted(channelId: Long, profileName: String) {
        if (!ENABLED) return
        auditLog {
            Log.d(TAG, "bufferTune START channelId=$channelId profile=$profileName")
        }
    }

    fun logPlaybackFactoryCreated(
        stackId: Int,
        okHttpClientId: Int,
        reason: String,
        config: com.grid.tv.data.network.NetworkPlaybackConfig?
    ) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "playbackFactory CREATE stackId=$stackId okHttpClientId=$okHttpClientId " +
                    "reason=$reason config=${config?.toLogLine() ?: "default"} " +
                    memorySnapshotSuffix()
            )
        }
    }

    fun logPlaybackFactoryReuse(stackId: Int, okHttpClientId: Int) {
        if (!ENABLED) return
        auditLog {
            Log.d(
                TAG,
                "playbackFactory REUSE stackId=$stackId okHttpClientId=$okHttpClientId"
            )
        }
    }

    fun logPlaybackFactoryInvalidated(reason: String) {
        if (!ENABLED) return
        auditLog {
            Log.i(TAG, "playbackFactory INVALIDATE reason=$reason")
        }
    }

    fun logBufferStartupReady(elapsedMs: Long, profileName: String, playerHash: Int) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "bufferStartup READY profile=$profileName elapsedMs=$elapsedMs playerHash=$playerHash"
            )
        }
    }

    private var rebufferCount = 0

    fun logRebufferEvent(profileName: String, playerHash: Int) {
        if (!ENABLED) return
        rebufferCount++
        auditLog {
            Log.w(
                TAG,
                "bufferRebuffer count=$rebufferCount profile=$profileName playerHash=$playerHash"
            )
        }
    }

    internal fun rebufferCountForTests(): Int = rebufferCount

    internal fun resetBufferMetricsForTests() {
        rebufferCount = 0
    }

    fun logProgrammeLookup(
        caller: String,
        programCount: Int,
        resultCount: Int,
        elapsedMs: Long
    ) {
        if (!ENABLED) return
        auditLog {
            Log.d(
                TAG,
                "programmesForChannel caller=$caller thread=main:${isMainThread()} " +
                    "inputPrograms=$programCount result=$resultCount elapsedMs=$elapsedMs"
            )
        }
    }

    fun logProgrammeIndexBuild(channelCount: Int, programCount: Int, elapsedMs: Long) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "programmeIndex BUILD thread=main:${isMainThread()} " +
                    "channels=$channelCount programs=$programCount buildMs=$elapsedMs " +
                    "lookupComplexity=O(1)"
            )
        }
    }

    fun logProgrammeIndexLookup(channelId: Long, resultCount: Int, elapsedNs: Long) {
        if (!ENABLED) return
        auditLog {
            Log.d(
                TAG,
                "programmeIndex LOOKUP channelId=$channelId result=$resultCount " +
                    "elapsedNs=$elapsedNs thread=main:${isMainThread()}"
            )
        }
    }

    private var epgRecompositionCount = 0

    fun logEpgRecomposition(label: String) {
        if (!ENABLED) return
        epgRecompositionCount++
        auditLog {
            Log.d(TAG, "epgRecompose label=$label count=$epgRecompositionCount thread=main:${isMainThread()}")
        }
    }

    internal fun epgRecompositionCountForTests(): Int = epgRecompositionCount

    internal fun resetEpgMetricsForTests() {
        epgRecompositionCount = 0
    }

    private var focusNavigationEvents = 0
    private var focusSideEffectBlocks = 0

    fun logFocusNavigation(label: String, channelIndex: Int) {
        if (!ENABLED) return
        focusNavigationEvents++
        auditLog {
            Log.d(
                TAG,
                "focusNav label=$label channelIndex=$channelIndex count=$focusNavigationEvents " +
                    "thread=main:${isMainThread()}"
            )
        }
    }

    fun logFocusSideEffectBlocked(label: String, reason: String) {
        if (!ENABLED) return
        focusSideEffectBlocks++
        auditLog {
            Log.w(
                TAG,
                "focusNav BLOCKED label=$label reason=$reason blocks=$focusSideEffectBlocks"
            )
        }
    }

    internal fun resetFocusNavigationMetricsForTests() {
        focusNavigationEvents = 0
        focusSideEffectBlocks = 0
    }

    fun logGridKeyFilter(keyName: String, channelIndex: Int, totalProgramCount: Int) {
        if (!ENABLED) return
        auditLog {
            Log.d(
                TAG,
                "handleGridKey key=$keyName channelIndex=$channelIndex " +
                    "programmeIndexLookup thread=main:${isMainThread()} catalogPrograms=$totalProgramCount"
            )
        }
    }

    private var zapSequence = 0
    private var pendingZapStartMs = 0L
    private var pendingZapChannelId: Long? = null
    private val zapReadyTimesMs = mutableListOf<Long>()
    private var playbackStatusEmissionsSinceZap = 0
    private var playbackUiEmissionsSinceZap = 0
    private var tunePipelineStartsSinceZap = 0
    private var tuneSuppressedSinceZap = 0
    private var totalPlaybackStatusEmissions = 0
    private var totalPlaybackUiEmissions = 0
    private var totalTunePipelineStarts = 0
    private var totalTuneSuppressed = 0

    fun logTuneSuppressed(
        reason: String,
        channelId: Long,
        streamUrl: String
    ) {
        if (!ENABLED) return
        totalTuneSuppressed++
        tuneSuppressedSinceZap++
        auditLog {
            Log.i(
                TAG,
                "tune SUPPRESS reason=$reason channelId=$channelId " +
                    "urlHash=${streamUrl.hashCode()} zapWindow=$tuneSuppressedSinceZap " +
                    "totalSuppressed=$totalTuneSuppressed"
            )
        }
    }

    fun logTuneReuseSkipFlush(channelId: Long, streamUrl: String) {
        if (!ENABLED) return
        auditLog {
            Log.d(
                TAG,
                "tune REUSE_SKIP_FLUSH channelId=$channelId urlHash=${streamUrl.hashCode()}"
            )
        }
    }

    fun logTunePipelineStart(channelId: Long, streamUrl: String, configureFailover: Boolean) {
        if (!ENABLED) return
        totalTunePipelineStarts++
        tunePipelineStartsSinceZap++
        auditLog {
            Log.i(
                TAG,
                "tune PIPELINE_START channelId=$channelId urlHash=${streamUrl.hashCode()} " +
                    "failover=$configureFailover zapWindow=$tunePipelineStartsSinceZap " +
                    "totalStarts=$totalTunePipelineStarts"
            )
        }
    }

    fun logTunePipelineEnd(channelId: Long, streamUrl: String) {
        if (!ENABLED) return
        auditLog {
            Log.d(
                TAG,
                "tune PIPELINE_END channelId=$channelId urlHash=${streamUrl.hashCode()}"
            )
        }
    }

    fun recordPlaybackStatusEmission(status: com.grid.tv.player.StreamPlaybackStatus) {
        if (!ENABLED) return
        totalPlaybackStatusEmissions++
        playbackStatusEmissionsSinceZap++
        auditLog {
            Log.d(TAG, "playbackStatus emit status=$status total=$totalPlaybackStatusEmissions zapWindow=$playbackStatusEmissionsSinceZap")
        }
    }

    fun recordPlaybackUiEmission(ui: com.grid.tv.player.LivePlaybackUiState) {
        if (!ENABLED) return
        totalPlaybackUiEmissions++
        playbackUiEmissionsSinceZap++
    }

    fun beginZap(channelId: Long, playerHash: Int) {
        if (!ENABLED) return
        zapSequence++
        pendingZapStartMs = System.currentTimeMillis()
        pendingZapChannelId = channelId
        playbackStatusEmissionsSinceZap = 0
        playbackUiEmissionsSinceZap = 0
        tunePipelineStartsSinceZap = 0
        tuneSuppressedSinceZap = 0
        auditLog {
            Log.i(
                TAG,
                "zap BEGIN seq=$zapSequence channelId=$channelId playerHash=$playerHash"
            )
        }
    }

    fun completeZap(playerHash: Int, channelId: Long) {
        if (!ENABLED) return
        if (pendingZapChannelId != channelId) return
        val elapsedMs = (System.currentTimeMillis() - pendingZapStartMs).coerceAtLeast(0L)
        zapReadyTimesMs.add(elapsedMs)
        pendingZapChannelId = null
        auditLog {
            Log.i(
                TAG,
                "zap READY seq=$zapSequence channelId=$channelId elapsedMs=$elapsedMs " +
                    "playerHash=$playerHash totalZaps=${zapReadyTimesMs.size} " +
                    "statusEmits=$playbackStatusEmissionsSinceZap uiEmits=$playbackUiEmissionsSinceZap " +
                    "tuneStarts=$tunePipelineStartsSinceZap tuneSuppressed=$tuneSuppressedSinceZap"
            )
        }
        if (tunePipelineStartsSinceZap > 1 || tuneSuppressedSinceZap > 0) {
            auditLog {
                Log.w(
                    TAG,
                    "tune ANOMALY channelId=$channelId pipelineStarts=$tunePipelineStartsSinceZap " +
                        "suppressed=$tuneSuppressedSinceZap (expected 1 start, 0 suppressed per user zap)"
                )
            }
        }
        if (zapSequence % 10 == 0) {
            logZapMemoryCheckpoint(zapSequence)
        }
        if (zapSequence % 50 == 0) {
            logZapBatchSummary(50)
        }
    }

    fun logZapMemoryCheckpoint(zapCount: Int) {
        if (!ENABLED) return
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nativeMb = runCatching { Debug.getNativeHeapAllocatedSize() / (1024 * 1024) }.getOrDefault(-1L)
        auditLog {
            Log.i(
                TAG,
                "zap MEMORY checkpoint zaps=$zapCount heapUsedMb=$heapUsedMb nativeMb=$nativeMb"
            )
        }
    }

    fun logZapBatchSummary(lastN: Int) {
        if (!ENABLED) return
        val samples = zapReadyTimesMs.takeLast(lastN)
        if (samples.isEmpty()) return
        val avgMs = samples.average().toLong()
        val minMs = samples.minOrNull() ?: 0L
        val maxMs = samples.maxOrNull() ?: 0L
        auditLog {
            Log.i(
                TAG,
                "zap SUMMARY lastN=$lastN avgReadyMs=$avgMs minReadyMs=$minMs maxReadyMs=$maxMs"
            )
        }
    }

    fun logDecoderSession(
        event: String,
        owner: String,
        playerHash: Int,
        snapshot: com.grid.tv.player.DecoderPressureTracker.Snapshot
    ) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "decoderSession $event owner=$owner hash=$playerHash " +
                    "players=${snapshot.playerCount} surfaces=${snapshot.surfaceCount} " +
                    "activeDecoders=${snapshot.activeVideoDecoderCount} " +
                    "maxPlayers=${snapshot.maxConcurrentPlayers} maxSurfaces=${snapshot.maxConcurrentSurfaces} " +
                    "maxDecoders=${snapshot.maxActiveVideoDecoders} " +
                    "droppedFrames=${snapshot.totalDroppedFrames} formatSwitches=${snapshot.totalFormatSwitches}"
            )
        }
    }

    fun logDecoderSurface(event: String, owner: String, playerHash: Int, surfaceType: String, count: Int) {
        if (!ENABLED) return
        auditLog {
            Log.i(
                TAG,
                "decoderSurface $event owner=$owner playerHash=$playerHash type=$surfaceType activeSurfaces=$count"
            )
        }
    }

    fun logDecoderInitialized(playerHash: Int, decoderName: String, initMs: Long) {
        if (!ENABLED) return
        auditLog {
            Log.i(TAG, "decoderInit hash=$playerHash name=$decoderName initMs=$initMs")
        }
    }

    fun logDecoderReleased(playerHash: Int, decoderName: String) {
        if (!ENABLED) return
        auditLog {
            Log.i(TAG, "decoderRelease hash=$playerHash name=$decoderName")
        }
    }

    fun logDecoderDroppedFrames(playerHash: Int, droppedFrames: Int, elapsedMs: Long) {
        if (!ENABLED) return
        auditLog {
            Log.w(TAG, "decoderDropped hash=$playerHash dropped=$droppedFrames elapsedMs=$elapsedMs")
        }
    }

    fun logDecoderFormatSwitch(playerHash: Int, width: Int, height: Int, bitrate: Int) {
        if (!ENABLED) return
        auditLog {
            Log.d(TAG, "decoderFormatSwitch hash=$playerHash ${width}x$height bitrate=$bitrate")
        }
    }

    fun logDecoderPressure(
        level: com.grid.tv.player.DecoderPressureLevel,
        reason: String,
        snapshot: com.grid.tv.player.DecoderPressureTracker.Snapshot,
        profile: com.grid.tv.player.DeviceDecoderProfile
    ) {
        if (!ENABLED) return
        auditLog {
            val message =
                "decoderPressure level=$level device=${profile.deviceLabel} reason=$reason " +
                    "players=${snapshot.playerCount}/${profile.criticalConcurrentDecoders} " +
                    "surfaces=${snapshot.surfaceCount}/${profile.criticalConcurrentSurfaces} " +
                    "activeDecoders=${snapshot.activeVideoDecoderCount} " +
                    "maxPlayers=${snapshot.maxConcurrentPlayers} maxSurfaces=${snapshot.maxConcurrentSurfaces} " +
                    "maxDecoders=${snapshot.maxActiveVideoDecoders} " +
                    "droppedFrames=${snapshot.totalDroppedFrames} formatSwitches=${snapshot.totalFormatSwitches} " +
                    "owners=${snapshot.owners.joinToString()}"
            when (level) {
                com.grid.tv.player.DecoderPressureLevel.CRITICAL -> Log.e(TAG, message)
                com.grid.tv.player.DecoderPressureLevel.WARN -> Log.w(TAG, message)
                com.grid.tv.player.DecoderPressureLevel.NORMAL -> Log.i(TAG, message)
            }
        }
    }

    private val jsonParseCounts = linkedMapOf<String, Int>()
    private var jsonParseMainThreadViolations = 0

    fun logJsonParse(label: String, elapsedMs: Long, itemCount: Int) {
        if (!ENABLED) return
        jsonParseCounts[label.substringBefore(' ')] = (jsonParseCounts[label.substringBefore(' ')] ?: 0) + 1
        auditLog {
            val countSuffix = if (itemCount >= 0) " items=$itemCount" else ""
            Log.d(TAG, "jsonParse label=$label elapsedMs=$elapsedMs$countSuffix thread=main:${isMainThread()}")
        }
    }

    fun logJsonParseMainThreadViolation(label: String) {
        if (!ENABLED) return
        jsonParseMainThreadViolations++
        auditLog {
            Log.e(TAG, "jsonParse MAIN_THREAD_VIOLATION label=$label count=$jsonParseMainThreadViolations")
        }
    }

    /** Test-only reset for unit tests. */
    internal fun resetJsonParseMetricsForTests() {
        jsonParseCounts.clear()
        jsonParseMainThreadViolations = 0
    }

    /** Test-only reset for unit tests. */
    internal fun resetZapMetricsForTests() {
        zapSequence = 0
        pendingZapStartMs = 0L
        pendingZapChannelId = null
        zapReadyTimesMs.clear()
    }

    internal fun zapReadySamplesForTests(): List<Long> = zapReadyTimesMs.toList()
}

inline fun <T> runVodPipelineCatching(
    stage: String,
    block: () -> T
): Result<T> {
    return runCatching(block).also { result ->
        result.exceptionOrNull()?.let { error ->
            runCatching {
                Log.e(
                    "VodCatalogPipeline",
                    "SWALLOWED_EXCEPTION stage=$stage type=${error.javaClass.simpleName} message=${error.message}",
                    error
                )
            }
        }
    }
}
