package com.grid.tv.player

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * Shared HTTP playback failure classification and diagnostics for ExoPlayer load/failover paths.
 */
object PlaybackHttpFailure {

    private const val LOG_TAG = "PlaybackHttp"

    fun responseCode(throwable: Throwable?): Int? {
        var current = throwable
        while (current != null) {
            (current as? HttpDataSource.InvalidResponseCodeException)?.responseCode?.let { return it }
            current = current.cause
        }
        return null
    }

    fun responseCode(error: PlaybackException): Int? = responseCode(error.cause)

    fun invalidResponseException(throwable: Throwable?): HttpDataSource.InvalidResponseCodeException? {
        var current = throwable
        while (current != null) {
            (current as? HttpDataSource.InvalidResponseCodeException)?.let { return it }
            current = current.cause
        }
        return null
    }

    /** True for client/provider errors (including IPTV-specific 458) that must not be retried. */
    fun isRetriableHttpStatus(code: Int): Boolean = when (code) {
        408, 429 -> true
        in 500..599 -> true
        else -> false
    }

    fun isFatalHttpStatus(code: Int): Boolean = code >= 400 && !isRetriableHttpStatus(code)

    fun isRetriablePlaybackError(error: PlaybackException): Boolean {
        if (error.isCodecCapabilityError()) return false
        val httpCode = responseCode(error) ?: return error.errorCode != PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        return isRetriableHttpStatus(httpCode)
    }

    fun logHttpFailure(
        error: PlaybackException,
        streamUrl: String?,
        tag: String = LOG_TAG
    ) {
        val httpError = invalidResponseException(error.cause) ?: run {
            Log.w(
                tag,
                "playback_http_failure code=${error.errorCode} url=${streamUrl?.take(160)} msg=${error.message}"
            )
            return
        }
        val headerSummary = httpError.headerFields.entries
            .joinToString(separator = "; ") { (name, values) ->
                "$name=${values.joinToString(",") { it.take(120) }}"
            }
            .take(500)
        Log.w(
            tag,
            "playback_http_failure status=${httpError.responseCode} " +
                "message=${httpError.responseMessage?.take(120)} " +
                "url=${streamUrl?.take(160)} headers=$headerSummary"
        )
    }

    fun logLoadFailure(
        exception: Throwable,
        streamUrl: String?,
        dataType: Int,
        errorCount: Int
    ) {
        val httpError = invalidResponseException(exception)
        if (httpError == null) {
            Log.w(
                LOG_TAG,
                "load_failure dataType=$dataType attempt=$errorCount url=${streamUrl?.take(160)} " +
                    "error=${exception.message?.take(160)}"
            )
            return
        }
        val headerSummary = httpError.headerFields.entries
            .joinToString(separator = "; ") { (name, values) ->
                "$name=${values.joinToString(",") { it.take(120) }}"
            }
            .take(500)
        Log.w(
            LOG_TAG,
            "load_failure dataType=$dataType attempt=$errorCount status=${httpError.responseCode} " +
                "message=${httpError.responseMessage?.take(120)} url=${streamUrl?.take(160)} " +
                "headers=$headerSummary"
        )
    }
}
