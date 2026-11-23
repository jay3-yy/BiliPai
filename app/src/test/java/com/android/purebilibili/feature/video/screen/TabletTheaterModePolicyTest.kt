package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabletTheaterModePolicyTest {

    @Test
    fun theaterModeYieldsToCompanionPaneAndTabletopPosture() {
        assertTrue(
            resolveTabletTheaterModeActive(
                requested = true,
                companionPane = false,
                useTabletopLayout = false,
            )
        )
        assertFalse(
            resolveTabletTheaterModeActive(
                requested = false,
                companionPane = false,
                useTabletopLayout = false,
            )
        )
        assertFalse(
            resolveTabletTheaterModeActive(
                requested = true,
                companionPane = true,
                useTabletopLayout = false,
            )
        )
        assertFalse(
            resolveTabletTheaterModeActive(
                requested = true,
                companionPane = false,
                useTabletopLayout = true,
            )
        )
    }

    @Test
    fun theaterPlayerSizeCapsStageHeightWhileMaximizingWidth() {
        // 1280x800 平板横屏：影院模式最大高度 800 * 0.68 = 544dp，对应 16:9 宽 967.1dp
        val (theaterW, theaterH) = resolveTabletTheaterPlayerSizeDp(
            availableWidthDp = 1280f,
            screenHeightDp = 800,
            isTheaterMode = true,
            useTabletopLayout = false,
            policyMaxWidthDp = 1080,
        )
        assertEquals(800f * 0.68f, theaterH, 0.5f)
        assertEquals(theaterH * 16f / 9f, theaterW, 0.5f)

        // 1600x1000 超宽屏：突破常规 1080dp 限制，按 68% 屏高放大到 1208.9dp
        val (largeW, _) = resolveTabletTheaterPlayerSizeDp(
            availableWidthDp = 1600f,
            screenHeightDp = 1000,
            isTheaterMode = true,
            useTabletopLayout = false,
            policyMaxWidthDp = 1080,
        )
        assertTrue(largeW > 1080f)

        // 普通模式保持原 policyMaxWidthDp 上限
        val (normalW, normalH) = resolveTabletTheaterPlayerSizeDp(
            availableWidthDp = 1280f,
            screenHeightDp = 800,
            isTheaterMode = false,
            useTabletopLayout = false,
            policyMaxWidthDp = 1080,
        )
        assertEquals(1080f, normalW, 0.01f)
        assertEquals(1080f * 9f / 16f, normalH, 0.01f)
    }

    @Test
    fun phoneTheaterModeActiveAndStageHeightBoundaries() {
        assertTrue(
            resolvePhoneTheaterModeActive(
                requested = true,
                useTabletLayout = false,
                isFullscreenMode = false,
                isInPipMode = false,
                isPortraitFullscreen = false,
            )
        )
        assertFalse(
            resolvePhoneTheaterModeActive(
                requested = true,
                useTabletLayout = true,
                isFullscreenMode = false,
                isInPipMode = false,
                isPortraitFullscreen = false,
            )
        )
        assertFalse(
            resolvePhoneTheaterModeActive(
                requested = true,
                useTabletLayout = false,
                isFullscreenMode = true,
                isInPipMode = false,
                isPortraitFullscreen = false,
            )
        )

        // 390x844 手机竖屏：原 16:9 高度约 219dp，影院舞台放大至 min(844*0.44=371.4, 219*1.42=311.0) = 311dp
        val stageHeight = resolvePhoneTheaterStageHeightDp(
            baseInlineHeightDp = 219f,
            screenHeightDp = 844,
            isTheaterMode = true,
        )
        assertEquals(219f * 1.42f, stageHeight, 0.5f)
        assertEquals(
            219f,
            resolvePhoneTheaterStageHeightDp(
                baseInlineHeightDp = 219f,
                screenHeightDp = 844,
                isTheaterMode = false,
            ),
            0.01f,
        )
    }
}
