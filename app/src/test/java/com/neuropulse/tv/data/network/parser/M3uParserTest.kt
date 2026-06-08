package com.neuropulse.tv.data.network.parser

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesBackupUrlFromExtinf() = runBlocking {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id=\"ch1\" tvg-name=\"Test\" backup-url=\"https://backup.test/1.m3u8\",Test
            https://main.test/1.m3u8
        """.trimIndent()

        val final = M3uParser().parseAsFlow(1, text).first { it.done }
        assertEquals("https://backup.test/1.m3u8", final.channels.first().backupStreamUrl)
    }
}
