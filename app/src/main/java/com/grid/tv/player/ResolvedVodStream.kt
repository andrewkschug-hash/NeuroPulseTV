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
    val resolutionSource: String
) {
    fun toStreamFormat(): IptvStreamFormat = mediaSourceType.toStreamFormat()

    fun logFinal() {
        Log.i(
            TAG,
            "VOD_RESOLVED_FINAL type=$mediaSourceType source=$resolutionSource override=$wasUnknownOverride"
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
        detection: StreamTypeDetector.Detection
    ): ResolvedVodStream? {
        if (StreamTypeDetector.isFatalVodPreflightReason(detection.reason)) {
            return null
        }

        val (mediaType, wasOverride) = when (detection.format) {
            IptvStreamFormat.HLS -> VodMediaSourceType.HLS to false
            IptvStreamFormat.PROGRESSIVE -> VodMediaSourceType.PROGRESSIVE to false
            IptvStreamFormat.UNKNOWN -> VodMediaSourceType.PROGRESSIVE to true
        }

        val source = detection.contentType?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: detection.reason

        return ResolvedVodStream(
            url = url,
            mediaSourceType = mediaType,
            wasUnknownOverride = wasOverride,
            resolutionSource = source
        )
    }
}
