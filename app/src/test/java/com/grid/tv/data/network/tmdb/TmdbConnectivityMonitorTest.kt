package com.grid.tv.data.network.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class TmdbConnectivityMonitorTest {

    @Test
    fun recordFailure_setsUserWarning() {
        val monitor = TmdbConnectivityMonitor()
        monitor.recordFailure(SSLHandshakeException("Chain validation failed"))
        assertEquals(TmdbConnectivityMonitor.USER_WARNING, monitor.warningMessage.value)
    }

    @Test
    fun recordSuccess_clearsWarning() {
        val monitor = TmdbConnectivityMonitor()
        monitor.recordFailure(SSLHandshakeException("Chain validation failed"))
        monitor.recordSuccess()
        assertNull(monitor.warningMessage.value)
    }
}
