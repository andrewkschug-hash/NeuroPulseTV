package com.grid.tv.data.db

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Thread-safe lazy holder so Room opens on a background thread when prewarmed at startup,
 * instead of blocking the main thread during the first Hilt injection.
 */
internal object AppDatabaseHolder {
    private const val TAG = "AppDatabaseHolder"
    private const val PREWARM_WAIT_MS = 10_000L

    private val lock = Any()
    private val prewarmLatch = CountDownLatch(1)

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        instance?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            waitForPrewarm()
            instance?.let { return it }
            Log.w(TAG, "Database accessed on main before prewarm completed — deferring open")
        }
        return openDatabase(context.applicationContext)
    }

    /** Opens the database file on the calling thread — call from a background dispatcher. */
    fun prewarm(context: Context) {
        try {
            openDatabase(context.applicationContext)
        } finally {
            prewarmLatch.countDown()
        }
    }

    private fun waitForPrewarm() {
        if (prewarmLatch.count == 0L) return
        prewarmLatch.await(PREWARM_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    private fun openDatabase(context: Context): AppDatabase =
        synchronized(lock) {
            instance ?: buildDatabase(context).also { instance = it }
        }

    private fun buildDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "streamflow.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Never use fallbackToDestructiveMigration* — wipes user data on missing/broken migration.
            .addMigrations(*DbMigrations.ALL)
            .build()
}
