package com.grid.tv.domain.epg

import com.grid.tv.data.db.dao.CanonicalChannelDao
import com.grid.tv.data.db.dao.EpgLearnedMappingDao
import com.grid.tv.data.db.entity.CanonicalChannelEntity
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgMatcher @Inject constructor(
    private val normalizer: ChannelNameNormalizer,
    private val canonicalDao: CanonicalChannelDao,
    private val learnedDao: EpgLearnedMappingDao
) {
    suspend fun match(
        channelName: String,
        playlistTvgId: String?,
        candidates: List<EpgSourceChannelEntity>
    ): EpgMatchOutcome {
        val normalizedInput = normalizer.normalize(channelName)
        val ranked = mutableListOf<EpgMatchCandidate>()
        var exactIdMatched = false

        playlistTvgId?.trim()?.takeIf { it.isNotEmpty() }?.let { tvgId ->
            candidates.firstOrNull { it.epgId.equals(tvgId, ignoreCase = true) }?.let { hit ->
                exactIdMatched = true
                ranked += EpgMatchCandidate(
                    epgId = hit.epgId,
                    epgName = hit.displayName,
                    confidence = 100,
                    source = hit.source,
                    reason = EpgMatchReason.TVG_ID_EXACT
                )
            }
            if (!exactIdMatched) {
                val normalizedTvgId = EpgIdNormalizer.normalize(tvgId)
                if (normalizedTvgId.isNotEmpty()) {
                    candidates.firstOrNull { EpgIdNormalizer.normalize(it.epgId) == normalizedTvgId }?.let { hit ->
                        exactIdMatched = true
                        ranked += EpgMatchCandidate(
                            epgId = hit.epgId,
                            epgName = hit.displayName,
                            confidence = 99,
                            source = hit.source,
                            reason = EpgMatchReason.TVG_ID_NORMALIZED
                        )
                    }
                }
            }
        }

        learnedDao.get(normalizedInput)?.let { learned ->
            val hit = resolveCandidate(learned.epgId, learned.epgDisplayName, learned.source, candidates)
            if (hit != null) {
                ranked += EpgMatchCandidate(
                    epgId = hit.epgId,
                    epgName = hit.displayName,
                    confidence = 100,
                    source = hit.source,
                    reason = EpgMatchReason.LEARNED_MAPPING
                )
            } else {
                ranked += EpgMatchCandidate(
                    epgId = learned.epgId,
                    epgName = learned.epgDisplayName,
                    confidence = 100,
                    source = learned.source,
                    reason = EpgMatchReason.LEARNED_MAPPING
                )
            }
        }

        findCanonicalMatch(normalizedInput)?.let { canonical ->
            resolveCanonicalCandidate(canonical, candidates)?.let { hit ->
                ranked += EpgMatchCandidate(
                    epgId = hit.epgId,
                    epgName = hit.displayName,
                    confidence = 98,
                    source = hit.source,
                    reason = EpgMatchReason.CANONICAL_ALIAS
                )
            }
        }

        candidates
            .filter { it.normalizedName == normalizedInput }
            .forEach { hit ->
                ranked += EpgMatchCandidate(
                    epgId = hit.epgId,
                    epgName = hit.displayName,
                    confidence = 100,
                    source = hit.source,
                    reason = EpgMatchReason.NORMALIZED_EXACT
                )
            }

        if (!exactIdMatched) {
            val alreadyMatched = ranked.map { it.epgId }.toSet()
            val displayNames = candidates.map { it.displayName }
            normalizer.bestFuzzyMatch(channelName, displayNames, minConfidence = 55)?.let { (index, confidence) ->
                val hit = candidates[index]
                if (!alreadyMatched.contains(hit.epgId)) {
                    ranked += EpgMatchCandidate(
                        epgId = hit.epgId,
                        epgName = hit.displayName,
                        confidence = confidence,
                        source = hit.source,
                        reason = EpgMatchReason.FUZZY
                    )
                }
            }
        }

        val deduped = ranked
            .groupBy { it.epgId }
            .map { (_, group) -> group.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }

        return EpgMatchOutcome(
            best = deduped.firstOrNull(),
            candidates = deduped.take(10)
        )
    }

    private suspend fun findCanonicalMatch(normalizedInput: String): CanonicalChannelEntity? {
        val all = canonicalDao.all()
        return all.firstOrNull { canonical ->
            if (canonical.normalizedName == normalizedInput) return@firstOrNull true
            canonical.aliases.split('|').any { alias ->
                normalizer.normalize(alias) == normalizedInput
            }
        }
    }

    private fun resolveCanonicalCandidate(
        canonical: CanonicalChannelEntity,
        candidates: List<EpgSourceChannelEntity>
    ): EpgSourceChannelEntity? {
        candidates.firstOrNull { it.epgId.equals(canonical.epgId, ignoreCase = true) }?.let { return it }
        val canonicalNorm = canonical.normalizedName
        return candidates.firstOrNull { it.normalizedName == canonicalNorm }
    }

    private fun resolveCandidate(
        epgId: String,
        displayName: String,
        source: String,
        candidates: List<EpgSourceChannelEntity>
    ): EpgSourceChannelEntity? {
        return candidates.firstOrNull { it.epgId.equals(epgId, ignoreCase = true) }
            ?: candidates.firstOrNull {
                normalizer.normalize(it.displayName) == normalizer.normalize(displayName)
            }
    }
}
