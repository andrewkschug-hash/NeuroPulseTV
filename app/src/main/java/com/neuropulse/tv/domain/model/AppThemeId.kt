package com.neuropulse.tv.domain.model

enum class AppThemeId(val displayName: String) {
    NEURO_BLUE("Neuro Blue"),
    OLED_BLACK("OLED Black"),
    NETFLIX_RED("Netflix Red"),
    SPORTS_GREEN("Sports Green"),
    PURPLE_NEON("Purple Neon");

    companion object {
        fun fromStored(raw: String?): AppThemeId =
            entries.firstOrNull { it.name == raw } ?: NEURO_BLUE
    }
}
