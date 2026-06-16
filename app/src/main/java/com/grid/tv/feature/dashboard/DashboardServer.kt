package com.grid.tv.feature.dashboard

import fi.iki.elonen.NanoHTTPD

class DashboardServer(
    port: Int = 8080,
    private val providers: DashboardProviders
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri
        val html = when (path) {
            "/", "/index" -> providers.index()
            "/playlists" -> providers.playlists()
            "/favorites" -> providers.favorites()
            "/health" -> providers.health()
            "/history" -> providers.history()
            "/epg-refresh" -> {
                providers.triggerEpgRefresh()
                "<html><body><h2>EPG refresh triggered</h2></body></html>"
            }
            else -> "<html><body><h2>Not Found</h2></body></html>"
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}

interface DashboardProviders {
    fun index(): String
    fun playlists(): String
    fun favorites(): String
    fun health(): String
    fun history(): String
    fun triggerEpgRefresh()
}
