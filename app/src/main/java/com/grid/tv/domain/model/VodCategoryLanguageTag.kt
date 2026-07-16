package com.grid.tv.domain.model

/**
 * Lightweight script detection for provider category labels.
 * Used only for UI tagging/grouping — never renames or drops categories.
 */
object VodCategoryLanguageTag {
    private val ARABIC = Regex("[\\u0600-\\u06FF]")
    private val CYRILLIC = Regex("[\\u0400-\\u04FF]")
    private val CJK = Regex("[\\u3040-\\u30FF\\u3400-\\u9FFF\\uAC00-\\uD7AF]")

    fun containsNonLatinScript(name: String): Boolean {
        val sample = name.trim()
        if (sample.isEmpty()) return false
        return ARABIC.containsMatchIn(sample) ||
            CYRILLIC.containsMatchIn(sample) ||
            CJK.containsMatchIn(sample)
    }

    fun isArabicScript(name: String): Boolean = ARABIC.containsMatchIn(name)
}
