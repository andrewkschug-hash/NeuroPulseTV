package com.grid.tv.domain.repository

import com.grid.tv.domain.model.MovieDetails
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    fun observeMovieDetails(movieId: Long): Flow<MovieDetails?>

    suspend fun getMovieDetails(movieId: Long, forceRefresh: Boolean = false): MovieDetails
}
