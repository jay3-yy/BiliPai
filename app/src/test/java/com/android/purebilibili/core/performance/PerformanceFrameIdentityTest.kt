package com.android.purebilibili.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceFrameIdentityTest {
    @Test
    fun `duplicate raw callback contributes one frame but equal duration distinct frames remain`() {
        val identity = PerformanceFrameIdentity()
        val frames = PerformanceFrameSummary()
        val start = 137_194_000_000L
        // 同一原始帧重复回调，不用经调整的 UI 起点或耗时作为帧身份。
        for (time in listOf(start, start, start + 16_666_667, start + 16_666_667)) {
            if (identity.accept(time)) frames.add(186_151_614, false)
        }
        assertEquals(2L, identity.duplicateCallbacks)
        assertEquals(2L, frames.frames)
        assertEquals(2L, frames.longFrames100Ms)
    }

    @Test
    fun `frame identities belong to each window and accept the first timestamp`() {
        val first = PerformanceFrameIdentity()
        val second = PerformanceFrameIdentity()
        assertTrue(first.accept(0))
        assertFalse(first.accept(0))
        assertTrue(second.accept(0))
    }

    @Test
    fun `wide system deadline does not erase a long UI frame`() {
        val duration = 186_151_614L
        val janky = isPerformanceUiJank(duration, 9_862_200_000)
        val frames = PerformanceFrameSummary().apply { add(duration, janky) }
        assertFalse(janky)
        assertEquals(1L, frames.longFrames100Ms)
        assertTrue(isPerformanceUiJank(duration, 11_600_000))
    }
}
