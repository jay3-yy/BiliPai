package com.android.purebilibili.feature.video

import com.android.purebilibili.navigation3.predictiveback.resolveMiuixVideoCardOuterScales
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceClipRadiusPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVideoCardWindowGeometryTest {
    @Test
    fun inAppAndTransparentWindowsUseTheSameCardBoundsAcrossWindowSizes() {
        for ((width, height) in listOf(1080f to 2400f, 1600f to 1000f, 480f to 1600f)) {
            for ((sourceX, sourceY) in listOf(.45f to .25f, .9f to .12f, .3f to 1.2f)) {
                for (progress in listOf(0f, .1f, .5f, .9f, 1f)) {
                    val window = resolveAppVideoCardWindowSize(
                        width * sourceX, height * sourceY, width, height, progress,
                    )
                    val inApp = resolveMiuixVideoCardOuterScales(sourceX, sourceY, progress, 1f)
                    assertEquals(width * inApp.x, window.width, .001f)
                    assertEquals(height * inApp.y, window.height, .001f)
                }
            }
        }
    }

    @Test
    fun returnKeepsDetailsVisibleUntilCardLanding() {
        assertEquals(0f, resolveAppVideoCardSeedAlpha(1f, true), .001f)
        assertEquals(0f, resolveAppVideoCardSeedAlpha(.5f, true), .001f)
        val landing = resolveAppVideoCardSeedAlpha(.1f, true)
        assertTrue(landing > 0f && landing < 1f)
        assertEquals(1f, resolveAppVideoCardSeedAlpha(0f, true), .001f)
        assertTrue(resolveAppVideoCardSeedAlpha(.05f, true) > landing)
    }

    @Test
    fun interruptedOpeningKeepsItsCurrentHandoffAndSwitchesAtFullExpansion() {
        assertEquals(.6f, resolveAppVideoCardSeedAlpha(.4f, false), .001f)
        assertEquals(.8f, resolveAppVideoCardSeedAlpha(.2f, false), .001f)
        assertEquals(1f, resolveAppVideoCardSeedAlpha(0f, false), .001f)
        assertEquals(
            resolveAppVideoCardSeedAlpha(1f, false),
            resolveAppVideoCardSeedAlpha(1f, true), .001f,
        )
    }

    @Test
    fun transparentRowUsesCoverCornerForWindowClipping() {
        assertEquals(36f, resolveVideoCardSourceClipRadiusPx(0f, 12f, 3f), .001f)
        // 无封面圆角时保留真实直角，不添加额外的默认圆角。
        assertEquals(0f, resolveVideoCardSourceClipRadiusPx(0f, null, 3f), .001f)
    }

    @Test
    fun wholeCardCornerTakesPriorityOverCoverCorner() {
        assertEquals(24f, resolveVideoCardSourceClipRadiusPx(24f, 12f, 3f), .001f)
    }

    @Test
    fun frameClockUsesActualElapsedTimeAndSystemDurationScale() {
        val origin = 9_000_000_000_000_000L
        val nextFrame = origin + 16_000_000L
        assertEquals(16_000_000L, resolveAppVideoCardElapsedNanos(origin, nextFrame, 1f))
        assertEquals(8_000_000L, resolveAppVideoCardElapsedNanos(origin, nextFrame, 2f))
        assertEquals(0L, resolveAppVideoCardElapsedNanos(origin, origin - 1, 1f))
    }

    @Test
    fun cornerKeepsScreenSpaceRadiusAndFinishesAtWindowCorner() {
        val halfScale = resolveAppVideoCardWindowRadius(20f, 72f, .5f, .5f, 600f, 900f)
        val fullScale = resolveAppVideoCardWindowRadius(20f, 72f, .5f, 1f, 600f, 900f)
        assertEquals(46f, halfScale * .5f, .001f)
        assertEquals(46f, fullScale, .001f)
        assertEquals(72f, resolveAppVideoCardWindowRadius(20f, 72f, 1f, 1f, 1080f, 2400f), .001f)
        assertEquals(40f, resolveAppVideoCardWindowRadius(32f, 72f, 0f, .25f, 40f, 20f), .001f)
    }

    @Test
    fun landscapeCardInterpolatesAspectRatioBeforeClipping() {
        val middle = resolveAppVideoCardWindowSize(400f, 300f, 1080f, 2400f, .5f)
        assertEquals(740f, middle.width, .001f)
        assertEquals(1099.7222f, middle.height, .001f)
    }

    @Test
    fun portraitCardFitsHeightEnvelopeAndKeepsBothEndpoints() {
        val start = resolveAppVideoCardWindowSize(300f, 900f, 1080f, 2400f, 0f)
        val end = resolveAppVideoCardWindowSize(300f, 900f, 1080f, 2400f, 1f)
        val middle = resolveAppVideoCardWindowSize(300f, 900f, 1080f, 2400f, .5f)
        assertEquals(300f, start.width, .001f)
        assertEquals(900f, start.height, .001f)
        assertEquals(1080f, end.width, .001f)
        assertEquals(2400f, end.height, .001f)
        assertEquals(1650f, middle.height, .001f)
        assertEquals(631.9149f, middle.width, .001f)
    }
}
