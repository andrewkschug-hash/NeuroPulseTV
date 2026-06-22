package com.grid.tv.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Thread-safe lazy holder so Room opens on a background thread when prewarmed at startup,
 * instead of blocking the main thread during the first Hilt injection.
 */
internal object AppDatabaseHolder {
    private val lock = Any()

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        instance?.let { return it }
        return synchronized(lock) {
            instance ?: buildDatabase(context.applicationContext).also { instance = it }
        }
    }

    /** Opens the database file on the calling thread — call from a background dispatcher. */
    fun prewarm(context: Context) {
        get(context)
    }

    private fun buildDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "streamflow.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(DbMigrations.MIGRATION_2_3)
            .addMigrations(DbMigrations.MIGRATION_3_4)
            .addMigrations(DbMigrations.MIGRATION_4_5)
            .addMigrations(DbMigrations.MIGRATION_5_6)
            .addMigrations(DbMigrations.MIGRATION_6_7)
            .addMigrations(DbMigrations.MIGRATION_7_8)
            .addMigrations(DbMigrations.MIGRATION_8_9)
            .addMigrations(DbMigrations.MIGRATION_9_10)
            .addMigrations(DbMigrations.MIGRATION_10_11)
            .addMigrations(DbMigrations.MIGRATION_11_12)
            .addMigrations(DbMigrations.MIGRATION_12_13)
            .addMigrations(DbMigrations.MIGRATION_13_14)
            .addMigrations(DbMigrations.MIGRATION_14_15)
            .addMigrations(DbMigrations.MIGRATION_15_16)
            .addMigrations(DbMigrations.MIGRATION_16_17)
            .addMigrations(DbMigrations.MIGRATION_17_18)
            .addMigrations(DbMigrations.MIGRATION_18_19)
            .addMigrations(DbMigrations.MIGRATION_19_20)
            .addMigrations(DbMigrations.MIGRATION_20_21)
            .addMigrations(DbMigrations.MIGRATION_21_22)
            .addMigrations(DbMigrations.MIGRATION_22_23)
            .addMigrations(DbMigrations.MIGRATION_23_24)
            .addMigrations(DbMigrations.MIGRATION_24_25)
            .addMigrations(DbMigrations.MIGRATION_25_26)
            .addMigrations(DbMigrations.MIGRATION_26_27)
            .addMigrations(
                DbMigrations.MIGRATION_27_28,
                DbMigrations.MIGRATION_28_29,
                DbMigrations.MIGRATION_29_30,
                DbMigrations.MIGRATION_30_31
            )
            .build()
}
