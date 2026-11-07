package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.store.LiquidGlassAdvancedSettings
import com.android.purebilibili.core.store.LiquidGlassAdvancedPreset
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.android.purebilibili.core.store.normalizeLiquidGlassProgress
import com.android.purebilibili.core.store.normalizeLiquidGlassStrength
import com.android.purebilibili.core.store.resolveLiquidGlassStrengthFromProgress
import com.android.purebilibili.core.store.resolveLegacyLiquidGlassProgress
import com.android.purebilibili.core.store.resolveLegacyLiquidGlassMode

data class LiquidGlassTuning(
    val readabilityMode: LiquidGlassReadabilityMode,
    val mode: LiquidGlassMode,
    val progress: Float,
    val strength: Float,
    val backdropBlurRadius: Float,
    val progressiveBlurRadius: Float,
    val progressiveBlurStartFraction: Float = BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION,
    val progressiveBlurEndFraction: Float,
    val progressiveBlurCurve: Float,
    val surfaceAlpha: Float,
    val whiteOverlayAlpha: Float,
    val saturation: Float,
    val refractionAmount: Float,
    val refractionHeight: Float,
    val indicatorTintAlpha: Float,
    val indicatorLensBoost: Float,
    val indicatorEdgeWarpBoost: Float,
    val indicatorChromaticBoost: Float,
    val contentReadabilityBoost: Float,
    val contentReadabilityScrimAlpha: Float,
    val contentDistortionScale: Float,
    val shellChromaticAberrationAmount: Float,
    val indicatorChromaticAberrationAmount: Float,
    val scrollCoupledRefractionAmount: Float,
    val fixedResolutionBlur: Boolean = false,
)

private const val UPSTREAM_BALANCED_CHROMATIC_CONTROL = 0.56f
private const val UPSTREAM_INDICATOR_CHROMATIC_ABERRATION = 0.5f
private const val BALANCED_CONTENT_DISTORTION = 0.45f
private const val MAX_CONTENT_DISTORTION_SCALE = 1.8f
internal const val LIQUID_GLASS_BALANCED_BACKDROP_BLUR_RADIUS_DP = 4f
internal const val LIQUID_GLASS_FROSTED_BACKDROP_BLUR_RADIUS_DP = 10f

internal fun resolveLiquidGlassTuning(
    progress: Float,
    advancedSettings: LiquidGlassAdvancedSettings = LiquidGlassAdvancedSettings(),
    readabilityMode: LiquidGlassReadabilityMode = LiquidGlassReadabilityMode.STABLE,
): LiquidGlassTuning {
    val normalizedProgress = normalizeLiquidGlassProgress(progress)
    val classicFrosted = advancedSettings.preset == LiquidGlassAdvancedPreset.CLASSIC_FROSTED
    val mode = when {
        normalizedProgress < 0.34f -> LiquidGlassMode.CLEAR
        normalizedProgress < 0.68f -> LiquidGlassMode.BALANCED
        else -> LiquidGlassMode.FROSTED
    }
    val configuredReadability = advancedSettings.contentReadability.coerceIn(0f, 1f)
    // Use the configured strength directly: the former fourth-power curve reduced the
    // default 62% protection to a barely visible scrim over detailed video covers.
    val readabilityProtection = configuredReadability
    // Readability protection must remain active across the full slider. It is strongest for
    // transparent glass, but a user-selected 100% must not become a no-op around 50%.
    val readabilityWeight = midpointLerp(1f, 0.6f, 0.25f, normalizedProgress)
    val contentReadabilityBoost = readabilityWeight * readabilityProtection
    val contentReadabilityScrimAlpha = contentReadabilityBoost * 0.34f
    val chromaticControl = advancedSettings.chromaticAberration.coerceIn(0f, 1f)
    // Miuix keeps the 24dp shell achromatic and applies 0.5 dispersion to the moving
    // 10dp/14dp indicator. The balanced anchor reproduces that split exactly.
    val shellChromaticAmount = if (chromaticControl > UPSTREAM_BALANCED_CHROMATIC_CONTROL) {
        (chromaticControl - UPSTREAM_BALANCED_CHROMATIC_CONTROL) /
            (1f - UPSTREAM_BALANCED_CHROMATIC_CONTROL) *
            UPSTREAM_INDICATOR_CHROMATIC_ABERRATION
    } else {
        0f
    }
    val indicatorChromaticAmount = (
        chromaticControl / UPSTREAM_BALANCED_CHROMATIC_CONTROL
    ).coerceIn(0f, 1f) * UPSTREAM_INDICATOR_CHROMATIC_ABERRATION
    val configuredDistortion = advancedSettings.contentDistortion.coerceIn(0f, 1f)
    // Preserve the upstream-balanced anchor (0.45 -> 1x), then continue toward 1.8x instead of
    // saturating at 0.81 and leaving the final fifth of the slider ineffective.
    val contentDistortionScale = if (configuredDistortion <= BALANCED_CONTENT_DISTORTION) {
        configuredDistortion / BALANCED_CONTENT_DISTORTION
    } else {
        1f + (configuredDistortion - BALANCED_CONTENT_DISTORTION) /
            (1f - BALANCED_CONTENT_DISTORTION) *
            (MAX_CONTENT_DISTORTION_SCALE - 1f)
    }
    val scrollCouplingAmount = midpointLerp(1f, 0f, 0f, normalizedProgress)
    return LiquidGlassTuning(
        readabilityMode = readabilityMode,
        mode = mode,
        progress = normalizedProgress,
        strength = resolveLiquidGlassStrengthFromProgress(normalizedProgress),
        // The clear endpoint intentionally preserves the dynamic dock's formerly accidental
        // crystal-glass recipe: no backdrop blur, but enough tint, saturation and refraction
        // to keep the capsule legible over moving content. The midpoint remains the original
        // BiliPai material. Frosted stays stronger, but is capped so typical densities stay
        // in a single Miuix 4× blur pass instead of the 8×/16× cross-fade band (σ≈20 / σ≈44)
        // that renders two full cascades per capsule and hitches the live slider.
        // 经典磨砂对齐 iOS 18 毛玻璃：均匀重模糊让背景只剩色块，配合较厚的
        // 底色染色形成奶感，并对背景轻微提饱和保留 vibrancy；其他预设仍走原有配方。
        backdropBlurRadius = if (classicFrosted) midpointLerp(20f, 30f, 40f, normalizedProgress) else midpointLerp(
            0f,
            LIQUID_GLASS_BALANCED_BACKDROP_BLUR_RADIUS_DP,
            LIQUID_GLASS_FROSTED_BACKDROP_BLUR_RADIUS_DP,
            normalizedProgress,
        ),
        progressiveBlurRadius = advancedSettings.progressiveBlurRadius.coerceIn(0f, 1f) * 40f,
        progressiveBlurStartFraction = BILIPAI_PROGRESSIVE_TOP_BLUR_START_FRACTION,
        progressiveBlurEndFraction = 0.25f +
            advancedSettings.progressiveBlurExtent.coerceIn(0f, 1f) * 0.75f,
        progressiveBlurCurve = 0.35f +
            advancedSettings.progressiveBlurCurve.coerceIn(0f, 1f) * 1.65f,
        surfaceAlpha = if (classicFrosted) midpointLerp(0.32f, 0.38f, 0.50f, normalizedProgress)
            else midpointLerp(0.40f, 0.40f, 0.54f, normalizedProgress),
        whiteOverlayAlpha = if (classicFrosted) 0f else midpointLerp(0.04f, 0.04f, 0.14f, normalizedProgress),
        saturation = if (classicFrosted) 1.15f else midpointLerp(1.5f, 1.5f, 1.24f, normalizedProgress),
        refractionAmount = if (classicFrosted) 0f else midpointLerp(24f, 24f, 8f, normalizedProgress),
        refractionHeight = if (classicFrosted) 0f else midpointLerp(24f, 24f, 8f, normalizedProgress),
        indicatorTintAlpha = midpointLerp(0.20f, 0.28f, 0.38f, normalizedProgress),
        // 照搬 HyperIsland LiquidGlassNavigationBar 的指示器 lens：
        // refractionHeight = 10dp、refractionAmount = 14dp（按壳高比例换算），
        // chromaticAberration = 0.5f。参考不再额外放大折射 —— 原先的 1.35/1.40 系数
        // 会把 CLEAR 端的折射推到 11.8dp/17.15dp，比参考长一截（上下色散被摊薄）。
        indicatorLensBoost = 1f,
        indicatorEdgeWarpBoost = 1f,
        indicatorChromaticBoost = 1f,
        contentReadabilityBoost = contentReadabilityBoost,
        contentReadabilityScrimAlpha = contentReadabilityScrimAlpha,
        contentDistortionScale = contentDistortionScale,
        shellChromaticAberrationAmount = shellChromaticAmount,
        indicatorChromaticAberrationAmount = indicatorChromaticAmount,
        scrollCoupledRefractionAmount = if (classicFrosted) 0f else scrollCouplingAmount,
        fixedResolutionBlur = classicFrosted,
    )
}

internal fun resolveLiquidGlassIndicatorChromaticAberration(
    tuning: LiquidGlassTuning,
): Float = if (tuning.indicatorChromaticAberrationAmount > 0.01f) {
    (tuning.indicatorChromaticAberrationAmount * tuning.indicatorChromaticBoost)
        .coerceIn(0f, UPSTREAM_INDICATOR_CHROMATIC_ABERRATION)
} else {
    0f
}

internal fun resolveLiquidGlassTuning(
    mode: LiquidGlassMode,
    strength: Float
): LiquidGlassTuning {
    return resolveLiquidGlassTuning(
        progress = resolveLegacyLiquidGlassProgress(
            mode = mode,
            strength = normalizeLiquidGlassStrength(strength)
        )
    )
}

internal fun resolveLiquidGlassTuning(style: LiquidGlassStyle): LiquidGlassTuning {
    return when (style) {
        LiquidGlassStyle.SUKISU -> sukisuLiquidGlassTuning()
        else -> resolveLiquidGlassTuning(progress = resolveLegacyLiquidGlassProgress(style))
    }
}

private fun sukisuLiquidGlassTuning(): LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)

private fun midpointLerp(
    start: Float,
    midpoint: Float,
    stop: Float,
    fraction: Float
): Float = if (fraction <= 0.5f) {
    lerp(start, midpoint, fraction * 2f)
} else {
    lerp(midpoint, stop, (fraction - 0.5f) * 2f)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
