package com.grid.tv.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonParseMetricsTest {

    @After
    fun tearDown() {
        PerformanceAudit.resetJsonParseMetricsForTests()
    }

    @Test
    fun onIoThread_runsBlockAndReturnsValue() {
        val result = JsonParseMetrics.onIoThread(label = "test_parse", itemCount = 3) {
            "{\"ok\":true}"
        }
        assertEquals("{\"ok\":true}", result)
    }
}
