package com.grid.tv.domain.epg

data class XmlTvChannelRef(
    val id: String,
    val displayName: String
)

enum class EpgLinkMatchReason {
    EXACT_ID,
    NORMALIZED_ID,
    NORMALIZED_NAME,
    NO_MATCH
}

data class EpgLinkResult(
    val xmlTvChannelId: String?,
    val reason: EpgLinkMatchReason
)

/**
 * Resolves playlist tvg-id / tvg-name values to XMLTV channel ids stored on programmes.
 */
class EpgChannelLinkResolver(
    xmlTvChannels: List<XmlTvChannelRef>
) {
    private val byExactIdLower: Map<String, XmlTvChannelRef>
    private val byNormalizedId: Map<String, XmlTvChannelRef>
    private val byNormalizedName: Map<String, XmlTvChannelRef>

    init {
        val channels = xmlTvChannels.distinctBy { it.id }
        byExactIdLower = channels.associateBy { it.id.lowercase() }
        byNormalizedId = buildMap {
            channels.forEach { channel ->
                val key = EpgIdNormalizer.normalize(channel.id)
                if (key.isNotEmpty()) putIfAbsent(key, channel)
            }
        }
        byNormalizedName = buildMap {
            channels.forEach { channel ->
                val key = EpgIdNormalizer.normalize(channel.displayName)
                if (key.isNotEmpty()) putIfAbsent(key, channel)
            }
        }
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

        val normalizedName = EpgIdNormalizer.normalize(tvgName)
        if (normalizedName.isNotEmpty()) {
            byNormalizedName[normalizedName]?.let {
                return EpgLinkResult(it.id, EpgLinkMatchReason.NORMALIZED_NAME)
            }
        }

        return EpgLinkResult(null, EpgLinkMatchReason.NO_MATCH)
    }
}
