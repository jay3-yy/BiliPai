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
    fun `fullscreen fixed setting uses precise proportional range`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 240f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 10,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(6_000L, delta)
    }

    @Test
    fun `fullscreen precise range caps long drags at selected maximum`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 1200f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 10,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(10_000L, delta)
    }

    @Test
    fun `fullscreen falls back to linear when setting disabled`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = false,
            totalDragDistanceX = 110f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 15,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(22_000L, delta)
    }

    @Test
    fun `portrait uses configurable precise seek range regardless of fullscreen setting`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = false,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 50f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1.2f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(4_500L, delta)
    }

    @Test
    fun `portrait precise seek range caps long drags`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = false,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 1200f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 15,
            gestureSensitivity = 2f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(15_000L, delta)
    }

    @Test
    fun `fullscreen precise range keeps very small drags below commit threshold`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 20f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 10,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(500L, delta)
    }

    @Test
    fun `configured seek range reaches selected maximum after half screen drag`() {
        val fullscreenDelta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 400f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 60,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )
        val inlineDelta = resolveHorizontalSeekDeltaMs(
            isFullscreen = false,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 400f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 60,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(30_000L, fullscreenDelta)
        assertEquals(60_000L, inlineDelta)
    }

    @Test
    fun `fullscreen fixed-step seek waits for loaded step setting`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 110f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = null,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertNull(delta)
    }

    @Test
    fun `fixed seek stays precise across video durations and viewport densities`() {
        for (durationMs in listOf(60_000L, 10_800_000L)) {
            for (widthPx in listOf(720f, 1440f)) {
                assertEquals(
                    6_000L,
                    resolveHorizontalSeekDeltaMs(
                        isFullscreen = false,
                        fullscreenSwipeSeekEnabled = true,
                        totalDragDistanceX = widthPx * 0.1f,
                        containerWidthPx = widthPx,
                        fullscreenSwipeSeekSeconds = 15,
                        inlineSwipeSeekSeconds = 30,
                        gestureSensitivity = 1f,
                        durationMs = durationMs,
                        useRelativeDuration = false,
                        relativeSeekPercent = 90
                    )
                )
            }
        }
    }

    @Test
    fun `relative seek uses full width and media percentage instead of fixed ranges`() {
        for (isFullscreen in listOf(false, true)) {
            for (durationMs in listOf(120_000L, 10_800_000L)) {
                assertEquals(
                    durationMs / 16,
                    resolveHorizontalSeekDeltaMs(
                        isFullscreen = isFullscreen,
                        fullscreenSwipeSeekEnabled = false,
                        totalDragDistanceX = 200f,
                        containerWidthPx = 800f,
                        fullscreenSwipeSeekSeconds = null,
                        inlineSwipeSeekSeconds = 5,
                        gestureSensitivity = 1f,
                        durationMs = durationMs,
                        useRelativeDuration = true,
                        relativeSeekPercent = 25
                    )
                )
            }
        }
    }

    @Test
    fun `relative seek respects sensitivity and caps both drag directions`() {
        for ((dragPx, expectedMs) in listOf(100f to 15_000L, 1200f to 30_000L, -1200f to -30_000L)) {
            assertEquals(
                expectedMs,
                resolveHorizontalSeekDeltaMs(
                    isFullscreen = true,
                    fullscreenSwipeSeekEnabled = true,
                    totalDragDistanceX = dragPx,
                    containerWidthPx = 400f,
                    fullscreenSwipeSeekSeconds = 15,
                    inlineSwipeSeekSeconds = 30,
                    gestureSensitivity = 2f,
                    durationMs = 120_000L,
                    useRelativeDuration = true,
                    relativeSeekPercent = 25
                )
            )
        }
    }

    @Test
    fun `relative seek waits for a valid duration and viewport`() {
        for ((durationMs, widthPx) in listOf(0L to 800f, -1L to 800f, 120_000L to 0f)) {
            assertNull(
                resolveHorizontalSeekDeltaMs(
                    isFullscreen = true,
                    fullscreenSwipeSeekEnabled = true,
                    totalDragDistanceX = 100f,
                    containerWidthPx = widthPx,
                    fullscreenSwipeSeekSeconds = 15,
                    inlineSwipeSeekSeconds = 30,
                    gestureSensitivity = 1f,
                    durationMs = durationMs,
                    useRelativeDuration = true,
                    relativeSeekPercent = 90
                )
            )
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
