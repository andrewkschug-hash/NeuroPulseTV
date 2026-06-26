package com.grid.tv.feature.scanner

import com.grid.tv.player.PlaybackHttpDataSourceFactory
import okhttp3.Request

/** Shared IPTV channel-probe HTTP shape — mimics ExoPlayer/Android TV playback clients. */
internal object ChannelProbeRequests {
  /** VLC / Android TV style UA accepted by most IPTV middleware. */
  const val USER_AGENT: String = PlaybackHttpDataSourceFactory.STREAM_USER_AGENT

  /** Fetch only the manifest / stream header — avoids full segment downloads. */
  const val RANGE_BYTES: String = "bytes=0-1024"

  const val RANGE_HEADER: String = "Range"

  fun buildGet(url: String, range: String = RANGE_BYTES): Request =
    Request.Builder()
      .url(url)
      .get()
      .header("User-Agent", USER_AGENT)
      .header("Accept", "*/*")
      .header(RANGE_HEADER, range)
      .build()
}
