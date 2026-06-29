package com.grid.tv.player

import android.util.Log

enum class VodMediaSourceType {
    HLS,
    PROGRESSIVE;

    fun toStreamFormat(): IptvStreamFormat = when (this) {
        HLS -> IptvStreamFormat.HLS
        PROGRESSIVE -> IptvStreamFormat.PROGRESSIVE
    }
}

/**
 * Final VOD playback decision computed before ExoPlayer creation.
 */
data class ResolvedVodStream(
    val url: String,
    val mediaSourceType: VodMediaSourceType,
    val wasUnknownOverride: Boolean,
    val resolutionSource: String,
    val qualityVariants: List<String> = listOf(url),
    val selectedVariantIndex: Int = 0,
    val selectedVariantLabel: String = "Original",
) {
    fun toStreamFormat(): IptvStreamFormat = mediaSourceType.toStreamFormat()

    fun hasMoreQualityVariants(): Boolean = selectedVariantIndex < qualityVariants.lastIndex

    fun logFinal() {
        Log.i(
            TAG,
            "VOD_RESOLVED_FINAL type=$mediaSourceType source=$resolutionSource " +
                "override=$wasUnknownOverride variant=$selectedVariantLabel " +
                "index=$selectedVariantIndex/${qualityVariants.size}"
        )
    }

    companion object {
        private const val TAG = "StreamType"
    }
}

/** Centralizes VOD preflight output into a single deterministic playback decision. */
object VodStreamResolver {

    fun resolve(
        url: String,
        detection: StreamTypeDetector.Detection,
        title: String? = null,
    ): ResolvedVodStream? {
        if (StreamTypeDetector.isFatalVodPreflightReason(detection.reason)) {
            return null
        }

        val qualitySelection = VodPlaybackQualityLadder.selectInitialVariant(url, title)
        val playbackUrl = qualitySelection.selectedUrl

        val (mediaType, wasOverride) = when (detection.format) {
            IptvStreamFormat.HLS -> VodMediaSourceType.HLS to false
            IptvStreamFormat.PROGRESSIVE -> VodMediaSourceType.PROGRESSIVE to false
            IptvStreamFormat.UNKNOWN -> {
                if (playbackUrl.contains(".m3u8", ignoreCase = true)) {
                    VodMediaSourceType.HLS to true
                } else {
                    VodMediaSourceType.PROGRESSIVE to true
                }
            }
        }

        val source = detection.contentType?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: detection.reason

        return ResolvedVodStream(
            url = playbackUrl,
            mediaSourceType = mediaType,
            wasUnknownOverride = wasOverride,
            resolutionSource = source,
            qualityVariants = qualitySelection.urls,
            selectedVariantIndex = qualitySelection.selectedIndex,
            selectedVariantLabel = qualitySelection.variants[qualitySelection.selectedIndex].label,
        )
    }
}
