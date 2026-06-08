package com.neuropulse.tv.feature.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFilenameUtilTest {

    @Test
    fun sanitize_replacesForbiddenCharacters() {
        val out = RecordingFilenameUtil.sanitize("Sky:Sports/HD?*")
        assertFalse(out.contains(":"))
        assertFalse(out.contains("/"))
        assertFalse(out.contains("?"))
        assertFalse(out.contains("*"))
    }

    @Test
    fun buildFileName_hasMp4Suffix() {
        val file = RecordingFilenameUtil.buildFileName("A", "B", 0L)
        assertTrue(file.endsWith(".mp4"))
    }
}
