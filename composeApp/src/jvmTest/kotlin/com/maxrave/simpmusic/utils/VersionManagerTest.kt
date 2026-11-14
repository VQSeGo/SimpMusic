package com.maxrave.simpmusic.utils

import com.maxrave.domain.data.model.update.UpdateAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionManagerTest {
    @Test
    fun `tag with v prefix equals the bare version`() {
        assertFalse(isNewerRelease(current = "2.5.0", tag = "v2.5.0"))
        assertFalse(isNewerRelease(current = "2.5.0", tag = "2.5.0"))
    }

    @Test
    fun `higher numbers are newer`() {
        assertTrue(isNewerRelease(current = "2.5.0", tag = "v2.5.1"))
        assertTrue(isNewerRelease(current = "2.9.0", tag = "v2.10.0"))
        assertTrue(isNewerRelease(current = "2.5", tag = "v2.5.0.1"))
    }

    @Test
    fun `older numbers are not newer`() {
        assertFalse(isNewerRelease(current = "2.5.1", tag = "v2.5.0"))
        assertFalse(isNewerRelease(current = "2.10.0", tag = "v2.9.0"))
    }

    @Test
    fun `a suffix alone does not make a release newer`() {
        assertFalse(isNewerRelease(current = "2.5.0", tag = "v2.5.0-1"))
        assertFalse(isNewerRelease(current = "2.5.0", tag = "v2.5.0-hf"))
        assertTrue(isNewerRelease(current = "2.5.0", tag = "v2.5.1-1"))
    }

    @Test
    fun `final release is newer than its pre-release`() {
        assertTrue(isNewerRelease(current = "2.5.0-beta", tag = "v2.5.0"))
        assertFalse(isNewerRelease(current = "2.5.0", tag = "v2.5.0-beta"))
    }

    @Test
    fun `garbage or empty is never newer`() {
        assertFalse(isNewerRelease(current = "2.5.0", tag = ""))
        assertFalse(isNewerRelease(current = "2.5.0", tag = "v"))
        assertFalse(isNewerRelease(current = "2.5.0", tag = "nightly"))
        assertFalse(isNewerRelease(current = "2.5.0", tag = "v2.x.0"))
        assertFalse(isNewerRelease(current = "", tag = "v9.9.9"))
        assertFalse(isNewerRelease(current = "dev", tag = "v9.9.9"))
    }

    private val assets =
        listOf(
            UpdateAsset("SimpMusic-universal.apk", "https://x/universal.apk"),
            UpdateAsset("SimpMusic-arm64.apk", "https://x/arm64.apk"),
            UpdateAsset("SimpMusic-Windows-installer.zip", "https://x/win.zip"),
            UpdateAsset("SimpMusic-x86_64.AppImage", "https://x/linux.AppImage"),
        )

    @Test
    fun `each platform gets its own file`() {
        assertEquals("https://x/arm64.apk", pickUpdateDownloadUrl(assets, "https://x/page", "android"))
        assertEquals("https://x/linux.AppImage", pickUpdateDownloadUrl(assets, "https://x/page", "Linux"))
        assertEquals("https://x/win.zip", pickUpdateDownloadUrl(assets, "https://x/page", "Windows 11"))
    }

    @Test
    fun `falls back to the universal apk, then the page, then the releases list`() {
        assertEquals(
            "https://x/universal.apk",
            pickUpdateDownloadUrl(assets.take(1), "https://x/page", "android"),
        )
        assertEquals("https://x/page", pickUpdateDownloadUrl(emptyList(), "https://x/page", "Linux"))
        assertEquals("https://x/page", pickUpdateDownloadUrl(assets, "https://x/page", "Mac OS X"))
        assertEquals(FORK_RELEASES_URL, pickUpdateDownloadUrl(emptyList(), null, "Linux"))
    }
}
