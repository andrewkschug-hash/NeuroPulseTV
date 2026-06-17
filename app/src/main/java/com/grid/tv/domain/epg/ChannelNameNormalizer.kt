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
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    fun calculateConfidence(channelName: String, epgName: String): Int {
        val c = normalize(channelName)
        val e = normalize(epgName)
        if (c.isBlank() || e.isBlank()) return 0
        if (c == e) return 100
        if (c.contains(e) || e.contains(c)) return 90
        val dist = levenshtein(c, e)
        if (dist == 1) return 85
        if (dist == 2) return 75

        val cWords = c.split(" ").filter { it.isNotBlank() }
        val eWords = e.split(" ").filter { it.isNotBlank() }.toSet()
        val matchCount = cWords.count { eWords.contains(it) }
        if (cWords.isNotEmpty() && matchCount == cWords.size) return 70
        if (cWords.isNotEmpty() && matchCount * 2 > cWords.size) return 55
        return 0
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
