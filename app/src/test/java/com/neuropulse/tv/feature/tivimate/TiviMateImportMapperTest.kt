package com.neuropulse.tv.feature.tivimate

import org.junit.Assert.assertEquals
import org.junit.Test

class TiviMateImportMapperTest {
    @Test
    fun mapPlaylist_handlesBlankName() {
        val mapper = TiviMateImportMapper()
        val result = mapper.mapPlaylist("", "http://playlist")
        assertEquals("Imported Playlist", result.name)
    }
}
