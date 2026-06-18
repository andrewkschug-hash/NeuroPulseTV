package com.grid.tv.domain.model

/** Summary returned by [com.grid.tv.domain.repository.IptvRepository.refreshEpgNow] for diagnostics. */
data class EpgRefreshReport(
    val playlistsTotal: Int,
    val attempts: List<EpgFetchAttempt>
) {
    val urlsAttempted: Int get() = attempts.count { it.url != null }
    val totalBytesReceived: Long get() = attempts.sumOf { it.bytesReceived.toLong() }
    val totalChannelsStored: Int get() = attempts.sumOf { it.channelsStored }
    val totalProgrammesStored: Int get() = attempts.sumOf { it.programmesStored }
    val failures: List<EpgFetchAttempt> get() = attempts.filter { it.error != null || it.skippedReason != null }
}

data class EpgFetchAttempt(
    val playlistName: String,
    val playlistId: Long,
    val endpointKind: String?,
    val url: String? = null,
    val skippedReason: String? = null,
    val httpCode: Int? = null,
    val bytesReceived: Int = 0,
    val channelsParsed: Int = 0,
    val programmesParsed: Int = 0,
    val channelsStored: Int = 0,
    val programmesStored: Int = 0,
    val error: String? = null
)
