package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.LiquidGlassAdvancedSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * iOS 26 预设只改底栏壳层材质；指示器的滑动、形变、色散和配色继续走
 * BiliPai 现有策略。
 */
class BottomBarGlassMaterialPolicyTest {

    @Test
    fun `tuned glass scroll animation can be omitted without changing material`() {
        for (dark in listOf(false, true)) {
            for (tuningProgress in listOf(0f, 0.5f, 1f)) {
                val tuning = resolveLiquidGlassTuning(progress = tuningProgress)
                val resting = resolveBottomBarGlassMaterialSpec(
                    preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
                    isDarkTheme = dark,
                    isScrolling = false,
                    scrollProgress = 0f,
                    glassEnabled = true,
                    motionProgress = 0f,
                    pressProgress = 0f,
                    liquidGlassTuning = tuning,
                )
                for (scrollProgress in listOf(0.1f, 0.5f, 1f)) {
                    assertEquals(
                        resting,
                        resolveBottomBarGlassMaterialSpec(
                            preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
                            isDarkTheme = dark,
                            isScrolling = true,
                            scrollProgress = scrollProgress,
                            glassEnabled = true,
                            motionProgress = 0f,
                            pressProgress = 0f,
                            liquidGlassTuning = tuning,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `enum value 1 round trips to ios26 refined`() {
        assertSame(BottomBarLiquidGlassPreset.IOS26_REFINED, BottomBarLiquidGlassPreset.fromValue(1))
        assertSame(BottomBarLiquidGlassPreset.BILIPAI_TUNED, BottomBarLiquidGlassPreset.fromValue(0))
        assertSame(BottomBarLiquidGlassPreset.BILIPAI_TUNED, BottomBarLiquidGlassPreset.fromValue(999))
    }

    @Test
    fun `ios26 preserves every indicator policy from bilipai tuned`() {
        assertEquals(
            shouldUseBottomBarCombinedIndicatorBackdrop(BottomBarLiquidGlassPreset.BILIPAI_TUNED),
            shouldUseBottomBarCombinedIndicatorBackdrop(BottomBarLiquidGlassPreset.IOS26_REFINED)
        )
        assertEquals(
            shouldRenderBottomBarForegroundAboveIndicator(BottomBarLiquidGlassPreset.BILIPAI_TUNED),
            shouldRenderBottomBarForegroundAboveIndicator(BottomBarLiquidGlassPreset.IOS26_REFINED)
        )
        assertEquals(
            shouldUseBottomBarIndicatorLens(BottomBarLiquidGlassPreset.BILIPAI_TUNED),
            shouldUseBottomBarIndicatorLens(BottomBarLiquidGlassPreset.IOS26_REFINED)
        )
        assertEquals(
            resolveBottomBarEffectiveBackdropPresetProgress(
                preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
                motionProgress = 0.6f,
                pressProgress = 0.35f
            ),
            resolveBottomBarEffectiveBackdropPresetProgress(
                preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
                motionProgress = 0.6f,
                pressProgress = 0.35f
            )
        )
        assertEquals(
            resolveBottomBarPresetPanelOffsets(
                preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
                rawPanelOffsetPx = 42f
            ),
            resolveBottomBarPresetPanelOffsets(
                preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
                rawPanelOffsetPx = 42f
            )
        )
    }

    @Test
    fun `bilipai tuned material spec preserves old shell values`() {
        val spec = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
            isDarkTheme = false,
            isScrolling = false,
            glassEnabled = true,
            motionProgress = 0f,
            pressProgress = 0f
        )

        assertEquals(4f, spec.blurRadiusDp)
        assertTrue(spec.vibrancy)
        assertEquals(24f, spec.shellRefractionHeightDp)
        assertEquals(24f, spec.shellRefractionAmountDp)
        assertEquals(0f, spec.shellChromaticAberration)
        assertEquals(Color.Transparent, spec.foregroundTint)
        assertEquals(1f, spec.highlightWidthScale)
        assertEquals(1f, spec.shadowAlphaScale)
        assertEquals(null, spec.innerRimGlow)
    }

    @Test
    fun `zero content distortion disables shell refraction for every preset`() {
        val noDistortionTuning = resolveLiquidGlassTuning(
            progress = 0f,
            advancedSettings = LiquidGlassAdvancedSettings(contentDistortion = 0f),
        )

        BottomBarLiquidGlassPreset.entries.forEach { preset ->
            val spec = resolveBottomBarGlassMaterialSpec(
                preset = preset,
                isDarkTheme = false,
                isScrolling = false,
                glassEnabled = true,
                motionProgress = 0f,
                pressProgress = 0f,
                liquidGlassTuning = noDistortionTuning,
            )

            assertEquals(0f, spec.shellRefractionHeightDp)
            assertEquals(0f, spec.shellRefractionAmountDp)
        }
    }

    @Test
    fun `ios26 material spec uses BiliPai visible shell chain`() {
        val light = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            isDarkTheme = false,
            isScrolling = false,
            glassEnabled = true,
            motionProgress = 0f,
            pressProgress = 0f
        )
        val dark = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            isDarkTheme = true,
            isScrolling = false,
            glassEnabled = true,
            motionProgress = 0f,
            pressProgress = 0f
        )

        assertEquals(24f, light.shellRefractionHeightDp)
        assertEquals(24f, light.shellRefractionAmountDp)
        assertTrue(light.vibrancy)
        assertEquals(0f, light.shellChromaticAberration)
        assertEquals(null, light.shellShader)
        assertEquals(4f, light.blurRadiusDp)
        assertEquals(1f, light.highlightWidthScale)
        assertEquals(1f, light.shadowAlphaScale)
        assertEquals(BottomBarInnerRimGlowSpec(radiusDp = 5f, alpha = 0.09f), light.innerRimGlow)
        assertEquals(0f, light.foregroundTint.alpha, 0.001f)
        assertEquals(0f, dark.foregroundTint.alpha, 0.001f)
        assertEquals(
            null,
            resolveBottomBarGlassMaterialSpec(
                preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
                isDarkTheme = false, isScrolling = false, glassEnabled = true,
                motionProgress = 0f, pressProgress = 0f
            ).shellShader
        )
    }

    @Test
    fun `ios26 scroll does not add a foreground tint`() {
        val idle = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            isDarkTheme = false, isScrolling = false, glassEnabled = true,
            motionProgress = 0.5f, pressProgress = 0.2f
        )
        val scrolling = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            isDarkTheme = false, isScrolling = true, glassEnabled = true,
            motionProgress = 0.5f, pressProgress = 0.2f
        )

        assertEquals(idle.foregroundTint.alpha, scrolling.foregroundTint.alpha, 0.001f)
        assertEquals(Color.Transparent, scrolling.foregroundTint)
        assertEquals(idle.shellRefractionHeightDp, scrolling.shellRefractionHeightDp)
        assertEquals(idle.shellRefractionAmountDp, scrolling.shellRefractionAmountDp)
        assertEquals(
            resolveBottomBarEffectiveBackdropPresetProgress(
                preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
                motionProgress = 0.5f, pressProgress = 0.2f
            ),
            resolveBottomBarEffectiveBackdropPresetProgress(
                preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
                motionProgress = 0.5f, pressProgress = 0.2f
            )
        )
    }

    @Test
    fun `ios26 scroll tint stays transparent in dark theme`() {
        val darkScrolling = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            isDarkTheme = true,
            isScrolling = true,
            glassEnabled = true,
            motionProgress = 0f,
            pressProgress = 0f
        )

        assertEquals(Color.Transparent, darkScrolling.foregroundTint)
    }

    @Test
    fun `ios26 scroll progress lifts rim glow monotonically without touching backdrop`() {
        fun specAt(scrollProgress: Float) = resolveBottomBarGlassMaterialSpec(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            isDarkTheme = false,
            isScrolling = false,
            scrollProgress = scrollProgress,
            glassEnabled = true,
            motionProgress = 0f,
            pressProgress = 0f
        )
        val idle = specAt(0f)
        val mid = specAt(0.5f)
        val full = specAt(1f)
        val idleAlpha = idle.innerRimGlow!!.alpha
        val midAlpha = mid.innerRimGlow!!.alpha
        val fullAlpha = full.innerRimGlow!!.alpha

        // 提亮只走内圈辉光，且随 scrollProgress 单调递增 —— 单向变化即不可能明暗交替。
        assertEquals(0.09f, idleAlpha, 0.001f)
        assertTrue(midAlpha > idleAlpha)
        assertTrue(fullAlpha > midAlpha)
        assertEquals(0.16f, fullAlpha, 0.001f)

        // 提亮不碰 backdrop 相关属性（blur / 前景着色 / 高光宽度），故内容明暗不会被放大。
        assertEquals(idle.blurRadiusDp!!, full.blurRadiusDp!!, 0.001f)
        assertEquals(idle.foregroundTint, full.foregroundTint)
        assertEquals(idle.highlightWidthScale, full.highlightWidthScale, 0.001f)
    }

    @Test
    fun `ios26 scroll material exits slower than it enters`() {
        assertEquals(140, resolveBottomBarMaterialScrollAnimationDurationMillis(isScrolling = true))
        assertEquals(420, resolveBottomBarMaterialScrollAnimationDurationMillis(isScrolling = false))
    }

    @Test
    fun `container alpha is resolved by material policy`() {
        val tuned = resolveBottomBarGlassMaterialContainerColor(
            surfaceColor = Color.White,
            preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
            glassEnabled = true, fallbackAlpha = 1f
        )
        val ios26Light = resolveBottomBarGlassMaterialContainerColor(
            surfaceColor = Color.White,
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            glassEnabled = true, fallbackAlpha = 1f
        )
        val ios26Dark = resolveBottomBarGlassMaterialContainerColor(
            surfaceColor = Color.Black,
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            glassEnabled = true, fallbackAlpha = 1f
        )

        assertEquals(0.40f, tuned.alpha, 0.005f)
        assertEquals(0.40f, ios26Light.alpha, 0.005f)
        assertEquals(0.40f, ios26Dark.alpha, 0.005f)
    }

    @Test
    fun `shader uniforms map center to padding plus half size`() {
        val u = resolveLiquidGlassShaderUniforms(
            widthPx = 600f,
            heightPx = 160f,
            paddingPx = 20f,
            cornerRadiusPx = 80f,
            thicknessPx = 33f,
            refractIndex = 1.5f,
            refractIntensity = 0.70f,
            intensityScale = 1f
        )

        assertEquals(20f + 300f, u.centerX, 0.001f)
        assertEquals(20f + 80f, u.centerY, 0.001f)
        assertEquals(300f, u.halfWidth, 0.001f)
        assertEquals(80f, u.halfHeight, 0.001f)
        assertEquals(640f, u.resolutionX, 0.001f)
        assertEquals(200f, u.resolutionY, 0.001f)
        assertEquals(80f, u.cornerRadiusPx, 0.001f)
        assertEquals(33f, u.thicknessPx, 0.001f)
        assertEquals(1.5f, u.refractIndex, 0.001f)
        assertEquals(0.70f, u.refractIntensity, 0.001f)
    }

    @Test
    fun `shader uniforms clamp corner radius to half min dimension`() {
        val u = resolveLiquidGlassShaderUniforms(
            widthPx = 600f, heightPx = 160f, paddingPx = 0f,
            cornerRadiusPx = 999f,
            thicknessPx = 33f, refractIndex = 1.5f,
            refractIntensity = 0.70f, intensityScale = 1f
        )
        assertEquals(80f, u.cornerRadiusPx, 0.001f)
    }

    @Test
    fun `shader uniforms scale intensity for transition`() {
        val full = resolveLiquidGlassShaderUniforms(
            widthPx = 600f, heightPx = 160f, paddingPx = 0f,
            cornerRadiusPx = 80f, thicknessPx = 33f, refractIndex = 1.5f,
            refractIntensity = 0.70f, intensityScale = 1f
        )
        val half = resolveLiquidGlassShaderUniforms(
            widthPx = 600f, heightPx = 160f, paddingPx = 0f,
            cornerRadiusPx = 80f, thicknessPx = 33f, refractIndex = 1.5f,
            refractIntensity = 0.70f, intensityScale = 0.5f
        )
        assertEquals(0.70f, full.refractIntensity, 0.001f)
        assertEquals(0.35f, half.refractIntensity, 0.001f)
    }
}
