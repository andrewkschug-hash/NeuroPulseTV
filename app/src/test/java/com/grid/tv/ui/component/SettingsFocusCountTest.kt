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

    @Test
    fun profileInitialFocusIsActiveProfileCard() {
        val focusCount = profileContentFocusCount(
            profileCount = 2,
            activeProfileId = 1L,
            hasActiveProfile = true
        )
        val cards = buildSettingsSectionCards(
            kind = SettingsSectionKind.Profile,
            contentFocusCount = focusCount,
            profileCount = 2,
            activeProfileId = 1L,
            hasActiveProfile = true
        )
        val index = initialSettingsContentFocusIndex(
            kind = SettingsSectionKind.Profile,
            sectionCards = cards,
            parentalStart = focusCount - 7
        )
        assertEquals(0, index)
    }
}
