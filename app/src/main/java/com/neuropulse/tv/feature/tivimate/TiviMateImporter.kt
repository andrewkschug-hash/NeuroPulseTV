package com.neuropulse.tv.feature.tivimate

import android.content.ContentResolver
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.neuropulse.tv.data.db.dao.PlaylistDao
import com.neuropulse.tv.data.db.dao.ProfileFavoriteDao
import com.neuropulse.tv.data.db.entity.PlaylistEntity
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

class TiviMateImporter @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val favoriteDao: ProfileFavoriteDao,
    private val mapper: TiviMateImportMapper
) {
    suspend fun importZip(contentResolver: ContentResolver, uri: Uri, cacheDir: File, profileId: Long): ImportSummary {
        val zipFile = File(cacheDir, "tivimate_import.zip")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(zipFile).use { out -> input.copyTo(out) }
        }

        val dbFile = File(cacheDir, "tivimate.db")
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("tivimate.db")) {
                    FileOutputStream(dbFile).use { out -> zip.copyTo(out) }
                    break
                }
                entry = zip.nextEntry
            }
        }

        if (!dbFile.exists()) return ImportSummary(0, 0, 0)

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        var playlists = 0
        var favorites = 0

        db.rawQuery("SELECT name, url FROM playlists", null).use { c ->
            while (c.moveToNext()) {
                val entity = mapper.mapPlaylist(c.getString(0) + " (Imported)", c.getString(1))
                playlistDao.insert(entity)
                playlists++
            }
        }

        db.rawQuery("SELECT channel_id FROM favorites", null).use { c ->
            while (c.moveToNext()) {
                favoriteDao.add(mapper.mapFavorite(profileId, c.getLong(0)))
                favorites++
            }
        }
        db.close()

        return ImportSummary(channels = 0, favorites = favorites, playlists = playlists)
    }
}

data class ImportSummary(
    val channels: Int,
    val favorites: Int,
    val playlists: Int
)
