package com.grid.tv.feature.tivimate

import com.grid.tv.data.db.entity.PlaylistEntity
import com.grid.tv.data.db.entity.ProfileFavoriteEntity
import javax.inject.Inject

class TiviMateImportMapper @Inject constructor() {
    fun mapPlaylist(name: String, url: String): PlaylistEntity {
        val safeName = if (name.isBlank()) "Imported Playlist" else name
        return PlaylistEntity(name = safeName, url = url, refreshIntervalHours = 24)
    }

    fun mapFavorite(profileId: Long, channelId: Long): ProfileFavoriteEntity {
        return ProfileFavoriteEntity(profileId = profileId, channelId = channelId)
    }
}
