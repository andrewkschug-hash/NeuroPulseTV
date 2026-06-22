package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "movie_details",
    primaryKeys = ["tmdbId"],
    indices = [Index("imdbId")]
)
data class MovieDetailsEntity(
    val tmdbId: Long,
    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    /** Comma-separated genre names. */
    val genres: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val imdbId: String? = null,
    val status: String? = null,
    val originalLanguage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
