package com.grid.tv.feature.vod

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

@Singleton
class VodLanguagePreferenceStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _preferredLanguages = MutableStateFlow(readPreferred())
    private val _includeUntaggedContent = MutableStateFlow(readIncludeUntagged())

    val preferredLanguages: Flow<Set<String>> = _preferredLanguages.asStateFlow()
    val includeUntaggedContent: Flow<Boolean> = _includeUntaggedContent.asStateFlow()

    val filterOptions: Flow<VodLanguageFilterOptions> = combine(
        preferredLanguages,
        includeUntaggedContent
    ) { languages, includeUntagged ->
        VodLanguageFilterOptions(languages, includeUntagged)
    }

    fun setPreferredLanguages(languages: Set<String>) {
        val normalized = languages.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        prefs.edit()
            .putString(KEY_LANGUAGES, normalized.joinToString(DELIMITER))
            .apply()
        _preferredLanguages.value = normalized
    }

    fun setIncludeUntaggedContent(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_INCLUDE_UNTAGGED, enabled)
            .apply()
        _includeUntaggedContent.value = enabled
    }

    fun currentPreferredLanguages(): Set<String> = _preferredLanguages.value

    fun currentIncludeUntaggedContent(): Boolean = _includeUntaggedContent.value

    fun currentFilterOptions(): VodLanguageFilterOptions =
        VodLanguageFilterOptions(_preferredLanguages.value, _includeUntaggedContent.value)

    private fun readPreferred(): Set<String> {
        val raw = prefs.getString(KEY_LANGUAGES, null) ?: return emptySet()
        return raw.split(DELIMITER).map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    }

    private fun readIncludeUntagged(): Boolean =
        prefs.getBoolean(KEY_INCLUDE_UNTAGGED, DEFAULT_INCLUDE_UNTAGGED)

    companion object {
        private const val PREFS_NAME = "vod_language_preferences"
        private const val KEY_LANGUAGES = "preferred_languages"
        private const val KEY_INCLUDE_UNTAGGED = "include_untagged_content"
        private const val DELIMITER = "\u001F"
        const val DEFAULT_INCLUDE_UNTAGGED = true
    }
}
