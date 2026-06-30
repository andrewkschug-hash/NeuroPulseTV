package com.grid.tv.data.db

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.room.Room
import androidx.room.RoomDatabase
import com.grid.tv.feature.startup.StartupTiming
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Thread-safe lazy holder so Room opens on a background thread when prewarmed at startup,
 * instead of blocking the main thread during the first Hilt injection.
 */
internal object AppDatabaseHolder {
    private const val PREWARM_WAIT_MS = 10_000L

    private val lock = Any()
    private val prewarmLatch = CountDownLatch(1)
    private val roomOpenExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "room-open").apply { isDaemon = true }
    }

    @Volatile
    private var instance: AppDatabase? = null

    fun isReady(): Boolean = instance != null

    fun get(context: Context): AppDatabase {
        StartupTiming.log("AppDatabaseHolder.get() entered")
        instance?.let {
            StartupTiming.log("AppDatabaseHolder.get() returning cached instance")
            return it
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            StartupTiming.log(
                "AppDatabaseHolder.get() on MAIN — delegating Room open to background executor"
            )
            return roomOpenExecutor.submit<AppDatabase> {
                openDatabaseOffMain(context.applicationContext)
            }.get()
        }
        return openDatabaseOffMain(context.applicationContext)
    }

    /** Opens the database file on the calling thread — call from a background dispatcher. */
    fun prewarm(context: Context) {
        StartupTiming.log("AppDatabaseHolder.prewarm() start")
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                roomOpenExecutor.submit {
                    openDatabase(context.applicationContext)
                }.get()
            } else {
                openDatabase(context.applicationContext)
            }
        } finally {
            prewarmLatch.countDown()
            StartupTiming.log("AppDatabaseHolder.prewarm() latch released")
        }
    }

    private fun openDatabaseOffMain(context: Context): AppDatabase {
        waitForPrewarm()
        return openDatabase(context)
    }

    private fun waitForPrewarm() {
        if (prewarmLatch.count == 0L) {
            StartupTiming.log("AppDatabaseHolder.waitForPrewarm() skipped — latch already open")
            return
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            StartupTiming.log("AppDatabaseHolder.waitForPrewarm() skipped on MAIN")
            return
        }
        StartupTiming.log("AppDatabaseHolder.waitForPrewarm() CountDownLatch.await($PREWARM_WAIT_MS) start")
        val waitStart = SystemClock.elapsedRealtime()
        val completed = prewarmLatch.await(PREWARM_WAIT_MS, TimeUnit.MILLISECONDS)
        val waitMs = SystemClock.elapsedRealtime() - waitStart
        StartupTiming.recordSpan("AppDatabaseHolder.CountDownLatch.await", waitMs)
        StartupTiming.log(
            "AppDatabaseHolder.waitForPrewarm() completed in ${waitMs}ms " +
                "completed=$completed remaining=${prewarmLatch.count}"
        )
    }

    private fun openDatabase(context: Context): AppDatabase =
        synchronized(lock) {
            instance?.let { return it }
            val db = StartupTiming.trace("AppDatabaseHolder.Room.build") {
                buildDatabase(context)
            }
            instance = db
            StartupTiming.log("AppDatabaseHolder returning database instance")
            db
        }

    private fun buildDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "streamflow.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Never use fallbackToDestructiveMigration* — wipes user data on missing/broken migration.
            .addMigrations(*DbMigrations.ALL)
            .build()
}
