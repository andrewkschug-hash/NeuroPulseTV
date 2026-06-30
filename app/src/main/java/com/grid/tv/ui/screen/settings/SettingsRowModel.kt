package com.grid.tv.ui.screen.settings

sealed class SettingsRowModel {
    abstract val id: String
    open val focusable: Boolean = true

    data class Toggle(
        override val id: String,
        val label: String,
        val subtitle: String? = null,
        val checked: Boolean,
        val onToggle: () -> Unit,
    ) : SettingsRowModel()

    data class Selection(
        override val id: String,
        val label: String,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
    ) : SettingsRowModel()

    data class Action(
        override val id: String,
        val label: String,
        val subtitle: String? = null,
        val value: String? = null,
        val enabled: Boolean = true,
        val destructive: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingsRowModel()

    data class Info(
        override val id: String,
        val label: String,
        val value: String,
    ) : SettingsRowModel() {
        override val focusable: Boolean = false
    }

    data class TextInput(
        override val id: String,
        val label: String,
        val value: String,
        val onValueChange: (String) -> Unit,
        val placeholder: String = "",
        val isPassword: Boolean = false,
    ) : SettingsRowModel()
}

fun focusableOptionIndices(rows: List<SettingsRowModel>): List<Int> =
    rows.mapIndexedNotNull { index, row -> index.takeIf { row.focusable } }

fun SettingsFocusUiState.clampOptionIndex(rows: List<SettingsRowModel>) {
    val focusable = focusableOptionIndices(rows)
    if (focusable.isEmpty()) {
        optionIndex = 0
        return
    }
    if (optionIndex !in focusable) {
        optionIndex = focusable.first()
    }
}
