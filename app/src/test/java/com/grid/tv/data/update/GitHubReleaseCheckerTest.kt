package com.grid.tv.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {

    @Test
    fun isNewerVersion_detectsPatchBump() {
        assertTrue(GitHubReleaseChecker.isNewerVersion("2.2.0", "2.1.0"))
        assertTrue(GitHubReleaseChecker.isNewerVersion("v2.2.0", "2.1.0"))
    }

    @Test
    fun isNewerVersion_rejectsSameOrOlder() {
        assertFalse(GitHubReleaseChecker.isNewerVersion("2.1.0", "2.1.0"))
        assertFalse(GitHubReleaseChecker.isNewerVersion("2.0.0", "2.1.0"))
        assertFalse(GitHubReleaseChecker.isNewerVersion("v2.1.0", "2.1.0"))
    }
}
