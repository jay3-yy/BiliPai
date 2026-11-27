package com.android.purebilibili.feature.bangumi

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiUiPolicyTest {

    @Test
    fun `landscape natural cover only enters fullscreen after user request`() {
        assertEquals(
            false,
            resolveBangumiFullscreen(
                isLandscape = true,
                isTablet = false,
                usesInWindowFullscreen = true,
                userRequestedFullscreen = false,
            )
        )
        assertEquals(
            true,
            resolveBangumiFullscreen(
                isLandscape = true,
                isTablet = false,
                usesInWindowFullscreen = true,
                userRequestedFullscreen = true,
            )
        )
    }

    @Test
    fun `portrait natural phone keeps landscape driven fullscreen`() {
        assertEquals(
            true,
            resolveBangumiFullscreen(
                isLandscape = true,
                isTablet = false,
                usesInWindowFullscreen = false,
                userRequestedFullscreen = false,
            )
        )
    }

    @Test
    fun `cover badge on light gold uses dark label`() {
        val colors = resolveBangumiCoverBadgeColors(
            primary = Color(0xFFE8C9A0),
            onPrimary = Color(0xFF3A2A18),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFE6E1DC),
        )
        assertTrue(colors.contentColor.luminance() < 0.45f)
    }

    @Test
    fun `cover badge on dark primary keeps light label`() {
        val colors = resolveBangumiCoverBadgeColors(
            primary = Color(0xFF8B5A2B),
            onPrimary = Color.White,
            surface = Color(0xFF121212),
            onSurface = Color(0xFFE6E1DC),
        )
        assertTrue(colors.contentColor.luminance() > 0.5f)
    }

    @Test
    fun `bangumi navigation title font should be reduced on phone`() {
        assertEquals(22f, resolveBangumiNavigationTitleFontSizeSp(screenWidthDp = 393), 0.01f)
        assertEquals(20f, resolveBangumiNavigationTitleFontSizeSp(screenWidthDp = 320), 0.01f)
    }

    @Test
    fun `bangumi type tab font should avoid oversized labels`() {
        assertEquals(16f, resolveBangumiTypeTabFontSizeSp(screenWidthDp = 393), 0.01f)
        assertEquals(14f, resolveBangumiTypeTabFontSizeSp(screenWidthDp = 320), 0.01f)
    }

    @Test
    fun `portrait player controls should clear status bar and danmaku should start below controls`() {
        val statusInsetDp = 28f
        val containerPadding = resolveBangumiPortraitPlayerContainerTopPaddingDp(
            statusBarsInsetDp = statusInsetDp
        )
        val topControlsPadding = resolveBangumiPlayerTopControlsPaddingTopDp(
            isFullscreen = false,
            statusBarsInsetDp = statusInsetDp
        )
        val danmakuTopInset = resolveBangumiDanmakuTopInsetDp(
            isFullscreen = false,
            statusBarsInsetDp = statusInsetDp
        )

        assertEquals(statusInsetDp, containerPadding, 0.01f)
        assertEquals(8f, topControlsPadding, 0.01f)
        assertEquals(52f, danmakuTopInset, 0.01f)
        assertTrue(danmakuTopInset > topControlsPadding)
    }

    @Test
    fun `portrait player container inset should sanitize invalid status bar values`() {
        assertEquals(0f, resolveBangumiPortraitPlayerContainerTopPaddingDp(Float.NaN), 0.01f)
        assertEquals(0f, resolveBangumiPortraitPlayerContainerTopPaddingDp(-10f), 0.01f)
    }

    @Test
    fun `fullscreen player should not crop danmaku top area`() {
        assertEquals(
            0f,
            resolveBangumiDanmakuTopInsetDp(isFullscreen = true, statusBarsInsetDp = 28f),
            0.01f
        )
    }

    @Test
    fun `episode preview window should use selected range instead of first episodes`() {
        val window = resolveBangumiEpisodePreviewWindow(
            episodeCount = 1259,
            selectedPage = 5,
            episodesPerPage = 50,
            previewCount = 6
        )

        assertEquals(250, window.startIndex)
        assertEquals(256, window.endExclusive)
    }

    @Test
    fun `episode preview window should clamp to final partial range`() {
        val window = resolveBangumiEpisodePreviewWindow(
            episodeCount = 259,
            selectedPage = 5,
            episodesPerPage = 50,
            previewCount = 6
        )

        assertEquals(250, window.startIndex)
        assertEquals(256, window.endExclusive)
    }

    @Test
    fun `episode pages should expose ascending and descending ranges`() {
        assertEquals(26, resolveBangumiEpisodePageCount(1259, 50))
        assertEquals("1-50", resolveBangumiEpisodePageLabel(1259, 0, 50, descending = false))
        assertEquals("1259-1210", resolveBangumiEpisodePageLabel(1259, 0, 50, descending = true))
        assertEquals("9-1", resolveBangumiEpisodePageLabel(1259, 25, 50, descending = true))
    }

    @Test
    fun `episode ordering should reverse without mutating source`() {
        val source = listOf(1, 2, 3)

        assertEquals(listOf(3, 2, 1), orderBangumiEpisodes(source, descending = true))
        assertEquals(listOf(1, 2, 3), source)
    }

    @Test
    fun `episode page resolution preserves active episode across sort direction toggles`() {
        // 120 episodes, sourceIndex = 10 (11th episode): ascending page 0, descending orderedIndex 109 -> page 2
        assertEquals(0, resolveBangumiEpisodePageForOrder(120, sourceIndex = 10, episodesPerPage = 50, descending = false))
        assertEquals(2, resolveBangumiEpisodePageForOrder(120, sourceIndex = 10, episodesPerPage = 50, descending = true))
        // sourceIndex = 115 (116th episode): ascending page 2, descending orderedIndex 4 -> page 0
        assertEquals(2, resolveBangumiEpisodePageForOrder(120, sourceIndex = 115, episodesPerPage = 50, descending = false))
        assertEquals(0, resolveBangumiEpisodePageForOrder(120, sourceIndex = 115, episodesPerPage = 50, descending = true))
        // Invalid or empty falls back cleanly to 0
        assertEquals(0, resolveBangumiEpisodePageForOrder(0, sourceIndex = 0, episodesPerPage = 50, descending = true))
        assertEquals(0, resolveBangumiEpisodePageForOrder(120, sourceIndex = -1, episodesPerPage = 50, descending = true))
    }

    @Test
    fun `toggle orientation on ordinary phone exits to portrait when fullscreen`() {
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveBangumiToggleOrientationTarget(
                isFullscreen = true,
                isTablet = false,
                usesInWindowFullscreen = false,
            )
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolveBangumiToggleOrientationTarget(
                isFullscreen = false,
                isTablet = false,
                usesInWindowFullscreen = false,
            )
        )
    }

    @Test
    fun `toggle orientation on tablet or in-window returns null`() {
        assertEquals(
            null,
            resolveBangumiToggleOrientationTarget(
                isFullscreen = true,
                isTablet = true,
                usesInWindowFullscreen = false,
            )
        )
        assertEquals(
            null,
            resolveBangumiToggleOrientationTarget(
                isFullscreen = false,
                isTablet = false,
                usesInWindowFullscreen = true,
            )
        )
    }
}
