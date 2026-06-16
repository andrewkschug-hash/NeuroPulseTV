package com.neuropulse.tv.data.network.tmdb

import com.neuropulse.tv.data.network.AppHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TmdbIntegrationSmokeTest {

    @Test
    fun findGodfatherByImdbId() = runBlocking {
        val service = TmdbService(AppHttpClient())
        val enrichment = service.enrichByImdb("tt0068646")

        assertNotNull("TMDB enrichment should resolve for IMDb tt0068646", enrichment)
        assertEquals("movie", enrichment?.mediaType)
        assertNotNull(enrichment?.tmdbId)
        assertNotNull(enrichment?.title)
    }
}
