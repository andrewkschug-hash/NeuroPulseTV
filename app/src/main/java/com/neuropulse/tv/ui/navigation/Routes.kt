package com.neuropulse.tv.ui.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Player : Routes("player/{channelId}") {
        fun build(channelId: Long): String = "player/$channelId"
    }
    data object DirectPlayer : Routes("direct-player/{title}/{url}") {
        fun build(title: String, url: String): String {
            val t = android.net.Uri.encode(title)
            val u = android.net.Uri.encode(url)
            return "direct-player/$t/$u"
        }
    }
    data object Series : Routes("series/{seriesId}") {
        fun build(seriesId: Long = -1L): String = "series/$seriesId"
    }
    data object Settings : Routes("settings")
    data object EpgResolver : Routes("epg-resolver")
    data object Recordings : Routes("recordings")
    data object Multiview : Routes("multiview")
    data object SplitView : Routes("split/{channelId}") {
        fun build(channelId: Long): String = "split/$channelId"
    }
}
