package com.grid.tv.domain.model

/** Playlist-scoped VOD watch progress identity (replaces bare streamId keys). */
data class VodProgressKey(
    val playlistId: Long,
    val streamId: Long
) {
    fun asPair(): Pair<Long, Long> = playlistId to streamId

    companion object {
        fun of(playlistId: Long, streamId: Long): VodProgressKey =
            VodProgressKey(playlistId.coerceAtLeast(0L), streamId)
    }
}

object VodProgressKeys {
    private const val STREAM_MASK = 0xFFFFFFFFL

    /** Encodes VOD rows in [profile_watch_history.channelId] (negative values). */
    fun syntheticChannelId(playlistId: Long, streamId: Long): Long {
        if (playlistId <= 0L) return -streamId
        val packed = ((playlistId and STREAM_MASK) shl 32) or (streamId and STREAM_MASK)
        return -packed
    }

    fun decode(channelId: Long): VodProgressKey {
        require(channelId < 0L) { "VOD channelId must be negative: $channelId" }
        val packed = -channelId
        if (packed <= STREAM_MASK) {
            return VodProgressKey(playlistId = 0L, streamId = packed)
        }
        return VodProgressKey(
            playlistId = packed ushr 32,
            streamId = packed and STREAM_MASK
        )
    }
}
