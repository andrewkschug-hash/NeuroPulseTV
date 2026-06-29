package com.grid.tv.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration37To38Test {

    private val dbName = "migration_37_38_test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration37To38_repairsDriftedPlaylistFavoriteGroupsSchema() {
        var db = helper.createDatabase(dbName, 36).apply {
            execSQL(
                """
                CREATE TABLE playlist_favorite_groups (
                    playlistId INTEGER NOT NULL,
                    groupKey TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(playlistId, groupKey)
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE INDEX index_playlist_favorite_groups_playlistId " +
                    "ON playlist_favorite_groups(playlistId)"
            )
            execSQL(
                """
                INSERT INTO playlist_favorite_groups
                    (playlistId, groupKey, sortOrder, createdAt)
                VALUES (1, 'sports', 0, 1000)
                """.trimIndent()
            )
            close()
        }

        db = helper.runMigrationsAndValidate(
            dbName,
            DbMigrations.SCHEMA_VERSION,
            true,
            DbMigrations.MIGRATION_36_37,
            DbMigrations.MIGRATION_37_38,
        )

        db.query("SELECT playlistId, groupKey, sortOrder, createdAt FROM playlist_favorite_groups")
            .use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
                assertEquals("sports", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(1000L, cursor.getLong(3))
            }

        db.close()
    }
}
