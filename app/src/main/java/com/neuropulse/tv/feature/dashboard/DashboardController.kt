package com.neuropulse.tv.feature.dashboard

import fi.iki.elonen.NanoHTTPD
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardController @Inject constructor() {
    private var server: DashboardServer? = null

    fun startOrGetUrl(): String {
        if (server == null) {
            server = DashboardServer(
                port = 8080,
                providers = object : DashboardProviders {
                    override fun index(): String = "<html><body><h2>StreamFlow Dashboard</h2><ul><li><a href='/playlists'>Playlists</a></li><li><a href='/favorites'>Favorites</a></li><li><a href='/health'>Health</a></li><li><a href='/history'>History</a></li><li><a href='/epg-refresh'>Refresh EPG</a></li></ul></body></html>"
                    override fun playlists(): String = "<html><body><h3>Manage playlists via TV app settings.</h3></body></html>"
                    override fun favorites(): String = "<html><body><h3>Favorites available in TV app.</h3></body></html>"
                    override fun health(): String = "<html><body><h3>Stream health report available in TV settings.</h3></body></html>"
                    override fun history(): String = "<html><body><h3>Watch history available in TV app.</h3></body></html>"
                    override fun triggerEpgRefresh() = Unit
                }
            ).apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        }
        return "http://device-ip:8080"
    }
}
