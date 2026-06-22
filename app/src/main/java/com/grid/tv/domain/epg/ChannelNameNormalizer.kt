package com.grid.tv.domain.epg

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelNameNormalizer @Inject constructor() {

    fun normalize(name: String): String {
        var s = name.lowercase(Locale.getDefault())
        s = s.replace(Regex("\\b(uk|us|ca|au|nz|za|ie|fr|de|es|it|pl)\\b"), " ")
        s = s.replace(Regex("\\[(uk|us|ca|au|nz|za|ie|fr|de|es|it|pl)]"), " ")
        s = s.replace(Regex("\\((uk|us|ca|au|nz|za|ie|fr|de|es|it|pl)\\)"), " ")
        s = s.replace(Regex("\\b(hd|fhd|uhd|4k|sd|lq|hq)\\b"), " ")
        s = s.replace(Regex("\\b(1080p|720p|480p|4320p)\\b"), " ")
        s = s.replace(Regex("\\+[0-9]{1,2}\\b"), " ")
        s = s.replace(Regex("\\b(tv|channel|network|broadcasting)\\b"), " ")
        s = s.replace(Regex("[|\\[\\]()._:\\-+]"), " ")
        s = s.replace(Regex("\\b0+(\\d+)\\b"), "$1")
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    fun compact(name: String): String = normalize(name).replace(" ", "")

    private fun tokens(name: String): List<String> =
        normalize(name).split(" ").filter { it.isNotBlank() }

    fun calculateConfidence(channelName: String, epgName: String): Int {
        val c = normalize(channelName)
        val e = normalize(epgName)
        if (c.isBlank() || e.isBlank()) return 0
        if (c == e) return 100

        if (c.contains(e) || e.contains(c)) {
            val shorter = if (c.length <= e.length) c else e
            val longer = if (c.length <= e.length) e else c
            if (Regex("\\b${Regex.escape(shorter)}\\b").containsMatchIn(longer)) return 90
        }

        val cCompact = compact(channelName)
        val eCompact = compact(epgName)
        if (cCompact.length >= 3 && eCompact.length >= 3) {
            if (cCompact == eCompact) return 100
            if (eCompact.contains(cCompact) || cCompact.contains(eCompact)) return 88
        }

        val cTokens = tokens(channelName)
        val eTokenSet = tokens(epgName).toSet()
        if (cTokens.isNotEmpty() && cTokens.all { token -> eTokenSet.contains(token) || e.contains(token) }) {
            return 88
        }

        val dist = levenshtein(cCompact, eCompact)
        if (dist == 1) return 85
        if (dist == 2) return 75
        val maxLen = maxOf(cCompact.length, eCompact.length)
        if (maxLen >= 4) {
            val similarity = 100 - ((dist * 100) / maxLen)
            if (similarity >= 78) return similarity.coerceIn(70, 84)
        }

        val cWords = c.split(" ").filter { it.isNotBlank() }
        val eWords = e.split(" ").filter { it.isNotBlank() }.toSet()
        val matchCount = cWords.count { eWords.contains(it) }
        if (cWords.isNotEmpty() && matchCount == cWords.size) return 70
        if (cWords.isNotEmpty() && matchCount * 2 > cWords.size) return 55
        return 0
    }

    fun bestFuzzyMatch(
        channelName: String,
        candidates: List<String>,
        minConfidence: Int = 55
    ): Pair<Int, Int>? {
        var bestIndex = -1
        var bestScore = 0
        candidates.forEachIndexed { index, candidate ->
            val score = calculateConfidence(channelName, candidate)
            if (score >= minConfidence && score > bestScore) {
                bestIndex = index
                bestScore = score
            }
        }
        return if (bestIndex >= 0) bestIndex to bestScore else null
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
