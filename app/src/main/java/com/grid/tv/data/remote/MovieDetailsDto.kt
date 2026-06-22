package com.grid.tv.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetMovieDetailsRequest(
    @SerialName("movie_id") val movieId: Long
)

@Serializable
data class MovieDetailsDto(
    val id: Long,
    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    @SerialName("runtime_minutes") val runtimeMinutes: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val status: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null
)
