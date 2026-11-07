package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.LiquidGlassAdvancedPreset
import com.android.purebilibili.core.store.LiquidGlassAdvancedSettings
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.store.resolveLiquidGlassAdvancedPreset
import com.android.purebilibili.core.store.resolveLegacyLiquidGlassProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassTuningTest {

    @Test
    fun `classic frosted slider varies background blur without adding optical distortion`() {
        val settings = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.CLASSIC_FROSTED)
        val clearEnd = resolveLiquidGlassTuning(0f, settings)
        val middle = resolveLiquidGlassTuning(0.5f, settings)
        val frostedEnd = resolveLiquidGlassTuning(1f, settings)
        assertTrue(clearEnd.backdropBlurRadius < middle.backdropBlurRadius)
        assertTrue(middle.backdropBlurRadius < frostedEnd.backdropBlurRadius)
        assertTrue(clearEnd.surfaceAlpha < frostedEnd.surfaceAlpha)
        for (progress in listOf(0f, 0.5f, 1f)) {
            val frosted = resolveLiquidGlassTuning(progress, settings)
            val glass = resolveLiquidGlassTuning(progress)
            assertTrue(frosted.fixedResolutionBlur)
            assertTrue(!glass.fixedResolutionBlur)
            assertTrue(frosted.backdropBlurRadius > glass.backdropBlurRadius)
            assertTrue(frosted.surfaceAlpha < glass.surfaceAlpha)
            assertEquals(0f, frosted.refractionAmount, 0.0001f)
            assertEquals(0f, frosted.refractionHeight, 0.0001f)
            assertEquals(0f, frosted.scrollCoupledRefractionAmount, 0.0001f)
        }
    }

    @Test
    fun `classic frosted keeps blur while removing shell and indicator lenses across materials`() {
        val settings = resolveLiquidGlassAdvancedPreset(LiquidGlassAdvancedPreset.CLASSIC_FROSTED)
        assertEquals(
            settings,
            com.android.purebilibili.core.store.resolveLiquidGlassAdvancedSettings(
                presetValue = settings.preset.value,
                contentReadability = null, chromaticAberration = null, contentDistortion = null,
            ),
        )
        val tuning = resolveLiquidGlassTuning(
            com.android.purebilibili.core.store.CLASSIC_FROSTED_GLASS_PROGRESS, settings,
        )
        assertTrue(tuning.backdropBlurRadius > resolveLiquidGlassTuning(0.5f).backdropBlurRadius)
        assertTrue(tuning.contentReadabilityScrimAlpha > 0f)
        assertEquals(0f, tuning.contentDistortionScale, 0.0001f)
        assertEquals(0f, resolveLiquidGlassIndicatorChromaticAberration(tuning), 0.0001f)
        for (preset in com.android.purebilibili.core.store.BottomBarLiquidGlassPreset.entries) {
            for (dark in listOf(false, true)) {
                val material = resolveBottomBarGlassMaterialSpec(
                    preset = preset, isDarkTheme = dark, isScrolling = true,
                    glassEnabled = true, motionProgress = 1f, pressProgress = 1f, liquidGlassTuning = tuning,
                )
                assertEquals(tuning.backdropBlurRadius, requireNotNull(material.blurRadiusDp), 0.0001f)
                assertEquals(0f, material.shellRefractionAmountDp, 0.0001f)
                assertEquals(0f, material.shellChromaticAberration, 0.0001f)
                val native = resolveBottomBarGlassMaterialSpec(
                    preset = preset, isDarkTheme = dark, isScrolling = false,
                    glassEnabled = false, motionProgress = 0f, pressProgress = 0f, liquidGlassTuning = tuning,
                )
                assertEquals(null, native.blurRadiusDp)
            }
        }
    }

    @Test
    fun `readability can be disabled without changing material slider`() {
        val settings = LiquidGlassAdvancedSettings(contentReadability = 0f)
        val clear = resolveLiquidGlassTuning(progress = 0f, advancedSettings = settings)
        val frosted = resolveLiquidGlassTuning(progress = 1f, advancedSettings = settings)

        assertEquals(0f, clear.contentReadabilityScrimAlpha, 0.0001f)
        assertEquals(0f, frosted.contentReadabilityScrimAlpha, 0.0001f)
        assertEquals(0f, clear.contentReadabilityBoost, 0.0001f)
        assertTrue(frosted.backdropBlurRadius > clear.backdropBlurRadius)
        assertTrue(frosted.surfaceAlpha > clear.surfaceAlpha)
        assertTrue(clear.refractionAmount > frosted.refractionAmount)
    }

    @Test
    fun `stable readability is default and adaptive mode is explicit`() {
        assertEquals(
            LiquidGlassReadabilityMode.STABLE,
            resolveLiquidGlassTuning(progress = 0.5f).readabilityMode,
        )
        assertEquals(
            LiquidGlassReadabilityMode.ADAPTIVE,
            resolveLiquidGlassTuning(
                progress = 0.5f,
                readabilityMode = LiquidGlassReadabilityMode.ADAPTIVE,
            ).readabilityMode,
        )
    }

    @Test
    fun `clear progress stays more transparent than frosted progress`() {
        val clear = resolveLiquidGlassTuning(progress = 0f)
        val frosted = resolveLiquidGlassTuning(progress = 1f)

        assertTrue(clear.backdropBlurRadius < frosted.backdropBlurRadius)
        assertTrue(clear.surfaceAlpha < frosted.surfaceAlpha)
        assertTrue(clear.refractionAmount > frosted.refractionAmount)
        assertTrue(clear.saturation > frosted.saturation)
    }

    @Test
    fun `progress is clamped into safe range`() {
        val low = resolveLiquidGlassTuning(progress = -1f)
        val high = resolveLiquidGlassTuning(progress = 3f)

        assertEquals(0f, low.progress, 0.0001f)
        assertEquals(1f, high.progress, 0.0001f)
        assertTrue(high.backdropBlurRadius >= low.backdropBlurRadius)
    }

    @Test
    fun `progress continuously shifts optical emphasis from lens to frost`() {
        val clear = resolveLiquidGlassTuning(progress = 0f)
        val middle = resolveLiquidGlassTuning(progress = 0.5f)
        val frosted = resolveLiquidGlassTuning(progress = 1f)

        // 照搬参考项目：指示器折射基准不随雾化程度放大，
        // 三档都等于上游的 10dp / 14dp 基准（否则上下色散比参考更长）。
        assertEquals(1f, clear.indicatorLensBoost, 0.0001f)
        assertEquals(1f, middle.indicatorLensBoost, 0.0001f)
        assertEquals(1f, frosted.indicatorLensBoost, 0.0001f)
        assertEquals(1f, clear.indicatorEdgeWarpBoost, 0.0001f)
        assertEquals(1f, middle.indicatorEdgeWarpBoost, 0.0001f)
        assertEquals(1f, frosted.indicatorEdgeWarpBoost, 0.0001f)
        assertTrue(
            clear.shellChromaticAberrationAmount >= middle.shellChromaticAberrationAmount
        )
        assertTrue(
            middle.shellChromaticAberrationAmount >= frosted.shellChromaticAberrationAmount
        )
        assertTrue(clear.scrollCoupledRefractionAmount >= middle.scrollCoupledRefractionAmount)
        assertTrue(middle.scrollCoupledRefractionAmount >= frosted.scrollCoupledRefractionAmount)
    }

    @Test
    fun `clear progress preserves the crystal dynamic dock material`() {
        val clear = resolveLiquidGlassTuning(progress = 0f)

        assertEquals(0f, clear.backdropBlurRadius, 0.0001f)
        assertEquals(0.40f, clear.surfaceAlpha, 0.0001f)
        assertEquals(0.04f, clear.whiteOverlayAlpha, 0.0001f)
        assertEquals(1.5f, clear.saturation, 0.0001f)
        assertEquals(24f, clear.refractionAmount, 0.0001f)
    }

    @Test
    fun `frosted backdrop blur stays stronger than balanced without the heavy cascade`() {
        val middle = resolveLiquidGlassTuning(progress = 0.5f)
        val frosted = resolveLiquidGlassTuning(progress = 1f)

        assertEquals(
            LIQUID_GLASS_BALANCED_BACKDROP_BLUR_RADIUS_DP,
            middle.backdropBlurRadius,
            0.0001f,
        )
        assertEquals(
            LIQUID_GLASS_FROSTED_BACKDROP_BLUR_RADIUS_DP,
            frosted.backdropBlurRadius,
            0.0001f,
        )
        assertTrue(frosted.backdropBlurRadius > middle.backdropBlurRadius)
        // 10dp × density 4 × 0.45 σ-factor = 18, below Miuix's σ≈20 8× blend band.
        assertTrue(frosted.backdropBlurRadius * 4f * 0.45f < 20f)
    }

    @Test
    fun `shell refraction height stays in a capsule-safe range`() {
        val clear = resolveLiquidGlassTuning(progress = 0f)
        val frosted = resolveLiquidGlassTuning(progress = 1f)

        assertTrue(clear.refractionHeight <= 24f)
        assertTrue(frosted.refractionHeight <= clear.refractionHeight)
    }

    @Test
    fun `readability protection stays active across the full material slider`() {
        // 清晰预设已改用通透端配方（readability≈0.62），保护曲线用满档可读性验证。
        val fullReadability = LiquidGlassAdvancedSettings(contentReadability = 1f)
        val clear = resolveLiquidGlassTuning(
            progress = 0f,
            advancedSettings = fullReadability,
        )
        val balanced = resolveLiquidGlassTuning(
            progress = 0.5f,
            advancedSettings = fullReadability,
        )
        val frosted = resolveLiquidGlassTuning(
            progress = 1f,
            advancedSettings = fullReadability,
        )

        assertEquals(1f, clear.contentReadabilityBoost, 0.0001f)
        assertTrue(clear.contentReadabilityBoost > balanced.contentReadabilityBoost)
        assertTrue(balanced.contentReadabilityBoost > frosted.contentReadabilityBoost)
        assertTrue(frosted.contentReadabilityBoost > 0f)
        assertTrue(clear.contentReadabilityScrimAlpha > balanced.contentReadabilityScrimAlpha)
        assertTrue(balanced.contentReadabilityScrimAlpha > frosted.contentReadabilityScrimAlpha)
        assertTrue(frosted.contentReadabilityScrimAlpha > 0f)
    }

    @Test
    fun `prism preset adds more chromatic separation and distortion than readable preset`() {
        val readable = resolveLiquidGlassTuning(
            progress = 0f,
            advancedSettings = resolveLiquidGlassAdvancedPreset(
                LiquidGlassAdvancedPreset.READABLE
            ),
        )
        val prism = resolveLiquidGlassTuning(
            progress = 0f,
            advancedSettings = resolveLiquidGlassAdvancedPreset(
                LiquidGlassAdvancedPreset.PRISM
            ),
        )

        assertTrue(
            prism.shellChromaticAberrationAmount > readable.shellChromaticAberrationAmount
        )
        assertTrue(prism.contentDistortionScale > readable.contentDistortionScale)
        assertTrue(readable.contentDistortionScale > 0f)
        assertTrue(prism.contentReadabilityBoost > 0f)
    }

    @Test
    fun `zero content distortion disables content warping completely`() {
        val tuning = resolveLiquidGlassTuning(
            progress = 0f,
            advancedSettings = LiquidGlassAdvancedSettings(contentDistortion = 0f),
        )

        assertEquals(0f, tuning.contentDistortionScale, 0.0001f)
    }

    @Test
    fun `readability responds monotonically across the whole control range`() {
        val samples = listOf(0f, 0.2f, 0.4f, 0.62f, 0.8f, 1f).map { value ->
            resolveLiquidGlassTuning(
                progress = 0.5f,
                advancedSettings = LiquidGlassAdvancedSettings(contentReadability = value),
            ).contentReadabilityBoost
        }

        assertTrue(samples.zipWithNext().all { (start, end) -> end > start })
    }

    @Test
    fun `distortion preserves balanced anchor and reaches max without a dead zone`() {
        fun distortion(value: Float) = resolveLiquidGlassTuning(
            progress = 0.5f,
            advancedSettings = LiquidGlassAdvancedSettings(contentDistortion = value),
        ).contentDistortionScale

        assertEquals(1f, distortion(0.45f), 0.0001f)
        assertEquals(1.8f, distortion(1f), 0.0001f)
        assertTrue(distortion(0.9f) < distortion(1f))
    }

    @Test
    fun `legacy mode and strength map into ordered continuous progress`() {
        val clear = resolveLegacyLiquidGlassProgress(
            mode = LiquidGlassMode.CLEAR,
            strength = 0.42f
        )
        val balanced = resolveLegacyLiquidGlassProgress(
            mode = LiquidGlassMode.BALANCED,
            strength = 0.52f
        )
        val frosted = resolveLegacyLiquidGlassProgress(
            mode = LiquidGlassMode.FROSTED,
            strength = 0.62f
        )

        assertTrue(clear < balanced)
        assertTrue(balanced < frosted)
    }

    @Test
    fun `sukisu style keeps the original value one slot`() {
        assertEquals(LiquidGlassStyle.SUKISU, LiquidGlassStyle.fromValue(1))
    }

    @Test
    fun `sukisu style uses floating bottom bar glass recipe`() {
        val tuning = resolveLiquidGlassTuning(LiquidGlassStyle.SUKISU)

        assertEquals(LiquidGlassMode.BALANCED, tuning.mode)
        assertEquals(0.5f, tuning.progress, 0.0001f)
        assertEquals(4f, tuning.backdropBlurRadius, 0.0001f)
        assertEquals(0.40f, tuning.surfaceAlpha, 0.0001f)
        assertEquals(1.5f, tuning.saturation, 0.0001f)
        assertEquals(24f, tuning.refractionAmount, 0.0001f)
        assertEquals(24f, tuning.refractionHeight, 0.0001f)
        assertEquals(0.28f, tuning.indicatorTintAlpha, 0.0001f)
        assertEquals(0.12648f, tuning.contentReadabilityScrimAlpha, 0.0001f)
        assertEquals(0f, tuning.shellChromaticAberrationAmount, 0.0001f)
        assertEquals(0.5f, tuning.indicatorChromaticAberrationAmount, 0.0001f)
    }

    @Test
    fun `shared indicator chromatic policy is identical at equal tuning`() {
        val tuning = resolveLiquidGlassTuning(
            progress = 0.5f,
            advancedSettings = resolveLiquidGlassAdvancedPreset(
                LiquidGlassAdvancedPreset.BALANCED
            ),
        )

        assertEquals(
            0.5f,
            resolveLiquidGlassIndicatorChromaticAberration(tuning),
            0.0001f,
        )
    }
    @Test
    fun `default indicator dispersion matches upstream across glass material strengths`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val tuning = resolveLiquidGlassTuning(progress = progress)
            assertEquals(1f, tuning.indicatorChromaticBoost, 0.0001f)
            assertEquals(0.5f, resolveLiquidGlassIndicatorChromaticAberration(tuning), 0.0001f)
            assertEquals(0f, tuning.shellChromaticAberrationAmount, 0.0001f)
        }
    }

}
