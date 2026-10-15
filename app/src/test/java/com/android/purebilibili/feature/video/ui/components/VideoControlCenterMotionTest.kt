package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoControlCenterMotionTest {
    @Test
    fun shortPullReturnsOpenButDeliberatePullCloses() {
        assertFalse(shouldCloseVideoControlCenter(progress = 0.8f, openingVelocity = 0f))
        assertTrue(shouldCloseVideoControlCenter(progress = 0.5f, openingVelocity = 0f))
    }

    @Test
    fun releaseDirectionCanReverseTheDistanceDecision() {
        assertTrue(shouldCloseVideoControlCenter(progress = 0.9f, openingVelocity = -3f))
        assertFalse(shouldCloseVideoControlCenter(progress = 0.4f, openingVelocity = 3f))
    }

    @Test
    fun upwardPullAddsResistanceOnlyAfterFullyOpen() {
        val motion = VideoControlCenterMotion()
        motion.dragBy(1f)
        assertEquals(1f, motion.progress)
        motion.dragBy(0.4f)
        assertEquals(1.14f, motion.progress, absoluteTolerance = 0.0001f)
    }

    @Test
    fun overExpansionSettlesOpenAndCanBeReversedWithoutADeadZone() {
        val motion = VideoControlCenterMotion()
        motion.dragBy(20f)
        assertEquals(1.25f, motion.progress)
        assertFalse(shouldCloseVideoControlCenter(motion.progress, openingVelocity = 0f))
        motion.dragBy(-0.25f)
        assertEquals(1f, motion.progress)
        motion.dragBy(-20f)
        assertEquals(0f, motion.progress)
    }

    @Test
    fun horizontalCloseProgressMapsToTheSameReleaseDecision() {
        assertFalse(ControlCenterMotionSpec.shouldClose(1f - 0.2f, -0.4f))
        assertTrue(ControlCenterMotionSpec.shouldClose(1f - 0.4f, 0f))
        // A fast opening fling reverses a mostly closed panel, in either orientation.
        assertFalse(ControlCenterMotionSpec.shouldClose(1f - 0.8f, -(-3f)))
    }

    @Test
    fun extremeReleaseVelocityIsBoundedBeforeStartingTheSpring() {
        assertEquals(4f, ControlCenterMotionSpec.releaseVelocity(100f))
        assertEquals(-4f, ControlCenterMotionSpec.releaseVelocity(-100f))
    }
}
