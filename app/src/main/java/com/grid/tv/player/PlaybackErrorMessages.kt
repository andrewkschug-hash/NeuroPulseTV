package com.grid.tv.player

import androidx.media3.common.PlaybackException

/**
 * True when the device decoder cannot handle the stream (4K HEVC/Dolby Vision on emulators, etc.).
 * Retrying prepare() will not recover from these failures.
 */
fun PlaybackException.isCodecCapabilityError(): Boolean {
    if (errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) return true
    return generateSequence(this as Throwable?) { it.cause }
        .map { it.message.orEmpty() }
        .any { message ->
            message.contains("NO_EXCEEDS_CAPABILITIES", ignoreCase = true) ||
                message.contains("MediaCodecVideoDecoderException", ignoreCase = true) ||
                message.contains("NoSupport", ignoreCase = true) && message.contains("hevc", ignoreCase = true)
        }
}

fun PlaybackException.playbackErrorMessage(isEmulator: Boolean = false): String {
    if (isCodecCapabilityError()) {
        return if (isEmulator) {
            "This stream uses 4K HEVC/Dolby Vision, which the Android emulator cannot decode. " +
                "Test on a physical Android TV device, or pick an HD/SD stream from your provider."
        } else {
            "This video format is not supported on this device (often 4K HEVC or Dolby Vision). " +
                "Try a lower-quality stream if your provider offers one."
        }
    }
    return message?.takeIf { it.isNotBlank() }
        ?: "Playback failed. Check your connection and try again."
}

fun PlaybackException.isRetriablePlaybackError(): Boolean = PlaybackHttpFailure.isRetriablePlaybackError(this)
