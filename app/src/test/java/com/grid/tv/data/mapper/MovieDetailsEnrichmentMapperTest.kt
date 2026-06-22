package com.grid.tv.data.mapper

import com.grid.tv.domain.model.MovieDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieDetailsEnrichmentMapperTest {

    @Test
    fun toTmdbEnrichment_mapsMovieFields() {
        val enrichment = MovieDetailsEnrichmentMapper.toTmdbEnrichment(
            MovieDetails(
                id = 42L,
                title = "Test Movie",
                overview = "Overview",
                tagline = "Tagline",
                releaseDate = "2024-01-01",
                releaseYear = 2024,
                runtimeMinutes = 120,
                voteAverage = 8.2,
                voteCount = 100,
                genres = listOf("Action", "Drama"),
                posterUrl = "https://example.com/poster.jpg",
                backdropUrl = "https://example.com/backdrop.jpg",
                imdbId = "tt123",
                status = "Released",
                originalLanguage = "en"
            )
        )

        assertEquals(42L, enrichment.tmdbId)
        assertEquals("movie", enrichment.mediaType)
        assertEquals("Test Movie", enrichment.title)
        assertEquals("Overview", enrichment.overview)
        assertEquals(8.2, enrichment.voteAverage)
        assertEquals("Action, Drama", enrichment.genres)
    }
}
