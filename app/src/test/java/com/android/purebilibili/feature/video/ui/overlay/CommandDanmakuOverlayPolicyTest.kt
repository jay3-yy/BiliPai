package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.feature.video.ui.feedback.canSubmitVideoFollowAction
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandDanmakuOverlayPolicyTest {

    @Test
    fun `attention follow is disabled while unknown submitting or already followed`() {
        assertFalse(canSubmitVideoFollowAction(false, false, false))
        assertTrue(canSubmitVideoFollowAction(true, false, false))
        assertFalse(canSubmitVideoFollowAction(true, false, true))
        assertFalse(canSubmitVideoFollowAction(true, true, false))
    }

    @Test
    fun `command card horizontal offset is clamped inside player bounds`() {
        val containerWidthPx = 1080
        val cardWidthPx = 588

        assertEquals(492, resolveCommandDanmakuHorizontalOffsetPx(containerWidthPx, cardWidthPx, 0.82f))
        assertEquals(0, resolveCommandDanmakuHorizontalOffsetPx(containerWidthPx, cardWidthPx, -0.2f))
    }

    @Test
    fun `command card width is capped by a narrow player viewport`() {
        assertEquals(320, resolveCommandDanmakuCardWidthPx(320, 420))
        assertEquals(0, resolveCommandDanmakuCardWidthPx(320, -1))
    }

    @Test
    fun `command card vertical offset is clamped by measured card height`() {
        assertEquals(192, resolveCommandDanmakuVerticalOffsetPx(320, 128, 0.8f))
        assertEquals(0, resolveCommandDanmakuVerticalOffsetPx(320, 400, 0.8f))
        assertEquals(0, resolveCommandDanmakuVerticalOffsetPx(320, 128, -0.2f))
    }

    @Test
    fun `attention percentage positions keep both edges clear inside the viewport`() {
        assertEquals(12, resolveAttentionCommandOffsetPx(320, 120, 0f, 12))
        assertEquals(100, resolveAttentionCommandOffsetPx(320, 120, 0.5f, 12))
        assertEquals(188, resolveAttentionCommandOffsetPx(320, 120, 1f, 12))
        assertEquals(12, resolveAttentionCommandOffsetPx(320, 120, -1f, 12))
        assertEquals(188, resolveAttentionCommandOffsetPx(320, 120, 2f, 12))
    }

    @Test
    fun `attention placement handles oversized cards and unknown percentages without invalid offsets`() {
        assertEquals(0, resolveAttentionCommandOffsetPx(80, 120, 1f, 12))
        assertEquals(0, resolveAttentionCommandOffsetPx(0, 120, 0.5f, 12))
        assertEquals(100, resolveAttentionCommandOffsetPx(320, 120, Float.NaN, 12))
    }

    @Test
    fun `bottom right command stays above the permanently reserved control region`() {
        val inset = resolveCommandDanmakuBottomInsetPx(
            viewportHeightPx = 320,
            surfaceHeightPx = 320,
            controlsReserveHeightPx = 96,
        )
        val placementHeight = 320 - inset
        val y = resolveAttentionCommandOffsetPx(placementHeight, 28, 1f, 12)

        assertEquals(96, inset)
        assertEquals(184, y)
        assertEquals(212, y + 28)
    }

    @Test
    fun `letterboxing only excludes controls that actually intersect the video viewport`() {
        assertEquals(0, resolveCommandDanmakuBottomInsetPx(600, 1200, 180))
        assertEquals(70, resolveCommandDanmakuBottomInsetPx(900, 1000, 120))
        assertEquals(120, resolveCommandDanmakuBottomInsetPx(900, 900, 120))
    }

    @Test
    fun `reserved controls cannot create a negative or oversized placement region`() {
        assertEquals(320, resolveCommandDanmakuBottomInsetPx(320, 320, 500))
        assertEquals(0, resolveCommandDanmakuBottomInsetPx(320, 320, -1))
        assertEquals(0, resolveCommandDanmakuBottomInsetPx(0, 320, 96))
    }
}
