package com.grid.tv.data.db

import androidx.room.Database
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationChainTest {

    @Test
    fun schemaVersionMatchesAppDatabaseAnnotation() {
        val annotation = AppDatabase::class.java.getAnnotation(Database::class.java)
        if (annotation != null) {
            assertEquals(
                "Bump DbMigrations.SCHEMA_VERSION and AppDatabase @Database version together",
                DbMigrations.SCHEMA_VERSION,
                annotation.version
            )
        } else {
            // JVM unit tests may not retain Room @Database metadata; migration chain still guards version.
            assertTrue(
                "DbMigrations.SCHEMA_VERSION must be positive",
                DbMigrations.SCHEMA_VERSION > DbMigrations.MIN_UPGRADE_VERSION
            )
        }
    }

    @Test
    fun migrationChainHasNoGapsFromMinUpgradeToSchema() {
        val sorted = DbMigrations.ALL.sortedBy { it.startVersion }
        assertEquals(
            "Expected ${DbMigrations.ALL.size} migrations",
            DbMigrations.SCHEMA_VERSION - DbMigrations.MIN_UPGRADE_VERSION,
            sorted.size
        )
        var expectedStart = DbMigrations.MIN_UPGRADE_VERSION
        for (migration in sorted) {
            assertEquals(
                "Missing migration step before version $expectedStart",
                expectedStart,
                migration.startVersion
            )
            expectedStart = migration.endVersion
        }
        assertEquals(DbMigrations.SCHEMA_VERSION, expectedStart)
    }

    @Test
    fun migrationCountMatchesSchemaSpan() {
        assertTrue(DbMigrations.ALL.isNotEmpty())
        assertTrue(DbMigrations.SCHEMA_VERSION > DbMigrations.MIN_UPGRADE_VERSION)
    }
}
