package com.grid.tv.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {

    private val dbName = "migration-5-6-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate5To6() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO playlists(id, name, url, lastRefreshed, refreshIntervalHours, epgUrl, isLocalFile, type) VALUES(1, 'P', 'u', 0, 24, NULL, 0, 'M3U')")
            execSQL("INSERT INTO channels(id, number, name, groupName, logoUrl, epgId, streamUrl, backupStreamUrl, playlistId, createdAt, catchupMode, catchupSource, catchupDays) VALUES(1,1,'CNN','News',NULL,NULL,'u',NULL,1,0,NULL,NULL,0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 6, true, DbMigrations.MIGRATION_5_6)
    }
}
