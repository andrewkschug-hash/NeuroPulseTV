package com.grid.tv.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DbMigrations {
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playback_session_telemetry (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    channelId INTEGER NOT NULL,
                    streamId TEXT NOT NULL,
                    providerId INTEGER NOT NULL,
                    sessionStart INTEGER NOT NULL,
                    sessionEnd INTEGER NOT NULL,
                    watchDurationMs INTEGER NOT NULL,
                    startupTimeMs INTEGER NOT NULL,
                    bufferingEventCount INTEGER NOT NULL,
                    bufferingDurationMs INTEGER NOT NULL,
                    playbackErrorCount INTEGER NOT NULL,
                    streamSwitchCount INTEGER NOT NULL,
                    reconnectAttempts INTEGER NOT NULL,
                    playbackSuccess INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_session_telemetry_channelId ON playback_session_telemetry(channelId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_session_telemetry_streamId ON playback_session_telemetry(streamId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_session_telemetry_providerId ON playback_session_telemetry(providerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_session_telemetry_sessionStart ON playback_session_telemetry(sessionStart)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stream_source_health (
                    channelId INTEGER NOT NULL,
                    streamId TEXT NOT NULL,
                    healthScore INTEGER NOT NULL,
                    healthTier TEXT NOT NULL,
                    sessionCount INTEGER NOT NULL,
                    avgStartupTimeMs REAL NOT NULL,
                    avgBufferingDurationMs REAL NOT NULL,
                    failureRate REAL NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    PRIMARY KEY(channelId, streamId)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS channel_health_aggregate (
                    channelId INTEGER NOT NULL PRIMARY KEY,
                    healthScore INTEGER NOT NULL,
                    healthTier TEXT NOT NULL,
                    sessionCount INTEGER NOT NULL,
                    streamCount INTEGER NOT NULL,
                    lastUpdated INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS provider_health_aggregate (
                    providerId INTEGER NOT NULL PRIMARY KEY,
                    healthScore INTEGER NOT NULL,
                    healthTier TEXT NOT NULL,
                    sessionCount INTEGER NOT NULL,
                    channelCount INTEGER NOT NULL,
                    lastUpdated INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vod_watch_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId INTEGER NOT NULL,
                    contentId TEXT NOT NULL,
                    contentType TEXT NOT NULL,
                    seriesId INTEGER,
                    seasonNumber INTEGER,
                    episodeNumber INTEGER,
                    progressPercent REAL NOT NULL,
                    positionMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    lastWatched INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_watch_events_profileId ON vod_watch_events(profileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_watch_events_seriesId ON vod_watch_events(seriesId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_watch_events_profileId_seriesId ON vod_watch_events(profileId, seriesId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS series_follows (
                    profileId INTEGER NOT NULL,
                    seriesId INTEGER NOT NULL,
                    seriesTitle TEXT NOT NULL,
                    playlistId INTEGER NOT NULL,
                    following INTEGER NOT NULL,
                    autoFollowed INTEGER NOT NULL,
                    followedAt INTEGER NOT NULL,
                    PRIMARY KEY(profileId, seriesId)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vod_catalog_episodes (
                    playlistId INTEGER NOT NULL,
                    seriesId INTEGER NOT NULL,
                    seriesTitle TEXT NOT NULL,
                    seasonNumber INTEGER NOT NULL,
                    episodeNumber INTEGER NOT NULL,
                    episodeId INTEGER NOT NULL,
                    episodeTitle TEXT NOT NULL,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, seriesId, seasonNumber, episodeNumber)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_catalog_episodes_seriesId ON vod_catalog_episodes(seriesId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_catalog_episodes_addedAt ON vod_catalog_episodes(addedAt)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vod_user_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    seriesId INTEGER,
                    seasonNumber INTEGER,
                    episodeNumber INTEGER,
                    seriesTitle TEXT NOT NULL,
                    episodeTitle TEXT,
                    contentKey TEXT,
                    createdAt INTEGER NOT NULL,
                    readAt INTEGER,
                    pushPending INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_user_notifications_profileId ON vod_user_notifications(profileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_user_notifications_profileId_readAt ON vod_user_notifications(profileId, readAt)")
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN subtitlePosition TEXT NOT NULL DEFAULT 'BOTTOM'"
            )
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN subtitleDelayMs INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channels ADD COLUMN backupStreamUrl2 TEXT")
            db.execSQL("ALTER TABLE channels ADD COLUMN backupStreamUrl3 TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stream_failover_stats (
                    channelId INTEGER NOT NULL PRIMARY KEY,
                    failoverCount INTEGER NOT NULL DEFAULT 0,
                    successfulRecoveryCount INTEGER NOT NULL DEFAULT 0,
                    lastFailoverAt INTEGER NOT NULL DEFAULT 0,
                    lastRecoveryAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS canonical_channels (
                    id TEXT NOT NULL PRIMARY KEY,
                    canonicalName TEXT NOT NULL,
                    country TEXT NOT NULL,
                    epgId TEXT NOT NULL,
                    logoUrl TEXT,
                    category TEXT,
                    aliases TEXT NOT NULL,
                    normalizedName TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_channels_normalizedName ON canonical_channels(normalizedName)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_channels_epgId ON canonical_channels(epgId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_channels_country ON canonical_channels(country)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_learned_mappings (
                    normalizedOriginalName TEXT NOT NULL PRIMARY KEY,
                    originalNameSample TEXT NOT NULL,
                    epgId TEXT NOT NULL,
                    epgDisplayName TEXT NOT NULL,
                    source TEXT NOT NULL,
                    learnedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_learned_mappings_epgId ON epg_learned_mappings(epgId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_match_analytics (
                    id INTEGER NOT NULL PRIMARY KEY,
                    totalAttempts INTEGER NOT NULL DEFAULT 0,
                    autoMatched INTEGER NOT NULL DEFAULT 0,
                    suggested INTEGER NOT NULL DEFAULT 0,
                    manualCorrections INTEGER NOT NULL DEFAULT 0,
                    unmatched INTEGER NOT NULL DEFAULT 0,
                    tvgIdMatches INTEGER NOT NULL DEFAULT 0,
                    learnedMatches INTEGER NOT NULL DEFAULT 0,
                    canonicalMatches INTEGER NOT NULL DEFAULT 0,
                    exactNameMatches INTEGER NOT NULL DEFAULT 0,
                    fuzzyMatches INTEGER NOT NULL DEFAULT 0,
                    lastUpdatedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT OR IGNORE INTO epg_match_analytics (id) VALUES (1)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_alias_hits (
                    normalizedAlias TEXT NOT NULL PRIMARY KEY,
                    originalNameSample TEXT NOT NULL,
                    hitCount INTEGER NOT NULL DEFAULT 1,
                    lastSeenAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                "ALTER TABLE epg_resolution_suggestions ADD COLUMN matchReason TEXT NOT NULL DEFAULT 'FUZZY'"
            )
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS subtitle_cache (
                    imdbId TEXT NOT NULL,
                    language TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    sourceSubtitleId TEXT,
                    downloadedAt INTEGER NOT NULL,
                    PRIMARY KEY(imdbId, language)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN mediaType TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN overview TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN tagline TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN releaseDate TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN runtimeMinutes INTEGER")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN writers TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN voteCount INTEGER")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN spokenLanguages TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN originCountry TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN status TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN ageCertification TEXT")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN numberOfSeasons INTEGER")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN numberOfEpisodes INTEGER")
            db.execSQL("ALTER TABLE title_enrichment ADD COLUMN episodeRunTime TEXT")
        }
    }

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

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN hideAdultContent INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN sleepTimerAutoEnabled INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS channel_scan (
                    channelId INTEGER NOT NULL PRIMARY KEY,
                    status TEXT NOT NULL,
                    lastCheckedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN autoScanEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN scanIntervalMinutes INTEGER NOT NULL DEFAULT 5")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN concurrentChecks INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN scanOnMetered INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN lastFullScanAt INTEGER")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN preferredSearchInput TEXT NOT NULL DEFAULT 'KEYBOARD'"
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN parentalPinLockEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN maxContentRating TEXT NOT NULL DEFAULT 'ALL_AGES'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN connectionTimeoutSeconds INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN useProxy INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN proxyUrl TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN showEpgProgramInfoOnSidebar INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN startChannelFromBeginningOnCatchup INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN defaultStreamQuality TEXT NOT NULL DEFAULT 'AUTO'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN bufferSize TEXT NOT NULL DEFAULT 'MEDIUM'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN autoReconnectOnDrop INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN preferHardwareDecoding INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN aspectRatio TEXT NOT NULL DEFAULT 'AUTO'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN subtitlesEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN subtitleLanguage TEXT NOT NULL DEFAULT 'en'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN subtitleFontSize TEXT NOT NULL DEFAULT 'MEDIUM'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN deinterlacingEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN miniPlayerEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN sidebarAutoHideSeconds INTEGER NOT NULL DEFAULT 5")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN showChannelNumbers INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN dpadSidebarSensitivity TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("ALTER TABLE profile_settings ADD COLUMN clockDisplay TEXT NOT NULL DEFAULT 'OFF'")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS series_recording_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    seriesTitle TEXT NOT NULL,
                    seriesId INTEGER,
                    recordNewOnly INTEGER NOT NULL DEFAULT 1,
                    playlistId INTEGER NOT NULL,
                    paddingStartMins INTEGER NOT NULL DEFAULT 2,
                    paddingEndMins INTEGER NOT NULL DEFAULT 5,
                    maxEpisodesToKeep INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN recordQuality TEXT NOT NULL DEFAULT 'ORIGINAL'"
            )
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN recordedPlaybackSpeed REAL NOT NULL DEFAULT 1.0"
            )
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS continue_watching (
                    profileId INTEGER NOT NULL,
                    contentKey TEXT NOT NULL,
                    contentType TEXT NOT NULL,
                    streamId INTEGER,
                    seriesId INTEGER,
                    seasonNumber INTEGER,
                    episodeNumber INTEGER,
                    title TEXT NOT NULL,
                    posterUrl TEXT,
                    streamUrl TEXT NOT NULL,
                    positionMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    lastWatchedAt INTEGER NOT NULL,
                    PRIMARY KEY (profileId, contentKey)
                )
                """.trimIndent()
            )
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN themeId TEXT NOT NULL DEFAULT 'NEURO_BLUE'"
            )
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN pictureInPictureEnabled INTEGER NOT NULL DEFAULT 1"
            )

            db.execSQL(
                """
                INSERT OR IGNORE INTO continue_watching (
                    profileId, contentKey, contentType, streamId, seriesId, seasonNumber,
                    episodeNumber, title, posterUrl, streamUrl, positionMs, durationMs, lastWatchedAt
                )
                SELECT
                    profileId,
                    'movie:' || (-channelId),
                    'MOVIE',
                    (-channelId),
                    NULL,
                    NULL,
                    NULL,
                    COALESCE(lastProgramTitle, 'Movie'),
                    NULL,
                    '',
                    lastPosition,
                    CAST(COALESCE(genreHint, '0') AS INTEGER),
                    lastWatched
                FROM profile_watch_history
                WHERE channelId < 0 AND lastPosition > 0
                """.trimIndent()
            )

            val profileCursor = db.query("SELECT id FROM user_profiles")
            profileCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val profileId = cursor.getLong(0)
                    seedDefaultFavoriteGroups(db, profileId)
                }
            }
        }

        private fun seedDefaultFavoriteGroups(db: SupportSQLiteDatabase, profileId: Long) {
            val existing = db.query(
                "SELECT COUNT(*) FROM favorite_groups WHERE profileId = ?",
                arrayOf(profileId.toString())
            )
            val count = existing.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (count > 0) return

            val defaults = listOf("Favorites", "Sports", "Movies", "Kids", "News")
            defaults.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT INTO favorite_groups (profileId, name, sortOrder) VALUES (?, ?, ?)",
                    arrayOf(profileId, name, index)
                )
            }

            val favoritesCursor = db.query(
                "SELECT id FROM favorite_groups WHERE profileId = ? AND name = 'Favorites' LIMIT 1",
                arrayOf(profileId.toString())
            )
            val favoritesGroupId = favoritesCursor.use {
                if (it.moveToFirst()) it.getLong(0) else null
            } ?: return

            db.execSQL(
                "UPDATE profile_favorites SET groupId = ? WHERE profileId = ? AND groupId IS NULL",
                arrayOf(favoritesGroupId, profileId)
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE recorded_media ADD COLUMN integrityStatus TEXT NOT NULL DEFAULT 'OK'"
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS title_enrichment (
                    providerKey TEXT NOT NULL,
                    normalizedTitle TEXT NOT NULL,
                    releaseYear INTEGER,
                    tmdbId INTEGER,
                    imdbId TEXT,
                    cast TEXT,
                    directors TEXT,
                    rating REAL,
                    popularity REAL,
                    posterUrl TEXT,
                    backdropUrl TEXT,
                    genres TEXT,
                    keywords TEXT,
                    contentVector TEXT,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(providerKey)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_title_enrichment_tmdbId ON title_enrichment(tmdbId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_title_enrichment_normalizedTitle ON title_enrichment(normalizedTitle)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profile_taste_genome (
                    profileId INTEGER NOT NULL,
                    tasteVector TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(profileId)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN guideChannelGroups TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE profile_settings ADD COLUMN guideFiltersConfigured INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vod_streams (
                    rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    playlistId INTEGER NOT NULL,
                    streamId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    streamUrl TEXT NOT NULL,
                    posterUrl TEXT,
                    plot TEXT,
                    cast TEXT,
                    director TEXT,
                    genre TEXT,
                    rating TEXT,
                    duration TEXT,
                    categoryId TEXT,
                    addedEpochSec INTEGER,
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_streams_playlistId ON vod_streams(playlistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_streams_streamId ON vod_streams(streamId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_streams_categoryId ON vod_streams(categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_streams_addedEpochSec ON vod_streams(addedEpochSec)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_vod_streams_playlistId_streamId ON vod_streams(playlistId, streamId)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vod_categories (
                    playlistId INTEGER NOT NULL,
                    categoryId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    PRIMARY KEY(playlistId, categoryId),
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_categories_playlistId ON vod_categories(playlistId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS series_shows (
                    rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    playlistId INTEGER NOT NULL,
                    seriesId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    coverUrl TEXT,
                    categoryId TEXT,
                    genre TEXT,
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_series_shows_playlistId ON series_shows(playlistId)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_series_shows_playlistId_seriesId ON series_shows(playlistId, seriesId)"
            )
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS profile_genre_affinity (
                    profileId INTEGER NOT NULL,
                    genre TEXT NOT NULL,
                    score INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(profileId, genre)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_profile_genre_affinity_profileId ON profile_genre_affinity(profileId)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS featured_banner_stats (
                    profileId INTEGER NOT NULL,
                    contentKey TEXT NOT NULL,
                    impressionCount INTEGER NOT NULL DEFAULT 0,
                    clickCount INTEGER NOT NULL DEFAULT 0,
                    lastShownAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(profileId, contentKey)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_featured_banner_stats_profileId ON featured_banner_stats(profileId)"
            )
        }
    }

    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS series_categories (
                    playlistId INTEGER NOT NULL,
                    categoryId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    PRIMARY KEY(playlistId, categoryId),
                    FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_series_categories_playlistId ON series_categories(playlistId)"
            )
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO series_categories (playlistId, categoryId, name)
                SELECT DISTINCT playlistId, categoryId, categoryId
                FROM series_shows
                WHERE categoryId IS NOT NULL AND TRIM(categoryId) != ''
                """.trimIndent()
            )
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS movie_details (
                    tmdbId INTEGER NOT NULL,
                    title TEXT,
                    overview TEXT,
                    tagline TEXT,
                    releaseDate TEXT,
                    releaseYear INTEGER,
                    runtimeMinutes INTEGER,
                    voteAverage REAL,
                    voteCount INTEGER,
                    genres TEXT,
                    posterUrl TEXT,
                    backdropUrl TEXT,
                    imdbId TEXT,
                    status TEXT,
                    originalLanguage TEXT,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(tmdbId)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_details_imdbId ON movie_details(imdbId)")
        }
    }
}
