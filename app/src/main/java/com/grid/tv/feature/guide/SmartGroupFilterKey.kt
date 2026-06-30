package com.grid.tv.feature.guide

import com.grid.tv.domain.model.ChannelGroupIdentity

/** Encoded guide-filter keys for smart country → category buckets. */
object SmartGroupFilterKey {
    private const val PREFIX = "smart"
    private const val FIELD_SEP = '\u001E'

    fun encode(country: String, category: String): String =
        "$PREFIX${ChannelGroupIdentity.SEPARATOR}$country$FIELD_SEP$category"

    fun decode(key: String): Pair<String, String>? {
        if (!isSmartKey(key)) return null
        val payload = key.substringAfter(ChannelGroupIdentity.SEPARATOR)
        val sep = payload.indexOf(FIELD_SEP)
        if (sep <= 0) return null
        val country = payload.substring(0, sep)
        val category = payload.substring(sep + 1)
        if (country.isBlank() || category.isBlank()) return null
        return country to category
    }

    fun isSmartKey(key: String): Boolean =
        key.startsWith("$PREFIX${ChannelGroupIdentity.SEPARATOR}")
}
