package com.grid.tv.ui.viewmodel

import androidx.compose.runtime.Immutable

/** Single immutable collector payload for [com.grid.tv.ui.screen.HomeEpgScreen]. */
@Immutable
data class HomeEpgScreenSnapshot(
    val epg: EpgUiSnapshot,
    val chrome: HomeEpgChromeSnapshot
) {
    companion object {
        val INITIAL = HomeEpgScreenSnapshot(
            epg = EpgUiSnapshot.EMPTY,
            chrome = HomeEpgChromeSnapshot.INITIAL
        )
    }
}
