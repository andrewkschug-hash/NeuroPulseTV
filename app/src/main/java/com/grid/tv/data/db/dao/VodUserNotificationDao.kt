package com.grid.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.grid.tv.data.db.entity.VodUserNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VodUserNotificationDao {
    @Insert
    suspend fun insert(notification: VodUserNotificationEntity): Long

    @Insert
    suspend fun insertAll(notifications: List<VodUserNotificationEntity>)

    @Query(
        """
        SELECT * FROM vod_user_notifications
        WHERE profileId = :profileId AND readAt IS NULL
        ORDER BY createdAt DESC
        """
    )
    suspend fun unreadForProfile(profileId: Long): List<VodUserNotificationEntity>

    @Query(
        """
        SELECT * FROM vod_user_notifications
        WHERE profileId = :profileId AND readAt IS NULL
        ORDER BY createdAt DESC
        """
    )
    fun observeUnread(profileId: Long): Flow<List<VodUserNotificationEntity>>

    @Query("SELECT COUNT(*) FROM vod_user_notifications WHERE profileId = :profileId AND readAt IS NULL")
    suspend fun unreadCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM vod_user_notifications WHERE profileId = :profileId AND readAt IS NULL")
    fun observeUnreadCount(profileId: Long): Flow<Int>

    @Query("UPDATE vod_user_notifications SET readAt = :readAt WHERE id = :id")
    suspend fun markRead(id: Long, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE vod_user_notifications SET readAt = :readAt WHERE profileId = :profileId AND readAt IS NULL")
    suspend fun markAllRead(profileId: Long, readAt: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT * FROM vod_user_notifications
        WHERE profileId = :profileId AND pushPending = 1
        ORDER BY createdAt ASC
        """
    )
    suspend fun pendingPush(profileId: Long): List<VodUserNotificationEntity>

    @Query("UPDATE vod_user_notifications SET pushPending = 0 WHERE id = :id")
    suspend fun markPushDispatched(id: Long)

    @Query("DELETE FROM vod_user_notifications WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM vod_user_notifications")
    suspend fun deleteAll()
}
