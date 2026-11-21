package com.android.purebilibili.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateMirrorPolicyTest {
    private val assetUrl =
        "https://github.com/jay3-yy/BiliPai/releases/download/v0.9.0-beta.1/BiliPai-0.9.0-beta.1.apk"

    @Test
    fun `mirror defaults to GHFast for existing installs`() {
        assertEquals(AppUpdateMirror.GHFAST, AppUpdateMirror.fromValue(null))
        assertEquals(AppUpdateMirror.GHFAST, AppUpdateMirror.fromValue("unknown"))
        assertEquals(AppUpdateMirror.GH_PROXY, AppUpdateMirror.fromValue("GH_PROXY"))
        assertEquals(AppUpdateMirror.HIWIKI, AppUpdateMirror.fromValue("HIWIKI"))
        assertEquals(AppUpdateMirror.CUSTOM, AppUpdateMirror.fromValue("CUSTOM"))
    }

    @Test
    fun `presets forward the exact versioned APK`() {
        assertEquals("https://ghfast.top/$assetUrl", resolveAppUpdateMirrorUrl(AppUpdateMirror.GHFAST, "", assetUrl))
        assertEquals("https://gh-proxy.org/$assetUrl", resolveAppUpdateMirrorUrl(AppUpdateMirror.GH_PROXY, "", assetUrl))
        assertEquals("https://gh.idayer.com/$assetUrl", resolveAppUpdateMirrorUrl(AppUpdateMirror.HIWIKI, "", assetUrl))
        assertEquals(
            "https://mirror.example/proxy/$assetUrl",
            resolveAppUpdateMirrorUrl(AppUpdateMirror.CUSTOM, " https://mirror.example/proxy// ", assetUrl),
        )
    }

    @Test
    fun `custom prefixes require HTTPS and an unambiguous base URL`() {
        listOf(
            "",
            "http://mirror.example",
            "https://",
            "https://user:pass@mirror.example/",
            "https://mirror.example/?url=",
            "https://mirror.example/#fragment",
            "https://mirror.example:65536/",
        ).forEach { prefix ->
            assertNull(prefix, normalizeAppUpdateMirrorPrefix(prefix))
        }
        assertEquals("https://mirror.example/%E9%95%9C%E5%83%8F/", normalizeAppUpdateMirrorPrefix("https://mirror.example/镜像"))
        assertNull(resolveAppUpdateMirrorUrl(AppUpdateMirror.GHFAST, "", "https://example.org/example.apk"))
    }

    @Test
    fun `mirror preference defaults to disabled with GHFast`() {
        val preference = AppUpdateMirrorPreference()
        assertFalse(preference.enabled)
        assertEquals(AppUpdateMirror.GHFAST, preference.source)
        assertEquals("", preference.customPrefix)
    }

    @Test
    fun `primary update download uses mirror only when enabled with valid sha256 and prefix`() {
        val sha256 = "a1b2".repeat(16)
        assertNull(
            resolvePrimaryAppUpdateMirrorUrl(
                mirrorEnabled = false,
                source = AppUpdateMirror.GHFAST,
                customPrefix = "",
                assetUrl = assetUrl,
                expectedSha256 = sha256,
            ),
        )
        assertNull(
            resolvePrimaryAppUpdateMirrorUrl(
                mirrorEnabled = true,
                source = AppUpdateMirror.GHFAST,
                customPrefix = "",
                assetUrl = assetUrl,
                expectedSha256 = null,
            ),
        )
        assertNull(
            resolvePrimaryAppUpdateMirrorUrl(
                mirrorEnabled = true,
                source = AppUpdateMirror.GHFAST,
                customPrefix = "",
                assetUrl = assetUrl,
                expectedSha256 = "invalid",
            ),
        )
        assertNull(
            resolvePrimaryAppUpdateMirrorUrl(
                mirrorEnabled = true,
                source = AppUpdateMirror.CUSTOM,
                customPrefix = "",
                assetUrl = assetUrl,
                expectedSha256 = sha256,
            ),
        )
        assertEquals(
            "https://ghfast.top/$assetUrl",
            resolvePrimaryAppUpdateMirrorUrl(
                mirrorEnabled = true,
                source = AppUpdateMirror.GHFAST,
                customPrefix = "",
                assetUrl = assetUrl,
                expectedSha256 = sha256,
            ),
        )
    }

    @Test
    fun `mirror toggle subtitles stay non blank and distinct`() {
        val enabledSubtitle = resolveGithubMirrorEnabledSubtitle(true)
        val disabledSubtitle = resolveGithubMirrorEnabledSubtitle(false)
        assertTrue(enabledSubtitle.isNotBlank())
        assertTrue(disabledSubtitle.isNotBlank())
        assertTrue(enabledSubtitle != disabledSubtitle)
    }

    @Test
    fun `mirror downloads require a full SHA-256 digest`() {
        assertTrue(isAppUpdateSha256("a1B2".repeat(16)))
        assertFalse(isAppUpdateSha256(null))
        assertFalse(isAppUpdateSha256("a".repeat(63)))
        assertFalse(isAppUpdateSha256("g".repeat(64)))
    }
}
