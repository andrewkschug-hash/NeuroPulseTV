package com.grid.tv.domain.model

/** How live channel groups are organized in the guide sidebar. */
enum class ChannelGroupNavigationMode {
    /** Country → category smart hierarchy (default). */
    SMART,
    /** Raw provider group names from M3U/Xtream. */
    PROVIDER;

    companion object {
        fun fromStored(raw: String?): ChannelGroupNavigationMode =
            entries.firstOrNull { it.name == raw?.trim() } ?: SMART
    }
}
