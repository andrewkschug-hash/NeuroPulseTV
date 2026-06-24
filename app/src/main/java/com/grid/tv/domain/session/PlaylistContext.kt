package com.grid.tv.domain.session

import com.grid.tv.data.db.dao.PlaylistDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user's active playlist scope. Explicit playlist ids on content always win;
 * this context supplies defaults when callers omit scope (e.g. hub browse rows, channel zap).
 */
@Singleton
class PlaylistContext @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    companion object {
        const val UNSPECIFIED = 0L

        fun resolve(explicit: Long?, active: Long): Long {
            explicit?.takeIf { it > UNSPECIFIED }?.let { return it }
            return active.takeIf { it > UNSPECIFIED } ?: UNSPECIFIED
        }

        fun resolveOrNull(explicit: Long?, active: Long): Long? =
            explicit?.takeIf { it > UNSPECIFIED } ?: active.takeIf { it > UNSPECIFIED }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activePlaylistId = MutableStateFlow(UNSPECIFIED)
    val activePlaylistId: StateFlow<Long> = _activePlaylistId.asStateFlow()

    init {
        scope.launch {
            playlistDao.observeAll().collect { playlists ->
                val ids = playlists.map { it.id }.toSet()
                val current = _activePlaylistId.value
                when {
                    ids.isEmpty() -> _activePlaylistId.value = UNSPECIFIED
                    current in ids -> Unit
                    else -> _activePlaylistId.value = playlists.first().id
                }
            }
        }
    }

    fun setActive(playlistId: Long) {
        if (playlistId <= UNSPECIFIED) {
            _activePlaylistId.value = UNSPECIFIED
            return
        }
        _activePlaylistId.value = playlistId
    }

    fun resolve(explicit: Long?): Long =
        resolve(explicit, _activePlaylistId.value)

    fun resolveOrNull(explicit: Long?): Long? =
        resolveOrNull(explicit, _activePlaylistId.value)
}
