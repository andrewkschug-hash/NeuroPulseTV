package com.grid.tv.feature.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgProgramTextDecoderTest {

    @Test
    fun decodesXtreamBase64Title() {
        assertEquals(
            "Walking With Dinosaurs",
            EpgProgramTextDecoder.decode("V2Fsa2luZyBXaXRoIERpbm9zYXVycw==")
        )
    }

    @Test
    fun decodesXtreamBase64Description() {
        assertEquals(
            "Live: Bloomberg",
            EpgProgramTextDecoder.decode("TGl2ZTogQmxvb21iZXJn")
        )
    }

    @Test
    fun leavesPlainTextUntouched() {
        assertEquals("Family Feud", EpgProgramTextDecoder.decode("Family Feud"))
        assertEquals("BIG3 Basketball", EpgProgramTextDecoder.decode("BIG3 Basketball"))
    }

    @Test
    fun leavesShortAlphanumericUntouched() {
        assertEquals("News", EpgProgramTextDecoder.decode("News"))
    }
}
