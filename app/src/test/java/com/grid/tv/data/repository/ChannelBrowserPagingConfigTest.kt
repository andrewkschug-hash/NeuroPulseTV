package com.grid.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelBrowserPagingConfigTest {

    @Test
    fun pageSize_isMultipleOfGridColumns() {
        assertEquals(0, IptvRepositoryImpl.CHANNEL_BROWSER_PAGE_SIZE % 4)
    }

    @Test
    fun prefetch_isLessThanPageSize() {
        assertTrue(IptvRepositoryImpl.CHANNEL_BROWSER_PREFETCH < IptvRepositoryImpl.CHANNEL_BROWSER_PAGE_SIZE)
    }

    @Test
    fun sportsFilterParams_nonSportsMode() {
        val (matchSports, ids) = sportsFilterParams(null)
        assertFalse(matchSports)
        assertEquals(listOf(""), ids)
    }

    @Test
    fun sportsFilterParams_emptySportsSetUsesSentinel() {
        val (matchSports, ids) = sportsFilterParams(emptySet())
        assertTrue(matchSports)
        assertEquals(listOf("__none__"), ids)
    }

    private fun sportsFilterParams(sportsEpgIds: Set<String>?): Pair<Boolean, List<String>> {
        if (sportsEpgIds == null) return false to listOf("")
        if (sportsEpgIds.isEmpty()) return true to listOf("__none__")
        return true to sportsEpgIds.toList().sorted()
    }
}
