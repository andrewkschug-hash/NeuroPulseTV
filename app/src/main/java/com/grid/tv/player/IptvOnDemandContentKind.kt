package com.grid.tv.player

/**
 * On-demand playback content classification for URL/format routing.
 * Keeps VOD, series, catch-up, and local files off live-IPTV heuristics.
 */
enum class IptvOnDemandContentKind {
    VOD_MOVIE,
    VOD_SERIES,
    CATCHUP,
    RECORDING,
    LOCAL_FILE
}

enum class IptvPlaybackScope {
    LIVE,
    ON_DEMAND
}
