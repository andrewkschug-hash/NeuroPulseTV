package com.grid.tv.domain.epg

import com.grid.tv.data.db.dao.EpgAliasHitDao
import com.grid.tv.data.db.dao.EpgLearnedMappingDao
import com.grid.tv.data.db.dao.EpgMatchAnalyticsDao
import com.grid.tv.data.db.entity.EpgAliasHitEntity
import com.grid.tv.data.db.entity.EpgLearnedMappingEntity
import com.grid.tv.data.db.entity.EpgMatchAnalyticsEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgMatchAnalyticsTracker @Inject constructor(
    private val analyticsDao: EpgMatchAnalyticsDao,
    private val aliasHitDao: EpgAliasHitDao,
    private val learnedDao: EpgLearnedMappingDao,
    private val normalizer: ChannelNameNormalizer
) {
    suspend fun recordAutoMatch(reason: EpgMatchReason) {
        val current = analyticsDao.get() ?: EpgMatchAnalyticsEntity()
        analyticsDao.upsert(
            current.copy(
                totalAttempts = current.totalAttempts + 1,
                autoMatched = current.autoMatched + 1,
                tvgIdMatches = current.tvgIdMatches + if (reason == EpgMatchReason.TVG_ID_EXACT) 1 else 0,
                learnedMatches = current.learnedMatches + if (reason == EpgMatchReason.LEARNED_MAPPING) 1 else 0,
                canonicalMatches = current.canonicalMatches + if (reason == EpgMatchReason.CANONICAL_ALIAS) 1 else 0,
                exactNameMatches = current.exactNameMatches + if (reason == EpgMatchReason.NORMALIZED_EXACT) 1 else 0,
                fuzzyMatches = current.fuzzyMatches + if (reason == EpgMatchReason.FUZZY) 1 else 0,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordSuggested(reason: EpgMatchReason) {
        val current = analyticsDao.get() ?: EpgMatchAnalyticsEntity()
        analyticsDao.upsert(
            current.copy(
                totalAttempts = current.totalAttempts + 1,
                suggested = current.suggested + 1,
                fuzzyMatches = current.fuzzyMatches + if (reason == EpgMatchReason.FUZZY) 1 else 0,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordUnmatched() {
        val current = analyticsDao.get() ?: EpgMatchAnalyticsEntity()
        analyticsDao.upsert(
            current.copy(
                totalAttempts = current.totalAttempts + 1,
                unmatched = current.unmatched + 1,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordManualCorrection(originalName: String) {
        val current = analyticsDao.get() ?: EpgMatchAnalyticsEntity()
        analyticsDao.upsert(
            current.copy(
                manualCorrections = current.manualCorrections + 1,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
        recordAliasHit(originalName)
    }

    suspend fun recordAliasHit(originalName: String) {
        val normalized = normalizer.normalize(originalName)
        if (normalized.isBlank()) return
        val existing = aliasHitDao.get(normalized)
        aliasHitDao.upsert(
            EpgAliasHitEntity(
                normalizedAlias = normalized,
                originalNameSample = originalName,
                hitCount = (existing?.hitCount ?: 0) + 1,
                lastSeenAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveLearnedMapping(
        originalName: String,
        epgId: String,
        epgDisplayName: String,
        source: String
    ) {
        val normalized = normalizer.normalize(originalName)
        if (normalized.isBlank()) return
        learnedDao.upsert(
            EpgLearnedMappingEntity(
                normalizedOriginalName = normalized,
                originalNameSample = originalName,
                epgId = epgId,
                epgDisplayName = epgDisplayName,
                source = source
            )
        )
        recordAliasHit(originalName)
    }

    suspend fun summary(): EpgAnalyticsSummary {
        val stats = analyticsDao.get() ?: EpgMatchAnalyticsEntity()
        val total = stats.totalAttempts.coerceAtLeast(1)
        val matched = stats.autoMatched + stats.manualCorrections
        val topAliases = aliasHitDao.topAliases(10).map { it.originalNameSample to it.hitCount }
        return EpgAnalyticsSummary(
            totalAttempts = stats.totalAttempts,
            autoMatched = stats.autoMatched,
            suggested = stats.suggested,
            manualCorrections = stats.manualCorrections,
            unmatched = stats.unmatched,
            matchRatePercent = matched * 100f / total,
            unmatchedRatePercent = stats.unmatched * 100f / total,
            manualCorrectionRatePercent = stats.manualCorrections * 100f / total,
            tvgIdMatches = stats.tvgIdMatches,
            learnedMatches = stats.learnedMatches,
            canonicalMatches = stats.canonicalMatches,
            exactNameMatches = stats.exactNameMatches,
            fuzzyMatches = stats.fuzzyMatches,
            topAliases = topAliases
        )
    }
}
