package com.android.purebilibili.feature.video.ui.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TriplePressHapticPolicyTest {

    @Test
    fun `tracker emits charge tick once per crossed checkpoint and complete once at end`() {
        val tracker = TriplePressHapticCadenceTracker(floatArrayOf(0.25f, 0.6f, 0.9f))

        assertNull(tracker.onProgress(0.10f))
        assertEquals(TriplePressHapticCue.ChargeTick, tracker.onProgress(0.25f))
        assertNull(tracker.onProgress(0.40f))
        assertEquals(TriplePressHapticCue.ChargeTick, tracker.onProgress(0.62f))
        assertEquals(TriplePressHapticCue.ChargeTick, tracker.onProgress(0.95f))
        assertEquals(TriplePressHapticCue.Complete, tracker.onProgress(1.0f))
        assertNull(tracker.onProgress(1.0f))
    }

    @Test
    fun `tracker skips intermediate checkpoints cleanly on fast frame jumps and resets on cancel`() {
        val tracker = TriplePressHapticCadenceTracker(floatArrayOf(0.2f, 0.5f, 0.8f))

        assertEquals(TriplePressHapticCue.ChargeTick, tracker.onProgress(0.65f))
        assertNull(tracker.onProgress(0.75f))

        tracker.reset()
        assertEquals(TriplePressHapticCue.ChargeTick, tracker.onProgress(0.22f))
        assertEquals(TriplePressHapticCue.Complete, tracker.onProgress(1.0f))
    }
}
