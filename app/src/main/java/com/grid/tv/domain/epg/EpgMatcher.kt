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

        playlistTvgId?.trim()?.takeIf { it.isNotEmpty() }?.let { tvgId ->
            candidates.firstOrNull { it.epgId.equals(tvgId, ignoreCase = true) }?.let { hit ->
                ranked += EpgMatchCandidate(
                    epgId = hit.epgId,
                    epgName = hit.displayName,
                    confidence = 100,
                    source = hit.source,
                    reason = EpgMatchReason.TVG_ID_EXACT
                )
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

        candidates.forEach { candidate ->
            val confidence = normalizer.calculateConfidence(channelName, candidate.displayName)
            if (confidence >= 55) {
                ranked += EpgMatchCandidate(
                    epgId = candidate.epgId,
                    epgName = candidate.displayName,
                    confidence = confidence,
                    source = candidate.source,
                    reason = EpgMatchReason.FUZZY
                )
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
