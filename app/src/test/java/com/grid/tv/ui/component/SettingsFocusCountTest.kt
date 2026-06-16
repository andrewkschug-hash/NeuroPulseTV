package com.grid.tv.ui.component

import com.grid.tv.domain.model.PlaylistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusCountTest {

    @Test
    fun playbackSectionCardsCoverFullChain() {
        val cards = buildSettingsSectionCards(
            kind = SettingsSectionKind.Playback,
            contentFocusCount = PLAYBACK_FOCUS_COUNT
        )
        assertEquals(4, cards.size)
        assertEquals(0, cards.first().firstFocusIndex)
        assertEquals(PLAYBACK_FOCUS_COUNT, cards.last().lastFocusIndex + 1)
    }

    @Test
    fun guideSectionCardsCoverFullChain() {
        val cards = buildSettingsSectionCards(
            kind = SettingsSectionKind.Guide,
            contentFocusCount = GUIDE_FOCUS_COUNT
        )
        assertEquals(GUIDE_FOCUS_COUNT, cards.last().lastFocusIndex + 1)
    }

    @Test
    fun interfaceSectionCardsCoverFullChain() {
        val cards = buildSettingsSectionCards(
            kind = SettingsSectionKind.Interface,
            contentFocusCount = INTERFACE_FOCUS_COUNT
        )
        assertEquals(INTERFACE_FOCUS_COUNT, cards.last().lastFocusIndex + 1)
    }

    @Test
    fun aboutSectionCardsCoverFullChain() {
        val cards = buildSettingsSectionCards(
            kind = SettingsSectionKind.About,
            contentFocusCount = ABOUT_FOCUS_COUNT
        )
        assertEquals(ABOUT_FOCUS_COUNT, cards.last().lastFocusIndex + 1)
    }

    @Test
    fun connectionsFormCountsMatchXtreamFields() {
        assertTrue(connectionsAddFocusCount(PlaylistType.XTREAM) >= 17)
    }
}
