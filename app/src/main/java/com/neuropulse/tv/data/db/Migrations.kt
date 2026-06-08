package com.neuropulse.tv.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DbMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channels ADD COLUMN backupStreamUrl TEXT")
            db.execSQL("ALTER TABLE channels ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    avatarColor TEXT NOT NULL,
                    pin TEXT,
                    isParental INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS active_profile (
                    singletonId INTEGER NOT NULL PRIMARY KEY,
                    profileId INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profile_favorites (
                    profileId INTEGER NOT NULL,
                    channelId INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(profileId, channelId)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profile_watch_history (
                    profileId INTEGER NOT NULL,
                    channelId INTEGER NOT NULL,
                    lastPosition INTEGER NOT NULL,
                    lastWatched INTEGER NOT NULL,
                    totalWatchMs INTEGER NOT NULL,
                    hourBucket INTEGER NOT NULL,
                    genreHint TEXT,
                    PRIMARY KEY(profileId, channelId)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profile_settings (
                    profileId INTEGER NOT NULL PRIMARY KEY,
                    preferredAudioLanguage TEXT NOT NULL,
                    epgRowHeight TEXT NOT NULL,
                    streamRetries INTEGER NOT NULL,
                    previewEnabled INTEGER NOT NULL,
                    gameLockEnabled INTEGER NOT NULL,
                    lastSleepTimer INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stream_health (
                    channelId INTEGER NOT NULL PRIMARY KEY,
                    lastSuccessfulLoad INTEGER NOT NULL,
                    bufferEventsPerSession REAL NOT NULL,
                    averageLoadTimeMs INTEGER NOT NULL,
                    reliabilityScore INTEGER NOT NULL,
                    sessions INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL("INSERT INTO user_profiles(name, avatarColor, pin, isParental) VALUES('Default', '#1E90FF', NULL, 0)")
            db.execSQL("INSERT INTO active_profile(singletonId, profileId) VALUES(1, 1)")
            db.execSQL("INSERT INTO profile_settings(profileId, preferredAudioLanguage, epgRowHeight, streamRetries, previewEnabled, gameLockEnabled, lastSleepTimer) VALUES(1, 'en', 'NORMAL', 3, 1, 0, 30)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scheduled_recordings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    channelId INTEGER NOT NULL,
                    programTitle TEXT NOT NULL,
                    startTime INTEGER NOT NULL,
                    endTime INTEGER NOT NULL,
                    streamUrl TEXT NOT NULL,
                    channelName TEXT NOT NULL,
                    status TEXT NOT NULL,
                    outputPath TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS recorded_media (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    channelId INTEGER NOT NULL,
                    channelName TEXT NOT NULL,
                    programTitle TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    fileSizeBytes INTEGER NOT NULL,
                    recordedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN type TEXT NOT NULL DEFAULT 'M3U'")
            db.execSQL("ALTER TABLE playlists ADD COLUMN xtreamServerUrl TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN xtreamUsername TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN xtreamPassword TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN xtreamAccountStatus TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN xtreamExpiryDateEpochSec INTEGER")
            db.execSQL("ALTER TABLE playlists ADD COLUMN xtreamMaxConnections INTEGER")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channels ADD COLUMN epgResolutionStatus TEXT NOT NULL DEFAULT 'UNRESOLVED'")
            db.execSQL("ALTER TABLE channels ADD COLUMN epgResolutionConfidence INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE channels ADD COLUMN epgResolutionSource TEXT")
            db.execSQL("ALTER TABLE channels ADD COLUMN epgLastAttemptAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE channels SET epgResolutionStatus='CONFIRMED', epgResolutionConfidence=100, epgResolutionSource='legacy' WHERE epgId IS NOT NULL AND epgId != ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_source_channels (
                    epgId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    normalizedName TEXT NOT NULL,
                    source TEXT NOT NULL,
                    logoUrl TEXT,
                    cachedAt INTEGER NOT NULL,
                    PRIMARY KEY(epgId, source)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_source_channels_source ON epg_source_channels(source)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_source_channels_normalizedName ON epg_source_channels(normalizedName)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_resolution_suggestions (
                    channelId TEXT NOT NULL,
                    suggestedEpgId TEXT NOT NULL,
                    suggestedEpgName TEXT NOT NULL,
                    confidence INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    isDismissed INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(channelId, suggestedEpgId)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN stalkerPortalUrl TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN stalkerMacAddress TEXT")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN recordingStoragePath TEXT")
            db.execSQL("ALTER TABLE recorded_media ADD COLUMN playbackPositionMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE recorded_media ADD COLUMN thumbnailPath TEXT")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS favorite_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE profile_favorites ADD COLUMN groupId INTEGER")
            db.execSQL("ALTER TABLE profile_favorites ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE user_profiles ADD COLUMN allowedStartMinutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE user_profiles ADD COLUMN allowedEndMinutes INTEGER NOT NULL DEFAULT 1439")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN lastSeenVersion TEXT")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN sleepTimerMinutes INTEGER NOT NULL DEFAULT 30")
            db.execSQL("ALTER TABLE profile_watch_history ADD COLUMN lastProgramTitle TEXT")
        }
    }
}
