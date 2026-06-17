package com.grid.tv.domain.epg

enum class EpgMatchReason(val label: String) {
    TVG_ID_EXACT("Exact TVG-ID match"),
    LEARNED_MAPPING("Previously corrected by you"),
    CANONICAL_ALIAS("Canonical channel alias"),
    NORMALIZED_EXACT("Exact normalized name"),
    FUZZY("Fuzzy name similarity"),
    NONE("No match")
}

data class EpgMatchCandidate(
    val epgId: String,
    val epgName: String,
    val confidence: Int,
    val source: String,
    val reason: EpgMatchReason
)

data class EpgMatchOutcome(
    val best: EpgMatchCandidate?,
    val candidates: List<EpgMatchCandidate>
)

data class EpgFixProposal(
    val channelId: Long,
    val channelName: String,
    val proposedEpgId: String,
    val proposedEpgName: String,
    val confidence: Int,
    val reason: EpgMatchReason,
    val source: String
)

data class EpgAnalyticsSummary(
    val totalAttempts: Long = 0,
    val autoMatched: Long = 0,
    val suggested: Long = 0,
    val manualCorrections: Long = 0,
    val unmatched: Long = 0,
    val matchRatePercent: Float = 0f,
    val unmatchedRatePercent: Float = 0f,
    val manualCorrectionRatePercent: Float = 0f,
    val tvgIdMatches: Long = 0,
    val learnedMatches: Long = 0,
    val canonicalMatches: Long = 0,
    val exactNameMatches: Long = 0,
    val fuzzyMatches: Long = 0,
    val topAliases: List<Pair<String, Long>> = emptyList()
)
