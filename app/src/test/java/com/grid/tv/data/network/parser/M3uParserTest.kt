package com.grid.tv.data.network.parser

import kotlinx.coroutines.flow.toList
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

        val channels = M3uParser().parseAsFlow(1, text).toList()
            .flatMap { it.batch }
        assertEquals("https://backup.test/1.m3u8", channels.first().backupStreamUrl)
    }
}
