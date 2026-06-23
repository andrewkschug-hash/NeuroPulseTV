package com.grid.tv.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDownloadAssetsTest {

    @Test
    fun isInstallableAsset_acceptsApkAndApkZip() {
        assertTrue(ReleaseDownloadAssets.isInstallableAsset("app-google-release.apk"))
        assertTrue(ReleaseDownloadAssets.isInstallableAsset("app-google-release.V1.03.apk.zip"))
        assertFalse(ReleaseDownloadAssets.isInstallableAsset("release-notes.md"))
        assertFalse(ReleaseDownloadAssets.isInstallableAsset("bundle.zip"))
    }
}
