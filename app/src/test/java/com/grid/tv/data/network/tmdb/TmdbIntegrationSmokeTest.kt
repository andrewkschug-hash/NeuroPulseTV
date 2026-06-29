package com.grid.tv.data.network.tmdb

import com.grid.tv.BuildConfig
import com.grid.tv.data.network.testAppHttpClient
import com.grid.tv.data.security.SecureCredentialStore
import com.grid.tv.feature.enrichment.ApiKeyBootstrap
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLException

class TmdbIntegrationSmokeTest {

    @Test
    fun findGodfatherByImdbId() = runBlocking {
        assumeTrue(BuildConfig.TMDB_API_KEY.isNotBlank())

        val store = mockk<SecureCredentialStore>(relaxed = true)
        every { store.getTmdbApiKey() } returns BuildConfig.TMDB_API_KEY
        val bootstrap = ApiKeyBootstrap(store)
        val monitor = TmdbConnectivityMonitor()
        val service = TmdbService(testAppHttpClient(), store, monitor, bootstrap)
        try {
            val enrichment = service.enrichByImdb("tt0068646")
            assertNotNull("TMDB enrichment should resolve for IMDb tt0068646", enrichment)
            assertEquals("movie", enrichment?.mediaType)
            assertNotNull(enrichment?.tmdbId)
            assertNotNull(enrichment?.title)
        } catch (e: SSLException) {
            assumeNoException(e)
        } catch (e: IOException) {
            assumeNoException(e)
        }
    }
}
