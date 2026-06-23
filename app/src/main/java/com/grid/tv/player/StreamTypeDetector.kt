package com.grid.tv.player

import android.util.Log
import androidx.media3.common.MimeTypes

/**
 * Strict IPTV stream type classification for playback routing.
 *
 * Does not default ambiguous live URLs to HLS — HLS requires a `.m3u8` URL plus manifest
 * or playlist content-type confirmation from preflight sniff.
 */
object StreamTypeDetector {

    private const val TAG = "StreamType"

    val HLS_CONTENT_TYPES: Set<String> = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "application/mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl"
    )

    private val TS_URL_SUFFIXES = listOf(".ts", ".ts?")

    private val VOD_PROGRESSIVE_CONTENT_TYPES = setOf(
        "video/x-matroska",
        "video/mp4",
        "application/octet-stream"
    )

    private val VOD_PROGRESSIVE_URL_MARKERS = listOf(".mkv", ".mp4", ".mov")

    data class Detection(
        val format: IptvStreamFormat,
        val contentType: String? = null,
        val firstBytesSignature: String = "",
        val reason: String = ""
    )

    /** URL-only hints — never sufficient alone for HLS routing. */
    fun classifyUrlPath(url: String): IptvStreamFormat {
        val normalized = url.trim().lowercase()
        if (normalized.isEmpty()) return IptvStreamFormat.UNKNOWN
        if (isTsUrl(normalized)) return IptvStreamFormat.PROGRESSIVE
        if (normalized.contains(".m3u8")) return IptvStreamFormat.UNKNOWN
        if (normalized.contains(".mp4") || normalized.contains(".mkv")) return IptvStreamFormat.PROGRESSIVE
        return IptvStreamFormat.UNKNOWN
    }

    /**
     * Authoritative classification using URL, optional Content-Type, and optional body prefix.
     *
     * HLS only when URL ends with `.m3u8` AND (manifest `#EXTM3U` OR HLS content-type).
     * TS / progressive when URL ends with `.ts` OR content-type is `video/mp2t`.
     */
    fun classify(
        url: String,
        contentType: String?,
        firstBytes: String?
    ): Detection {
        val normalizedUrl = url.trim().lowercase()
        val baseContentType = contentType?.substringBefore(';')?.trim()?.lowercase()
        val signature = firstBytesSignature(firstBytes)
        val manifest = isHlsManifestSnippet(firstBytes.orEmpty())
        val hlsMime = baseContentType != null && isHlsContentType(baseContentType)
        val tsMime = baseContentType == "video/mp2t"
        val hasM3u8Url = normalizedUrl.contains(".m3u8")

        val format = when {
            isTsUrl(normalizedUrl) || tsMime -> IptvStreamFormat.PROGRESSIVE
            hasM3u8Url && (manifest || hlsMime) -> IptvStreamFormat.HLS
            hasM3u8Url -> IptvStreamFormat.UNKNOWN
            else -> IptvStreamFormat.UNKNOWN
        }

        val reason = when (format) {
            IptvStreamFormat.HLS -> "m3u8_url+manifest_or_mpegurl"
            IptvStreamFormat.PROGRESSIVE -> when {
                isTsUrl(normalizedUrl) -> "ts_url"
                tsMime -> "video_mp2t"
                else -> "progressive"
            }
            IptvStreamFormat.UNKNOWN -> when {
                hasM3u8Url -> "m3u8_without_manifest"
                else -> "insufficient_signal"
            }
        }

        return Detection(
            format = format,
            contentType = contentType,
            firstBytesSignature = signature,
            reason = reason
        ).also(::logDetection)
    }

    fun isVodProgressiveUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return VOD_PROGRESSIVE_URL_MARKERS.any { lower.contains(it) }
    }

    fun isVodProgressiveContentType(contentType: String?): Boolean {
        val base = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (base.isBlank()) return false
        return base in VOD_PROGRESSIVE_CONTENT_TYPES
    }

    fun isHtmlBlockedResponse(contentType: String?, firstBytes: String): Boolean {
        val base = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (base.contains("text/html")) return true
        val trimmed = firstBytes.trimStart().lowercase()
        return trimmed.startsWith("<!doctype html") ||
            trimmed.startsWith("<html") ||
            (trimmed.contains("<head>") && trimmed.contains("</html>"))
    }

    fun isFatalVodPreflightBlock(
        httpCode: Int,
        contentType: String?,
        firstBytes: String?
    ): Boolean {
        if (httpCode in 400..599) return true
        if (firstBytes.isNullOrEmpty()) return true
        return isHtmlBlockedResponse(contentType, firstBytes)
    }

    fun isFatalVodPreflightReason(reason: String): Boolean =
        reason == "blank_url" ||
            reason.startsWith("http_") ||
            reason == "empty_body" ||
            reason == "html_blocked"

    /**
     * VOD-only: UNKNOWN is never fatal unless [isFatalVodPreflightReason] already matched.
     * Maps ambiguous on-demand streams to progressive playback.
     * @deprecated Prefer [VodStreamResolver.resolve] in the VOD entry point.
     */
    fun applyVodOverride(detection: Detection): Detection {
        if (detection.format != IptvStreamFormat.UNKNOWN) return detection
        logVodOverride("unknown_but_allowed")
        return detection.copy(
            format = IptvStreamFormat.PROGRESSIVE,
            reason = "vod_unknown_progressive_fallback"
        ).also(::logDetection)
    }

    fun logVodOverride(reason: String) {
        Log.i(TAG, "STREAM_TYPE_VOD_OVERRIDE applied=true reason=$reason")
    }

    fun isTsUrl(url: String): Boolean {
        val lower = url.lowercase()
        return TS_URL_SUFFIXES.any { lower.contains(it) }
    }

    fun isHlsContentType(contentType: String): Boolean {
        val base = contentType.substringBefore(';').trim().lowercase()
        return base == MimeTypes.APPLICATION_M3U8 || base in HLS_CONTENT_TYPES ||
            base.contains("mpegurl")
    }

    fun isHlsManifestSnippet(text: String): Boolean =
        text.contains("#EXTM3U", ignoreCase = true)

    fun isHlsParserFailure(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            val name = current.javaClass.name
            if (name.contains("ParserException", ignoreCase = true)) return true
            val message = current.message.orEmpty()
            if (message.contains("#EXTM3U", ignoreCase = true) ||
                message.contains("HlsPlaylistParser", ignoreCase = true) ||
                message.contains("does not start with #EXTM3U", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun logDetection(detection: Detection) {
        val typeLabel = when (detection.format) {
            IptvStreamFormat.HLS -> "HLS"
            IptvStreamFormat.PROGRESSIVE -> "TS"
            IptvStreamFormat.UNKNOWN -> "UNKNOWN"
        }
        Log.i(TAG, "STREAM_TYPE_DETECTED type=$typeLabel reason=${detection.reason}")
        Log.i(
            TAG,
            "CONTENT_TYPE_HEADER ${detection.contentType?.substringBefore(';')?.trim().orEmpty().ifBlank { "none" }}"
        )
        Log.i(
            TAG,
            "FIRST_BYTES_SIGNATURE ${detection.firstBytesSignature.ifBlank { "empty" }}"
        )
    }

    fun logMediaSourceSelected(format: IptvStreamFormat, url: String) {
        val label = when (format) {
            IptvStreamFormat.HLS -> "HlsMediaSource"
            IptvStreamFormat.PROGRESSIVE -> "ProgressiveMediaSource"
            IptvStreamFormat.UNKNOWN -> "none"
        }
        Log.i(TAG, "MEDIA_SOURCE_SELECTED source=$label urlHash=${url.hashCode()}")
    }

    private fun firstBytesSignature(firstBytes: String?): String {
        if (firstBytes.isNullOrEmpty()) return ""
        val trimmed = firstBytes.trimStart().take(48)
        return trimmed.replace('\n', ' ').replace('\r', ' ')
    }
}
