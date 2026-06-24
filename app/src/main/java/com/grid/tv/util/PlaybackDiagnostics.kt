package com.grid.tv.util

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.media3.common.PlaybackException
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.player.devicePlaybackCapabilities
import com.grid.tv.player.isCodecCapabilityError
import com.grid.tv.player.isRecoverableForPlayback

/**
 * Structured diagnostics for Chromecast / Android TV vs emulator playback issues.
 * Filter logcat: `adb logcat -s PlaybackDiag VodCatalog JsonParseMetrics`
 */
object PlaybackDiagnostics {
    private const val TAG = "PlaybackDiag"

    fun logDeviceProfile(context: Context) {
        val caps = context.devicePlaybackCapabilities()
        val lowEnd = LowEndDeviceMode.profile(context)
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = memorySnapshot()
        Log.i(
            TAG,
            "DEVICE_PROFILE model=${Build.MODEL} device=${Build.DEVICE} " +
                "sdk=${Build.VERSION.SDK_INT} tv=${caps.isTelevision} " +
                "emulator=${caps.isEmulator} lowEnd=${caps.isLowEndDevice} " +
                "ramMb=${caps.totalRamMb} memoryClass=${am?.memoryClass ?: -1} " +
                "lowRam=${am?.isLowRamDevice == true} startup=${caps.startupPriority} " +
                "coilMemMb=${lowEnd.coilMemoryCacheBytes / 1024 / 1024} " +
                "usedHeapMb=${mem.usedMb} maxHeapMb=${mem.maxMb} heapPct=${mem.usedPercent}"
        )
        logCodecSupport()
    }

    fun logMemory(stage: String) {
        val mem = memorySnapshot()
        Log.i(
            TAG,
            "MEMORY stage=$stage usedHeapMb=${mem.usedMb} maxHeapMb=${mem.maxMb} " +
                "heapPct=${mem.usedPercent}"
        )
    }

    fun logPlaybackError(
        owner: String,
        error: PlaybackException,
        streamUrl: String? = null,
        channelId: Long? = null
    ) {
        val codec = error.isCodecCapabilityError()
        val recoverable = error.isRecoverableForPlayback()
        Log.e(
            TAG,
            "PLAYBACK_ERROR owner=$owner code=${error.errorCodeName()} " +
                "codecCapability=$codec recoverable=$recoverable " +
                "channelId=$channelId url=${streamUrl?.take(120)} " +
                "message=${error.message}"
        )
        logCauseChain(error)
        if (codec) {
            Log.e(TAG, "PLAYBACK_ERROR_HINT owner=$owner likely HW decoder / format mismatch (H264/H265/AAC)")
        }
    }

    fun logVodNetworkFetch(url: String, httpCode: Int, rawBytes: Int, elapsedMs: Long) {
        Log.i(
            TAG,
            "VOD_NETWORK_FETCH http=$httpCode bytes=$rawBytes elapsedMs=$elapsedMs " +
                "url=${url.take(120)}"
        )
    }

    fun logVodNetworkFailure(url: String, error: Throwable) {
        Log.e(
            TAG,
            "VOD_NETWORK_FAILURE type=${error.javaClass.simpleName} message=${error.message} " +
                "url=${url.take(120)}",
            error
        )
    }

    private fun logCodecSupport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS)
        val mimeTypes = listOf(
            "video/avc",
            "video/hevc",
            "audio/mp4a-latm",
            "audio/mpeg"
        )
        for (mime in mimeTypes.distinct()) {
            val decoders = codecs.codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } }
                .take(3)
                .joinToString(",") { it.name }
            Log.i(TAG, "CODEC_DECODER mime=$mime supported=${decoders.isNotEmpty()} names=$decoders")
        }
    }

    private fun logCauseChain(error: Throwable, depth: Int = 0) {
        if (depth > 6) return
        val indent = "  ".repeat(depth)
        Log.e(
            TAG,
            "PLAYBACK_CAUSE$indent ${error.javaClass.name}: ${error.message}"
        )
        error.cause?.let { logCauseChain(it, depth + 1) }
    }

    private fun PlaybackException.errorCodeName(): String =
        when (errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "FORMAT_UNSUPPORTED"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_INIT_FAILED"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "NETWORK_FAILED"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "NETWORK_TIMEOUT"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "HTTP_BAD_STATUS"
            PlaybackException.ERROR_CODE_TIMEOUT -> "TIMEOUT"
            else -> "CODE_$errorCode"
        }

    internal data class MemorySnapshot(val usedMb: Long, val maxMb: Long, val usedPercent: Int)

    internal fun memorySnapshotForLog(): MemorySnapshot = memorySnapshot()

    private fun memorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory().coerceAtLeast(1L)
        return MemorySnapshot(
            usedMb = used / (1024 * 1024),
            maxMb = max / (1024 * 1024),
            usedPercent = ((used.toDouble() / max.toDouble()) * 100).toInt()
        )
    }
}
