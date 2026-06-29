package com.grid.tv.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * Probes device decoder support before attempting high-profile VOD streams
 * (4K HEVC / Dolby Vision on emulators, etc.).
 */
@OptIn(UnstableApi::class)
object CodecCapabilityChecker {

    private const val LOG_TAG = "CodecCapability"

    fun isVideoFormatSupported(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Float = 30f,
    ): Boolean {
        if (width <= 0 || height <= 0) return true
        return try {
            val codecInfo = MediaCodecUtil.getDecoderInfo(mimeType, false, false)
            val supported = codecInfo?.isVideoSizeAndRateSupportedV21(
                width,
                height,
                frameRate.toDouble().coerceAtLeast(0.0),
            ) ?: false
            Log.d(
                LOG_TAG,
                "mime=$mimeType ${width}x$height@${frameRate}fps supported=$supported " +
                    "decoder=${codecInfo?.name ?: "none"}"
            )
            supported
        } catch (error: MediaCodecUtil.DecoderQueryException) {
            Log.w(LOG_TAG, "decoder query failed mime=$mimeType", error)
            true
        }
    }

    fun isVariantSupported(variant: VodPlaybackQualityLadder.Variant): Boolean =
        isVideoFormatSupported(variant.mimeType, variant.width, variant.height, variant.frameRate)
}

data class PlaybackVideoProfile(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val frameRate: Float = 30f,
) {
    companion object {
        val UHD_HEVC = PlaybackVideoProfile(MimeTypes.VIDEO_H265, 3840, 2160)
        val FHD_HEVC = PlaybackVideoProfile(MimeTypes.VIDEO_H265, 1920, 1080)
        val FHD_H264 = PlaybackVideoProfile(MimeTypes.VIDEO_H264, 1920, 1080)
        val SD_H264 = PlaybackVideoProfile(MimeTypes.VIDEO_H264, 854, 480)
    }
}
