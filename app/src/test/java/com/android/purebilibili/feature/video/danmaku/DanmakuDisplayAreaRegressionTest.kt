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
