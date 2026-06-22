package com.grid.tv.data.network

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTextFetcherRetryTest {

    @Test
    fun connectionAbort_isRetryable() {
        val error = IOException(
            "Failed writing EPG body to /cache/epg.tmp: Software caused connection abort",
            SocketException("Software caused connection abort")
        )
        assertTrue(RemoteTextFetcher.isRetryableNetworkError(error))
        assertTrue(RemoteTextFetcher.isRetryableErrorMessage(error.message))
    }

    @Test
    fun eof_isRetryable() {
        assertTrue(RemoteTextFetcher.isRetryableNetworkError(EOFException()))
    }

    @Test
    fun httpError_isNotRetryable() {
        assertFalse(RemoteTextFetcher.isRetryableErrorMessage("HTTP 404"))
    }

    @Test
    fun cacheExhaustion_isNotRetryable() {
        assertFalse(
            RemoteTextFetcher.isRetryableNetworkError(
                IOException("Insufficient cache space for EPG download (64MB free, need at least 128MB headroom)")
            )
        )
    }
}
