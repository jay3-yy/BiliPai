package com.android.purebilibili.core.network.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubMirrorPolicyTest {

    @Test
    fun `default settings are disabled with GHFast`() {
        val defaults = GithubMirrorSettings()
        assertFalse(defaults.enabled)
        assertEquals(GithubMirrorSource.GHFAST, defaults.source)
        assertEquals("", defaults.customPrefix)
    }

    @Test
    fun `recognizes mirrorable github resource URLs`() {
        val allowed = listOf(
            "https://raw.githubusercontent.com/jay3-yy/BiliPai/main/README.md",
            "https://gist.githubusercontent.com/user/123456/raw/plugin.json",
            "https://github.com/jay3-yy/BiliPai/releases/download/v0.9.0/app.apk",
            "https://github.com/jay3-yy/BiliPai/releases/latest/download/app.apk",
            "https://github.com/owner/repo/raw/main/file.json",
            "https://github.com/owner/repo/archive/refs/tags/v1.0.zip",
            "https://RAW.GITHUBUSERCONTENT.COM:443/owner/repo/main/file.png",
        )
        allowed.forEach { url ->
            assertTrue(isMirrorableGithubResourceUrl(url), "Expected mirrorable: $url")
        }
    }

    @Test
    fun `rejects github api avatars html pages non https and lookalike domains`() {
        val rejected = listOf(
            "https://api.github.com/repos/jay3-yy/BiliPai/releases",
            "https://avatars.githubusercontent.com/u/116626041?v=4",
            "https://github.com/jay3-yy/BiliPai",
            "https://github.com/jay3-yy/BiliPai/issues/new",
            "https://github.com/jay3-yy/BiliPai/releases",
            "http://raw.githubusercontent.com/jay3-yy/BiliPai/main/README.md",
            "https://raw.githubusercontent.com.evil.com/jay3-yy/BiliPai/main/README.md",
            "https://user:pass@raw.githubusercontent.com/jay3-yy/BiliPai/main/README.md",
            "https://raw.githubusercontent.com:8443/jay3-yy/BiliPai/main/README.md",
            "https://ghfast.top/https://raw.githubusercontent.com/jay3-yy/BiliPai/main/README.md",
            "https://raw.githubusercontent.com/",
            "https://github.com/owner/repo/releases",
        )
        rejected.forEach { url ->
            assertFalse(isMirrorableGithubResourceUrl(url), "Expected rejected: $url")
        }
    }

    @Test
    fun `resolves mirror URL only when enabled and valid`() {
        val rawUrl = "https://raw.githubusercontent.com/AIboy996/bilibili_poster/main/database.json"
        val disabled = GithubMirrorSettings(enabled = false, source = GithubMirrorSource.GHFAST)
        assertNull(resolveGithubMirrorUrl(disabled, rawUrl))

        val ghFast = GithubMirrorSettings(enabled = true, source = GithubMirrorSource.GHFAST)
        assertEquals("https://ghfast.top/$rawUrl", resolveGithubMirrorUrl(ghFast, " $rawUrl "))

        val customValid = GithubMirrorSettings(
            enabled = true,
            source = GithubMirrorSource.CUSTOM,
            customPrefix = "https://mirror.example/proxy//",
        )
        assertEquals("https://mirror.example/proxy/$rawUrl", resolveGithubMirrorUrl(customValid, rawUrl))

        val customBlank = GithubMirrorSettings(
            enabled = true,
            source = GithubMirrorSource.CUSTOM,
            customPrefix = "",
        )
        assertNull(resolveGithubMirrorUrl(customBlank, rawUrl))

        val customInvalid = GithubMirrorSettings(
            enabled = true,
            source = GithubMirrorSource.CUSTOM,
            customPrefix = "http://mirror.example/",
        )
        assertNull(resolveGithubMirrorUrl(customInvalid, rawUrl))
    }
}
