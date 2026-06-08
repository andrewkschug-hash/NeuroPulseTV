package com.neuropulse.tv.feature.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingConflictResolverTest {

    @Test
    fun resolve_allowsWhenLessThanLimit() {
        assertTrue(RecordingConflictResolver.resolve(listOf(1L)).allowed)
    }

    @Test
    fun resolve_blocksWhenAtLimit() {
        assertFalse(RecordingConflictResolver.resolve(listOf(1L, 2L)).allowed)
    }
}
