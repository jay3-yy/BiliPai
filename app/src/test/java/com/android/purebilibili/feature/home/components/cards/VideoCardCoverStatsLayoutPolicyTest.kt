package com.android.purebilibili.feature.home.components.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCardCoverStatsLayoutPolicyTest {

    @Test
    fun `primary stat badge reserves enough width for wan count`() {
        val width = resolveVideoCardPrimaryStatBadgeMinWidthDp("6.2万")

        assertEquals(58f, width, 0.0001f)
    }

    @Test
    fun `primary stat badge expands for longer formatted counts`() {
        val width = resolveVideoCardPrimaryStatBadgeMinWidthDp("123.4万")

        assertEquals(70f, width, 0.0001f)
    }

    @Test
    fun `compact cover stats reserves readable width for play and comment counts`() {
        val layout = resolveVideoCardCompactCoverStatsLayout(
            availableWidthDp = 148f,
            primaryStatText = "2.0亿",
            secondaryStatText = "1.2万",
            hasOnlineCount = true
        )

        assertEquals(58f, layout.primaryMinWidthDp, 0.0001f)
        assertEquals(64f, layout.secondaryMinWidthDp, 0.0001f)
        assertEquals(false, layout.showOnlineCount)
        assertEquals(0f, layout.statsEndPaddingDp, 0.0001f)
    }

    @Test
    fun `compact cover stats keeps secondary count visible on narrow cards`() {
        val layout = resolveVideoCardCompactCoverStatsLayout(
            availableWidthDp = 132f,
            primaryStatText = "5035.0万",
            secondaryStatText = "9999",
            hasOnlineCount = true
        )

        assertEquals(true, layout.showSecondaryStat)
        assertEquals(false, layout.showOnlineCount)
        assertEquals(0f, layout.statsEndPaddingDp, 0.0001f)
    }

    @Test
    fun `compact cover stats reserves right side for duration badge`() {
        val layout = resolveVideoCardCompactCoverStatsLayout(
            availableWidthDp = 148f,
            primaryStatText = "已看79%",
            secondaryStatText = null,
            hasOnlineCount = true,
            durationBadgeMinWidthDp = 40f
        )

        assertEquals(46f, layout.statsEndPaddingDp, 0.0001f)
        assertEquals(false, layout.showOnlineCount)
    }

    @Test
    fun `cover overlay reserves bottom room when history progress bar is visible`() {
        val layout = resolveVideoCardCoverOverlayBottomLayout(showHistoryProgressBar = true)

        assertEquals(2f, layout.historyProgressBarHeightDp, 0.0001f)
        assertEquals(4f, layout.compactStatsBottomPaddingDp, 0.0001f)
        assertEquals(8f, layout.floatingDurationBottomPaddingDp, 0.0001f)
    }

    @Test
    fun `cover overlay keeps stable bottom padding without history progress bar`() {
        val layout = resolveVideoCardCoverOverlayBottomLayout(showHistoryProgressBar = false)

        assertEquals(2f, layout.historyProgressBarHeightDp, 0.0001f)
        assertEquals(4f, layout.compactStatsBottomPaddingDp, 0.0001f)
        assertEquals(8f, layout.floatingDurationBottomPaddingDp, 0.0001f)
    }

    @Test
    fun `duration stat pill expands width for hh mm ss text`() {
        assertEquals(60f, resolveVideoCardDurationStatMinWidthDp("12:34"), 0.0001f)
        assertEquals(68f, resolveVideoCardDurationStatMinWidthDp("1:25:10"), 0.0001f)
    }

    @Test
    fun `duration placement never hides an available comment count on narrow covers`() {
        for (width in listOf(80f, 132f, 148f, 160f)) {
            for ((badgeWidth, statWidth) in listOf(0f to 0f, 40f to 0f, 0f to 60f, 0f to 68f)) {
                val layout = resolveVideoCardCompactCoverStatsLayout(
                    availableWidthDp = width,
                    primaryStatText = "2.0亿",
                    secondaryStatText = "1.2万",
                    hasOnlineCount = true,
                    durationBadgeMinWidthDp = badgeWidth,
                    durationStatMinWidthDp = statWidth,
                )
                assertEquals(true, layout.showSecondaryStat)
                assertEquals(false, layout.showOnlineCount)
            }
        }
    }

    @Test
    fun `duration stat pill keeps comment count when cover width allows`() {
        val layout = resolveVideoCardCompactCoverStatsLayout(
            availableWidthDp = 200f,
            primaryStatText = "2.0亿",
            secondaryStatText = "1.2万",
            hasOnlineCount = true,
            durationStatMinWidthDp = resolveVideoCardDurationStatMinWidthDp("12:34")
        )

        assertEquals(true, layout.showSecondaryStat)
        assertEquals(false, layout.showOnlineCount)
    }

    @Test
    fun `duration stat pill without comment count keeps online count on wide covers`() {
        val layout = resolveVideoCardCompactCoverStatsLayout(
            availableWidthDp = 190f,
            primaryStatText = "2.0亿",
            secondaryStatText = null,
            hasOnlineCount = true,
            durationStatMinWidthDp = resolveVideoCardDurationStatMinWidthDp("12:34")
        )

        // 播放量 58 + 时长 60 + 间距 6 + 在线 52 + 间距 6 = 182 ≤ 190，在线保留
        assertEquals(true, layout.showOnlineCount)
        assertEquals(false, layout.showSecondaryStat)
    }
}
