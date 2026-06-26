package com.grid.tv.player

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.grid.tv.util.PerformanceAudit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class DecoderPressureLevel {
    NORMAL,
    WARN,
    CRITICAL
}

/**
 * Tracks concurrent ExoPlayer / MediaCodec sessions and PlayerView surface bindings.
 * Logs when Chromecast-class devices approach hardware decoder exhaustion.
 */
class DecoderPressureTracker() {
    private val profile = DeviceDecoderLimits.profile()
    private val lock = Any()

    private val sessions = ConcurrentHashMap<Int, DecoderSession>()
    private val surfaces = ConcurrentHashMap<String, SurfaceBinding>()
    private val activeVideoDecoders = ConcurrentHashMap.newKeySet<Int>()
    private val analyticsListeners = ConcurrentHashMap<Int, AnalyticsListener>()

    private val maxConcurrentPlayers = AtomicInteger(0)
    private val maxConcurrentSurfaces = AtomicInteger(0)
    private val maxActiveVideoDecoders = AtomicInteger(0)
    private val totalDroppedFrames = AtomicInteger(0)
    private val totalFormatSwitches = AtomicInteger(0)

    @Volatile
    private var lastPressureLevel = DecoderPressureLevel.NORMAL

    data class DecoderSession(
        val owner: String,
        val playerHash: Int,
        val registeredAtMs: Long,
        var decoderName: String? = null,
        var videoWidth: Int = 0,
        var videoHeight: Int = 0,
        var videoBitrate: Int = 0,
        var droppedFrames: Int = 0,
        var formatSwitchCount: Int = 0,
        var playWhenReady: Boolean = false,
        var playbackState: Int = 0
    )

    data class SurfaceBinding(
        val owner: String,
        val playerHash: Int,
        val surfaceType: String,
        val attachedAtMs: Long
    )

    data class Snapshot(
        val playerCount: Int,
        val surfaceCount: Int,
        val activeVideoDecoderCount: Int,
        val maxConcurrentPlayers: Int,
        val maxConcurrentSurfaces: Int,
        val maxActiveVideoDecoders: Int,
        val totalDroppedFrames: Int,
        val totalFormatSwitches: Int,
        val pressureLevel: DecoderPressureLevel,
        val owners: List<String>
    )

    @UnstableApi
    fun registerPlayer(owner: String, player: ExoPlayer) {
        val hash = System.identityHashCode(player)
        val session = DecoderSession(
            owner = owner,
            playerHash = hash,
            registeredAtMs = System.currentTimeMillis(),
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState
        )
        sessions[hash] = session

        val listener = createAnalyticsListener(hash)
        analyticsListeners[hash] = listener
        player.addAnalyticsListener(listener)
        player.addListener(
            object : androidx.media3.common.Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    sessions[hash]?.playWhenReady = playWhenReady
                    evaluatePressure("playWhenReady owner=$owner")
                }

                override fun onPlaybackStateChanged(state: Int) {
                    sessions[hash]?.playbackState = state
                }
            }
        )

        updateMaxConcurrentPlayers()
        PerformanceAudit.logDecoderSession("REGISTER", owner, hash, snapshotLocked())
        evaluatePressure("register owner=$owner")
        Log.i(TAG, "register owner=$owner hash=$hash activePlayers=${sessions.size}")
    }

    fun unregisterPlayer(player: ExoPlayer) {
        val hash = System.identityHashCode(player)
        analyticsListeners.remove(hash)?.let { player.removeAnalyticsListener(it) }
        val removed = sessions.remove(hash)
        activeVideoDecoders.remove(hash)
        surfaces.entries.removeIf { it.value.playerHash == hash }
        updateMaxConcurrentPlayers()
        updateMaxConcurrentSurfaces()
        removed?.let {
            PerformanceAudit.logDecoderSession("UNREGISTER", it.owner, hash, snapshotLocked())
            evaluatePressure("unregister owner=${it.owner}")
            Log.i(TAG, "unregister owner=${it.owner} hash=$hash activePlayers=${sessions.size}")
        }
    }

    fun onSurfaceAttached(owner: String, player: ExoPlayer?, surfaceType: String) {
        val playerHash = player?.let { System.identityHashCode(it) } ?: -1
        val key = surfaceKey(owner, playerHash)
        surfaces[key] = SurfaceBinding(
            owner = owner,
            playerHash = playerHash,
            surfaceType = surfaceType,
            attachedAtMs = System.currentTimeMillis()
        )
        updateMaxConcurrentSurfaces()
        PerformanceAudit.logDecoderSurface("ATTACH", owner, playerHash, surfaceType, surfaces.size)
        evaluatePressure("surfaceAttach owner=$owner type=$surfaceType")
        Log.d(TAG, "surface ATTACH owner=$owner type=$surfaceType playerHash=$playerHash count=${surfaces.size}")
    }

    fun onSurfaceDetached(owner: String, player: ExoPlayer?) {
        val playerHash = player?.let { System.identityHashCode(it) } ?: -1
        val key = surfaceKey(owner, playerHash)
        surfaces.remove(key)
        updateMaxConcurrentSurfaces()
        PerformanceAudit.logDecoderSurface("DETACH", owner, playerHash, "", surfaces.size)
        evaluatePressure("surfaceDetach owner=$owner")
        Log.d(TAG, "surface DETACH owner=$owner playerHash=$playerHash count=${surfaces.size}")
    }

    fun snapshot(): Snapshot = synchronized(lock) { snapshotLocked() }

    internal fun resetForTests() {
        sessions.clear()
        surfaces.clear()
        activeVideoDecoders.clear()
        analyticsListeners.clear()
        maxConcurrentPlayers.set(0)
        maxConcurrentSurfaces.set(0)
        maxActiveVideoDecoders.set(0)
        totalDroppedFrames.set(0)
        totalFormatSwitches.set(0)
        lastPressureLevel = DecoderPressureLevel.NORMAL
    }

    @UnstableApi
    private fun createAnalyticsListener(playerHash: Int): AnalyticsListener {
        return object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                sessions[playerHash]?.droppedFrames = (sessions[playerHash]?.droppedFrames ?: 0) + droppedFrames
                totalDroppedFrames.addAndGet(droppedFrames)
                PerformanceAudit.logDecoderDroppedFrames(playerHash, droppedFrames, elapsedMs)
                if (droppedFrames > 0) {
                    evaluatePressure("droppedFrames=$droppedFrames elapsedMs=$elapsedMs")
                }
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                sessions[playerHash]?.let { session ->
                    session.formatSwitchCount += 1
                    session.videoWidth = format.width
                    session.videoHeight = format.height
                    session.videoBitrate = format.bitrate
                }
                totalFormatSwitches.incrementAndGet()
                PerformanceAudit.logDecoderFormatSwitch(
                    playerHash = playerHash,
                    width = format.width,
                    height = format.height,
                    bitrate = format.bitrate
                )
                evaluatePressure("formatSwitch ${format.width}x${format.height} bitrate=${format.bitrate}")
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                activeVideoDecoders.add(playerHash)
                sessions[playerHash]?.decoderName = decoderName
                updateMaxActiveVideoDecoders()
                PerformanceAudit.logDecoderInitialized(
                    playerHash = playerHash,
                    decoderName = decoderName,
                    initMs = initializationDurationMs
                )
                evaluatePressure("decoderInit name=$decoderName initMs=$initializationDurationMs")
            }

            override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
                activeVideoDecoders.remove(playerHash)
                updateMaxActiveVideoDecoders()
                PerformanceAudit.logDecoderReleased(playerHash, decoderName)
                evaluatePressure("decoderReleased name=$decoderName")
            }
        }
    }

    private fun evaluatePressure(reason: String) {
        val snap = snapshotLocked()
        val level = computePressureLevel(snap)
        if (level != lastPressureLevel || level != DecoderPressureLevel.NORMAL) {
            lastPressureLevel = level
            PerformanceAudit.logDecoderPressure(
                level = level,
                reason = reason,
                snapshot = snap,
                profile = profile
            )
            when (level) {
                DecoderPressureLevel.WARN ->
                    Log.w(TAG, "DECODER_PRESSURE WARN reason=$reason $snap")
                DecoderPressureLevel.CRITICAL ->
                    Log.e(TAG, "DECODER_PRESSURE CRITICAL reason=$reason $snap")
                DecoderPressureLevel.NORMAL -> Unit
            }
        }
    }

    private fun computePressureLevel(snap: Snapshot): DecoderPressureLevel {
        val decoderLoad = maxOf(
            snap.activeVideoDecoderCount,
            snap.playerCount
        )
        val surfaceLoad = snap.surfaceCount
        return when {
            decoderLoad >= profile.criticalConcurrentDecoders ||
                surfaceLoad >= profile.criticalConcurrentSurfaces ->
                DecoderPressureLevel.CRITICAL
            decoderLoad >= profile.warnConcurrentDecoders ||
                surfaceLoad >= profile.warnConcurrentSurfaces ->
                DecoderPressureLevel.WARN
            else -> DecoderPressureLevel.NORMAL
        }
    }

    private fun snapshotLocked(): Snapshot {
        val owners = sessions.values.map { it.owner }.distinct()
        return Snapshot(
            playerCount = sessions.size,
            surfaceCount = surfaces.size,
            activeVideoDecoderCount = activeVideoDecoders.size,
            maxConcurrentPlayers = maxConcurrentPlayers.get(),
            maxConcurrentSurfaces = maxConcurrentSurfaces.get(),
            maxActiveVideoDecoders = maxActiveVideoDecoders.get(),
            totalDroppedFrames = totalDroppedFrames.get(),
            totalFormatSwitches = totalFormatSwitches.get(),
            pressureLevel = computePressureLevel(
                Snapshot(
                    playerCount = sessions.size,
                    surfaceCount = surfaces.size,
                    activeVideoDecoderCount = activeVideoDecoders.size,
                    maxConcurrentPlayers = maxConcurrentPlayers.get(),
                    maxConcurrentSurfaces = maxConcurrentSurfaces.get(),
                    maxActiveVideoDecoders = maxActiveVideoDecoders.get(),
                    totalDroppedFrames = totalDroppedFrames.get(),
                    totalFormatSwitches = totalFormatSwitches.get(),
                    pressureLevel = DecoderPressureLevel.NORMAL,
                    owners = owners
                )
            ),
            owners = owners
        )
    }

    private fun updateMaxConcurrentPlayers() {
        maxConcurrentPlayers.updateAndGet { current -> maxOf(current, sessions.size) }
    }

    private fun updateMaxConcurrentSurfaces() {
        maxConcurrentSurfaces.updateAndGet { current -> maxOf(current, surfaces.size) }
    }

    private fun updateMaxActiveVideoDecoders() {
        maxActiveVideoDecoders.updateAndGet { current -> maxOf(current, activeVideoDecoders.size) }
    }

    private fun surfaceKey(owner: String, playerHash: Int): String = "$owner:$playerHash"

    companion object {
        const val TAG = "DecoderPressure"
    }
}
