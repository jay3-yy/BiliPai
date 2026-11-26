package com.android.purebilibili.feature.screenshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppScreenshotWatermarkPolicyTest {

    @Test
    fun promptTrigger_respectsEnabledBlockedAndCooldown() {
        assertFalse(
            shouldTriggerGlobalScreenshotPrompt(
                enabled = false,
                blocked = false,
                nowMs = 5_000L,
                lastTriggerMs = 0L,
            )
        )
        assertFalse(
            shouldTriggerGlobalScreenshotPrompt(
                enabled = true,
                blocked = true,
                nowMs = 5_000L,
                lastTriggerMs = 0L,
            )
        )
        assertTrue(
            shouldTriggerGlobalScreenshotPrompt(
                enabled = true,
                blocked = false,
                nowMs = 5_000L,
                lastTriggerMs = 0L,
            )
        )
        assertFalse(
            shouldTriggerGlobalScreenshotPrompt(
                enabled = true,
                blocked = false,
                nowMs = 6_000L,
                lastTriggerMs = 5_000L,
                cooldownMs = 1_800L,
            )
        )
        assertTrue(
            shouldTriggerGlobalScreenshotPrompt(
                enabled = true,
                blocked = false,
                nowMs = 6_800L,
                lastTriggerMs = 5_000L,
                cooldownMs = 1_800L,
            )
        )
    }

    @Test
    fun watermarkHeaderHeight_scalesProportionallyAndClampsBounds() {
        assertEquals(96, resolveWatermarkHeaderHeightPx(0))
        assertEquals(88, resolveWatermarkHeaderHeightPx(720))
        assertEquals(95, resolveWatermarkHeaderHeightPx(1080))
        assertEquals(164, resolveWatermarkHeaderHeightPx(2560))

        assertEquals(140, resolveWatermarkHeaderHeightPx(0, hasVideoMetadata = true))
        assertEquals(132, resolveWatermarkHeaderHeightPx(720, hasVideoMetadata = true))
        assertEquals(143, resolveWatermarkHeaderHeightPx(1080, hasVideoMetadata = true))
        assertEquals(232, resolveWatermarkHeaderHeightPx(2560, hasVideoMetadata = true))
    }

    @Test
    fun videoMetadataLine_formatsTitleAndBvidCleanly() {
        assertEquals(null, buildWatermarkVideoMetadataLine(null, "   "))
        assertEquals("BV1xx411c7mD", buildWatermarkVideoMetadataLine(" ", "BV1xx411c7mD"))
        assertEquals("测试视频", buildWatermarkVideoMetadataLine(" 测试视频 ", null))
        assertEquals(
            "测试视频 · BV1xx411c7mD",
            buildWatermarkVideoMetadataLine(" 测试视频 ", " BV1xx411c7mD "),
        )
    }
}
