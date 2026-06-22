package com.grid.tv.domain.model

data class MovieDetails(
    val id: Long,
    val title: String?,
    val overview: String?,
    val tagline: String?,
    val releaseDate: String?,
    val releaseYear: Int?,
    val runtimeMinutes: Int?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val genres: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
    val imdbId: String?,
    val status: String?,
    val originalLanguage: String?
)
