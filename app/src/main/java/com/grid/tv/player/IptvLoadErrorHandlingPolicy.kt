package com.grid.tv.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * IPTV-tuned segment/manifest retry policy for flaky third-party HLS origins.
 *
 * Retries transient HTTP/CDN failures with 1s → 3s → 8s backoff; does not retry fatal 404s.
 */
@UnstableApi
class IptvLoadErrorHandlingPolicy(
    private val metrics: PlaybackMetricsLogger? = null
) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (!isRecoverable(loadErrorInfo)) {
            PlaybackHttpFailure.logLoadFailure(
                exception = loadErrorInfo.exception,
                streamUrl = loadErrorInfo.loadEventInfo.uri?.toString(),
                dataType = loadErrorInfo.mediaLoadData.dataType,
                errorCount = loadErrorInfo.errorCount
            )
            logRetry(loadErrorInfo, accepted = false, delayMs = C.TIME_UNSET)
            return C.TIME_UNSET
        }
        val attemptIndex = (loadErrorInfo.errorCount - 1).coerceAtLeast(0)
        if (attemptIndex >= MAX_RECOVERABLE_ATTEMPTS || attemptIndex >= RETRY_BACKOFF_MS.size) {
            logRetry(loadErrorInfo, accepted = false, delayMs = C.TIME_UNSET)
            return C.TIME_UNSET
        }
        val delay = RETRY_BACKOFF_MS[attemptIndex]
        logRetry(loadErrorInfo, accepted = true, delayMs = delay)
        return delay
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = when (dataType) {
        C.DATA_TYPE_MANIFEST -> MANIFEST_MIN_RETRIES
        C.DATA_TYPE_MEDIA -> SEGMENT_MIN_RETRIES
        else -> super.getMinimumLoadableRetryCount(dataType)
    }

    private fun isRecoverable(info: LoadErrorHandlingPolicy.LoadErrorInfo): Boolean {
        val cause = info.exception
        val httpCode = PlaybackHttpFailure.responseCode(cause)
        if (httpCode != null) {
            return PlaybackHttpFailure.isRetriableHttpStatus(httpCode)
        }
        return cause is SocketTimeoutException ||
            cause is SSLException ||
            cause is ConnectException ||
            cause is UnknownHostException ||
            cause is SocketException ||
            cause is EOFException
    }

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

    companion object {
        /** Single delayed retry for transient CDN/server failures. */
        val RETRY_BACKOFF_MS = longArrayOf(1_000L)

        const val MAX_RECOVERABLE_ATTEMPTS = 1

        /** At most one retry beyond the initial load attempt. */
        const val MANIFEST_MIN_RETRIES = 1
        const val SEGMENT_MIN_RETRIES = 1
    }
}
