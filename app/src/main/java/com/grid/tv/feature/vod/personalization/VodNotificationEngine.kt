package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.VodUserNotificationDao
import com.grid.tv.data.db.entity.VodUserNotificationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodNotificationEngine @Inject constructor(
    private val notificationDao: VodUserNotificationDao
) {
    suspend fun createFromDetections(
        profileId: Long,
        detections: List<NewEpisodeDetection>,
        syncResult: CatalogMonitor.CatalogSyncResult?
    ): List<VodNotification> {
        val notifications = mutableListOf<VodNotification>()

        detections.forEach { detection ->
            notifications += createNotification(
                profileId = profileId,
                type = VodNotificationType.NEW_EPISODE,
                detection = detection
            )
        }

        syncResult?.addedSeasons?.forEach { season ->
            notifications += VodNotification(
                profileId = profileId,
                type = VodNotificationType.NEW_SEASON,
                seriesId = syncResult.seriesId,
                seasonNumber = season,
                episodeNumber = null,
                seriesTitle = syncResult.seriesTitle,
                episodeTitle = null,
                contentKey = null,
                createdAt = System.currentTimeMillis(),
                pushPending = true
            )
        }

        val persisted = notifications.map { it.toEntity() }
        if (persisted.isNotEmpty()) {
            notificationDao.insertAll(persisted)
        }
        return notifications
    }

    suspend fun getUnread(profileId: Long): List<VodNotification> =
        notificationDao.unreadForProfile(profileId).map { it.toDomain() }

    suspend fun unreadCount(profileId: Long): Int = notificationDao.unreadCount(profileId)

    suspend fun markRead(notificationId: Long) {
        notificationDao.markRead(notificationId)
    }

    suspend fun markAllRead(profileId: Long) {
        notificationDao.markAllRead(profileId)
    }

    suspend fun pendingPush(profileId: Long): List<VodNotification> =
        notificationDao.pendingPush(profileId).map { it.toDomain() }

    suspend fun markPushDispatched(notificationId: Long) {
        notificationDao.markPushDispatched(notificationId)
    }

    private fun createNotification(
        profileId: Long,
        type: VodNotificationType,
        detection: NewEpisodeDetection
    ): VodNotification = VodNotification(
        profileId = profileId,
        type = type,
        seriesId = detection.seriesId,
        seasonNumber = detection.seasonNumber,
        episodeNumber = detection.episodeNumber,
        seriesTitle = detection.seriesTitle,
        episodeTitle = detection.episodeTitle,
        contentKey = detection.contentKey,
        createdAt = System.currentTimeMillis(),
        pushPending = true
    )

    private fun VodNotification.toEntity() = VodUserNotificationEntity(
        id = id,
        profileId = profileId,
        type = type.name,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        seriesTitle = seriesTitle,
        episodeTitle = episodeTitle,
        contentKey = contentKey,
        createdAt = createdAt,
        readAt = readAt,
        pushPending = pushPending
    )

    private fun VodUserNotificationEntity.toDomain() = VodNotification(
        id = id,
        profileId = profileId,
        type = VodNotificationType.fromStored(type),
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        seriesTitle = seriesTitle,
        episodeTitle = episodeTitle,
        contentKey = contentKey,
        createdAt = createdAt,
        readAt = readAt,
        pushPending = pushPending
    )
}
