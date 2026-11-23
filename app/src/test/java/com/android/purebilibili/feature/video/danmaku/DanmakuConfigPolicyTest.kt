package com.android.purebilibili.feature.video.danmaku

import com.android.purebilibili.danmaku.parser.resolveBilibiliDanmakuFontScale

import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_BOTTOM
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_SCROLL
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_TOP
import com.android.purebilibili.danmaku.engine.DANMAKU_LAYER_REVERSE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuConfigPolicyTest {

    @Test
    fun `count bar reservation is bounded by the measured viewport`() {
        assertEquals(64f, resolveDanmakuHotBarReservedHeightPx(64f, 720), 0f)
        assertEquals(32f, resolveDanmakuHotBarReservedHeightPx(64f, 32), 0f)
        assertEquals(0f, resolveDanmakuHotBarReservedHeightPx(64f, 0), 0f)
    }

    @Test
    fun `hot danmaku slot uses one stable layout band`() {
        val viewport = DanmakuViewport(1920, 1080, 3f)
        val lineHeight = 18f * 3f * 1.6f
        val slot = resolveHotDanmakuSlotHeightPx(
            enabled = true,
            viewport = viewport,
            fontScale = 1f,
            lineHeightMultiplier = 1.6f,
            isFullscreen = true,
            barMinHeightPx = 30f * 3f,
        )

        assertEquals(30f * 3f, slot, 0.001f)
        assertEquals(
            lineHeight,
            resolveHotDanmakuSlotHeightPx(
                enabled = true,
                viewport = viewport,
                fontScale = 1f,
                lineHeightMultiplier = 1.6f,
                isFullscreen = true,
                barMinHeightPx = 1f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveHotDanmakuSlotHeightPx(
                enabled = false,
                viewport = viewport,
                fontScale = 1f,
                lineHeightMultiplier = 1.6f,
                isFullscreen = true,
                barMinHeightPx = 30f * 3f,
            ),
            0f,
        )
    }

    @Test
    fun `bilibili font grades should remain relative to user font scale`() {
        assertEquals(0.72f, resolveBilibiliDanmakuFontScale(18f), 0.001f)
        assertEquals(1.0f, resolveBilibiliDanmakuFontScale(25f), 0.001f)
        assertEquals(1.44f, resolveBilibiliDanmakuFontScale(36f), 0.001f)
        assertEquals(1.0f, resolveBilibiliDanmakuFontScale(0f), 0.001f)
    }

    @Test
    fun `scroll duration should respect explicit duration seconds and speed factor`() {
        assertEquals(
            7000L,
            resolveDanmakuScrollDurationMillis(
                scrollDurationSeconds = 7.0f,
                speedFactor = 1.0f,
                scrollFixedVelocity = false,
                viewportWidthPx = 1080
            )
        )
        assertEquals(
            10500L,
            resolveDanmakuScrollDurationMillis(
                scrollDurationSeconds = 7.0f,
                speedFactor = 1.5f,
                scrollFixedVelocity = false,
                viewportWidthPx = 1080
            )
        )
    }

    @Test
    fun `fixed velocity should scale scroll duration with viewport width`() {
        assertEquals(
            14000L,
            resolveDanmakuScrollDurationMillis(
                scrollDurationSeconds = 7.0f,
                speedFactor = 1.0f,
                scrollFixedVelocity = true,
                viewportWidthPx = 2160
            )
        )
    }

    @Test
    fun `pinned duration should clamp to safe bounds`() {
        assertEquals(1000L, resolveDanmakuPinnedDurationMillis(0.5f))
        assertEquals(4000L, resolveDanmakuPinnedDurationMillis(4.0f))
        assertEquals(18000L, resolveDanmakuPinnedDurationMillis(18.0f))
        assertEquals(50000L, resolveDanmakuPinnedDurationMillis(60f))
    }

    @Test
    fun `Pili selected range endpoints reach renderer unchanged`() {
        assertEquals(0f, normalizeDanmakuOpacity(0f), 0f)
        assertEquals(37.5f, resolveDanmakuTextSizePx(1f, 2.5f, false), 0f)
        assertEquals(60f, resolveDanmakuLayerLineHeightPx(20f, 3f), 0f)
        assertEquals(1000L, resolveDanmakuScrollDurationMillis(1f, 1f, false, 1080))
        assertEquals(50000L, resolveDanmakuScrollDurationMillis(50f, 1f, false, 1080))
    }

    @Test
    fun `known viewport line count should honor engine line budget`() {
        val regularLines = resolveDanmakuVisibleLineCount(
            visibleHeightPx = 500f,
            areaRatioHint = 0.5f,
            fontSize = 20f,
            strokeWidth = 1.5f,
            strokeEnabled = true,
            lineHeight = 1.6f,
            massiveMode = false
        )
        val massiveLines = resolveDanmakuVisibleLineCount(
            visibleHeightPx = 500f,
            areaRatioHint = 0.5f,
            fontSize = 20f,
            strokeWidth = 1.5f,
            strokeEnabled = true,
            lineHeight = 1.6f,
            massiveMode = true
        )

        assertTrue(massiveLines >= regularLines)
        val rowHeight = resolveDanmakuLayerLineHeightPx(20f, 1.6f)
        // Row pitch is the line height alone; there is no extra interline margin.
        assertTrue(rowHeight + (massiveLines - 1) * rowHeight <= 500f)
        assertTrue(rowHeight + massiveLines * rowHeight > 500f)
    }

    @Test
    fun `short viewport should not force a minimum line beyond the pixel budget`() {
        assertEquals(
            1,
            resolveDanmakuVisibleLineCount(
                visibleHeightPx = 42f,
                areaRatioHint = 0.5f,
                fontSize = 42f,
                strokeWidth = 1.5f,
                strokeEnabled = true,
                lineHeight = 1.0f,
                massiveMode = false
            )
        )
        assertEquals(
            0,
            resolveDanmakuVisibleLineCount(
                visibleHeightPx = 41f,
                areaRatioHint = 0.5f,
                fontSize = 42f,
                strokeWidth = 1.5f,
                strokeEnabled = true,
                lineHeight = 1.0f,
                massiveMode = true
            )
        )
    }

    @Test
    fun `fullscreen changes the logical baseline but preserves the user's scale`() {
        assertEquals(39.375f, resolveDanmakuTextSizePx(2.625f, 1f, false), 0.001f)
        assertEquals(47.25f, resolveDanmakuTextSizePx(2.625f, 1f, true), 0.001f)
        assertEquals(59.0625f, resolveDanmakuTextSizePx(2.625f, 1.5f, false), 0.001f)
        assertEquals(70.875f, resolveDanmakuTextSizePx(2.625f, 1.5f, true), 0.001f)
    }

    @Test
    fun `stroke thickness uses system density and zero still disables the outline`() {
        assertEquals(1.5f, resolveDanmakuStrokeWidthPx(1f, 1.5f), 0.001f)
        assertEquals(5.25f, resolveDanmakuStrokeWidthPx(3.5f, 1.5f), 0.001f)
        assertEquals(0f, resolveDanmakuStrokeWidthPx(3.5f, 0f), 0f)
    }

    @Test
    fun `unknown viewport should retain area based fallback line count`() {
        assertEquals(
            8,
            resolveDanmakuVisibleLineCount(
                visibleHeightPx = 0f,
                areaRatioHint = 0.5f,
                fontSize = 42f,
                strokeWidth = 1.5f,
                strokeEnabled = true,
                lineHeight = 1.6f,
                massiveMode = true
            )
        )
    }

    @Test
    fun `line height multiplier should be converted to engine px spacing`() {
        assertEquals(
            67.2f,
            resolveDanmakuLayerLineHeightPx(
                fontSize = 42f,
                lineHeightMultiplier = 1.6f
            ),
            0.001f
        )
    }

    @Test
    fun `static to scroll should remap pinned danmaku to scrolling layer`() {
        assertEquals(
            DANMAKU_LAYER_SCROLL,
            resolveDanmakuRenderLayerType(
                type = 4,
                staticDanmakuToScroll = true
            )
        )
        assertEquals(
            DANMAKU_LAYER_SCROLL,
            resolveDanmakuRenderLayerType(
                type = 5,
                staticDanmakuToScroll = true
            )
        )
        assertEquals(
            DANMAKU_LAYER_BOTTOM,
            resolveDanmakuRenderLayerType(
                type = 4,
                staticDanmakuToScroll = false
            )
        )
        assertEquals(
            DANMAKU_LAYER_TOP,
            resolveDanmakuRenderLayerType(
                type = 5,
                staticDanmakuToScroll = false
            )
        )
        assertEquals(
            DANMAKU_LAYER_REVERSE,
            resolveDanmakuRenderLayerType(type = 6, staticDanmakuToScroll = false)
        )
    }
}
