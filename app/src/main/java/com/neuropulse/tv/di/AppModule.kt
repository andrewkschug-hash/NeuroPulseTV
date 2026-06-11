package com.neuropulse.tv.di

import android.content.Context
import android.app.AlarmManager
import androidx.room.Room
import androidx.work.WorkManager
import com.neuropulse.tv.data.db.AppDatabase
import com.neuropulse.tv.data.db.DbMigrations
import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.ChannelScanDao
import com.neuropulse.tv.data.db.dao.EpgResolutionSuggestionDao
import com.neuropulse.tv.data.db.dao.FavoriteDao
import com.neuropulse.tv.data.db.dao.FavoriteGroupDao
import com.neuropulse.tv.data.db.dao.EpgSourceChannelDao
import com.neuropulse.tv.data.db.dao.PlaylistDao
import com.neuropulse.tv.data.db.dao.ProfileDao
import com.neuropulse.tv.data.db.dao.ProfileFavoriteDao
import com.neuropulse.tv.data.db.dao.ProfileSettingsDao
import com.neuropulse.tv.data.db.dao.ProfileWatchHistoryDao
import com.neuropulse.tv.data.db.dao.ProgramDao
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.RecordingDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.dao.StreamHealthDao
import com.neuropulse.tv.data.db.dao.WatchHistoryDao
import com.neuropulse.tv.data.network.parser.M3uParser
import com.neuropulse.tv.data.network.parser.XtreamParser
import com.neuropulse.tv.data.network.parser.XmlTvParser
import com.neuropulse.tv.data.repository.IptvRepositoryImpl
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "streamflow.db")
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
            .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    fun provideM3uParser(): M3uParser = M3uParser()

    @Provides
    fun provideXmlTvParser(): XmlTvParser = XmlTvParser()

    @Provides
    fun provideXtreamParser(): XtreamParser = XtreamParser()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideChannelScanDao(db: AppDatabase): ChannelScanDao = db.channelScanDao()

    @Provides
    fun provideProgramDao(db: AppDatabase): ProgramDao = db.programDao()

    @Provides
    fun provideRecordingDao(db: AppDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchHistoryDao(db: AppDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideProfileFavoriteDao(db: AppDatabase): ProfileFavoriteDao = db.profileFavoriteDao()

    @Provides
    fun provideProfileWatchHistoryDao(db: AppDatabase): ProfileWatchHistoryDao = db.profileWatchHistoryDao()

    @Provides
    fun provideProfileSettingsDao(db: AppDatabase): ProfileSettingsDao = db.profileSettingsDao()

    @Provides
    fun provideStreamHealthDao(db: AppDatabase): StreamHealthDao = db.streamHealthDao()

    @Provides
    fun provideScheduledRecordingDao(db: AppDatabase): ScheduledRecordingDao = db.scheduledRecordingDao()

    @Provides
    fun provideRecordedMediaDao(db: AppDatabase): RecordedMediaDao = db.recordedMediaDao()

    @Provides
    fun provideEpgSourceChannelDao(db: AppDatabase): EpgSourceChannelDao = db.epgSourceChannelDao()

    @Provides
    fun provideEpgResolutionSuggestionDao(db: AppDatabase): EpgResolutionSuggestionDao = db.epgResolutionSuggestionDao()

    @Provides
    fun provideFavoriteGroupDao(db: AppDatabase): FavoriteGroupDao = db.favoriteGroupDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    abstract fun bindRepository(impl: IptvRepositoryImpl): IptvRepository
}
