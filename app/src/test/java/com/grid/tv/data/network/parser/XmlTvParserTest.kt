package com.grid.tv.data.network.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

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

    @Test
    fun parse_compactUtcOffsetWithoutSpace() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="bbc1.uk"><display-name>BBC One</display-name></channel>
              <programme channel="bbc1.uk" start="20240616120000+0000" stop="20240616130000+0000">
                <title>Midday News</title>
              </programme>
            </tv>
        """.trimIndent()

        val program = parser.parse(xml).programs.single()
        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.JUNE, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, program.startTime)
        assertTrue(program.endTime > program.startTime)
    }

    @Test
    fun parse_zuluSuffixTimestamp() {
        val normalized = XmlTvParser.normalizeXmlTvTimestamp("20240616120000Z")
        assertEquals("20240616120000 +0000", normalized)
    }

    @Test
    fun stableProgramId_isDeterministic() {
        val a = XmlTvParser.stableProgramId("bbc1.uk", 1_718_534_400_000L)
        val b = XmlTvParser.stableProgramId("bbc1.uk", 1_718_534_400_000L)
        assertEquals(a, b)
        assertTrue(a > 0L)
    }

    private fun assertEqualsApprox(expected: Long, actual: Long, toleranceMs: Long) {
        assertTrue(
            "expected=$expected actual=$actual tolerance=$toleranceMs",
            kotlin.math.abs(expected - actual) <= toleranceMs
        )
    }
}
