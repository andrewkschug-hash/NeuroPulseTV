package com.grid.tv.data.repository

import com.grid.tv.data.db.dao.FavoriteGroupDao
import com.grid.tv.data.db.dao.ProfileFavoriteDao
import com.grid.tv.data.db.entity.FavoriteGroupEntity
import com.grid.tv.data.db.entity.ProfileFavoriteEntity
import com.grid.tv.domain.model.FavoriteGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FavoritesRepository @Inject constructor(
    private val groupDao: FavoriteGroupDao,
    private val favoriteDao: ProfileFavoriteDao
) {
    companion object {
        val DEFAULT_GROUP_NAMES = listOf("Favorites", "Sports", "Movies", "Kids", "News")
    }

    fun observeGroups(profileId: Long): Flow<List<FavoriteGroup>> =
        groupDao.observeForProfile(profileId).map { rows ->
            rows.map { FavoriteGroup(it.id, it.name, it.sortOrder) }
        }

    suspend fun ensureDefaultGroups(profileId: Long) {
        val existing = groupDao.getAllForProfile(profileId)
        if (existing.isNotEmpty()) return
        DEFAULT_GROUP_NAMES.forEachIndexed { index, name ->
            groupDao.insert(FavoriteGroupEntity(profileId = profileId, name = name, sortOrder = index))
        }
        val favoritesGroupId = groupDao.getByName(profileId, "Favorites")?.id ?: return
        favoriteDao.assignNullGroupTo(profileId, favoritesGroupId)
    }

    suspend fun createGroup(profileId: Long, name: String): Long {
        val nextOrder = (groupDao.getAllForProfile(profileId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        return groupDao.insert(FavoriteGroupEntity(profileId = profileId, name = name.trim(), sortOrder = nextOrder))
    }

    suspend fun renameGroup(profileId: Long, groupId: Long, name: String) {
        val group = groupDao.getById(groupId) ?: return
        if (group.profileId != profileId) return
        groupDao.update(group.copy(name = name.trim()))
    }

    suspend fun deleteGroup(profileId: Long, groupId: Long) {
        val group = groupDao.getById(groupId) ?: return
        if (group.profileId != profileId) return
        if (group.name == "Favorites") return
        favoriteDao.removeByGroup(profileId, groupId)
        groupDao.delete(groupId, profileId)
    }

    suspend fun reorderGroups(profileId: Long, orderedGroupIds: List<Long>) {
        orderedGroupIds.forEachIndexed { index, id ->
            val group = groupDao.getById(id) ?: return@forEachIndexed
            if (group.profileId != profileId) return@forEachIndexed
            groupDao.update(group.copy(sortOrder = index))
        }
    }

    suspend fun addChannelToGroup(profileId: Long, channelId: Long, groupId: Long) {
        val existing = favoriteDao.get(profileId, channelId)
        favoriteDao.upsert(
            ProfileFavoriteEntity(
                profileId = profileId,
                channelId = channelId,
                groupId = groupId,
                sortOrder = existing?.sortOrder ?: 0,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun removeChannelFromGroup(profileId: Long, channelId: Long, groupId: Long) {
        val existing = favoriteDao.get(profileId, channelId) ?: return
        if (existing.groupId == groupId) {
            favoriteDao.remove(profileId, channelId)
        }
    }

    suspend fun groupsForChannel(profileId: Long, channelId: Long): List<Long> =
        favoriteDao.getGroupIdsForChannel(profileId, channelId)
}
