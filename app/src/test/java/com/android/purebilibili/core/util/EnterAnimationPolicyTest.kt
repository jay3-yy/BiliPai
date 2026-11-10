package com.android.purebilibili.core.util

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnterAnimationPolicyTest {

    @Test
    fun reducedMotion_usesMinimalStaggerAndHigherStiffness() {
        val policy = resolveEnterMotionPolicy(MotionTier.Reduced)

        assertTrue(policy.staggerStepMs <= 12)
        assertTrue(policy.maxStaggerMs <= 80)
        assertTrue(policy.stiffness > resolveEnterMotionPolicy(MotionTier.Normal).stiffness)
    }

    @Test
    fun enhancedMotion_hasMoreExpressiveScaleThanNormal() {
        val normal = resolveEnterMotionPolicy(MotionTier.Normal)
        val enhanced = resolveEnterMotionPolicy(MotionTier.Enhanced)

        assertTrue(enhanced.initialScale < normal.initialScale)
        assertTrue(enhanced.translationFactor >= normal.translationFactor)
    }

    @Test
    fun coordinateWithSharedTransition_usesAlphaOnlyGeometry() {
        val coordinated = resolveEnterMotionPolicy(
            motionTier = MotionTier.Enhanced,
            coordinateWithSharedTransition = true
        )

        assertEquals(1f, coordinated.initialScale)
        assertEquals(0f, coordinated.translationFactor)
        assertTrue(coordinated.maxStaggerMs <= 72)
        assertTrue(coordinated.stiffness >= 560f)
    }

    @Test
    fun coordinateWithSharedTransition_isLighterThanStandaloneEnter() {
        val standalone = resolveEnterMotionPolicy(
            motionTier = MotionTier.Normal,
            coordinateWithSharedTransition = false
        )
        val coordinated = resolveEnterMotionPolicy(
            motionTier = MotionTier.Normal,
            coordinateWithSharedTransition = true
        )

        assertTrue(coordinated.maxStaggerMs <= standalone.maxStaggerMs)
        assertTrue(coordinated.staggerStepMs <= standalone.staggerStepMs)
        assertTrue(coordinated.initialScale >= standalone.initialScale)
        assertTrue(coordinated.translationFactor <= standalone.translationFactor)
    }
}
