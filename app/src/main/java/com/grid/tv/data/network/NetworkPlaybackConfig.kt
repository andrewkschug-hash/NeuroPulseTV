package com.grid.tv.data.network

import com.grid.tv.domain.model.AppSettings

/**
 * Snapshot of user network settings applied to stream playback.
 */
data class NetworkPlaybackConfig(
    val useProxy: Boolean,
    val proxyUrl: String,
    val connectionTimeoutSeconds: Int
) {
    fun toLogLine(): String =
        "proxy=${if (useProxy && proxyUrl.isNotBlank()) "on" else "off"} " +
            "connectTimeoutSec=$connectionTimeoutSeconds dns=IptvDns"
}

fun AppSettings.toNetworkPlaybackConfig(): NetworkPlaybackConfig =
    NetworkPlaybackConfig(
        useProxy = useProxy,
        proxyUrl = proxyUrl.trim(),
        connectionTimeoutSeconds = connectionTimeoutSeconds
    )
