package com.android.purebilibili.feature.anime4k

import com.android.purebilibili.core.ui.adaptive.DeviceThermalTier
import com.android.purebilibili.plugin.sdk.Anime4KConfig
import com.android.purebilibili.plugin.sdk.Anime4KPreset
import com.android.purebilibili.plugin.sdk.VideoEnhancementAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals

class Anime4KThermalPolicyTest {

    @Test
    fun downshiftsQualityToFastOnElevatedAndToFsrOnCriticalWhilePreservingSharpness() {
        val quality = Anime4KConfig(
            algorithm = VideoEnhancementAlgorithm.ANIME4K,
            preset = Anime4KPreset.QUALITY,
            fsrSharpness = 0.7f,
        )

        assertEquals(quality, resolveThermallyAdjustedEnhancement(quality, DeviceThermalTier.NORMAL))

        val elevated = resolveThermallyAdjustedEnhancement(quality, DeviceThermalTier.ELEVATED)
        assertEquals(VideoEnhancementAlgorithm.ANIME4K, elevated.algorithm)
        assertEquals(Anime4KPreset.FAST, elevated.preset)
        assertEquals(0.7f, elevated.fsrSharpness)

        val critical = resolveThermallyAdjustedEnhancement(quality, DeviceThermalTier.CRITICAL)
        assertEquals(VideoEnhancementAlgorithm.FSR_1_0, critical.algorithm)
        assertEquals(0.7f, critical.fsrSharpness)
    }

    @Test
    fun keepsFastOnElevatedAndFsrAcrossAllThermalTiers() {
        val fast = Anime4KConfig(
            algorithm = VideoEnhancementAlgorithm.ANIME4K,
            preset = Anime4KPreset.FAST,
        )
        assertEquals(fast, resolveThermallyAdjustedEnhancement(fast, DeviceThermalTier.ELEVATED))
        assertEquals(
            VideoEnhancementAlgorithm.FSR_1_0,
            resolveThermallyAdjustedEnhancement(fast, DeviceThermalTier.CRITICAL).algorithm,
        )

        val fsr = Anime4KConfig(
            algorithm = VideoEnhancementAlgorithm.FSR_1_0,
            fsrSharpness = 0.4f,
        )
        assertEquals(fsr, resolveThermallyAdjustedEnhancement(fsr, DeviceThermalTier.ELEVATED))
        assertEquals(fsr, resolveThermallyAdjustedEnhancement(fsr, DeviceThermalTier.CRITICAL))
    }
}
