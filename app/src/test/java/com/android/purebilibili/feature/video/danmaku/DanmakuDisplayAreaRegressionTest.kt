package com.android.purebilibili.feature.video.danmaku

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class DanmakuDisplayAreaRegressionTest {
    @Test
    fun countBarReservationKeepsTheFirstRowClearInBothDensityModesAndReleasesWhenDisabled() {
        val viewport = DanmakuViewport(1920, 1080, 1f)
        for (massive in listOf(false, true)) {
            val config = DanmakuConfig().apply {
                massiveMode = massive
                displayAreaRatio = .25f
            }
            val withoutBar = config.resolveRenderConfig(viewport, true)
            config.hotBarReservedHeightPx = 64f
            val withBar = config.resolveRenderConfig(viewport, true)

            assertEquals(64f, withBar.topMarginPx, .001f)
            assertEquals(withoutBar.bottomMarginPx, withBar.bottomMarginPx, .001f)
            assertTrue(withBar.lineCount in 1 until withoutBar.lineCount)
            assertTrue(withBar.topMarginPx + withBar.lineCount * withBar.lineHeightPx <= 270f)

            config.hotBarReservedHeightPx = 0f
            val restored = config.resolveRenderConfig(viewport, true)
            assertEquals(0f, restored.topMarginPx, .001f)
            assertEquals(withoutBar.lineCount, restored.lineCount)
        }
    }

    @Test
    fun countBarDoesNotForceAScrollingRowIntoAShortDisplayBand() {
        val config = DanmakuConfig().apply {
            massiveMode = true
            displayAreaRatio = .25f
            hotBarReservedHeightPx = 48f
        }
        val renderConfig = config.resolveRenderConfig(DanmakuViewport(480, 192, 1f), false)
        assertEquals(48f, renderConfig.topMarginPx, .001f)
        assertEquals(0, renderConfig.lineCount)
    }

    @Test
    fun changingAreaWhileMaskEnabledImmediatelyUpdatesTrackBudget() {
        val viewport = DanmakuViewport(1920, 1080, 1f)
        val config = DanmakuConfig().apply { smartOcclusionEnabled = true }
        config.displayAreaRatio = 1f
        val full = config.resolveRenderConfig(viewport, true)
        config.displayAreaRatio = .25f
        val quarter = config.resolveRenderConfig(viewport, true)

        assertTrue(quarter.lineCount < full.lineCount)
        assertEquals(810f, quarter.bottomMarginPx, .001f)
        assertTrue(quarter.topMarginPx + quarter.lineCount * quarter.lineHeightPx <= 270f)
        config.smartOcclusionEnabled = false
        val withoutMask = config.resolveRenderConfig(viewport, true)
        assertEquals(quarter.lineCount, withoutMask.lineCount)
        assertEquals(quarter.topMarginPx, withoutMask.topMarginPx, .001f)
        assertEquals(quarter.bottomMarginPx, withoutMask.bottomMarginPx, .001f)
    }
}
