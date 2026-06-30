package com.grid.tv.feature.guide

import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.SmartGroupCacheDao
import com.grid.tv.data.db.entity.SmartGroupCacheEntity
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ChannelGroupIdentity
import com.grid.tv.ui.component.GuideGroupCategory
import com.grid.tv.util.isAdultChannelGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class SmartGroupService @Inject constructor(
    private val channelDao: ChannelDao,
    private val smartGroupCacheDao: SmartGroupCacheDao,
) {
    fun observeCache(): Flow<List<SmartGroupCacheEntity>> = smartGroupCacheDao.observeAll()

    suspend fun rebuildCacheForPlaylist(playlistId: Long, syncGeneration: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            smartGroupCacheDao.clearByPlaylist(playlistId)
            val rows = channelDao.groupChannelCountsForPlaylist(playlistId)
            if (rows.isEmpty()) return@withContext
            smartGroupCacheDao.insertAll(rows.map { row -> row.toCacheEntity(syncGeneration) })
        }

    suspend fun ensureCachePopulated() = withContext(Dispatchers.IO) {
        if (smartGroupCacheDao.getAll().isNotEmpty()) return@withContext
        channelDao.distinctPlaylistIds().forEach { playlistId ->
            rebuildCacheForPlaylist(playlistId)
        }
    }

    private fun com.grid.tv.data.db.model.GroupChannelCountRow.toCacheEntity(syncGeneration: Long): SmartGroupCacheEntity {
        val groupKey = ChannelGroupIdentity.groupKey(playlistId, groupName)
        val classification = SmartGroupClassifier.classify(groupName)
        return SmartGroupCacheEntity(
            playlistId = playlistId,
            groupKey = groupKey,
            rawGroupName = groupName,
            normalizedName = classification.normalizedName,
            country = classification.country,
            category = classification.category,
            channelCount = channelCount,
            syncGeneration = syncGeneration,
        )
    }

    suspend fun resolveFilterGroups(selected: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        if (selected.isEmpty()) return@withContext emptySet()
        val cache = smartGroupCacheDao.getAll()
        if (cache.isEmpty()) return@withContext selected.filterNot { SmartGroupFilterKey.isSmartKey(it) }.toSet()
        val byBucket = cache.groupBy { it.country to it.category }
        val resolved = linkedSetOf<String>()
        selected.forEach { key ->
            if (!SmartGroupFilterKey.isSmartKey(key)) {
                resolved += key
                return@forEach
            }
            val (country, category) = SmartGroupFilterKey.decode(key) ?: return@forEach
            byBucket[country to category]?.forEach { entry -> resolved += entry.groupKey }
        }
        resolved
    }

    fun organizeFromCache(
        cache: List<SmartGroupCacheEntity>,
        hideAdult: Boolean = true,
    ): GuideCategoryProcessor.OrganizedGuideGroups {
        if (cache.isEmpty()) return GuideCategoryProcessor.OrganizedGuideGroups.EMPTY

        val buckets = linkedMapOf<Pair<String, String>, MutableList<SmartGroupCacheEntity>>()
        cache.forEach { entry ->
            if (hideAdult && (entry.category == "Adult" || isAdultChannelGroup(entry.rawGroupName))) return@forEach
            buckets.getOrPut(entry.country to entry.category) { mutableListOf() }.add(entry)
        }

        val countryCategories = buckets.entries
            .groupBy { it.key.first }
            .map { (country, bucketEntries) ->
                val categories = bucketEntries
                    .sortedWith(
                        compareBy(
                            { SmartGroupClassifier.categorySortIndex(it.key.second) },
                            { it.key.second }
                        )
                    )
                    .map { (bucketKey, entries) ->
                        SmartGroupFilterKey.encode(bucketKey.first, bucketKey.second) to
                            entries.sumOf { it.channelCount }
                    }
                GuideGroupCategory(
                    displayName = country,
                    groups = categories.map { it.first },
                    channelCount = categories.sumOf { it.second },
                )
            }
            .sortedWith(
                compareBy(
                    { SmartGroupClassifier.countrySortIndex(it.displayName) },
                    { it.displayName }
                )
            )

        val contentCategories = buckets.entries
            .groupBy { it.key.second }
            .map { (category, bucketEntries) ->
                GuideGroupCategory(
                    displayName = category,
                    groups = bucketEntries.map { (bucketKey, entries) ->
                        SmartGroupFilterKey.encode(bucketKey.first, bucketKey.second)
                    }.sorted(),
                    channelCount = bucketEntries.sumOf { it.value.sumOf { entry -> entry.channelCount } },
                )
            }
            .sortedWith(
                compareBy(
                    { SmartGroupClassifier.categorySortIndex(it.displayName) },
                    { it.displayName }
                )
            )

        val flat = countryCategories.map { country ->
            val childGroups = buckets.entries
                .filter { it.key.first == country.displayName }
                .sortedWith(
                    compareBy(
                        { SmartGroupClassifier.categorySortIndex(it.key.second) },
                        { it.key.second }
                    )
                )
                .map { (bucketKey, _) -> SmartGroupFilterKey.encode(bucketKey.first, bucketKey.second) }
            country.copy(groups = childGroups)
        }

        return GuideCategoryProcessor.OrganizedGuideGroups(
            allChannelCount = cache.sumOf { it.channelCount },
            countryCategories = countryCategories,
            contentCategories = contentCategories,
            providerCategories = emptyList(),
            flatCategories = flat,
        )
    }

    fun appliesSmartFilter(channel: Channel, selected: Set<String>, cache: List<SmartGroupCacheEntity>): Boolean {
        if (selected.isEmpty()) return true
        val providerKey = ChannelGroupIdentity.groupKey(channel.playlistId, channel.group)
        val byBucket = cache.groupBy { it.country to it.category }
        return selected.any { key ->
            if (!SmartGroupFilterKey.isSmartKey(key)) {
                return@any ChannelGroupIdentity.matches(channel, key)
            }
            val bucket = SmartGroupFilterKey.decode(key) ?: return@any false
            byBucket[bucket]?.any { it.groupKey == providerKey } == true
        }
    }
}
