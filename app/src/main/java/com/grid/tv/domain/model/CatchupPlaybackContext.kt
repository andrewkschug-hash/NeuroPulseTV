package com.grid.tv.domain.model

data class CatchupPlaybackSession(
    val programTitle: String,
    val channelName: String,
    val channelId: Long,
    val liveStreamUrl: String,
    val programStartMs: Long,
    val programEndMs: Long,
    val replayUrl: String
)

object CatchupPlaybackContext {
    @Volatile
    private var pending: CatchupPlaybackSession? = null

    fun stage(session: CatchupPlaybackSession) {
        pending = session
    }

    fun consume(): CatchupPlaybackSession? = pending.also { pending = null }

    fun peek(): CatchupPlaybackSession? = pending
}
