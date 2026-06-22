package com.grid.tv.player

import com.grid.tv.domain.model.BufferSize

/**
 * IPTV-tuned ExoPlayer buffer durations for channel surfing on constrained devices (e.g. CCwGTV).
 *
 * User [BufferSize] setting selects the profile. Values are centralized here for tuning and audit.
 */
object IptvBufferProfiles {

    data class Durations(
        val profileName: String,
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int
    ) {
        fun toLogString(): String =
            "profile=$profileName minMs=$minBufferMs maxMs=$maxBufferMs " +
                "startMs=$bufferForPlaybackMs rebufferMs=$bufferForPlaybackAfterRebufferMs"
    }

    /** Previous TV defaults: STABLE priority min + MEDIUM bufferSize max (audit baseline). */
    val LEGACY_TV_STABLE_MEDIUM: Durations = Durations(
        profileName = "LEGACY_TV_STABLE_MEDIUM",
        minBufferMs = 45_000,
        maxBufferMs = 1_800_000,
        bufferForPlaybackMs = 3_500,
        bufferForPlaybackAfterRebufferMs = 7_000
    )

    fun forPriority(priority: PlaybackStartupPriority): Durations = when (priority) {
        PlaybackStartupPriority.FAST -> Durations(
            profileName = "FAST",
            minBufferMs = 5_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 2_500
        )
        PlaybackStartupPriority.BALANCED -> Durations(
            profileName = "BALANCED",
            minBufferMs = 15_000,
            maxBufferMs = 120_000,
            bufferForPlaybackMs = 2_500,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
        PlaybackStartupPriority.STABLE -> Durations(
            profileName = "STABLE",
            minBufferMs = 25_000,
            maxBufferMs = 300_000,
            bufferForPlaybackMs = 3_500,
            bufferForPlaybackAfterRebufferMs = 8_000
        )
    }

    fun bufferSizeToPriority(bufferSize: BufferSize): PlaybackStartupPriority = when (bufferSize) {
        BufferSize.LOW -> PlaybackStartupPriority.FAST
        BufferSize.MEDIUM -> PlaybackStartupPriority.BALANCED
        BufferSize.HIGH -> PlaybackStartupPriority.STABLE
    }

    /**
     * @param startupPriority When non-null (preview/direct player), that profile wins.
     *        Otherwise [bufferSize] selects FAST / BALANCED / STABLE.
     */
    fun resolve(
        bufferSize: BufferSize,
        startupPriority: PlaybackStartupPriority?,
        isLowEndDevice: Boolean,
        isTelevision: Boolean = false
    ): Durations {
        val userPriority = startupPriority ?: bufferSizeToPriority(bufferSize)
        val priority = when {
            isLowEndDevice && isTelevision && userPriority == PlaybackStartupPriority.STABLE ->
                PlaybackStartupPriority.BALANCED
            isTelevision && userPriority != PlaybackStartupPriority.FAST && !isLowEndDevice ->
                PlaybackStartupPriority.STABLE
            else -> userPriority
        }
        val base = forPriority(priority)
        if (!isLowEndDevice) return base
        return base.copy(
            minBufferMs = base.minBufferMs.coerceAtMost(10_000),
            maxBufferMs = base.maxBufferMs.coerceAtMost(60_000),
            profileName = "${base.profileName}_LOW_END"
        )
    }

    fun maxBufferMsFor(bufferSize: BufferSize): Long =
        forPriority(bufferSizeToPriority(bufferSize)).maxBufferMs.toLong()
}
