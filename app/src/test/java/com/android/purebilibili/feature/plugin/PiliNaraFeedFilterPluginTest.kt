package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.plugin.FeedKind
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 推荐流过滤规则测试(纯函数, 对齐 PiliNara RecommendFilter)。
 */
class PiliNaraFeedFilterPluginTest {

    private fun video(
        title: String = "普通视频",
        mid: Long = 1001,
        duration: Int = 300,
        view: Int = 100_000,
        like: Int = 5_000,
        tname: String = "",
        isFollowed: Boolean = false
    ) = VideoItem(
        bvid = "BV1",
        title = title,
        owner = Owner(mid = mid, name = "UP$mid"),
        stat = Stat(view = view, like = like),
        duration = duration,
        tname = tname,
        isFollowed = isFollowed
    )

    private fun rules(
        minDuration: Long = 0,
        minPlay: Long = 0,
        minLikeRatio: Int = 0,
        banWords: String = "",
        zoneWords: String = "",
        blockedMids: Map<Long, String> = emptyMap(),
        whitelistMids: Map<Long, String> = emptyMap(),
        exemptFollowed: Boolean = true,
        applyHot: Boolean = false,
        applyRank: Boolean = false,
        applySearch: Boolean = false
    ): PiliNaraFeedFilterConfig = PiliNaraFeedFilterConfig(
        minDurationForRcmd = minDuration,
        minPlayForRcmd = minPlay,
        minLikeRatioForRecommend = minLikeRatio,
        banWordForRecommend = banWords,
        banWordForZone = zoneWords,
        recommendBlockedMids = blockedMids,
        whitelistMids = whitelistMids,
        exemptFilterForFollowed = exemptFollowed,
        applyToHotVideos = applyHot,
        applyToRankVideos = applyRank,
        applyToSearch = applySearch
    )

    private fun show(
        cfg: PiliNaraFeedFilterConfig,
        item: VideoItem,
        kind: FeedKind = FeedKind.HOME_RECOMMEND
    ): Boolean = shouldShowFeedItem(
        config = cfg,
        item = item,
        feedKind = kind,
        rcmdRegExp = PiliNaraFeedFilterPlugin.parseBanWordToRegex(cfg.banWordForRecommend)
            ?.let { Regex(it, RegexOption.IGNORE_CASE) },
        zoneRegExp = PiliNaraFeedFilterPlugin.parseBanWordToRegex(cfg.banWordForZone)
            ?.let { Regex(it, RegexOption.IGNORE_CASE) }
    )

    // ==================== 基础规则 ====================

    @Test
    fun `default config shows everything`() {
        assertTrue(show(rules(), video()))
    }

    @Test
    fun `hides short videos below min duration`() {
        val cfg = rules(minDuration = 60)
        assertFalse(show(cfg, video(duration = 30)))
        assertTrue(show(cfg, video(duration = 120)))
    }

    @Test
    fun `unknown duration is not filtered`() {
        // duration <= 0 视为无数据, 不拦截
        assertTrue(show(rules(minDuration = 60), video(duration = 0)))
    }

    @Test
    fun `hides low play videos`() {
        val cfg = rules(minPlay = 1000)
        assertFalse(show(cfg, video(view = 500)))
        assertTrue(show(cfg, video(view = 5000)))
    }

    @Test
    fun `hides low like ratio videos`() {
        val cfg = rules(minLikeRatio = 2)
        // 点赞率 100/100000 = 0.1% < 2% → 隐藏
        assertFalse(show(cfg, video(view = 100_000, like = 100)))
        // like*100=10000 < 2*10000=20000 → 隐藏
        assertFalse(show(cfg, video(view = 10_000, like = 100)))
        // 点赞率 5000/100000 = 5% >= 2% → 显示
        assertTrue(show(cfg, video(view = 100_000, like = 5_000)))
    }

    @Test
    fun `hides titles matching keywords`() {
        val cfg = rules(banWords = "广告\n震惊")
        assertFalse(show(cfg, video(title = "震惊!必看")))
        assertFalse(show(cfg, video(title = "免费广告引流")))
        assertTrue(show(cfg, video(title = "正常视频")))
    }

    @Test
    fun `hides blocked users`() {
        val cfg = rules(blockedMids = mapOf(2001L to "屏蔽UP"))
        assertFalse(show(cfg, video(mid = 2001)))
        assertTrue(show(cfg, video(mid = 3001)))
    }

    @Test
    fun `whitelist exempts all rules`() {
        val cfg = rules(banWords = "广告", blockedMids = mapOf(2001L to "UP"), whitelistMids = mapOf(2001L to "白名单"))
        assertTrue(show(cfg, video(mid = 2001, title = "广告标题")))
    }

    @Test
    fun `zone keywords filter by tname`() {
        val cfg = rules(zoneWords = "游戏")
        assertFalse(show(cfg, video(tname = "游戏区")))
        assertTrue(show(cfg, video(tname = "音乐区")))
    }

    // ==================== 已关注豁免 ====================

    @Test
    fun `followed videos exempt in recommend feed`() {
        val cfg = rules(minDuration = 60)
        assertTrue(show(cfg, video(duration = 10, isFollowed = true)))
        assertFalse(show(cfg, video(duration = 10, isFollowed = false)))
    }

    @Test
    fun `followed exemption can be disabled`() {
        val cfg = rules(minDuration = 60, exemptFollowed = false)
        assertFalse(show(cfg, video(duration = 10, isFollowed = true)))
    }

    @Test
    fun `followed exemption only applies to recommend feed`() {
        // 需开启热门过滤, 规则才会真正执行
        val cfg = rules(minDuration = 60, applyHot = true)
        // 推荐流: 已关注豁免生效
        assertTrue(show(cfg, video(duration = 10, isFollowed = true), kind = FeedKind.HOME_RECOMMEND))
        // 热门: 无已关注豁免(filterAll 语义), 时长短被过滤
        assertFalse(show(cfg, video(duration = 10, isFollowed = true), kind = FeedKind.HOME_POPULAR))
    }

    // ==================== 按来源开关 ====================

    @Test
    fun `hot feed filtered only when applyToHotVideos on`() {
        val cfg = rules(minDuration = 60, applyHot = false)
        assertTrue(show(cfg, video(duration = 10), kind = FeedKind.HOME_POPULAR))

        val cfgOn = rules(minDuration = 60, applyHot = true)
        assertFalse(show(cfgOn, video(duration = 10), kind = FeedKind.HOME_POPULAR))
    }

    @Test
    fun `search filtered only when applyToSearch on`() {
        val cfg = rules(banWords = "广告", applySearch = false)
        assertTrue(show(cfg, video(title = "广告"), kind = FeedKind.SEARCH))

        val cfgOn = rules(banWords = "广告", applySearch = true)
        assertFalse(show(cfgOn, video(title = "广告"), kind = FeedKind.SEARCH))
    }

    @Test
    fun `rank filtered only when applyToRankVideos on`() {
        val cfg = rules(minDuration = 60, applyRank = false)
        assertTrue(show(cfg, video(duration = 10), kind = FeedKind.HOME_RANK))

        val cfgOn = rules(minDuration = 60, applyRank = true)
        assertFalse(show(cfgOn, video(duration = 10), kind = FeedKind.HOME_RANK))
    }

    // ==================== 工具函数 ====================

    @Test
    fun `parseBanWordToRegex joins lines with pipe`() {
        assertEquals("广告|震惊", PiliNaraFeedFilterPlugin.parseBanWordToRegex("广告\n震惊"))
        assertEquals("(a|b)|c", PiliNaraFeedFilterPlugin.parseBanWordToRegex("a|b\nc"))
        assertEquals(null, PiliNaraFeedFilterPlugin.parseBanWordToRegex(""))
        assertEquals(null, PiliNaraFeedFilterPlugin.parseBanWordToRegex("\n  \n"))
    }

    @Test
    fun `parseUidMap parses plain and named uids`() {
        val map = PiliNaraFeedFilterPlugin.parseUidMap("123456\n某某UP (789012)")
        assertEquals(setOf(123456L, 789012L), map.keys)
    }

    @Test
    fun `parseUidMap ignores empty lines`() {
        assertTrue(PiliNaraFeedFilterPlugin.parseUidMap("\n\n").isEmpty())
    }
}