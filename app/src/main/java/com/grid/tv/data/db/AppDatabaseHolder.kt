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
            // Never use fallbackToDestructiveMigration* — wipes user data on missing/broken migration.
            .addMigrations(*DbMigrations.ALL)
            .build()
}
