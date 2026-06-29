package com.grid.tv.ui.screen

import com.grid.tv.domain.model.VodContentFilter

/** Action taken when a filter/genre catalog becomes empty after refresh. */
sealed class VodFocusEmptyRecoveryAction {
    data class ApplyGenre(val index: Int) : VodFocusEmptyRecoveryAction()
    data object KeepFilter : VodFocusEmptyRecoveryAction()
    data class SwitchFilter(val filter: VodContentFilter) : VodFocusEmptyRecoveryAction()
    data object OpenSidebar : VodFocusEmptyRecoveryAction()
}

/**
 * Picks the best recovery path when browse content is empty.
 * Pure O(1) decision logic — no I/O on the caller thread.
 */
fun resolveVodFocusEmptyRecovery(
    contentFilter: VodContentFilter,
    genreIndex: Int,
    genreCount: Int,
    moviesBrowseCount: Int,
    seriesBrowseCount: Int,
    wallRowCount: Int,
    moviesCatalogTotal: Int = 0,
    seriesCatalogTotal: Int = 0,
    isCatalogLoading: Boolean = false,
): VodFocusEmptyRecoveryAction {
    if (isCatalogLoading) {
        return VodFocusEmptyRecoveryAction.KeepFilter
    }
    if (genreCount > 0 && genreIndex > 0) {
        return VodFocusEmptyRecoveryAction.ApplyGenre(0)
    }
    when (contentFilter) {
        VodContentFilter.MOVIES -> {
            if (moviesBrowseCount > 0 || moviesCatalogTotal > 0) {
                return VodFocusEmptyRecoveryAction.KeepFilter
            }
            if (seriesBrowseCount > 0) {
                return VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.SERIES)
            }
            if (wallRowCount > 0) {
                return VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.ALL)
            }
        }
        VodContentFilter.SERIES -> {
            if (seriesBrowseCount > 0 || seriesCatalogTotal > 0) {
                return VodFocusEmptyRecoveryAction.KeepFilter
            }
            if (moviesBrowseCount > 0) {
                return VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.MOVIES)
            }
            if (wallRowCount > 0) {
                return VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.ALL)
            }
        }
        VodContentFilter.ALL -> {
            if (moviesBrowseCount > 0) {
                return VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.MOVIES)
            }
            if (seriesBrowseCount > 0) {
                return VodFocusEmptyRecoveryAction.SwitchFilter(VodContentFilter.SERIES)
            }
        }
        else -> Unit
    }
    if (genreCount > 0) {
        return VodFocusEmptyRecoveryAction.ApplyGenre(0)
    }
    return VodFocusEmptyRecoveryAction.OpenSidebar
}
