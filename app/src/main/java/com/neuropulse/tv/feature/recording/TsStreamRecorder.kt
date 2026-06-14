package com.neuropulse.tv.feature.recording

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Copies MPEG-TS bytes from a direct stream URL or HLS playlist into a .ts file.
 * Avoids native FFmpeg so the app can stay on minSdk 21.
 */
class TsStreamRecorder(
    private val client: OkHttpClient,
    private val outputFile: File
) {
    @Volatile
    private var cancelled = false

    private var activeCall: okhttp3.Call? = null

    fun cancel() {
        cancelled = true
        activeCall?.cancel()
    }

    suspend fun record(streamUrl: String): Boolean {
        cancelled = false
        Log.i(TAG, "record() started streamUrl=$streamUrl output=${outputFile.absolutePath}")
        return try {
            if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                recordHls(streamUrl)
            } else {
                recordDirect(streamUrl)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "record() failed streamUrl=$streamUrl output=${outputFile.absolutePath}",
                e
            )
            false
        }
    }

    private fun recordDirect(url: String): Boolean {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            Log.i(TAG, "Direct stream HTTP request url=$url responseCode=${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "Direct stream HTTP failed url=$url responseCode=${response.code}")
                return false
            }
            val body = response.body ?: run {
                Log.e(TAG, "Direct stream response body is null url=$url responseCode=${response.code}")
                return false
            }
            outputFile.parentFile?.mkdirs()
            Log.i(
                TAG,
                "Opening output file path=${outputFile.absolutePath} exists=${outputFile.exists()} parentExists=${outputFile.parentFile?.exists() == true}"
            )
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    Log.i(
                        TAG,
                        "Output file opened path=${outputFile.absolutePath} length=${outputFile.length()}"
                    )
                    val progress = ByteProgressLogger("Direct stream")
                    val buffer = ByteArray(32 * 1024)
                    while (!cancelled) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        progress.onBytes(read)
                    }
                    progress.logFinal()
                }
            }
        }
        Log.i(
            TAG,
            "Direct stream finished path=${outputFile.absolutePath} length=${outputFile.length()} cancelled=$cancelled"
        )
        return outputFile.length() > 0
    }

    private suspend fun recordHls(playlistUrl: String): Boolean {
        Log.i(TAG, "Starting HLS recording playlistUrl=$playlistUrl output=${outputFile.absolutePath}")
        val mediaPlaylistUrl = resolveMediaPlaylistUrl(playlistUrl) ?: run {
            Log.e(TAG, "Failed to resolve HLS media playlist url=$playlistUrl")
            return false
        }
        Log.i(TAG, "Resolved HLS media playlist url=$mediaPlaylistUrl")
        val downloaded = mutableSetOf<String>()
        outputFile.parentFile?.mkdirs()
        Log.i(
            TAG,
            "Opening HLS output file path=${outputFile.absolutePath} exists=${outputFile.exists()}"
        )
        FileOutputStream(outputFile).use { output ->
            val progress = ByteProgressLogger("HLS recording")
            while (!cancelled && currentCoroutineContext().isActive) {
                val playlist = fetchText(mediaPlaylistUrl) ?: break
                val segments = parseSegmentUrls(mediaPlaylistUrl, playlist)
                var gotNewSegment = false
                for (segmentUrl in segments) {
                    if (cancelled) break
                    if (!downloaded.add(segmentUrl)) continue
                    if (appendSegment(output, segmentUrl, progress)) {
                        gotNewSegment = true
                    }
                }
                if (playlist.contains("#EXT-X-ENDLIST")) break
                if (!gotNewSegment) {
                    delay(targetDurationMs(playlist).coerceAtLeast(1_000))
                }
            }
            progress.logFinal()
        }
        Log.i(
            TAG,
            "HLS recording finished path=${outputFile.absolutePath} length=${outputFile.length()} cancelled=$cancelled"
        )
        return outputFile.length() > 0
    }

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
    ): Boolean {
        return try {
            val request = Request.Builder().url(segmentUrl).build()
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { response ->
                Log.i(TAG, "HLS segment HTTP request url=$segmentUrl responseCode=${response.code}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "HLS segment HTTP failed url=$segmentUrl responseCode=${response.code}")
                    return false
                }
                val body = response.body ?: run {
                    Log.e(TAG, "HLS segment body is null url=$segmentUrl responseCode=${response.code}")
                    return false
                }
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (!cancelled) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        progress.onBytes(read)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "appendSegment failed url=$segmentUrl", e)
            false
        }
    }

    private fun fetchText(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            activeCall = call
            call.execute().use { response ->
                Log.i(TAG, "Playlist HTTP request url=$url responseCode=${response.code}")
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
                Log.i(
                    TAG,
                    "$label: written $totalBytes bytes so far (file=${outputFile.absolutePath})"
                )
                lastLogAt = now
            }
        }

        fun logFinal() {
            Log.i(
                TAG,
                "$label: finished with $totalBytes bytes written (file=${outputFile.absolutePath}, length=${outputFile.length()})"
            )
        }
    }

    companion object {
        private const val TAG = "TsStreamRecorder"
        private const val PROGRESS_LOG_INTERVAL_MS = 10_000L
    }
}
