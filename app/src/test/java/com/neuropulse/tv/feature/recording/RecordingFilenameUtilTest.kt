package com.neuropulse.tv.feature.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun buildFileName_hasTsSuffixAndFormat() {
        val file = RecordingFilenameUtil.buildFileName("CBC", "The National", 1_704_000_000_000L)
        assertTrue(file.endsWith(".ts"))
        assertTrue(file.startsWith("CBC - The National -"))
    }

    @Test
    fun resolveUniqueFile_appendsCounterForDuplicates() {
        val dir = File(System.getProperty("java.io.tmpdir"), "grid_rec_test_${System.nanoTime()}")
        dir.mkdirs()
        val epoch = 1_704_000_000_000L
        File(dir, RecordingFilenameUtil.buildFileName("A", "B", epoch)).writeText("test")

        val resolved = RecordingFilenameUtil.resolveUniqueFile(dir, "A", "B", epoch)
        resolved.parentFile?.listFiles()?.forEach { it.delete() }
        dir.delete()
        assertTrue(resolved.name.contains("(2)"))
    }
}
