package com.android.purebilibili.core.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAudioEffectPolicyTest {

    @Test
    fun offloadEnabledOnlyForNormalSpeedOffPresetAndUnityAmplification() {
        assertTrue(shouldEnableBackgroundAudioOffload(1.0f, PlayerAudioEffectPreset.OFF, 1.0f))
        assertFalse(shouldEnableBackgroundAudioOffload(1.25f, PlayerAudioEffectPreset.OFF, 1.0f))
        assertFalse(shouldEnableBackgroundAudioOffload(1.0f, PlayerAudioEffectPreset.NIGHT_VOICE, 1.0f))
        assertFalse(shouldEnableBackgroundAudioOffload(1.0f, PlayerAudioEffectPreset.OFF, 1.2f))
        assertFalse(shouldEnableBackgroundAudioOffload(Float.NaN, PlayerAudioEffectPreset.OFF, 1.0f))
    }

    @Test
    fun amplificationGainDbMatchesLogScaleAndClampsRange() {
        assertEquals(0f, amplificationToGainDb(1.0f), 0.001f)
        assertEquals(0f, amplificationToGainDb(0.5f), 0.001f)
        assertEquals(6.02f, amplificationToGainDb(2.0f), 0.05f)
        assertEquals(9.54f, amplificationToGainDb(3.0f), 0.05f)
        assertEquals(amplificationToGainDb(3.0f), amplificationToGainDb(5.0f), 0.001f)
    }

    @Test
    fun spatialAudioBadgeReflectsActiveAndHeadTrackerState() {
        assertNull(SpatialAudioHardwareStatus(supported = false, enabled = true, available = true).formatBadge())
        assertNull(SpatialAudioHardwareStatus(supported = true, enabled = false, available = true).formatBadge())
        assertEquals(
            "空间音频已开启",
            SpatialAudioHardwareStatus(supported = true, enabled = true, available = true, headTrackerAvailable = false)
                .formatBadge(),
        )
        assertEquals(
            "空间音频 · 头部追踪",
            SpatialAudioHardwareStatus(supported = true, enabled = true, available = true, headTrackerAvailable = true)
                .formatBadge(),
        )
    }

    @Test
    fun presetStorageKeyRoundTripsAndDefaultsToOff() {
        PlayerAudioEffectPreset.entries.forEach { preset ->
            assertEquals(preset, PlayerAudioEffectPreset.fromStorageKey(preset.storageKey))
            assertEquals(PlayerAudioEffectPreset.EQ_CUTOFF_FREQUENCIES_HZ.size, preset.bandGainsDb.size)
        }
        assertEquals(PlayerAudioEffectPreset.OFF, PlayerAudioEffectPreset.fromStorageKey("unknown"))
        assertEquals(PlayerAudioEffectPreset.OFF, PlayerAudioEffectPreset.fromStorageKey(null))
    }
}
