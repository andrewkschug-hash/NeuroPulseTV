package com.grid.tv.data.mapper

import com.grid.tv.data.network.tmdb.TmdbEnrichment
import com.grid.tv.domain.model.MovieDetails

object MovieDetailsEnrichmentMapper {

    fun toTmdbEnrichment(details: MovieDetails): TmdbEnrichment = TmdbEnrichment(
        tmdbId = details.id,
        imdbId = details.imdbId,
        mediaType = "movie",
        title = details.title,
        overview = details.overview,
        tagline = details.tagline,
        releaseDate = details.releaseDate,
        runtimeMinutes = details.runtimeMinutes,
        genres = details.genres.joinToString(", "),
        keywords = null,
        voteAverage = details.voteAverage,
        voteCount = details.voteCount,
        popularity = null,
        posterUrl = details.posterUrl,
        backdropUrl = details.backdropUrl,
        cast = null,
        directors = null,
        writers = null,
        spokenLanguages = details.originalLanguage,
        originCountry = null,
        status = details.status,
        ageCertification = null
    )
}
