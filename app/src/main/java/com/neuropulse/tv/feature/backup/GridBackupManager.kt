package com.neuropulse.tv.feature.backup

import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.FavoriteGroupDao
import com.neuropulse.tv.data.db.dao.PlaylistDao
import com.neuropulse.tv.data.db.dao.ProfileDao
import com.neuropulse.tv.data.db.dao.ProfileFavoriteDao
import com.neuropulse.tv.data.db.dao.ProfileSettingsDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridBackupManager @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val profileDao: ProfileDao,
    private val profileSettingsDao: ProfileSettingsDao,
    private val profileFavoriteDao: ProfileFavoriteDao,
    private val favoriteGroupDao: FavoriteGroupDao,
    private val scheduledRecordingDao: ScheduledRecordingDao
) {
    suspend fun exportTo(file: File, profileId: Long): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "GRID")
        root.put("exportedAt", System.currentTimeMillis())

        val playlists = JSONArray()
        playlistDao.observeAll().first().forEach { p ->
            playlists.put(
                JSONObject()
                    .put("name", p.name)
                    .put("url", p.url)
                    .put("epgUrl", p.epgUrl)
                    .put("refreshIntervalHours", p.refreshIntervalHours)
                    .put("type", p.type)
            )
        }
        root.put("playlists", playlists)

        val groups = JSONArray()
        favoriteGroupDao.observeForProfile(profileId).first().forEach { g ->
            groups.put(JSONObject().put("name", g.name).put("sortOrder", g.sortOrder))
        }
        root.put("favoriteGroups", groups)

        val favorites = JSONArray()
        profileFavoriteDao.observeForProfile(profileId).first().forEach { f ->
            favorites.put(
                JSONObject()
                    .put("channelId", f.channelId)
                    .put("groupId", f.groupId)
                    .put("sortOrder", f.sortOrder)
            )
        }
        root.put("favorites", favorites)

        val scheduled = JSONArray()
        scheduledRecordingDao.observeUpcomingAndActive().first().forEach { s ->
            scheduled.put(
                JSONObject()
                    .put("channelId", s.channelId)
                    .put("channelName", s.channelName)
                    .put("programTitle", s.programTitle)
                    .put("startTime", s.startTime)
                    .put("endTime", s.endTime)
                    .put("status", s.status)
            )
        }
        root.put("scheduledRecordings", scheduled)

        profileSettingsDao.get(profileId)?.let { settings ->
            root.put(
                "settings",
                JSONObject()
                    .put("preferredAudioLanguage", settings.preferredAudioLanguage)
                    .put("epgRowHeight", settings.epgRowHeight)
                    .put("streamRetries", settings.streamRetries)
                    .put("sleepTimerMinutes", settings.sleepTimerMinutes)
                    .put("recordingStoragePath", settings.recordingStoragePath)
            )
        }

        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
        "Backup saved (${file.length() / 1024} KB)"
    }
}
