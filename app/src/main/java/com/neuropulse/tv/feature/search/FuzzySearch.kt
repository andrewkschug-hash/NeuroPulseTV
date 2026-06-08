package com.neuropulse.tv.feature.search

object FuzzySearch {

    enum class MatchType { EXACT, CONTAINS, FUZZY }

    data class ScoredResult<T>(
        val item: T,
        val score: Int,
        val matchType: MatchType
    )

    fun score(query: String, candidate: String): ScoredResult<String>? {
        if (query.isBlank()) return null
        val q = query.trim().lowercase()
        val c = candidate.lowercase()
        return when {
            c == q -> ScoredResult(candidate, 1000, MatchType.EXACT)
            c.startsWith(q) -> ScoredResult(candidate, 800, MatchType.CONTAINS)
            c.contains(q) -> ScoredResult(candidate, 600, MatchType.CONTAINS)
            else -> {
                val dist = levenshtein(q, c)
                val threshold = (q.length / 2).coerceAtLeast(2)
                if (dist <= threshold) {
                    ScoredResult(candidate, 400 - dist * 10, MatchType.FUZZY)
                } else null
            }
        }
    }

    fun <T> rank(
        query: String,
        items: List<T>,
        textSelector: (T) -> String,
        limit: Int = 8
    ): List<ScoredResult<T>> {
        if (query.isBlank()) return emptyList()
        return items.mapNotNull { item ->
            score(query, textSelector(item))?.let { ScoredResult(item, it.score, it.matchType) }
        }
            .sortedWith(
                compareByDescending<ScoredResult<T>> {
                    when (it.matchType) {
                        MatchType.EXACT -> 3
                        MatchType.CONTAINS -> 2
                        MatchType.FUZZY -> 1
                    }
                }.thenByDescending { it.score }
            )
            .take(limit)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var prev = dp[0]
            dp[0] = i + 1
            for (j in b.indices) {
                val temp = dp[j + 1]
                dp[j + 1] = when {
                    a[i] == b[j] -> prev
                    else -> 1 + minOf(prev, dp[j], dp[j + 1])
                }
                prev = temp
            }
        }
        return dp[b.length]
    }
}
