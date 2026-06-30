package com.grid.tv.ui.component

import com.grid.tv.ui.screen.settings.SettingsCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFocusCountTest {

    @Test
    fun settingsCategoryCount_isStable() {
        assertEquals(6, SettingsCategory.entries.size)
    }
}
