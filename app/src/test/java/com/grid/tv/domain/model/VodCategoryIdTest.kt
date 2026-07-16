package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCategoryIdTest {
    @Test
    fun canonicalizeUnifiesNumberAndStringForms() {
        assertEquals("45", VodCategoryId.canonicalize("45"))
        assertEquals("45", VodCategoryId.canonicalizeNumber(45))
        assertEquals("45", VodCategoryId.canonicalize("45.0"))
    }

    @Test
    fun canonicalizeTreatsZeroAndBlankAsMissing() {
        assertNull(VodCategoryId.canonicalize("0"))
        assertNull(VodCategoryId.canonicalizeNumber(0))
        assertNull(VodCategoryId.canonicalize(" "))
        assertNull(VodCategoryId.canonicalize(null))
    }

    @Test
    fun csvRoundTripPreservesMembershipOrder() {
        val csv = VodCategoryId.toCsv(listOf("12", "45", "12", "67"))
        assertEquals("12,45,67", csv)
        assertEquals(listOf("12", "45", "67"), VodCategoryId.fromCsv(csv))
    }

    @Test
    fun languageTagDetectsArabicWithoutRenaming() {
        assertTrue(VodCategoryLanguageTag.isArabicScript("أفلام"))
        assertTrue(VodCategoryLanguageTag.containsNonLatinScript("أفلام عربية"))
        assertFalse(VodCategoryLanguageTag.containsNonLatinScript("Action"))
    }
}
