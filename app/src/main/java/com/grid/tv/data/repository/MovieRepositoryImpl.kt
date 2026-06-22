package com.grid.tv.data.repository

import com.grid.tv.data.auth.SupabaseClientProvider
import com.grid.tv.data.db.dao.MovieDetailsDao
import com.grid.tv.data.mapper.MovieDetailsMapper
import com.grid.tv.data.remote.GetMovieDetailsRequest
import com.grid.tv.data.remote.MovieDetailsDto
import com.grid.tv.domain.model.MovieDetails
import com.grid.tv.domain.repository.MovieRepository
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val dao: MovieDetailsDao,
    private val supabaseClientProvider: SupabaseClientProvider
) : MovieRepository {

    override fun observeMovieDetails(movieId: Long): Flow<MovieDetails?> =
        dao.observe(movieId).map { entity -> entity?.let(MovieDetailsMapper::toDomain) }

    override suspend fun getMovieDetails(movieId: Long, forceRefresh: Boolean): MovieDetails {
        require(movieId > 0L) { "movie_id must be a positive TMDB id" }

        val cached = dao.get(movieId)
        if (!forceRefresh && cached != null && isFresh(cached.updatedAt)) {
            return MovieDetailsMapper.toDomain(cached)
        }

        return runCatching {
            val remote = fetchFromSupabase(movieId)
            val entity = MovieDetailsMapper.toEntity(remote)
            dao.upsert(entity)
            MovieDetailsMapper.toDomain(entity)
        }.getOrElse { error ->
            cached?.let { return MovieDetailsMapper.toDomain(it) }
            throw error
        }
    }

    private suspend fun fetchFromSupabase(movieId: Long): MovieDetailsDto {
        val client = supabaseClientProvider.clientOrNull()
            ?: error("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties")

        val response = client.functions.invoke(
            function = GET_MOVIE_DETAILS_FUNCTION,
            body = GetMovieDetailsRequest(movieId = movieId)
        )
        return response.body<MovieDetailsDto>()
    }

    private fun isFresh(updatedAtMs: Long): Boolean =
        System.currentTimeMillis() - updatedAtMs < CACHE_TTL_MS

    companion object {
        const val GET_MOVIE_DETAILS_FUNCTION = "get-movie-details"
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
