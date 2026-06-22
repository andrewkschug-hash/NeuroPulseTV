package com.grid.tv.ui.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object VodHub : Routes("vod/{initialTab}/{seriesId}") {
        fun build(initialTab: Int = 0, seriesId: Long = -1L): String = "vod/$initialTab/$seriesId"
    }
    /** @deprecated Use [VodHub] — kept for deep links redirecting to tab 0 */
    data object Movies : Routes("movies")
    data object Player : Routes("player/{channelId}") {
        fun build(channelId: Long): String = "player/$channelId"
    }
    data object DirectPlayer : Routes("direct-player/{recordingId}/{recordedAt}/{resume}/{resumePositionMs}/{title}/{url}") {
        fun build(
            title: String,
            url: String,
            recordingId: Long = 0L,
            recordedAt: Long = 0L,
            resume: Boolean = false,
            resumePositionMs: Long = 0L
        ): String {
            val t = android.net.Uri.encode(title)
            val u = android.net.Uri.encode(url)
            val resumeFlag = if (resume) 1 else 0
            val resumeMs = resumePositionMs.coerceAtLeast(0L)
            return "direct-player/$recordingId/$recordedAt/$resumeFlag/$resumeMs/$t/$u"
        }
    }
    data object Series : Routes("series/{seriesId}") {
        fun build(seriesId: Long = -1L): String = "series/$seriesId"
    }
    data object Settings : Routes("settings")
    data object EpgResolver : Routes("epg-resolver")
    data object Recordings : Routes("recordings")
    data object Multiview : Routes("multiview/{channelId}") {
        fun build(channelId: Long = 0L): String = "multiview/$channelId"
    }
    data object SplitView : Routes("split/{channelId}") {
        fun build(channelId: Long): String = "split/$channelId"
    }
}
