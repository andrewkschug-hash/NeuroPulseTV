package com.grid.tv.data.network.parser

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class XmlTvParserTest {

    private val parser = XmlTvParser()

    @Test
    fun parse_localTimeWithoutTimezoneUsesDeviceTimezone() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="test.ch"><display-name>Test</display-name></channel>
              <programme channel="test.ch" start="20240616120000" stop="20240616130000">
                <title>Local News</title>
              </programme>
            </tv>
        """.trimIndent()

        val parsed = parser.parse(xml)
        val program = parsed.programs.single()

        val expected = Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertTrue(program.startTime > 0L)
        assertEqualsApprox(expected, program.startTime, 60_000L)
    }

    private fun assertEqualsApprox(expected: Long, actual: Long, toleranceMs: Long) {
        assertTrue(
            "expected=$expected actual=$actual tolerance=$toleranceMs",
            kotlin.math.abs(expected - actual) <= toleranceMs
        )
    }
}
