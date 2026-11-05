package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoDetailWholeCardReturnTest {
    @Test
    fun wholeCardTakesOverInTheLastSegmentAndSeeksBackOnCancel() {
        fun alpha(depth: Float) = resolveVideoDetailWholeCardReturnAlpha(depth, true, false)
        assertEquals(0f, alpha(1f))
        assertEquals(0f, alpha(0.45f))
        assertEquals(1f, alpha(0.02f), 0.0001f)
        assertEquals(1f, alpha(0f))
        var previous = 0f
        for (step in 0..100) {
            val depth = 1f - step / 100f
            val current = alpha(depth)
            assertTrue(current >= previous)
            previous = current
        }
        // Cancellation must progressively give visual ownership back to the detail group.
        for (step in 0..100) {
            val current = alpha(step / 100f)
            assertTrue(current <= previous)
            previous = current
        }
        assertEquals(0f, resolveVideoDetailWholeCardReturnAlpha(0f, false, false))
    }

    @Test
    fun committedIdleKeepsTheSnapshotUntilEntryRemoval() {
        // Navigation may release its depth metadata one frame before removing the entry.
        assertEquals(1f, resolveVideoDetailWholeCardReturnAlpha(1f, false, true))
    }
}
