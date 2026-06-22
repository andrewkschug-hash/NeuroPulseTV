package com.grid.tv.feature.vod

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class VodLanguagePreferenceStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _preferredLanguages = MutableStateFlow(readPreferred())
    val preferredLanguages: Flow<Set<String>> = _preferredLanguages.asStateFlow()

    fun setPreferredLanguages(languages: Set<String>) {
        val normalized = languages.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        prefs.edit()
            .putString(KEY_LANGUAGES, normalized.joinToString(DELIMITER))
            .apply()
        _preferredLanguages.value = normalized
    }

    fun currentPreferredLanguages(): Set<String> = _preferredLanguages.value

    private fun readPreferred(): Set<String> {
        val raw = prefs.getString(KEY_LANGUAGES, null) ?: return emptySet()
        return raw.split(DELIMITER).map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    }

    companion object {
        private const val PREFS_NAME = "vod_language_preferences"
        private const val KEY_LANGUAGES = "preferred_languages"
        private const val DELIMITER = "\u001F"
    }
}
