package com.grid.tv.domain.epg

import com.grid.tv.data.db.dao.CanonicalChannelDao
import com.grid.tv.data.db.dao.EpgLearnedMappingDao
import com.grid.tv.data.db.entity.CanonicalChannelEntity
import com.grid.tv.data.db.entity.EpgLearnedMappingEntity
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class EpgMatcherTest {

    private lateinit var canonicalDao: CanonicalChannelDao
    private lateinit var learnedDao: EpgLearnedMappingDao
    private lateinit var matcher: EpgMatcher
    private val normalizer = ChannelNameNormalizer()

    @Before
    fun setup() {
        canonicalDao = mockk(relaxed = true)
        learnedDao = mockk(relaxed = true)
        matcher = EpgMatcher(normalizer, canonicalDao, learnedDao)
    }

    @Test
    fun match_prefersTvgId() = runTest {
        val candidates = listOf(
            epgSource("espn.us", "ESPN", "epg.best"),
            epgSource("espn2.us", "ESPN 2", "epg.best")
        )
        val outcome = matcher.match("Random Name", "espn.us", candidates)
        assertEquals(EpgMatchReason.TVG_ID_EXACT, outcome.best?.reason)
        assertEquals("espn.us", outcome.best?.epgId)
    }

    @Test
    fun match_usesLearnedMapping() = runTest {
        coEvery { learnedDao.get("espn") } returns EpgLearnedMappingEntity(
            normalizedOriginalName = "espn",
            originalNameSample = "ESPN HD",
            epgId = "espn.us",
            epgDisplayName = "ESPN",
            source = "learned"
        )
        val candidates = listOf(epgSource("espn.us", "ESPN", "epg.best"))
        val outcome = matcher.match("ESPN HD", null, candidates)
        assertEquals(EpgMatchReason.LEARNED_MAPPING, outcome.best?.reason)
    }

    @Test
    fun match_usesCanonicalAlias() = runTest {
        coEvery { canonicalDao.all() } returns listOf(
            CanonicalChannelEntity(
                id = "espn_us",
                canonicalName = "ESPN",
                country = "US",
                epgId = "espn.us",
                category = "Sports",
                aliases = "ESPN HD|ESPN East",
                normalizedName = "espn"
            )
        )
        val candidates = listOf(epgSource("espn.us", "ESPN", "epg.best"))
        val outcome = matcher.match("ESPN East", null, candidates)
        assertEquals(EpgMatchReason.CANONICAL_ALIAS, outcome.best?.reason)
    }

    @Test
    fun match_normalizedExact() = runTest {
        coEvery { canonicalDao.all() } returns emptyList()
        val candidates = listOf(epgSource("tsn5.ca", "TSN 5", "i.mjh.nz/ca"))
        val outcome = matcher.match("TSN 5 CA", null, candidates)
        assertNotNull(outcome.best)
        assertEquals(EpgMatchReason.NORMALIZED_EXACT, outcome.best?.reason)
    }

    private fun epgSource(epgId: String, name: String, source: String) = EpgSourceChannelEntity(
        epgId = epgId,
        displayName = name,
        normalizedName = normalizer.normalize(name),
        source = source,
        logoUrl = null,
        cachedAt = 0
    )
}
