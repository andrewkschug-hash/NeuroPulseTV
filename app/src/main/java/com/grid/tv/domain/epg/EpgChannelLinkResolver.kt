package com.grid.tv.domain.epg

data class XmlTvChannelRef(
    val id: String,
    val displayName: String
)

enum class EpgLinkMatchReason {
    EXACT_ID,
    NORMALIZED_ID,
    NORMALIZED_NAME,
    LEARNED_MAPPING,
    FUZZY_NAME,
    NO_MATCH
}

data class EpgLinkResult(
    val xmlTvChannelId: String?,
    val reason: EpgLinkMatchReason
)

/**
 * Resolves playlist tvg-id / tvg-name values to XMLTV channel ids stored on programmes.
 * Build one resolver per playlist (`xmltv:{playlistId}` source + that playlist's programme ids).
 * Exact ID matching runs first; fuzzy name matching is the fallback when IDs do not match.
 */
class EpgChannelLinkResolver(
    xmlTvChannels: List<XmlTvChannelRef>,
    learnedMappings: Map<String, String> = emptyMap(),
    private val normalizer: ChannelNameNormalizer = ChannelNameNormalizer()
) {
    private val channels: List<XmlTvChannelRef>
    private val byExactIdLower: Map<String, XmlTvChannelRef>
    private val byNormalizedId: Map<String, XmlTvChannelRef>
    private val byNormalizedName: Map<String, XmlTvChannelRef>
    private val learnedEpgIdByNormalizedName: Map<String, String>
    /** Built once — never allocate per [resolve] call. */
    private val fuzzyCandidateDisplayNames: List<String>?
    private val fuzzyMatchingEnabled: Boolean

    init {
        channels = xmlTvChannels.distinctBy { it.id }
        fuzzyMatchingEnabled = channels.size <= FUZZY_MATCH_MAX_CHANNELS
        fuzzyCandidateDisplayNames = if (fuzzyMatchingEnabled) {
            channels.map { it.displayName }
        } else {
            null
        }
        byExactIdLower = channels.associateBy { it.id.lowercase() }
        byNormalizedId = buildMap {
            channels.forEach { channel ->
                val key = EpgIdNormalizer.normalize(channel.id)
                if (key.isNotEmpty()) putIfAbsent(key, channel)
            }
        }
        byNormalizedName = buildMap {
            channels.forEach { channel ->
                val key = normalizer.normalize(channel.displayName)
                if (key.isNotEmpty()) putIfAbsent(key, channel)
            }
        }
        learnedEpgIdByNormalizedName = learnedMappings
    }

    fun resolve(tvgId: String?, tvgName: String?): EpgLinkResult {
        val trimmedId = tvgId?.trim().orEmpty()
        if (trimmedId.isNotEmpty()) {
            byExactIdLower[trimmedId.lowercase()]?.let {
                return EpgLinkResult(it.id, EpgLinkMatchReason.EXACT_ID)
            }
            val normalizedId = EpgIdNormalizer.normalize(trimmedId)
            if (normalizedId.isNotEmpty()) {
                byNormalizedId[normalizedId]?.let {
                    return EpgLinkResult(it.id, EpgLinkMatchReason.NORMALIZED_ID)
                }
            }
        }

        val normalizedChannelName = normalizer.normalize(tvgName.orEmpty())
        if (normalizedChannelName.isNotEmpty()) {
            learnedEpgIdByNormalizedName[normalizedChannelName]?.let { learnedEpgId ->
                channels.firstOrNull { it.id.equals(learnedEpgId, ignoreCase = true) }?.let {
                    return EpgLinkResult(it.id, EpgLinkMatchReason.LEARNED_MAPPING)
                }
                return EpgLinkResult(learnedEpgId, EpgLinkMatchReason.LEARNED_MAPPING)
            }

            byNormalizedName[normalizedChannelName]?.let {
                return EpgLinkResult(it.id, EpgLinkMatchReason.NORMALIZED_NAME)
            }

            val displayNames = fuzzyCandidateDisplayNames
            if (displayNames != null) {
                val fuzzyHit = normalizer.bestFuzzyMatch(tvgName.orEmpty(), displayNames, minConfidence = 55)
                if (fuzzyHit != null) {
                    val matched = channels[fuzzyHit.first]
                    return EpgLinkResult(matched.id, EpgLinkMatchReason.FUZZY_NAME)
                }
            }
        }

        return EpgLinkResult(null, EpgLinkMatchReason.NO_MATCH)
    }

    companion object {
        /** Fuzzy Levenshtein over larger catalogs stalls the guide and triggers GC thrashing. */
        const val FUZZY_MATCH_MAX_CHANNELS = 2_500
    }
}
