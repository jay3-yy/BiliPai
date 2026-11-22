package com.android.purebilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashExitAnimationSpecTest {

    @Test
    fun usesExpectedDefaultAnimationSpecValues() {
        assertEquals(600L, splashExitDurationMs())
        assertEquals(220f, splashExitTranslateYDp(), 0.001f)
        assertEquals(1.12f, splashExitScaleEnd(), 0.001f)
        assertEquals(24f, splashExitBlurRadiusEnd(), 0.001f)
        assertEquals(1000L, splashMaxKeepOnScreenMs())
    }

    @Test
    fun keepsIconOpaqueAtAnimationStartThenFadesOut() {
        assertEquals(1f, splashExitIconAlpha(0f), 0.001f)
        assertTrue(splashExitIconAlpha(0.2f) < 1f)
        assertTrue(splashExitIconAlpha(0.2f) > 0.8f)
        assertEquals(0f, splashExitIconAlpha(1f), 0.001f)
    }

    @Test
    fun keepsBackgroundOpaqueLongerAndFadesOutNearEnd() {
        assertEquals(1f, splashExitBackgroundAlpha(0f), 0.001f)
        assertTrue(splashExitBackgroundAlpha(0.24f) > 0.9f)
        assertTrue(splashExitBackgroundAlpha(0.6f) > 0.45f)
        assertEquals(0f, splashExitBackgroundAlpha(1f), 0.001f)
    }

    @Test
    fun appliesBlurMoreGentlyAtBeginning() {
        assertEquals(0f, splashExitBlurProgress(0.1f), 0.001f)
        assertTrue(splashExitBlurProgress(0.25f) < 0.25f)
        assertEquals(1f, splashExitBlurProgress(1f), 0.001f)
    }

    @Test
    fun keepsTrailSubtleAndFadesOutCompletely() {
        assertEquals(0f, splashTrailPrimaryAlpha(0f), 0.001f)
        assertTrue(splashTrailPrimaryAlpha(0.22f) > splashTrailSecondaryAlpha(0.22f))
        assertEquals(0f, splashTrailPrimaryAlpha(1f), 0.001f)
        assertEquals(0f, splashTrailSecondaryAlpha(1f), 0.001f)
    }

    @Test
    fun usesDynamicTravelDistanceToFlyOutOfScreen() {
        val minTravel = 220f
        val dynamic = splashExitTravelDistancePx(
            splashHeightPx = 2400,
            targetSizePx = 224,
            minTravelPx = minTravel
        )
        assertTrue(dynamic > minTravel)
        assertEquals(minTravel, splashExitTravelDistancePx(0, 224, minTravel), 0.001f)
    }
}
