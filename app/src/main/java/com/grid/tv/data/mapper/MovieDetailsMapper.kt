package com.grid.tv.data.mapper

import com.grid.tv.BuildConfig
import com.grid.tv.data.db.entity.MovieDetailsEntity
import com.grid.tv.data.remote.MovieDetailsDto
import com.grid.tv.domain.model.MovieDetails

object MovieDetailsMapper {

    private const val DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    fun toDomain(entity: MovieDetailsEntity): MovieDetails = MovieDetails(
        id = entity.tmdbId,
        title = entity.title,
        overview = entity.overview,
        tagline = entity.tagline,
        releaseDate = entity.releaseDate,
        releaseYear = entity.releaseYear,
        runtimeMinutes = entity.runtimeMinutes,
        voteAverage = entity.voteAverage,
        voteCount = entity.voteCount,
        genres = parseGenres(entity.genres),
        posterUrl = entity.posterUrl,
        backdropUrl = entity.backdropUrl,
        imdbId = entity.imdbId,
        status = entity.status,
        originalLanguage = entity.originalLanguage
    )

    fun toEntity(dto: MovieDetailsDto, fetchedAtMs: Long = System.currentTimeMillis()): MovieDetailsEntity =
        MovieDetailsEntity(
            tmdbId = dto.id,
            title = dto.title,
            overview = dto.overview,
            tagline = dto.tagline,
            releaseDate = dto.releaseDate,
            releaseYear = dto.releaseYear,
            runtimeMinutes = dto.runtimeMinutes,
            voteAverage = dto.voteAverage,
            voteCount = dto.voteCount,
            genres = dto.genres.joinToString(", ").ifBlank { null },
            posterUrl = imageUrl(dto.posterPath, "w500"),
            backdropUrl = imageUrl(dto.backdropPath, "w1280"),
            imdbId = dto.imdbId,
            status = dto.status,
            originalLanguage = dto.originalLanguage,
            updatedAt = fetchedAtMs
        )

    fun toDomain(dto: MovieDetailsDto): MovieDetails = toDomain(toEntity(dto))

    private fun parseGenres(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun imageUrl(path: String?, size: String): String? {
        if (path.isNullOrBlank()) return null
        val base = BuildConfig.TMDB_IMAGE_BASE_URL.trim().ifBlank { DEFAULT_IMAGE_BASE_URL }
        return "${base.trimEnd('/')}/$size/${path.trimStart('/')}"
    }
}
