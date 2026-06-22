package com.grid.tv.feature.recording

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Copies MPEG-TS bytes from a direct stream URL or HLS playlist into a .ts file.
 * Retries transient disconnects with exponential backoff and appends to the same output file.
 */
class TsStreamRecorder(
    private val client: OkHttpClient,
    private val outputFile: File,
    private val onHealthChanged: (RecordingHealth) -> Unit = {},
    private val shouldPauseForStorage: () -> Boolean = { false },
    private val onStorageFailure: () -> Unit = {}
) {
    @Volatile
    private var cancelled = false

    private var activeCall: okhttp3.Call? = null

    fun cancel() {
        cancelled = true
        activeCall?.cancel()
    }

    suspend fun record(streamUrl: String): RecordingOutcome {
        cancelled = false
        Log.i(TAG, "record() started streamUrl=$streamUrl output=${outputFile.absolutePath}")
        onHealthChanged(RecordingHealth.RECORDING)
        return try {
            if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                recordHls(streamUrl)
            } else {
                recordDirect(streamUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "record() failed streamUrl=$streamUrl output=${outputFile.absolutePath}", e)
            onHealthChanged(RecordingHealth.SIGNAL_LOST)
            outcome(signalLost = true)
        }
    }

    private suspend fun recordDirect(url: String): RecordingOutcome {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        var disconnectWindowStart: Long? = null
        var backoffMs = RecordingResilienceConfig.INITIAL_BACKOFF_MS
        var hadDropouts = false
        var totalGapPatchedMs = 0L
        var corruptedChunksSkipped = 0

        FileOutputStream(outputFile, false).use { output ->
            val progress = ByteProgressLogger("Direct stream")
            while (!cancelled && currentCoroutineContext().isActive) {
                var streamEnded = false
                try {
                    waitIfPaused()
                    onHealthChanged(RecordingHealth.RECORDING)
                    val request = Request.Builder().url(url).build()
                    val call = client.newCall(request)
                    activeCall = call
                    call.execute().use { response ->
                        Log.i(TAG, "Direct stream HTTP responseCode=${response.code}")
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}")
                        }
                        val body = response.body ?: throw IOException("Empty response body")
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            while (!cancelled && currentCoroutineContext().isActive) {
                                waitIfPaused()
                                val read = input.read(buffer)
                                if (read == -1) {
                                    streamEnded = true
                                    break
                                }
                                if (!isValidTsPayload(buffer, read)) {
                                    Log.w(TAG, "Skipping malformed TS chunk ($read bytes)")
                                    corruptedChunksSkipped++
                                    continue
                                }
                                if (isStorageExhausted()) {
                                    handleStorageFailure(progress)
                                    return outcome(hadDropouts = true, signalLost = true)
                                }
                                writeOrFail(output, buffer, read, progress)?.let { return it }
                                disconnectWindowStart = null
                                backoffMs = RecordingResilienceConfig.INITIAL_BACKOFF_MS
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (cancelled) break
                    Log.w(TAG, "Direct stream read error — will retry", e)
                    streamEnded = true
                }

                if (!streamEnded || cancelled) break

                hadDropouts = true
                val windowStart = disconnectWindowStart ?: System.currentTimeMillis().also {
                    disconnectWindowStart = it
                }
                if (System.currentTimeMillis() - windowStart > RecordingResilienceConfig.SIGNAL_LOST_WINDOW_MS) {
                    onHealthChanged(RecordingHealth.SIGNAL_LOST)
                    progress.logFinal()
                    return outcome(hadDropouts = true, signalLost = true)
                }

                onHealthChanged(RecordingHealth.RECONNECTING)
                patchGap(output, backoffMs)
                totalGapPatchedMs += backoffMs
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(RecordingResilienceConfig.MAX_BACKOFF_MS)
            }
            progress.logFinal()
        }

        onHealthChanged(RecordingHealth.RECORDING)
        return outcome(
            hadDropouts = hadDropouts,
            signalLost = false,
            gapPatchedMs = totalGapPatchedMs,
            corruptedChunksSkipped = corruptedChunksSkipped
        )
    }

    private suspend fun recordHls(playlistUrl: String): RecordingOutcome {
        Log.i(TAG, "Starting HLS recording playlistUrl=$playlistUrl")
        val mediaPlaylistUrl = resolveMediaPlaylistUrl(playlistUrl) ?: return outcome(signalLost = true)
        val downloaded = mutableSetOf<String>()
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        var disconnectWindowStart: Long? = null
        var backoffMs = RecordingResilienceConfig.INITIAL_BACKOFF_MS
        var hadDropouts = false
        var totalGapPatchedMs = 0L
        var corruptedChunksSkipped = 0

        FileOutputStream(outputFile, false).use { output ->
            val progress = ByteProgressLogger("HLS recording")
            while (!cancelled && currentCoroutineContext().isActive) {
                waitIfPaused()
                val playlist = fetchText(mediaPlaylistUrl)
                if (playlist == null) {
                    hadDropouts = true
                    val windowStart = disconnectWindowStart ?: System.currentTimeMillis().also {
                        disconnectWindowStart = it
                    }
                    if (System.currentTimeMillis() - windowStart > RecordingResilienceConfig.SIGNAL_LOST_WINDOW_MS) {
                        onHealthChanged(RecordingHealth.SIGNAL_LOST)
                        progress.logFinal()
                        return outcome(hadDropouts = true, signalLost = true)
                    }
                    onHealthChanged(RecordingHealth.RECONNECTING)
                    patchGap(output, backoffMs)
                    totalGapPatchedMs += backoffMs
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(RecordingResilienceConfig.MAX_BACKOFF_MS)
                    continue
                }

                onHealthChanged(RecordingHealth.RECORDING)
                disconnectWindowStart = null
                backoffMs = RecordingResilienceConfig.INITIAL_BACKOFF_MS

                val segments = parseSegmentUrls(mediaPlaylistUrl, playlist)
                var gotNewSegment = false
                for (segmentUrl in segments) {
                    if (cancelled) break
                    waitIfPaused()
                    if (!downloaded.add(segmentUrl)) continue
                    when (appendSegment(output, segmentUrl, progress)) {
                        SegmentAppendResult.APPENDED -> gotNewSegment = true
                        SegmentAppendResult.SKIPPED -> {
                            hadDropouts = true
                            corruptedChunksSkipped++
                            Log.w(TAG, "Skipped corrupt HLS segment url=$segmentUrl")
                        }
                        SegmentAppendResult.FAILED -> {
                            hadDropouts = true
                            patchGap(output, backoffMs)
                            totalGapPatchedMs += backoffMs
                        }
                    }
                }
                if (playlist.contains("#EXT-X-ENDLIST")) break
                if (!gotNewSegment) {
                    delay(targetDurationMs(playlist).coerceAtLeast(1_000))
                }
            }
            progress.logFinal()
        }
        return outcome(
            hadDropouts = hadDropouts,
            signalLost = false,
            gapPatchedMs = totalGapPatchedMs,
            corruptedChunksSkipped = corruptedChunksSkipped
        )
    }

    private fun outcome(
        hadDropouts: Boolean = false,
        signalLost: Boolean = false,
        gapPatchedMs: Long = 0L,
        corruptedChunksSkipped: Int = 0
    ): RecordingOutcome {
        val bytes = if (outputFile.exists()) outputFile.length() else 0L
        return RecordingOutcome(
            bytesWritten = bytes,
            hadDropouts = hadDropouts,
            signalLost = signalLost,
            gapPatchedMs = gapPatchedMs,
            corruptedChunksSkipped = corruptedChunksSkipped
        )
    }

    private fun patchGap(output: FileOutputStream, durationMs: Long) {
        val packetCount = (durationMs / 40L).toInt().coerceIn(1, 512)
        repeat(packetCount) {
            output.write(TS_NULL_PACKET)
        }
    }

    private suspend fun waitIfPaused() {
        while (!cancelled && shouldPauseForStorage()) {
            onHealthChanged(RecordingHealth.STORAGE_PAUSED)
            delay(STORAGE_PAUSE_POLL_MS)
        }
    }

    private fun storageDir(): File = outputFile.parentFile ?: outputFile

    private fun isStorageExhausted(): Boolean {
        val dir = storageDir()
        return !dir.canWrite() || StorageUtils.isCriticalLowStorage(dir)
    }

    private fun writeOrFail(
        output: FileOutputStream,
        buffer: ByteArray,
        read: Int,
        progress: ByteProgressLogger
    ): RecordingOutcome? {
        return try {
            output.write(buffer, 0, read)
            progress.onBytes(read)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Direct stream disk write failed", e)
            handleStorageFailure(progress)
            outcome(hadDropouts = true, signalLost = true)
        }
    }

    private fun handleStorageFailure(progress: ByteProgressLogger) {
        onStorageFailure()
        progress.logFinal()
    }

    private fun isValidTsPayload(buffer: ByteArray, length: Int): Boolean {
        if (length < 188) return true
        var offset = 0
        var validPackets = 0
        var checkedPackets = 0
        while (offset + 188 <= length) {
            checkedPackets++
            if (buffer[offset] == 0x47.toByte()) validPackets++
            offset += 188
        }
        if (checkedPackets == 0) return true
        return validPackets.toFloat() / checkedPackets.toFloat() >= 0.5f
    }

    private enum class SegmentAppendResult { APPENDED, SKIPPED, FAILED }

    private fun resolveMediaPlaylistUrl(playlistUrl: String): String? {
        return try {
            val text = fetchText(playlistUrl) ?: return null
            if (!text.contains("#EXT-X-STREAM-INF")) return playlistUrl
            val lines = text.lines()
            for (index in lines.indices) {
                if (lines[index].startsWith("#EXT-X-STREAM-INF")) {
                    val variant = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (variant != null) return resolveUrl(playlistUrl, variant.trim())
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "resolveMediaPlaylistUrl failed playlistUrl=$playlistUrl", e)
            null
        }
    }

    private fun parseSegmentUrls(playlistUrl: String, playlist: String): List<String> =
        playlist.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolveUrl(playlistUrl, it) }

    private fun targetDurationMs(playlist: String): Long {
        val line = playlist.lines().firstOrNull { it.startsWith("#EXT-X-TARGETDURATION:") } ?: return 2_000
        return line.substringAfter(':').trim().toDoubleOrNull()?.times(1_000)?.toLong() ?: 2_000
    }

    private fun appendSegment(
        output: FileOutputStream,
        segmentUrl: String,
        progress: ByteProgressLogger
    ): SegmentAppendResult {
        return try {
            val request = Request.Builder().url(segmentUrl).build()
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HLS segment HTTP failed url=$segmentUrl responseCode=${response.code}")
                    return SegmentAppendResult.FAILED
                }
                val body = response.body ?: return SegmentAppendResult.FAILED
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (!cancelled) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (!isValidTsPayload(buffer, read)) {
                            return SegmentAppendResult.SKIPPED
                        }
                        if (isStorageExhausted()) {
                            onStorageFailure()
                            return SegmentAppendResult.FAILED
                        }
                        try {
                            output.write(buffer, 0, read)
                            progress.onBytes(read)
                        } catch (e: IOException) {
                            Log.e(TAG, "HLS segment disk write failed url=$segmentUrl", e)
                            onStorageFailure()
                            return SegmentAppendResult.FAILED
                        }
                    }
                }
            }
            SegmentAppendResult.APPENDED
        } catch (e: Exception) {
            Log.e(TAG, "appendSegment failed url=$segmentUrl", e)
            SegmentAppendResult.FAILED
        }
    }

    private fun fetchText(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Playlist HTTP failed url=$url responseCode=${response.code}")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchText failed url=$url", e)
            null
        }
    }

    private fun resolveUrl(baseUrl: String, relative: String): String {
        if (relative.startsWith("http://", ignoreCase = true) ||
            relative.startsWith("https://", ignoreCase = true)
        ) {
            return relative
        }
        return URI(baseUrl).resolve(relative).toString()
    }

    private inner class ByteProgressLogger(private val label: String) {
        private var totalBytes = 0L
        private var lastLogAt = System.currentTimeMillis()

        fun onBytes(count: Int) {
            totalBytes += count
            val now = System.currentTimeMillis()
            if (now - lastLogAt >= PROGRESS_LOG_INTERVAL_MS) {
                Log.i(TAG, "$label: written $totalBytes bytes (file=${outputFile.absolutePath})")
                lastLogAt = now
            }
        }

        fun logFinal() {
            Log.i(
                TAG,
                "$label: finished with $totalBytes bytes (file=${outputFile.absolutePath}, length=${outputFile.length()})"
            )
        }
    }

    companion object {
        private const val TAG = "TsStreamRecorder"
        private const val PROGRESS_LOG_INTERVAL_MS = 10_000L
        private const val STORAGE_PAUSE_POLL_MS = 1_500L

        private val TS_NULL_PACKET = ByteArray(188).apply {
            this[0] = 0x47
            this[1] = 0x1F.toByte()
            this[2] = 0xFF.toByte()
            this[3] = 0x10
            for (index in 4 until size) {
                this[index] = 0xFF.toByte()
            }
        }
    }
}
