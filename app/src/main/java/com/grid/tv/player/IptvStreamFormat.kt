package com.grid.tv.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

/**
 * Stream container/format for IPTV playback routing.
 */
enum class IptvStreamFormat {
    /** HLS — route through [androidx.media3.exoplayer.hls.HlsMediaSource]. */
    HLS,
    /** Progressive TS/MP4 and other non-HLS sources. */
    PROGRESSIVE,
    /** Unknown — requires preflight sniff before playback; never route to HLS by default. */
    UNKNOWN;

    fun isHls(): Boolean = this == HLS
}

/**
 * Resolves IPTV stream format using metadata, probe cache, content-type, URL patterns, and extension.
 */
object IptvStreamFormatDetector {

    val HLS_CONTENT_TYPES: Set<String> = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "application/mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl"
    )

    private val PROGRESSIVE_EXTENSIONS = setOf(".ts", ".mp4", ".mkv", ".avi", ".flv", ".mpg", ".mpeg")

    /** Xtream / generic IPTV live paths without a .m3u8 suffix. */
    private val IPTV_HLS_URL_PATTERNS = listOf(
        Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE),
        Regex("""/live/[^/?#]+\.php""", RegexOption.IGNORE_CASE),
        Regex("""/streaming/clients/live\.php""", RegexOption.IGNORE_CASE),
        Regex("""/play/live""", RegexOption.IGNORE_CASE),
        Regex("""/hls/""", RegexOption.IGNORE_CASE),
        Regex("""/timeshift/""", RegexOption.IGNORE_CASE),
        Regex("""[?&]output=(m3u8|hls)""", RegexOption.IGNORE_CASE),
        Regex("""[?&]format=m3u8""", RegexOption.IGNORE_CASE),
        Regex("""[?&]type=(m3u8|hls)""", RegexOption.IGNORE_CASE),
        Regex("""/live/[^/]+/[^/]+/\d+""", RegexOption.IGNORE_CASE),
        Regex("""/live/\d+""", RegexOption.IGNORE_CASE),
        Regex("""[?&]stream(?:=|\?|&)""", RegexOption.IGNORE_CASE),
        Regex("""[?&]id=\d+""", RegexOption.IGNORE_CASE)
    )

    /**
     * Detection order:
     * 1. Explicit [MediaItem] mime type / custom metadata
     * 2. Probe registry (content-type or manifest sniff)
     * 3. Known IPTV URL patterns
     * 4. Progressive extension
     * 5. `.m3u8` extension fallback
     */
    fun detect(
        url: String,
        mediaItem: MediaItem? = null,
        registry: IptvStreamFormatRegistry? = null
    ): IptvStreamFormat {
        if (url.isBlank()) return IptvStreamFormat.UNKNOWN

        mediaItem?.localConfiguration?.mimeType?.let { mime ->
            formatFromMimeType(mime)?.let { return it }
        }

        readMetadataHint(mediaItem)?.let { return it }

        registry?.get(url)?.let { return it }

        if (matchesIptvHlsPattern(url)) return IptvStreamFormat.UNKNOWN

        if (PROGRESSIVE_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) {
            return IptvStreamFormat.PROGRESSIVE
        }

        if (url.contains(".m3u8", ignoreCase = true)) return IptvStreamFormat.UNKNOWN

        return IptvStreamFormat.UNKNOWN
    }

    /** Live IPTV URLs without a confirmed manifest remain UNKNOWN until preflight sniff. */
    fun resolveForPlayback(
        url: String,
        mediaItem: MediaItem? = null,
        registry: IptvStreamFormatRegistry? = null
    ): IptvStreamFormat {
        registry?.get(url)?.let { return it }
        readMetadataHint(mediaItem)?.let { return it }
        return when (val detected = detect(url, mediaItem, registry)) {
            IptvStreamFormat.UNKNOWN -> StreamTypeDetector.classifyUrlPath(url)
            else -> detected
        }
    }

    /**
     * On-demand routing: never applies live-URL HLS defaults to Xtream movie/series paths.
     */
    fun resolveForOnDemandPlayback(
        url: String,
        contentKind: IptvOnDemandContentKind,
        mediaItem: MediaItem? = null,
        registry: IptvStreamFormatRegistry? = null
    ): IptvStreamFormat {
        when (val detected = detect(url, mediaItem, registry)) {
            IptvStreamFormat.HLS, IptvStreamFormat.PROGRESSIVE -> return detected
            IptvStreamFormat.UNKNOWN -> Unit
        }
        return when (contentKind) {
            IptvOnDemandContentKind.CATCHUP -> resolveCatchupFormat(url)
            IptvOnDemandContentKind.VOD_MOVIE, IptvOnDemandContentKind.VOD_SERIES -> resolveVodFormat(url)
            IptvOnDemandContentKind.RECORDING, IptvOnDemandContentKind.LOCAL_FILE -> resolveLocalFormat(url)
        }
    }

    fun looksLikeVodUrl(url: String): Boolean =
        url.contains("/movie/", ignoreCase = true) ||
            url.contains("/series/", ignoreCase = true)

    fun looksLikeCatchupArchiveUrl(url: String): Boolean =
        url.contains("utc=", ignoreCase = true) ||
            url.contains("lutc=", ignoreCase = true) ||
            url.contains("/timeshift/", ignoreCase = true) ||
            (url.contains("start=", ignoreCase = true) && url.contains("duration=", ignoreCase = true))

    private fun resolveVodFormat(url: String): IptvStreamFormat {
        if (StreamTypeDetector.isTsUrl(url)) return IptvStreamFormat.PROGRESSIVE
        if (url.contains(".m3u8", ignoreCase = true)) return IptvStreamFormat.UNKNOWN
        if (PROGRESSIVE_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) {
            return IptvStreamFormat.PROGRESSIVE
        }
        if (looksLikeVodUrl(url)) return IptvStreamFormat.PROGRESSIVE
        return IptvStreamFormat.PROGRESSIVE
    }

    private fun resolveCatchupFormat(url: String): IptvStreamFormat {
        if (StreamTypeDetector.isTsUrl(url)) return IptvStreamFormat.PROGRESSIVE
        if (url.contains(".m3u8", ignoreCase = true)) return IptvStreamFormat.UNKNOWN
        if (PROGRESSIVE_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) {
            return IptvStreamFormat.PROGRESSIVE
        }
        return IptvStreamFormat.UNKNOWN
    }

    private fun resolveLocalFormat(url: String): IptvStreamFormat {
        if (url.contains(".m3u8", ignoreCase = true)) return IptvStreamFormat.HLS
        return IptvStreamFormat.PROGRESSIVE
    }

    fun formatFromMimeType(mimeType: String?): IptvStreamFormat? {
        val normalized = mimeType?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return null
        return when {
            normalized == MimeTypes.APPLICATION_M3U8 ||
                normalized in HLS_CONTENT_TYPES -> IptvStreamFormat.HLS
            normalized.startsWith("video/") ||
                normalized.startsWith("audio/") && normalized !in HLS_CONTENT_TYPES ->
                IptvStreamFormat.PROGRESSIVE
            else -> null
        }
    }

    fun formatFromContentType(contentType: String?): IptvStreamFormat? {
        val base = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (base.isBlank()) return null
        return when {
            base in HLS_CONTENT_TYPES || base == MimeTypes.APPLICATION_M3U8 -> IptvStreamFormat.HLS
            base == "video/mp2t" -> IptvStreamFormat.PROGRESSIVE
            base.startsWith("video/") || base == "application/octet-stream" -> null
            else -> null
        }
    }

    fun isHlsManifestSnippet(snippet: String): Boolean =
        snippet.contains("#EXTM3U", ignoreCase = true) ||
            snippet.contains("#EXTINF", ignoreCase = true)

    fun matchesIptvHlsPattern(url: String): Boolean =
        IPTV_HLS_URL_PATTERNS.any { it.containsMatchIn(url) }

    fun looksLikeLiveIptvUrl(url: String): Boolean =
        matchesIptvHlsPattern(url) ||
            url.contains("/live/", ignoreCase = true) ||
            url.contains("live.php", ignoreCase = true)

    private fun readMetadataHint(mediaItem: MediaItem?): IptvStreamFormat? {
        val extras = mediaItem?.mediaMetadata?.extras ?: return null
        return when (extras.getString(METADATA_KEY_STREAM_FORMAT)?.lowercase()) {
            "hls" -> IptvStreamFormat.HLS
            "progressive" -> IptvStreamFormat.PROGRESSIVE
            else -> null
        }
    }

    const val METADATA_KEY_STREAM_FORMAT = "iptv_stream_format"
    const val METADATA_KEY_PLAYBACK_SCOPE = "iptv_playback_scope"
    const val METADATA_KEY_CONTENT_KIND = "iptv_content_kind"
}
