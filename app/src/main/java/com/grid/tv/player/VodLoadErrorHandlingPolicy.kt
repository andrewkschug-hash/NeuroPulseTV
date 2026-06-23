package com.grid.tv.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * On-demand playback load policy: fail fast on HTTP errors (including IPTV 458/520)
 * with no automatic retries or failover storms.
 */
@UnstableApi
class VodLoadErrorHandlingPolicy(
    private val metrics: PlaybackMetricsLogger? = null
) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        PlaybackHttpFailure.logLoadFailure(
            exception = loadErrorInfo.exception,
            streamUrl = loadErrorInfo.loadEventInfo.uri?.toString(),
            dataType = loadErrorInfo.mediaLoadData.dataType,
            errorCount = loadErrorInfo.errorCount
        )
        logRetry(loadErrorInfo, accepted = false, delayMs = C.TIME_UNSET)
        return C.TIME_UNSET
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 0

    private fun logRetry(
        info: LoadErrorHandlingPolicy.LoadErrorInfo,
        accepted: Boolean,
        delayMs: Long
    ) {
        val httpCode = PlaybackHttpFailure.responseCode(info.exception)
        metrics?.logLoadRetry(
            dataType = info.mediaLoadData.dataType,
            errorCount = info.errorCount,
            httpStatus = httpCode,
            delayMs = delayMs,
            accepted = accepted,
            uri = info.loadEventInfo.uri?.toString()
        )
    }
}
