package com.grid.tv.player

import android.util.Log
import androidx.media3.common.MimeTypes

/**
 * Ordered VOD quality fallbacks: original → adaptive HLS → transcoded 1080p → SD.
 * Skips variants the device decoder cannot handle before ExoPlayer starts.
 */
object VodPlaybackQualityLadder {

    private const val LOG_TAG = "VodQualityLadder"

    data class Variant(
        val url: String,
        val label: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val frameRate: Float = 30f,
    )

    private val xtreamVodPath = Regex(
        """^(https?://[^/]+)/(movie|series)/([^/]+)/([^/]+)/(\d+)(?:\.(\w+))?/?$""",
        RegexOption.IGNORE_CASE
    )

    fun build(originalUrl: String, title: String?): List<Variant> {
        val profile = inferProfileFromTitle(title)
            ?: inferProfileFromUrl(originalUrl)
            ?: PlaybackVideoProfile.FHD_H264

        val variants = mutableListOf(
            Variant(
                url = originalUrl.trim(),
                label = "Original",
                mimeType = profile.mimeType,
                width = profile.width,
                height = profile.height,
                frameRate = profile.frameRate,
            )
        )

        parseXtreamVod(originalUrl)?.let { parts ->
            val hlsUrl = parts.hlsUrl()
            if (!originalUrl.contains(".m3u8", ignoreCase = true)) {
                variants += Variant(
                    url = hlsUrl,
                    label = "Adaptive HLS",
                    mimeType = MimeTypes.VIDEO_H264,
                    width = 1920,
                    height = 1080,
                )
            }
            val transcode1080 = parts.transcodeUrl(bitrateKbps = 2_500)
            if (variants.none { it.url == transcode1080 }) {
                variants += Variant(
                    url = transcode1080,
                    label = "1080p transcode",
                    mimeType = MimeTypes.VIDEO_H264,
                    width = 1920,
                    height = 1080,
                )
            }
            variants += Variant(
                url = parts.transcodeUrl(bitrateKbps = 800),
                label = "SD transcode",
                mimeType = MimeTypes.VIDEO_H264,
                width = 854,
                height = 480,
            )
        }

        return variants.distinctBy { it.url }
    }

    /**
     * Returns the first ladder step the device can decode, or the original URL as a last resort.
     */
    fun selectInitialVariant(originalUrl: String, title: String?): Selection {
        val ladder = build(originalUrl, title)
        val supportedIndex = ladder.indexOfFirst { CodecCapabilityChecker.isVariantSupported(it) }
        val index = if (supportedIndex >= 0) supportedIndex else 0
        val selected = ladder[index]
        if (supportedIndex > 0) {
            Log.i(
                LOG_TAG,
                "preflight skipped ${supportedIndex} unsupported variant(s); playing ${selected.label}"
            )
        }
        return Selection(
            variants = ladder,
            selectedIndex = index,
            selectedUrl = selected.url,
        )
    }

    data class Selection(
        val variants: List<Variant>,
        val selectedIndex: Int,
        val selectedUrl: String,
    ) {
        val urls: List<String> = variants.map { it.url }

        fun hasMoreVariants(): Boolean = selectedIndex < variants.lastIndex

        fun nextVariant(): Variant? = variants.getOrNull(selectedIndex + 1)
    }

    private fun inferProfileFromTitle(title: String?): PlaybackVideoProfile? {
        val normalized = title?.lowercase().orEmpty()
        if (normalized.isBlank()) return null
        return when {
            listOf("dolby vision", "dolbyvision", " dv ", "dv)", "(dv").any { normalized.contains(it) } ->
                PlaybackVideoProfile.UHD_HEVC
            listOf("4k", "uhd", "2160p", "ultra hd").any { normalized.contains(it) } ->
                PlaybackVideoProfile.UHD_HEVC
            listOf("hevc", "h265", "x265").any { normalized.contains(it) } ->
                PlaybackVideoProfile.FHD_HEVC
            else -> null
        }
    }

    private fun inferProfileFromUrl(url: String): PlaybackVideoProfile? {
        val normalized = url.lowercase()
        return when {
            normalized.contains(".m3u8") -> PlaybackVideoProfile.FHD_H264
            normalized.contains(".mkv") -> PlaybackVideoProfile.FHD_HEVC
            normalized.contains(".mp4") || normalized.contains(".ts") -> PlaybackVideoProfile.FHD_H264
            else -> null
        }
    }

    private data class XtreamVodParts(
        val base: String,
        val kind: String,
        val user: String,
        val pass: String,
        val streamId: String,
    ) {
        fun hlsUrl(): String = "$base/$kind/$user/$pass/$streamId.m3u8"

        fun transcodeUrl(bitrateKbps: Int): String =
            "$base/streaming/clients_live.php?username=$user&password=$pass&stream=$streamId&extension=ts&bitrate=$bitrateKbps"
    }

    private fun parseXtreamVod(url: String): XtreamVodParts? {
        val match = xtreamVodPath.matchEntire(url.trim()) ?: return null
        val (base, kind, user, pass, streamId, _) = match.destructured
        if (user.isBlank() || pass.isBlank() || streamId.isBlank()) return null
        return XtreamVodParts(base.trimEnd('/'), kind, user, pass, streamId)
    }
}
