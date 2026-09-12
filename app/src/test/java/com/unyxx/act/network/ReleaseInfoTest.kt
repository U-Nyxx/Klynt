package com.unyxx.act.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Update compare must never offer a downgrade or skip a newer tag. */
class ReleaseInfoTest {

    @Test
    fun `newer patch and minor win, equal and older lose`() {
        assertTrue(ReleaseInfo("1.0.4", "", "", 1).isNewerThan("1.0.3"))
        assertTrue(ReleaseInfo("1.1.0", "", "", 1).isNewerThan("1.0.9"))
        assertFalse(ReleaseInfo("1.0.3", "", "", 1).isNewerThan("1.0.3"))
        assertFalse(ReleaseInfo("1.0.2", "", "", 1).isNewerThan("1.0.3"))
    }

    @Test
    fun `compare tolerates v prefix and missing parts`() {
        assertEquals(0, ReleaseInfo.compareVersions("v1.0", "1.0.0"))
        assertTrue(ReleaseInfo.compareVersions("v1.0.4", "1.0.3") > 0)
    }

    @Test
    fun `from picks the single signed asset only`() {
        val release = GitHubRelease(
            tagName = "v1.0.4",
            body = "notes",
            assets = listOf(
                GitHubAsset("README.md", "https://x/r", 10),
                GitHubAsset("klynt-1.0.4-ArJk.apk", "https://x/a.apk", 20)
            )
        )
        val info = ReleaseInfo.from(release)!!
        assertEquals("1.0.4", info.version)
        assertEquals("https://x/a.apk", info.apkUrl)
    }
}
