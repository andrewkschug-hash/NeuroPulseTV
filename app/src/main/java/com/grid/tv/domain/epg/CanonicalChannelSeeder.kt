package com.grid.tv.domain.epg

import com.grid.tv.data.db.dao.CanonicalChannelDao
import com.grid.tv.data.db.entity.CanonicalChannelEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalChannelSeeder @Inject constructor(
    private val canonicalDao: CanonicalChannelDao,
    private val normalizer: ChannelNameNormalizer
) {
    suspend fun ensureSeeded() {
        if (canonicalDao.count() > 0) return
        canonicalDao.insertAll(
            CanonicalChannelSeed.channels.map { def ->
                CanonicalChannelEntity(
                    id = def.id,
                    canonicalName = def.canonicalName,
                    country = def.country,
                    epgId = def.epgId,
                    logoUrl = def.logoUrl,
                    category = def.category,
                    aliases = def.aliases.joinToString("|"),
                    normalizedName = normalizer.normalize(def.canonicalName)
                )
            }
        )
    }
}
