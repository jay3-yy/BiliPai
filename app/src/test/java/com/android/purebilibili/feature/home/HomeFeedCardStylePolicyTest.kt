package com.android.purebilibili.feature.home

import com.android.purebilibili.core.store.HomeFeedCardStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFeedCardStylePolicyTest {

    @Test
    fun currentStyle_usesSixteenByNineFullCover() {
        val layout = resolveHomeFeedCardLayout(HomeFeedCardStyle.CURRENT)

        assertEquals(HOME_FEED_FULL_COVER_ASPECT_RATIO, layout.coverAspectRatio, 0.0001f)
        assertEquals(16f / 9f, resolveHomeFeedCoverAspectRatio(HomeFeedCardStyle.CURRENT), 0.0001f)
        assertEquals(6, layout.outerPaddingDp)
        assertEquals(6, layout.itemSpacingDp)
        assertEquals(6, layout.verticalItemSpacingDp)
        assertEquals(16, layout.storyCardHorizontalPaddingDp)
        assertEquals(false, layout.compactMetadata)
        assertEquals("16:9", HomeFeedCardStyle.CURRENT.label)
    }

    @Test
    fun officialStyle_usesFourByThreeLikeOfficialDualColumnFeed() {
        val layout = resolveHomeFeedCardLayout(HomeFeedCardStyle.OFFICIAL)

        // 4:3 列表框 + 居中 Crop（CDN 源 16:9 会裁左右）
        assertEquals(HOME_FEED_OFFICIAL_COVER_ASPECT_RATIO, layout.coverAspectRatio, 0.0001f)
        assertEquals(4f / 3f, resolveHomeFeedCoverAspectRatio(HomeFeedCardStyle.OFFICIAL), 0.0001f)
        assertEquals(6, layout.outerPaddingDp)
        assertEquals(6, layout.itemSpacingDp)
        assertEquals(6, layout.verticalItemSpacingDp)
        assertEquals(0, layout.storyCardHorizontalPaddingDp)
        assertEquals(true, layout.compactMetadata)
        assertEquals("4:3", HomeFeedCardStyle.OFFICIAL.label)
    }

    @Test
    fun biliPaiStyle_usesSixteenByTenBetweenFullAndTall() {
        val layout = resolveHomeFeedCardLayout(HomeFeedCardStyle.BILIPAI)

        assertEquals(HOME_FEED_BILIPAI_COVER_ASPECT_RATIO, layout.coverAspectRatio, 0.0001f)
        assertEquals(16f / 10f, resolveHomeFeedCoverAspectRatio(HomeFeedCardStyle.BILIPAI), 0.0001f)
        assertEquals(6, layout.outerPaddingDp)
        assertEquals(6, layout.itemSpacingDp)
        assertEquals(6, layout.verticalItemSpacingDp)
        assertEquals(8, layout.storyCardHorizontalPaddingDp)
        assertEquals(true, layout.compactMetadata)
        assertEquals("16:10", HomeFeedCardStyle.BILIPAI.label)
        assertTrue(HomeFeedCardStyle.BILIPAI.subtitle.contains("封面"))
        assertEquals(2, HomeFeedCardStyle.BILIPAI.value)
        assertEquals(HomeFeedCardStyle.BILIPAI, HomeFeedCardStyle.fromValue(2))
    }

    @Test
    fun selectedCoverRatio_isPreservedAcrossColumnsAndWindowSizes() {
        val cases = listOf(
            HomeFeedCardStyle.CURRENT to 16f / 9f,
            HomeFeedCardStyle.OFFICIAL to 4f / 3f,
            HomeFeedCardStyle.BILIPAI to 16f / 10f,
        )
        cases.forEach { (style, ratio) ->
            com.android.purebilibili.core.util.WindowWidthSizeClass.entries.forEach { width ->
                (1..6).forEach { columns ->
                    assertEquals(
                        ratio,
                        resolveHomeFeedCardLayout(style, columns, width).coverAspectRatio,
                        0.0001f,
                        "Selected $style must survive $columns columns on $width",
                    )
                }
            }
        }
    }

    @Test
    fun multipleColumns_keepSelectedCardStyleRatio() {
        assertEquals(
            HOME_FEED_OFFICIAL_COVER_ASPECT_RATIO,
            resolveHomeFeedCoverAspectRatio(
                style = HomeFeedCardStyle.OFFICIAL,
                gridColumns = 2,
            ),
            0.0001f,
        )
        assertEquals(
            HOME_FEED_BILIPAI_COVER_ASPECT_RATIO,
            resolveHomeFeedCoverAspectRatio(
                style = HomeFeedCardStyle.BILIPAI,
                gridColumns = 3,
            ),
            0.0001f,
        )
    }
}
