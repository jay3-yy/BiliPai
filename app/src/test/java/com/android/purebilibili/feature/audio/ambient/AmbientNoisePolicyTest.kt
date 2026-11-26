package com.android.purebilibili.feature.audio.ambient

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmbientNoisePolicyTest {

    @Test
    fun `normalizeAmbientNoiseVolume clamps safely between min and max`() {
        assertEquals(AMBIENT_NOISE_MIN_VOLUME, normalizeAmbientNoiseVolume(0f), 0.0001f)
        assertEquals(AMBIENT_NOISE_MAX_VOLUME, normalizeAmbientNoiseVolume(2.5f), 0.0001f)
        assertEquals(0.45f, normalizeAmbientNoiseVolume(0.45f), 0.0001f)
    }

    @Test
    fun `synthesizer produces silence for NONE and non-zero bounded samples for active presets`() {
        val synth = AmbientNoiseSynthesizer(seed = 12345)
        val buffer = ShortArray(2048)

        synth.fillBuffer(AmbientNoisePreset.NONE, buffer)
        assertTrue(buffer.all { it == 0.toShort() })

        listOf(
            AmbientNoisePreset.RAIN,
            AmbientNoisePreset.FIREPLACE,
            AmbientNoisePreset.TRAIN,
        ).forEach { preset ->
            synth.reset()
            synth.fillBuffer(preset, buffer)
            val peak = buffer.maxOf { abs(it.toInt()) }
            assertTrue(peak > 100, "Preset $preset should produce audible signal, peak=$peak")
            assertTrue(peak < Short.MAX_VALUE, "Preset $preset should not hard-clip, peak=$peak")
        }
    }
}
