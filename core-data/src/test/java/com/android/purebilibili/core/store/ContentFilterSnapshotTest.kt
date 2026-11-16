package com.android.purebilibili.core.store

import org.junit.Assert.*
import org.junit.Test

class ContentFilterSnapshotTest {
    @Test
    fun migratedVideoRegexKeepsFeedSwitchesFollowExemptionAndWhitelist() {
        val rules = ContentFilterSnapshot(
            videoTitlePattern = "剧透", keywordFilteringEnabled = true,
            videoPolicy = ContentVideoKeywordPolicy(whitelist = setOf(42), exemptFollowed = true),
        )
        assertTrue(rules.blocksVideo("剧透", 1, surface = ContentVideoSurface.RELATED))
        assertFalse(rules.blocksVideo("剧透", 1, surface = ContentVideoSurface.SEARCH))
        assertFalse(rules.blocksVideo("剧透", 1, surface = ContentVideoSurface.POPULAR))
        assertFalse(rules.blocksVideo("剧透", 1, followed = true, surface = ContentVideoSurface.RECOMMEND))
        assertFalse(rules.blocksVideo("剧透", 42, surface = ContentVideoSurface.RELATED))
        assertTrue(rules.copy(blockedMids = setOf(42)).blocksVideo("普通内容", 42))
        assertTrue(rules.copy(videoPolicy = rules.videoPolicy.copy(applyToSearch = true))
            .blocksVideo("剧透", 1, surface = ContentVideoSurface.SEARCH))
    }
    @Test
    fun migratedRulesKeepTheirOriginalMatcherScopeAndPluginSwitch() {
        val rules = ContentFilterSnapshot(
            videoTitlePattern = "^剧情|预告$\nspoiler",
            adVideoKeywords = listOf("[推广]"), legacyDanmakuKeywords = listOf("剧透"),
            keywordFilteringEnabled = true, adFilteringEnabled = true, danmakuFilteringEnabled = true,
        )
        assertTrue(rules.blocksText("剧情介绍", ContentFilterScope.VIDEO))
        assertTrue(rules.blocksText("新片预告", ContentFilterScope.VIDEO))
        assertTrue(rules.blocksText("SPOILER", ContentFilterScope.VIDEO))
        assertTrue(rules.blocksText("包含[推广]", ContentFilterScope.VIDEO))
        assertFalse(rules.blocksText("推广", ContentFilterScope.VIDEO))
        assertTrue(rules.blocksText("剧透内容", ContentFilterScope.DANMAKU))
        assertFalse(rules.blocksText("剧透内容", ContentFilterScope.COMMENT))
        assertFalse(rules.copy(danmakuFilteringEnabled = false).blocksText("剧透", ContentFilterScope.DANMAKU))
        assertFalse(rules.copy(adFilteringEnabled = false).blocksText("[推广]", ContentFilterScope.VIDEO))
        assertEquals(listOf("剧透"), rules.effectiveDanmakuKeywords)
    }
    @Test
    fun sharedKeywordsOnlyApplyToSelectedScopesAndUseLiteralMatching() {
        val rules = ContentFilterSnapshot(
            keywords = listOf("Spoiler", "[剧透]"),
            scopes = setOf(ContentFilterScope.VIDEO, ContentFilterScope.COMMENT),
            keywordFilteringEnabled = true,
        )
        assertTrue(rules.blocksText("SPOILER alert", ContentFilterScope.VIDEO))
        assertTrue(rules.blocksText("包含[剧透]内容", ContentFilterScope.COMMENT))
        assertFalse(rules.blocksText("剧透", ContentFilterScope.COMMENT))
        assertFalse(rules.blocksText("spoiler", ContentFilterScope.DANMAKU))
        assertFalse(rules.copy(keywordFilteringEnabled = false).blocksText("spoiler", ContentFilterScope.VIDEO))
    }

    @Test
    fun manualUserBlocksRemainActiveWhenKeywordPluginIsDisabled() {
        val rules = ContentFilterSnapshot(blockedMids = setOf(42), keywordFilteringEnabled = false)
        assertTrue(rules.blocksUser(42))
        assertFalse(rules.blocksUser(43))
    }
}
