package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.adaptive.DeviceThermalTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpecialDanmakuThermalPolicyTest {

    @Test
    fun `keeps native vsync under normal thermals and steps down to 60Hz or 30Hz under thermal load`() {
        assertEquals(0L, resolveSpecialDanmakuMinFrameIntervalNs(DeviceThermalTier.NORMAL, activeCount = 1))
        assertEquals(0L, resolveSpecialDanmakuMinFrameIntervalNs(DeviceThermalTier.NORMAL, activeCount = 60))

        val elevatedLight = resolveSpecialDanmakuMinFrameIntervalNs(DeviceThermalTier.ELEVATED, activeCount = 4)
        val elevatedHeavy = resolveSpecialDanmakuMinFrameIntervalNs(DeviceThermalTier.ELEVATED, activeCount = 24)
        val criticalLight = resolveSpecialDanmakuMinFrameIntervalNs(DeviceThermalTier.CRITICAL, activeCount = 1)
        val criticalHeavy = resolveSpecialDanmakuMinFrameIntervalNs(DeviceThermalTier.CRITICAL, activeCount = 30)

        assertTrue(elevatedLight in 14_000_000L..16_700_000L)
        assertTrue(elevatedHeavy in 28_000_000L..33_400_000L)
        assertEquals(elevatedHeavy, criticalLight)
        assertEquals(criticalLight, criticalHeavy)
    }
}
