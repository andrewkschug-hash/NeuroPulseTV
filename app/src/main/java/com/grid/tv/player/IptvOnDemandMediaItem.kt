package com.grid.tv.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.grid.tv.domain.model.VodPlaybackMeta

/**
 * VOD / series / catch-up [MediaItem] without live-edge configuration.
 * Tags on-demand scope so [PlaybackHttpDataSourceFactory] skips live HLS heuristics.
 */
object IptvOnDemandMediaItem {

    fun build(
        url: String,
        contentKind: IptvOnDemandContentKind,
        registry: IptvStreamFormatRegistry? = null,
        formatOverride: IptvStreamFormat? = null
    ): MediaItem {
        val format = formatOverride
            ?: registry?.get(url)
            ?: IptvStreamFormatDetector.resolveForOnDemandPlayback(
                url = url,
                contentKind = contentKind,
                registry = registry
            )
        val extras = Bundle().apply {
            putString(IptvStreamFormatDetector.METADATA_KEY_PLAYBACK_SCOPE, IptvPlaybackScope.ON_DEMAND.name)
            putString(IptvStreamFormatDetector.METADATA_KEY_CONTENT_KIND, contentKind.name)
            putString(
                IptvStreamFormatDetector.METADATA_KEY_STREAM_FORMAT,
                if (format.isHls()) "hls" else "progressive"
            )
        }
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(extras)
                    .build()
            )
        if (format.isHls()) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    fun contentKindFor(
        isCatchupPlayback: Boolean,
        isRecordedPlayback: Boolean,
        vodMeta: VodPlaybackMeta
    ): IptvOnDemandContentKind = when {
        isRecordedPlayback -> IptvOnDemandContentKind.RECORDING
        isCatchupPlayback -> IptvOnDemandContentKind.CATCHUP
        vodMeta.isSeries -> IptvOnDemandContentKind.VOD_SERIES
        else -> IptvOnDemandContentKind.VOD_MOVIE
    }

    fun readContentKind(mediaItem: MediaItem?): IptvOnDemandContentKind? {
        val raw = mediaItem?.mediaMetadata?.extras
            ?.getString(IptvStreamFormatDetector.METADATA_KEY_CONTENT_KIND)
            ?: return null
        return runCatching { IptvOnDemandContentKind.valueOf(raw) }.getOrNull()
    }

    fun readPlaybackScope(mediaItem: MediaItem?): IptvPlaybackScope {
        val raw = mediaItem?.mediaMetadata?.extras
            ?.getString(IptvStreamFormatDetector.METADATA_KEY_PLAYBACK_SCOPE)
            ?: return IptvPlaybackScope.LIVE
        return when (raw.uppercase()) {
            IptvPlaybackScope.ON_DEMAND.name -> IptvPlaybackScope.ON_DEMAND
            else -> IptvPlaybackScope.LIVE
        }
    }
}
