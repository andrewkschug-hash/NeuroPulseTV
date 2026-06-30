package com.grid.tv.ui.screen.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.focus.TvFocusController
import com.grid.tv.util.TvTextInputSession

internal data class SettingsFocusDeps(
    val categoryCount: () -> Int,
    val optionRows: () -> List<SettingsRowModel>,
    val modalBlockingFocus: () -> Boolean,
    val profileMenuOpen: () -> Boolean,
    val onDismissProfileMenu: () -> Unit,
    val onBack: () -> Unit,
    val onNavigateTab: (EpgNavTab) -> Unit,
    val onOpenProfileMenu: () -> Unit,
    val handleScreenBack: () -> Boolean,
)

internal class SettingsFocusController(
    private val ui: SettingsFocusUiState,
) : TvFocusController<SettingsFocusZone> {
    private var deps: SettingsFocusDeps? = null

    fun bind(deps: SettingsFocusDeps) {
        this.deps = deps
    }

    private val d: SettingsFocusDeps
        get() = deps ?: error("SettingsFocusController.bind() must run before interaction")

    override val focusZone: SettingsFocusZone
        get() = ui.focusZone

    override fun transitionToZone(zone: SettingsFocusZone, detail: String) {
        ui.focusZone = zone
        when (zone) {
            SettingsFocusZone.OPTIONS -> ui.clampOptionIndex(d.optionRows())
            SettingsFocusZone.CATEGORIES -> Unit
            SettingsFocusZone.TOP_BAR -> Unit
        }
    }

    fun selectCategory(index: Int) {
        val clamped = index.coerceIn(0, (d.categoryCount() - 1).coerceAtLeast(0))
        ui.categoryIndex = clamped
        ui.optionIndex = 0
        ui.clampOptionIndex(d.optionRows())
    }

    fun activateFocusedOption() {
        val rows = d.optionRows()
        val row = rows.getOrNull(ui.optionIndex) ?: return
        when (row) {
            is SettingsRowModel.Toggle -> row.onToggle()
            is SettingsRowModel.Action -> if (row.enabled) row.onClick()
            is SettingsRowModel.Selection -> {
                val next = (row.selectedIndex + 1) % row.options.size.coerceAtLeast(1)
                row.onSelect(next)
            }
            is SettingsRowModel.TextInput -> Unit
            is SettingsRowModel.Info -> Unit
        }
    }

    private fun stepCategory(delta: Int) {
        val count = d.categoryCount()
        if (count <= 0) return
        selectCategory((ui.categoryIndex + delta).coerceIn(0, count - 1))
    }

    private fun stepOption(delta: Int) {
        val focusable = focusableOptionIndices(d.optionRows())
        if (focusable.isEmpty()) return
        val currentPos = focusable.indexOf(ui.optionIndex).coerceAtLeast(0)
        val nextPos = (currentPos + delta).coerceIn(0, focusable.lastIndex)
        ui.optionIndex = focusable[nextPos]
    }

    private fun cycleSelection(delta: Int): Boolean {
        val rows = d.optionRows()
        val row = rows.getOrNull(ui.optionIndex) as? SettingsRowModel.Selection ?: return false
        if (row.options.isEmpty()) return false
        val next = (row.selectedIndex + delta).coerceIn(0, row.options.lastIndex)
        if (next != row.selectedIndex) {
            row.onSelect(next)
        }
        return true
    }

    private fun handleTopBarKey(event: KeyEvent): Boolean {
        if (d.profileMenuOpen()) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    d.onDismissProfileMenu()
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    d.onDismissProfileMenu()
                    false
                }
                else -> false
            }
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                ui.topBarFocusIndex = (ui.topBarFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                ui.topBarFocusIndex = (ui.topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                true
            }
            Key.DirectionDown -> {
                transitionToZone(SettingsFocusZone.CATEGORIES, "topBarDown")
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (ui.topBarFocusIndex) {
                    in GridNavTabs.indices -> d.onNavigateTab(GridNavTabs[ui.topBarFocusIndex])
                    TopBarProfileIndex -> d.onOpenProfileMenu()
                }
                true
            }
            else -> false
        }
    }

    private fun handleCategoriesKey(event: KeyEvent): Boolean {
        return when (event.key) {
            Key.DirectionUp -> {
                if (ui.categoryIndex > 0) {
                    stepCategory(-1)
                } else {
                    transitionToZone(SettingsFocusZone.TOP_BAR, "categoriesUp")
                }
                true
            }
            Key.DirectionDown -> {
                if (ui.categoryIndex < d.categoryCount() - 1) {
                    stepCategory(1)
                }
                true
            }
            Key.DirectionLeft -> {
                d.onBack()
                true
            }
            Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                transitionToZone(SettingsFocusZone.OPTIONS, "categoriesRight")
                true
            }
            else -> false
        }
    }

    private fun handleOptionsKey(event: KeyEvent): Boolean {
        val rows = d.optionRows()
        val currentRow = rows.getOrNull(ui.optionIndex)
        return when (event.key) {
            Key.DirectionUp -> {
                stepOption(-1)
                true
            }
            Key.DirectionDown -> {
                stepOption(1)
                true
            }
            Key.DirectionLeft -> {
                if (currentRow is SettingsRowModel.Selection && cycleSelection(-1)) {
                    true
                } else {
                    transitionToZone(SettingsFocusZone.CATEGORIES, "optionsLeft")
                    true
                }
            }
            Key.DirectionRight -> {
                if (currentRow is SettingsRowModel.Selection) {
                    cycleSelection(1)
                } else {
                    false
                }
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                activateFocusedOption()
                true
            }
            else -> false
        }
    }

    override fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (d.modalBlockingFocus()) return false
        return when (ui.focusZone) {
            SettingsFocusZone.TOP_BAR -> handleTopBarKey(event)
            SettingsFocusZone.CATEGORIES -> handleCategoriesKey(event)
            SettingsFocusZone.OPTIONS -> handleOptionsKey(event)
        }
    }

    fun handleBack(): Boolean = d.handleScreenBack()
}
