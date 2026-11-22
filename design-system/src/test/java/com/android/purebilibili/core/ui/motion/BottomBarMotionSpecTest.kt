package com.android.purebilibili.core.ui.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomBarMotionSpecTest {

    @Test
    fun `android native floating drag uses a conservative fling projection`() {
        val spec = resolveBottomBarMotionSpec(BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)

        assertTrue(spec.drag.flingProjectionTimeSeconds <= 0.14f)
    }

    @Test
    fun `android native floating bottom bar uses bouncy indicator motion`() {
        val spec = resolveBottomBarMotionSpec(BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)

        assertTrue(spec.drag.selectionSpring.dampingRatio <= 0.72f)
        assertTrue(spec.drag.offsetSnapSpring.dampingRatio <= 0.68f)
        assertTrue(spec.indicator.deformationScaleXDelta >= 0.38f)
        // 照搬 HyperIsland LiquidGlassNavigationBar：
        // scaleX = spring(0.6f, 250f)，scaleY = spring(0.7f, 250f)。
        assertEquals(0.6f, spec.indicator.scaleSpring.dampingRatio, 0.001f)
        assertEquals(250f, spec.indicator.scaleSpring.stiffness, 0.001f)
        assertEquals(0.7f, spec.indicator.scaleYSpring.dampingRatio, 0.001f)
        assertEquals(250f, spec.indicator.scaleYSpring.stiffness, 0.001f)
        assertTrue(spec.indicator.railFractionStretchMultiplier >= 0.08f)
    }

    @Test
    fun `all bottom bar profiles keep non-amplifying base drag resistance`() {
        for (profile in BottomBarMotionProfile.entries) {
            val spec = resolveBottomBarMotionSpec(profile)
            assertTrue(spec.drag.baseResistance in 0.9f..1.0f)
        }
    }
}
