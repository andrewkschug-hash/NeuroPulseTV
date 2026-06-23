package com.grid.tv.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

/**
 * Live IPTV [MediaItem] with conservative live-edge offsets and HLS mime hints when applicable.
 */
object IptvLiveMediaItem {

    /** Target ~20s behind live edge; avoids aggressive live-window chasing on flaky HLS origins. */
    private const val TARGET_LIVE_OFFSET_MS = 20_000L
    private const val MIN_LIVE_OFFSET_MS = 10_000L
    private const val MAX_LIVE_OFFSET_MS = 45_000L

    fun build(
        streamUrl: String,
        registry: IptvStreamFormatRegistry? = null,
        forceLiveConfiguration: Boolean = true,
        formatOverride: IptvStreamFormat? = null
    ): MediaItem {
        val format = formatOverride
            ?: registry?.get(streamUrl)
            ?: StreamTypeDetector.classifyUrlPath(streamUrl)

        val builder = MediaItem.Builder().setUri(streamUrl)
        val formatKey = when (format) {
            IptvStreamFormat.HLS -> "hls"
            IptvStreamFormat.PROGRESSIVE -> "progressive"
            IptvStreamFormat.UNKNOWN -> "unknown"
        }
        builder.setMediaMetadata(
            MediaMetadata.Builder()
                .setExtras(
                    android.os.Bundle().apply {
                        putString(
                            IptvStreamFormatDetector.METADATA_KEY_PLAYBACK_SCOPE,
                            IptvPlaybackScope.LIVE.name
                        )
                        putString(IptvStreamFormatDetector.METADATA_KEY_STREAM_FORMAT, formatKey)
                    }
                )
                .build()
        )

        if (format.isHls()) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        if (forceLiveConfiguration && format.isHls()) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(TARGET_LIVE_OFFSET_MS)
                    .setMinOffsetMs(MIN_LIVE_OFFSET_MS)
                    .setMaxOffsetMs(MAX_LIVE_OFFSET_MS)
                    .build()
            )
        }

        return builder.build()
    }
}
