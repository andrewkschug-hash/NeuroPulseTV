package com.neuropulse.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_settings")
data class ProfileSettingsEntity(
    @PrimaryKey val profileId: Long,
    val preferredAudioLanguage: String = "en",
    val epgRowHeight: String = "NORMAL",
    val streamRetries: Int = 3,
    val previewEnabled: Boolean = true,
    val gameLockEnabled: Boolean = false,
    val lastSleepTimer: Int = 30,
    val recordingStoragePath: String? = null,
    val lastSeenVersion: String? = null,
    val sleepTimerMinutes: Int = 30
)
