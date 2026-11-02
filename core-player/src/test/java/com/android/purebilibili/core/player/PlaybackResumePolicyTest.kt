package com.android.purebilibili.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackResumePolicyTest {
    @Test fun `a new part starts at zero even with server progress`() {
        assertEquals(0L, resolvePlaybackResumePosition(0, 20_000, 60_000, 120_000))
    }
    @Test fun `fresh server progress wins over local entries including backwards seeks`() {
        assertEquals(60_000L, resolvePlaybackResumePosition(null, 20_000, 60_000, 120_000))
        assertEquals(10_000L, resolvePlaybackResumePosition(null, 20_000, 10_000, 120_000))
        assertEquals(0L, resolvePlaybackResumePosition(null, 20_000, 0, 120_000))
        assertEquals(60_000L, resolvePlaybackResumePosition(null, 0, 60_000, 120_000))
        assertEquals(20_000L, resolvePlaybackResumePosition(null, 20_000, null, 120_000))
    }
    @Test fun `a watched through video does not repeatedly resume at its ending`() {
        assertEquals(0L, resolvePlaybackResumePosition(null, 119_000, null, 120_000))
        assertEquals(119_000L, resolvePlaybackResumePosition(120_000, 0, 0, 120_000))
    }
    @Test fun `invalid negative progress starts at zero`() {
        assertEquals(0L, resolvePlaybackResumePosition(null, -1, -10, 120_000))
    }
}
