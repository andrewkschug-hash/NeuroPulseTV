package com.neuropulse.tv.feature.recording

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
        return try {
            if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                recordHls(streamUrl)
            } else {
                recordDirect(streamUrl)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun recordDirect(url: String): Boolean {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (!cancelled) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        return outputFile.length() > 0
    }

    private suspend fun recordHls(playlistUrl: String): Boolean {
        val mediaPlaylistUrl = resolveMediaPlaylistUrl(playlistUrl) ?: return false
        val downloaded = mutableSetOf<String>()
        FileOutputStream(outputFile).use { output ->
            while (!cancelled && currentCoroutineContext().isActive) {
                val playlist = fetchText(mediaPlaylistUrl) ?: break
                val segments = parseSegmentUrls(mediaPlaylistUrl, playlist)
                var gotNewSegment = false
                for (segmentUrl in segments) {
                    if (cancelled) break
                    if (!downloaded.add(segmentUrl)) continue
                    if (appendSegment(output, segmentUrl)) {
                        gotNewSegment = true
                    }
                }
                if (playlist.contains("#EXT-X-ENDLIST")) break
                if (!gotNewSegment) {
                    delay(targetDurationMs(playlist).coerceAtLeast(1_000))
                }
            }
        }
        return outputFile.length() > 0
    }

    private fun resolveMediaPlaylistUrl(playlistUrl: String): String? {
        val text = fetchText(playlistUrl) ?: return null
        if (!text.contains("#EXT-X-STREAM-INF")) return playlistUrl
        val lines = text.lines()
        for (index in lines.indices) {
            if (lines[index].startsWith("#EXT-X-STREAM-INF")) {
                val variant = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                if (variant != null) return resolveUrl(playlistUrl, variant.trim())
            }
        }
        return null
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

    private fun appendSegment(output: FileOutputStream, segmentUrl: String): Boolean {
        val request = Request.Builder().url(segmentUrl).build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (!cancelled) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            }
        }
        return true
    }

    private fun fetchText(url: String): String? {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
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
}
