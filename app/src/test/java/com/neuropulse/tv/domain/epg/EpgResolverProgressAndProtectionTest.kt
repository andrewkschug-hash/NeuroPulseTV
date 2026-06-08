package com.neuropulse.tv.domain.epg

import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.EpgResolutionSuggestionDao
import com.neuropulse.tv.data.db.dao.EpgSourceChannelDao
import com.neuropulse.tv.data.db.dao.ProgramDao
import com.neuropulse.tv.data.db.entity.ChannelEntity
import com.neuropulse.tv.data.db.entity.EpgSourceChannelEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpgResolverProgressAndProtectionTest {

    private lateinit var channelDao: ChannelDao
    private lateinit var programDao: ProgramDao
    private lateinit var sourceDao: EpgSourceChannelDao
    private lateinit var suggestionDao: EpgResolutionSuggestionDao
    private lateinit var engine: EpgResolverEngine

    @Before
    fun setup() {
        channelDao = mockk(relaxed = true)
        programDao = mockk(relaxed = true)
        sourceDao = mockk(relaxed = true)
        suggestionDao = mockk(relaxed = true)
        engine = EpgResolverEngine(channelDao, programDao, sourceDao, suggestionDao, OkHttpClient())
    }

    @Test
    fun resolveAllUnmatched_emitsProgress() = runTest {
        val row = ChannelEntity(id = 1, number = 1, name = "CNN", groupName = "News", logoUrl = null, epgId = null, streamUrl = "u", playlistId = 1)
        coEvery { channelDao.unresolvedBatch(50, 0, any(), any()) } returns listOf(row)
        coEvery { channelDao.unresolvedBatch(50, 50, any(), any()) } returns emptyList()
        coEvery { programDao.distinctChannelEpgIds() } returns listOf("cnn")
        coEvery { sourceDao.bySource(any()) } returns emptyList()

        val items = engine.resolveAllUnmatched().toList()
        assertTrue(items.isNotEmpty())
        assertTrue(items.last().completed == 1)
        coVerify { channelDao.applyResolution(1, any(), any(), any(), any(), any()) }
    }

    @Test
    fun protectedStatuses_neverProcessed() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        assertTrue(!engine.shouldProcessStatus("CONFIRMED", 0, cutoff))
        assertTrue(!engine.shouldProcessStatus("MANUAL", 0, cutoff))
        assertTrue(!engine.shouldProcessStatus("UNRESOLVABLE", System.currentTimeMillis(), cutoff))
        assertTrue(engine.shouldProcessStatus("UNRESOLVABLE", 0, cutoff))
        assertTrue(engine.shouldProcessStatus("UNRESOLVED", 0, cutoff))
    }
}
