package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.MovieDetails
import com.grid.tv.domain.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MovieDetailsUiState {
    data object Loading : MovieDetailsUiState
    data class Success(val details: MovieDetails) : MovieDetailsUiState
    data class Error(val message: String) : MovieDetailsUiState
}

@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MovieDetailsUiState>(MovieDetailsUiState.Loading)
    val uiState: StateFlow<MovieDetailsUiState> = _uiState.asStateFlow()

    private var loadedMovieId: Long? = null

    fun load(movieId: Long, forceRefresh: Boolean = false) {
        if (movieId <= 0L) {
            _uiState.value = MovieDetailsUiState.Error("Invalid movie id")
            return
        }
        if (!forceRefresh && loadedMovieId == movieId && _uiState.value is MovieDetailsUiState.Success) {
            return
        }

        viewModelScope.launch {
            if (_uiState.value !is MovieDetailsUiState.Success) {
                _uiState.value = MovieDetailsUiState.Loading
            }
            runCatching {
                movieRepository.getMovieDetails(movieId, forceRefresh = forceRefresh)
            }.onSuccess { details ->
                loadedMovieId = movieId
                _uiState.value = MovieDetailsUiState.Success(details)
            }.onFailure { error ->
                _uiState.value = MovieDetailsUiState.Error(
                    error.message ?: "We couldn't load movie details. Please try again."
                )
            }
        }
    }

    fun clear() {
        loadedMovieId = null
        _uiState.value = MovieDetailsUiState.Loading
    }
}
