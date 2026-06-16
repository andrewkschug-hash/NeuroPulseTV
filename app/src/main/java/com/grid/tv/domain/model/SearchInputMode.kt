package com.grid.tv.domain.model

enum class SearchInputMode {
    VOICE,
    KEYBOARD;

    companion object {
        fun fromStored(value: String?): SearchInputMode =
            entries.firstOrNull { it.name == value } ?: KEYBOARD
    }
}

enum class SearchBarState {
    DEFAULT,
    LISTENING,
    CONFIRMED
}
