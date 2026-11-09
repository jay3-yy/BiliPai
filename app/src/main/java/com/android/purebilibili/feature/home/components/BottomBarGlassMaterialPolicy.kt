package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset

internal data class BottomBarInnerRimGlowSpec(
    val radiusDp: Float,
    val alpha: Float
)

internal data class BottomBarShellShaderSpec(
    val thicknessDp: Float,
    val refractIndex: Float,
    val refractIntensity: Float
)

internal data class BottomBarGlassMaterialSpec(
    val blurRadiusDp: Float?,
    val vibrancy: Boolean,
    val shellRefractionHeightDp: Float,
    val shellRefractionAmountDp: Float,
    val shellChromaticAberration: Float,
    val foregroundTint: Color,
    val highlightWidthScale: Float,
    val shadowAlphaScale: Float,
    val innerRimGlow: BottomBarInnerRimGlowSpec?,
    val shellShader: BottomBarShellShaderSpec?
)

internal fun resolveBottomBarGlassMaterialSpec(
    preset: BottomBarLiquidGlassPreset,
    isDarkTheme: Boolean,
    isScrolling: Boolean,
    scrollProgress: Float = if (isScrolling) 1f else 0f,
    glassEnabled: Boolean,
    motionProgress: Float,
    pressProgress: Float,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
): BottomBarGlassMaterialSpec {
    if (!glassEnabled) {
        return BottomBarGlassMaterialSpec(
            blurRadiusDp = null,
            vibrancy = false,
            shellRefractionHeightDp = 0f,
            shellRefractionAmountDp = 0f,
            shellChromaticAberration = 0f,
            foregroundTint = Color.Transparent,
            highlightWidthScale = 1f,
            shadowAlphaScale = 1f,
            innerRimGlow = null,
            shellShader = null
        )
    }
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED ->
            biliPaiTunedBottomBarGlassMaterial(liquidGlassTuning)
        BottomBarLiquidGlassPreset.IOS26_REFINED -> ios26BottomBarGlassMaterial(
            motionProgress = motionProgress,
            pressProgress = pressProgress,
            scrollProgress = scrollProgress,
            liquidGlassTuning = liquidGlassTuning
        )
    }
}

internal fun resolveBottomBarGlassMaterialContainerColor(
    surfaceColor: Color,
    preset: BottomBarLiquidGlassPreset,
    glassEnabled: Boolean,
    fallbackAlpha: Float,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
): Color {
    if (!glassEnabled) return surfaceColor.copy(alpha = fallbackAlpha)
    // Upstream uses the theme surface at 40% in the balanced material; keep the slider's
    // requested opacity without applying another preset-specific reduction.
    return surfaceColor.copy(alpha = liquidGlassTuning.surfaceAlpha.coerceIn(0f, 1f))
}

private fun biliPaiTunedBottomBarGlassMaterial(
    tuning: LiquidGlassTuning
): BottomBarGlassMaterialSpec =
    BottomBarGlassMaterialSpec(
        blurRadiusDp = tuning.backdropBlurRadius,
        vibrancy = true,
        shellRefractionHeightDp = tuning.refractionHeight * tuning.contentDistortionScale,
        shellRefractionAmountDp = tuning.refractionAmount * tuning.contentDistortionScale,
        shellChromaticAberration = tuning.shellChromaticAberrationAmount,
        foregroundTint = Color.Transparent,
        highlightWidthScale = 1f,
        shadowAlphaScale = 1f,
        innerRimGlow = null,
        shellShader = null
    )

internal fun resolveBottomBarMaterialScrollAnimationDurationMillis(
    isScrolling: Boolean
): Int = if (isScrolling) 140 else 420

private fun ios26BottomBarGlassMaterial(
    motionProgress: Float,
    pressProgress: Float,
    scrollProgress: Float,
    liquidGlassTuning: LiquidGlassTuning
): BottomBarGlassMaterialSpec {
    // iOS26 在底栏实际走 Miuix/BiliPai drawBackdrop 链；这条链不消费 shellShader。
    // 因此壳层主材质必须使用 BiliPai 可见的 vibrancy + blur + lens 组合，否则只剩淡底色和弱内圈。
    val scrollLift = scrollProgress.coerceIn(0f, 1f)
    return BottomBarGlassMaterialSpec(
        blurRadiusDp = liquidGlassTuning.backdropBlurRadius,
        vibrancy = true,
        shellRefractionHeightDp = liquidGlassTuning.refractionHeight *
            liquidGlassTuning.contentDistortionScale,
        shellRefractionAmountDp = liquidGlassTuning.refractionAmount *
            liquidGlassTuning.contentDistortionScale,
        shellChromaticAberration = 0f,
        foregroundTint = Color.Transparent,
        highlightWidthScale = 1f,
        shadowAlphaScale = 1f,
        innerRimGlow = BottomBarInnerRimGlowSpec(
            radiusDp = 5f,
            alpha = lerp(0.09f, 0.16f, scrollLift)
        ),
        shellShader = null
    )
}

internal data class LiquidGlassShaderUniforms(
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val cornerRadiusPx: Float,
    val thicknessPx: Float,
    val refractIndex: Float,
    val refractIntensity: Float,
    val resolutionX: Float,
    val resolutionY: Float
)

internal fun resolveLiquidGlassShaderUniforms(
    widthPx: Float,
    heightPx: Float,
    paddingPx: Float,
    cornerRadiusPx: Float,
    thicknessPx: Float,
    refractIndex: Float,
    refractIntensity: Float,
    intensityScale: Float
): LiquidGlassShaderUniforms {
    val halfWidth = widthPx / 2f
    val halfHeight = heightPx / 2f
    val maxRadius = minOf(halfWidth, halfHeight)
    return LiquidGlassShaderUniforms(
        centerX = paddingPx + halfWidth,
        centerY = paddingPx + halfHeight,
        halfWidth = halfWidth,
        halfHeight = halfHeight,
        cornerRadiusPx = cornerRadiusPx.coerceIn(0f, maxRadius),
        thicknessPx = thicknessPx,
        refractIndex = refractIndex,
        refractIntensity = (refractIntensity * intensityScale).coerceAtLeast(0f),
        resolutionX = widthPx + paddingPx * 2f,
        resolutionY = heightPx + paddingPx * 2f
    )
}
