package com.grid.tv.data.network.tmdb

import com.grid.tv.BuildConfig
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.data.security.SecureCredentialStore
import com.grid.tv.feature.enrichment.ApiKeyBootstrap
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class TmdbIntegrationSmokeTest {

    @Test
    fun findGodfatherByImdbId() = runBlocking {
        assumeTrue(BuildConfig.TMDB_API_KEY.isNotBlank())

        val store = mockk<SecureCredentialStore>(relaxed = true)
        every { store.getTmdbApiKey() } returns BuildConfig.TMDB_API_KEY
        val bootstrap = ApiKeyBootstrap(store)
        val service = TmdbService(AppHttpClient(), store, bootstrap)
        val enrichment = service.enrichByImdb("tt0068646")

        assertNotNull("TMDB enrichment should resolve for IMDb tt0068646", enrichment)
        assertEquals("movie", enrichment?.mediaType)
        assertNotNull(enrichment?.tmdbId)
        assertNotNull(enrichment?.title)
    }
}
