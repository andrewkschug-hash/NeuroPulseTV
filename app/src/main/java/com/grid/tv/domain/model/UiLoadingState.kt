package com.grid.tv.domain.model

/**
 * Global UI loading contract: render immediately → skeleton → partial → final.
 * Never show a blank list while [Loading] is active.
 */
sealed class UiLoadingState<out T> {
    data object Empty : UiLoadingState<Nothing>()

    data class Loading<T>(
        val partial: List<T> = emptyList(),
        val showSkeleton: Boolean = partial.isEmpty(),
    ) : UiLoadingState<T>()

    data class Loaded<T>(val data: List<T>) : UiLoadingState<T>()

    data class Error<T>(
        val partial: List<T> = emptyList(),
        val message: String? = null,
    ) : UiLoadingState<T>()

    val isLoading: Boolean get() = this is Loading
    val isLoaded: Boolean get() = this is Loaded
    val hasRenderableContent: Boolean
        get() = when (this) {
            is Empty -> false
            is Loading -> partial.isNotEmpty() || showSkeleton
            is Loaded -> data.isNotEmpty()
            is Error -> partial.isNotEmpty()
        }

    fun itemsOrEmpty(): List<T> = when (this) {
        is Empty -> emptyList()
        is Loading -> partial
        is Loaded -> data
        is Error -> partial
    }
}
