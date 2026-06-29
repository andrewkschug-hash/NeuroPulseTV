package com.grid.tv.feature.startup

import android.content.Context
import com.grid.tv.feature.guide.GuideGroupMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists last-known channel group metadata for instant drawer hydration on cold start.
 *
 * Read synchronously during guide bootstrap (before the Room GROUP BY subscription) so the
 * channel-groups drawer can render immediately while live metadata refreshes in the background.
 */
@Singleton
class StartupGroupMetadataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): GuideGroupMetadata {
        val groupsRaw = prefs.getString(KEY_GROUPS, null) ?: return GuideGroupMetadata.EMPTY
        if (groupsRaw.isEmpty()) return GuideGroupMetadata.EMPTY
        val groups = groupsRaw.split(GROUP_SEP).filter { it.isNotBlank() }
        if (groups.isEmpty()) return GuideGroupMetadata.EMPTY
        val counts = LinkedHashMap<String, Int>(groups.size)
        prefs.getString(KEY_COUNTS, null)
            ?.split(COUNT_ENTRY_SEP)
            ?.filter { it.isNotBlank() }
            ?.forEach { entry ->
                val sep = entry.indexOf(COUNT_VALUE_SEP)
                if (sep > 0) {
                    val key = entry.substring(0, sep)
                    val count = entry.substring(sep + 1).toIntOrNull() ?: 0
                    if (key.isNotBlank()) counts[key] = count
                }
            }
        return GuideGroupMetadata(groups = groups, counts = counts)
    }

    fun write(metadata: GuideGroupMetadata) {
        if (metadata.groups.isEmpty()) return
        val groupsRaw = metadata.groups.joinToString(GROUP_SEP)
        val countsRaw = metadata.groups.joinToString(COUNT_ENTRY_SEP) { key ->
            "$key$COUNT_VALUE_SEP${metadata.counts[key] ?: 0}"
        }
        prefs.edit()
            .putString(KEY_GROUPS, groupsRaw)
            .putString(KEY_COUNTS, countsRaw)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun lastUpdatedAtMs(): Long = prefs.getLong(KEY_UPDATED_AT, 0L)

    companion object {
        private const val PREFS_NAME = "startup_group_metadata"
        private const val KEY_GROUPS = "groups"
        private const val KEY_COUNTS = "counts"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val GROUP_SEP = "\u001E"
        private const val COUNT_ENTRY_SEP = "\u001D"
        private const val COUNT_VALUE_SEP = "\u001F"
    }
}
