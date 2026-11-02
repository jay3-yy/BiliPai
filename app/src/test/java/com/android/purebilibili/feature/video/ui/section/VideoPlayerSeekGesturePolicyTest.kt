package com.android.purebilibili.feature.video.ui.section

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoPlayerSeekGesturePolicyTest {

    @Test
    fun `player seek requires horizontal dominance over vertical drift`() {
        assertTrue(
            shouldEngageHorizontalPlayerSeek(
                totalDragDistanceX = 80f,
                totalDragDistanceY = 20f,
            )
        )
        assertFalse(
            shouldEngageHorizontalPlayerSeek(
                totalDragDistanceX = 80f,
                totalDragDistanceY = 70f,
            )
        )
    }

    @Test
    fun `short video full width seeks the entire duration in either direction`() {
        for (width in listOf(400f, 800f)) {
            assertEquals(30_000L, resolveHorizontalSeekDeltaMs(width, width, 1f, 30_000L))
            assertEquals(-30_000L, resolveHorizontalSeekDeltaMs(-width, width, 1f, 30_000L))
            assertEquals(15_000L, resolveHorizontalSeekDeltaMs(width / 2, width, 1f, 30_000L))
        }
    }

    @Test
    fun `duration scaling remains precise and sensitivity cannot seek beyond the video`() {
        val normal = requireNotNull(resolveHorizontalSeekDeltaMs(200f, 800f, 1f, 600_000L))
        val sensitive = requireNotNull(resolveHorizontalSeekDeltaMs(200f, 800f, 2f, 600_000L))
        assertTrue(normal > 15_000L && normal < 30_000L)
        assertTrue(kotlin.math.abs(sensitive - normal * 2) <= 1L)
        assertEquals(600_000L, resolveHorizontalSeekDeltaMs(100_000f, 800f, 2f, 600_000L))
        assertEquals(-600_000L, resolveHorizontalSeekDeltaMs(-100_000f, 800f, 2f, 600_000L))
    }

    @Test
    fun `duration boundaries are continuous and longer videos allow larger jumps`() {
        for (boundary in listOf(60_000L, 1_380_000L, 2_700_000L)) {
            val before = resolveOfficialSwipeSeekSecondsPerScreen(boundary - 1)
            val after = resolveOfficialSwipeSeekSecondsPerScreen(boundary)
            assertTrue(kotlin.math.abs(after - before) < 1f)
        }
        val tenMinutes = resolveOfficialSwipeSeekSecondsPerScreen(600_000L)
        val oneHour = resolveOfficialSwipeSeekSecondsPerScreen(3_600_000L)
        val twoHours = resolveOfficialSwipeSeekSecondsPerScreen(7_200_000L)
        assertTrue(tenMinutes < oneHour && oneHour < twoHours)
    }

    @Test
    fun `gesture seek waits for a known duration and valid viewport`() {
        for (duration in listOf(0L, -1L)) {
            assertNull(resolveHorizontalSeekDeltaMs(100f, 800f, 1f, duration))
        }
        for (width in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(resolveHorizontalSeekDeltaMs(100f, width, 1f, 120_000L))
        }
    }

    @Test
    fun `gesture seek commit requires meaningful delta`() {
        assertEquals(
            false,
            shouldCommitGestureSeek(
                currentPositionMs = 100_000L,
                targetPositionMs = 100_500L
            )
        )
        assertEquals(
            true,
            shouldCommitGestureSeek(
                currentPositionMs = 100_000L,
                targetPositionMs = 102_000L
            )
        )
    }

    @Test
    fun `seek step haptic ticks once per second of target progress`() {
        assertFalse(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 10_200L,
                currentTargetMs = 10_800L
            )
        )
        assertTrue(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 10_200L,
                currentTargetMs = 11_100L
            )
        )
        assertTrue(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 11_100L,
                currentTargetMs = 9_900L
            )
        )
        assertFalse(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 5_000L,
                currentTargetMs = 5_000L
            )
        )
    }
}
