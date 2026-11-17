package com.android.purebilibili.core.ui.transition

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationBlurBlendTest {
    @Test
    fun smallGestureChangesVaryBlendContinuouslyWithoutChangingEffectRadius() {
        for (light in listOf(false, true)) {
            val full = resolveNavigationBlurBlendFrame(1f, isLightBackground = light, sdkInt = 35)
            assertTrue(full.radiusPx > 0f)
            assertEquals(1f, full.alpha)
            // 细小手势变化直接改变混合权重，不再等待半径跨过量化阈值。
            val before = resolveNavigationBlurBlendFrame(0.0714f, isLightBackground = light, sdkInt = 35)
            val after = resolveNavigationBlurBlendFrame(0.0715f, isLightBackground = light, sdkInt = 35)
            assertEquals(0.0001f, after.alpha - before.alpha, 0.000001f)
            assertEquals(full.radiusPx, before.radiusPx)
            assertEquals(full.radiusPx, after.radiusPx)
            assertEquals(0f, resolveNavigationBlurBlendFrame(0f, isLightBackground = light, sdkInt = 35).alpha)
        }
    }

    @Test
    fun reducedMotionAndOlderSystemsKeepTheClearPathAndOvershootStaysBounded() {
        for (frame in listOf(
            resolveNavigationBlurBlendFrame(1f, motionTier = MotionTier.Reduced, sdkInt = 35),
            resolveNavigationBlurBlendFrame(1f, sdkInt = 30),
            resolveNavigationBlurBlendFrame(-0.1f, sdkInt = 35),
        )) {
            assertEquals(0f, frame.alpha)
            assertEquals(0f, frame.radiusPx)
        }
        assertEquals(1f, resolveNavigationBlurBlendFrame(1.1f, sdkInt = 35).alpha)
    }
}
