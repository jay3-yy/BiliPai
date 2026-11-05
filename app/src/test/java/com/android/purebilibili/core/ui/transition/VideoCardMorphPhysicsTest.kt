package com.android.purebilibili.core.ui.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCardMorphPhysicsTest {
    @Test
    fun openingMovesPositionBeforeSizeAndSettlesExactly() {
        val physics = VideoCardMorphPhysics()
        val opening = Any()
        val frame = physics.sample(.4f, opening, 1f, 100_000_000, false, 100_000_000)
        assertTrue(frame.position > frame.size)
        assertTrue(frame.corner > frame.size)
        // 遮罩弹簧（0.95/0.12）先于模糊弹簧（1.0/0.25）到位：压暗先于模糊。
        assertTrue(frame.mask > frame.effects)
        assertEquals(VideoCardMorphFrame.at(1f), physics.sample(1f, opening, 1f, 3_000_000_000, false, 3_000_000_000))
    }

    @Test
    fun quickReturnAndReversalKeepEachPropertyPositionAndVelocity() {
        val physics = VideoCardMorphPhysics()
        val opening = Any()
        val before = physics.sample(.5f, opening, 1f, 120_000_000, false, 120_000_000)
        val returning = Any()
        val reversed = physics.sample(.5f, returning, 0f, 0, false, 120_000_000)
        assertFrameEquals(before, reversed)
        // 反向瞬间继承向外的速度，不能从静止重新播放一段返回动画。
        val next = physics.sample(.49f, returning, 0f, 1_000_000, false, 121_000_000)
        assertTrue(next.size > reversed.size)
        assertEquals(VideoCardMorphFrame.at(0f), physics.sample(0f, returning, 0f, 3_000_000_000, false, 3_120_000_000))
    }

    @Test
    fun gestureCancelStartsAtReleasedFrameAndRestoresDetails() {
        val physics = VideoCardMorphPhysics()
        physics.sample(1f, null, 1f, 0, false, 1_000_000_000)
        physics.sample(1f, null, 0f, 0, true, 1_016_000_000)
        val dragged = physics.sample(.6f, null, 0f, 0, true, 1_032_000_000)
        val cancel = Any()
        assertFrameEquals(dragged, physics.sample(.6f, cancel, 1f, 0, false, 1_032_000_000))
        assertEquals(VideoCardMorphFrame.at(1f), physics.sample(1f, cancel, 1f, 3_000_000_000, false, 4_032_000_000))
    }

    @Test
    fun longRunningDeviceAndAnimationScalePreserveGestureReleaseMotion() {
        fun releaseFrame(origin: Long, scale: Float): VideoCardMorphFrame {
            val physics = VideoCardMorphPhysics()
            fun wallTime(logical: Long) = origin + (logical * scale.toDouble()).toLong()
            physics.sample(1f, null, 1f, 0, false, wallTime(0), scale)
            physics.sample(1f, null, 0f, 0, true, wallTime(16_000_000), scale)
            physics.sample(.9f, null, 0f, 0, true, wallTime(32_000_000), scale)
            val cancel = Any()
            physics.sample(.9f, cancel, 1f, 0, false, wallTime(32_000_000), scale)
            return physics.sample(.92f, cancel, 1f, 10_000_000, false, wallTime(42_000_000), scale)
        }
        val reference = releaseFrame(1_000_000_000, 1f)
        assertFrameEquals(reference, releaseFrame(9_000_000_000_000_000, 1f))
        assertFrameEquals(reference, releaseFrame(9_000_000_000_000_000, .5f))
        assertFrameEquals(reference, releaseFrame(9_000_000_000_000_000, 2f))
    }

    private fun assertFrameEquals(expected: VideoCardMorphFrame, actual: VideoCardMorphFrame) {
        assertEquals(expected.position, actual.position, .00001f)
        assertEquals(expected.size, actual.size, .00001f)
        assertEquals(expected.corner, actual.corner, .00001f)
        assertEquals(expected.effects, actual.effects, .00001f)
        assertEquals(expected.mask, actual.mask, .00001f)
    }
}
