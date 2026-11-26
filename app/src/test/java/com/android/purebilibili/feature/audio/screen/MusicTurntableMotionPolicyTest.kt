package com.android.purebilibili.feature.audio.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicTurntableMotionPolicyTest {

    @Test
    fun `resolveTurntableTonearmAngleDeg rests on groove only when playing and not scrubbing`() {
        assertEquals(
            TURNTABLE_TONEARM_PLAYING_ANGLE_DEG,
            resolveTurntableTonearmAngleDeg(isPlaying = true, isScrubbing = false),
        )
        assertEquals(
            TURNTABLE_TONEARM_RESTING_ANGLE_DEG,
            resolveTurntableTonearmAngleDeg(isPlaying = false, isScrubbing = false),
        )
        assertEquals(
            TURNTABLE_TONEARM_RESTING_ANGLE_DEG,
            resolveTurntableTonearmAngleDeg(isPlaying = true, isScrubbing = true),
        )
    }

    @Test
    fun `resolveTurntableScrubDeltaMs scales drag distance and clamps to max range`() {
        assertEquals(14_000L, resolveTurntableScrubDeltaMs(100f))
        assertEquals(-14_000L, resolveTurntableScrubDeltaMs(-100f))
        assertEquals(TURNTABLE_SCRUB_MAX_DELTA_MS, resolveTurntableScrubDeltaMs(2_000f))
        assertEquals(-TURNTABLE_SCRUB_MAX_DELTA_MS, resolveTurntableScrubDeltaMs(-2_000f))
    }

    @Test
    fun `resolveTurntableScrubTickStep emits only when crossing a new step boundary`() {
        assertNull(resolveTurntableScrubTickStep(accumulatedDeltaMs = 1_500L, lastStep = 0L))
        assertEquals(1L, resolveTurntableScrubTickStep(accumulatedDeltaMs = 2_100L, lastStep = 0L))
        assertNull(resolveTurntableScrubTickStep(accumulatedDeltaMs = 3_900L, lastStep = 1L))
        assertEquals(-1L, resolveTurntableScrubTickStep(accumulatedDeltaMs = -2_200L, lastStep = 0L))
    }

    @Test
    fun `formatTurntableScrubBadge formats signed second deltas`() {
        assertEquals("+4s", formatTurntableScrubBadge(4_200L))
        assertEquals("-6s", formatTurntableScrubBadge(-6_100L))
        assertEquals("0s", formatTurntableScrubBadge(500L))
    }
}
