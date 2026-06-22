package com.grid.tv.domain.model

import com.grid.tv.feature.health.intelligence.StreamSourceId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelStreamsHealthOrderTest {

    private val channel = Channel(
        id = 1,
        number = 1,
        name = "News",
        group = "General",
        logoUrl = null,
        epgId = "news",
        streamUrl = "http://primary",
        backupStreamUrl = "http://backup1",
        backupStreamUrl2 = "http://backup2",
        backupStreamUrl3 = null,
        playlistId = 1,
        isFavorite = false
    )

    @Test
    fun sourceIdForUrl_mapsBackups() {
        assertEquals(StreamSourceId.PRIMARY.storageKey, channel.sourceIdForUrl("http://primary"))
        assertEquals(StreamSourceId.BACKUP_1.storageKey, channel.sourceIdForUrl("http://backup1"))
    }

    @Test
    fun orderStreamUrlsByHealthScores_promotesBestBackup() {
        val ordered = channel.orderStreamUrlsByHealthScores(
            mapOf(
                StreamSourceId.PRIMARY.storageKey to 60,
                StreamSourceId.BACKUP_1.storageKey to 95,
                StreamSourceId.BACKUP_2.storageKey to 70
            )
        )
        assertEquals("http://backup1", ordered.first())
        assertEquals("http://backup2", ordered[1])
        assertEquals("http://primary", ordered.last())
    }
}
