package com.android.purebilibili.core.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import top.yukonga.miuix.kmp.theme.Colors

internal const val APP_THEME_COLOR_TRANSITION_DURATION_MS = 240

/** Resolved endpoint palettes: no seed generation or contrast correction runs per animation frame. */
internal data class AppThemePalette(
    val material: ColorScheme,
    val miuix: Colors,
    val liquidAccent: Color,
)

/**
 * Single-clock palette transition driven by one normalized [Animatable] instead of 100+
 * independent color transition states. Idle themes hold zero active animation nodes and
 * return the exact target palette; mid-flight target changes smoothly retarget from the
 * currently displayed frame.
 */
@Composable
internal fun rememberAnimatedAppThemePalette(
    target: AppThemePalette,
    enabled: Boolean,
): AppThemePalette {
    // Disabling motion must remove the transition and its target-state writes too.
    // Running a snap transition still feeds fresh palette identities back into the
    // theme composition, even though the caller only displays the static endpoint.
    if (!enabled) return target

    var from by remember { mutableStateOf(target) }
    var activeTarget by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (isSameAppThemePalette(activeTarget, target)) {
            activeTarget = target
            if (progress.value >= 1f) {
                from = target
                return@LaunchedEffect
            }
            val remainingMs = ((1f - progress.value) * APP_THEME_COLOR_TRANSITION_DURATION_MS)
                .toInt()
                .coerceAtLeast(1)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(remainingMs, easing = AppMotionEasing.M1Standard),
            )
            from = target
            return@LaunchedEffect
        }
        val currentFrame = if (progress.value >= 1f) {
            activeTarget
        } else {
            lerpAppThemePalette(from, activeTarget, progress.value)
        }
        from = currentFrame
        activeTarget = target
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(APP_THEME_COLOR_TRANSITION_DURATION_MS, easing = AppMotionEasing.M1Standard),
        )
        from = target
    }

    val fraction = progress.value
    return if (fraction >= 1f || isSameAppThemePalette(from, activeTarget)) {
        activeTarget
    } else {
        lerpAppThemePalette(from, activeTarget, fraction)
    }
}

private inline fun lerpColorFast(start: Color, stop: Color, fraction: Float): Color =
    if (start == stop) start else lerp(start, stop, fraction)

private fun lerpAppThemePalette(
    start: AppThemePalette,
    stop: AppThemePalette,
    fraction: Float,
): AppThemePalette = AppThemePalette(
    material = lerpColorScheme(start.material, stop.material, fraction),
    miuix = lerpMiuixColors(start.miuix, stop.miuix, fraction),
    liquidAccent = lerpColorFast(start.liquidAccent, stop.liquidAccent, fraction),
)

private fun lerpColorScheme(
    start: ColorScheme,
    stop: ColorScheme,
    fraction: Float,
): ColorScheme = stop.copy(
    primary = lerpColorFast(start.primary, stop.primary, fraction),
    onPrimary = lerpColorFast(start.onPrimary, stop.onPrimary, fraction),
    primaryContainer = lerpColorFast(start.primaryContainer, stop.primaryContainer, fraction),
    onPrimaryContainer = lerpColorFast(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
    inversePrimary = lerpColorFast(start.inversePrimary, stop.inversePrimary, fraction),
    secondary = lerpColorFast(start.secondary, stop.secondary, fraction),
    onSecondary = lerpColorFast(start.onSecondary, stop.onSecondary, fraction),
    secondaryContainer = lerpColorFast(start.secondaryContainer, stop.secondaryContainer, fraction),
    onSecondaryContainer = lerpColorFast(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
    tertiary = lerpColorFast(start.tertiary, stop.tertiary, fraction),
    onTertiary = lerpColorFast(start.onTertiary, stop.onTertiary, fraction),
    tertiaryContainer = lerpColorFast(start.tertiaryContainer, stop.tertiaryContainer, fraction),
    onTertiaryContainer = lerpColorFast(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
    background = lerpColorFast(start.background, stop.background, fraction),
    onBackground = lerpColorFast(start.onBackground, stop.onBackground, fraction),
    surface = lerpColorFast(start.surface, stop.surface, fraction),
    onSurface = lerpColorFast(start.onSurface, stop.onSurface, fraction),
    surfaceVariant = lerpColorFast(start.surfaceVariant, stop.surfaceVariant, fraction),
    onSurfaceVariant = lerpColorFast(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
    surfaceTint = lerpColorFast(start.surfaceTint, stop.surfaceTint, fraction),
    inverseSurface = lerpColorFast(start.inverseSurface, stop.inverseSurface, fraction),
    inverseOnSurface = lerpColorFast(start.inverseOnSurface, stop.inverseOnSurface, fraction),
    error = lerpColorFast(start.error, stop.error, fraction),
    onError = lerpColorFast(start.onError, stop.onError, fraction),
    errorContainer = lerpColorFast(start.errorContainer, stop.errorContainer, fraction),
    onErrorContainer = lerpColorFast(start.onErrorContainer, stop.onErrorContainer, fraction),
    outline = lerpColorFast(start.outline, stop.outline, fraction),
    outlineVariant = lerpColorFast(start.outlineVariant, stop.outlineVariant, fraction),
    scrim = lerpColorFast(start.scrim, stop.scrim, fraction),
    surfaceBright = lerpColorFast(start.surfaceBright, stop.surfaceBright, fraction),
    surfaceDim = lerpColorFast(start.surfaceDim, stop.surfaceDim, fraction),
    surfaceContainer = lerpColorFast(start.surfaceContainer, stop.surfaceContainer, fraction),
    surfaceContainerHigh = lerpColorFast(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
    surfaceContainerHighest = lerpColorFast(start.surfaceContainerHighest, stop.surfaceContainerHighest, fraction),
    surfaceContainerLow = lerpColorFast(start.surfaceContainerLow, stop.surfaceContainerLow, fraction),
    surfaceContainerLowest = lerpColorFast(start.surfaceContainerLowest, stop.surfaceContainerLowest, fraction),
    primaryFixed = lerpColorFast(start.primaryFixed, stop.primaryFixed, fraction),
    primaryFixedDim = lerpColorFast(start.primaryFixedDim, stop.primaryFixedDim, fraction),
    onPrimaryFixed = lerpColorFast(start.onPrimaryFixed, stop.onPrimaryFixed, fraction),
    onPrimaryFixedVariant = lerpColorFast(start.onPrimaryFixedVariant, stop.onPrimaryFixedVariant, fraction),
    secondaryFixed = lerpColorFast(start.secondaryFixed, stop.secondaryFixed, fraction),
    secondaryFixedDim = lerpColorFast(start.secondaryFixedDim, stop.secondaryFixedDim, fraction),
    onSecondaryFixed = lerpColorFast(start.onSecondaryFixed, stop.onSecondaryFixed, fraction),
    onSecondaryFixedVariant = lerpColorFast(start.onSecondaryFixedVariant, stop.onSecondaryFixedVariant, fraction),
    tertiaryFixed = lerpColorFast(start.tertiaryFixed, stop.tertiaryFixed, fraction),
    tertiaryFixedDim = lerpColorFast(start.tertiaryFixedDim, stop.tertiaryFixedDim, fraction),
    onTertiaryFixed = lerpColorFast(start.onTertiaryFixed, stop.onTertiaryFixed, fraction),
    onTertiaryFixedVariant = lerpColorFast(start.onTertiaryFixedVariant, stop.onTertiaryFixedVariant, fraction),
)

private fun lerpMiuixColors(
    start: Colors,
    stop: Colors,
    fraction: Float,
): Colors = stop.copy(
    primary = lerpColorFast(start.primary, stop.primary, fraction),
    onPrimary = lerpColorFast(start.onPrimary, stop.onPrimary, fraction),
    primaryVariant = lerpColorFast(start.primaryVariant, stop.primaryVariant, fraction),
    onPrimaryVariant = lerpColorFast(start.onPrimaryVariant, stop.onPrimaryVariant, fraction),
    error = lerpColorFast(start.error, stop.error, fraction),
    onError = lerpColorFast(start.onError, stop.onError, fraction),
    errorContainer = lerpColorFast(start.errorContainer, stop.errorContainer, fraction),
    onErrorContainer = lerpColorFast(start.onErrorContainer, stop.onErrorContainer, fraction),
    disabledPrimary = lerpColorFast(start.disabledPrimary, stop.disabledPrimary, fraction),
    disabledOnPrimary = lerpColorFast(start.disabledOnPrimary, stop.disabledOnPrimary, fraction),
    disabledPrimaryButton = lerpColorFast(start.disabledPrimaryButton, stop.disabledPrimaryButton, fraction),
    disabledOnPrimaryButton = lerpColorFast(start.disabledOnPrimaryButton, stop.disabledOnPrimaryButton, fraction),
    disabledPrimarySlider = lerpColorFast(start.disabledPrimarySlider, stop.disabledPrimarySlider, fraction),
    primaryContainer = lerpColorFast(start.primaryContainer, stop.primaryContainer, fraction),
    onPrimaryContainer = lerpColorFast(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
    secondary = lerpColorFast(start.secondary, stop.secondary, fraction),
    onSecondary = lerpColorFast(start.onSecondary, stop.onSecondary, fraction),
    secondaryVariant = lerpColorFast(start.secondaryVariant, stop.secondaryVariant, fraction),
    onSecondaryVariant = lerpColorFast(start.onSecondaryVariant, stop.onSecondaryVariant, fraction),
    disabledSecondary = lerpColorFast(start.disabledSecondary, stop.disabledSecondary, fraction),
    disabledOnSecondary = lerpColorFast(start.disabledOnSecondary, stop.disabledOnSecondary, fraction),
    disabledSecondaryVariant = lerpColorFast(start.disabledSecondaryVariant, stop.disabledSecondaryVariant, fraction),
    disabledOnSecondaryVariant = lerpColorFast(start.disabledOnSecondaryVariant, stop.disabledOnSecondaryVariant, fraction),
    secondaryContainer = lerpColorFast(start.secondaryContainer, stop.secondaryContainer, fraction),
    onSecondaryContainer = lerpColorFast(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
    secondaryContainerVariant = lerpColorFast(start.secondaryContainerVariant, stop.secondaryContainerVariant, fraction),
    onSecondaryContainerVariant = lerpColorFast(start.onSecondaryContainerVariant, stop.onSecondaryContainerVariant, fraction),
    tertiaryContainer = lerpColorFast(start.tertiaryContainer, stop.tertiaryContainer, fraction),
    onTertiaryContainer = lerpColorFast(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
    tertiaryContainerVariant = lerpColorFast(start.tertiaryContainerVariant, stop.tertiaryContainerVariant, fraction),
    background = lerpColorFast(start.background, stop.background, fraction),
    onBackground = lerpColorFast(start.onBackground, stop.onBackground, fraction),
    onBackgroundVariant = lerpColorFast(start.onBackgroundVariant, stop.onBackgroundVariant, fraction),
    surface = lerpColorFast(start.surface, stop.surface, fraction),
    onSurface = lerpColorFast(start.onSurface, stop.onSurface, fraction),
    surfaceVariant = lerpColorFast(start.surfaceVariant, stop.surfaceVariant, fraction),
    onSurfaceSecondary = lerpColorFast(start.onSurfaceSecondary, stop.onSurfaceSecondary, fraction),
    onSurfaceVariantSummary = lerpColorFast(start.onSurfaceVariantSummary, stop.onSurfaceVariantSummary, fraction),
    onSurfaceVariantActions = lerpColorFast(start.onSurfaceVariantActions, stop.onSurfaceVariantActions, fraction),
    disabledOnSurface = lerpColorFast(start.disabledOnSurface, stop.disabledOnSurface, fraction),
    surfaceContainer = lerpColorFast(start.surfaceContainer, stop.surfaceContainer, fraction),
    onSurfaceContainer = lerpColorFast(start.onSurfaceContainer, stop.onSurfaceContainer, fraction),
    onSurfaceContainerVariant = lerpColorFast(start.onSurfaceContainerVariant, stop.onSurfaceContainerVariant, fraction),
    surfaceContainerHigh = lerpColorFast(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
    onSurfaceContainerHigh = lerpColorFast(start.onSurfaceContainerHigh, stop.onSurfaceContainerHigh, fraction),
    surfaceContainerHighest = lerpColorFast(start.surfaceContainerHighest, stop.surfaceContainerHighest, fraction),
    onSurfaceContainerHighest = lerpColorFast(start.onSurfaceContainerHighest, stop.onSurfaceContainerHighest, fraction),
    outline = lerpColorFast(start.outline, stop.outline, fraction),
    dividerLine = lerpColorFast(start.dividerLine, stop.dividerLine, fraction),
    windowDimming = lerpColorFast(start.windowDimming, stop.windowDimming, fraction),
    sliderKeyPoint = lerpColorFast(start.sliderKeyPoint, stop.sliderKeyPoint, fraction),
    sliderKeyPointForeground = lerpColorFast(start.sliderKeyPointForeground, stop.sliderKeyPointForeground, fraction),
    sliderBackground = lerpColorFast(start.sliderBackground, stop.sliderBackground, fraction),
)

private fun isSameAppThemePalette(a: AppThemePalette, b: AppThemePalette): Boolean {
    if (a === b) return true
    return a.liquidAccent == b.liquidAccent &&
        isSameColorScheme(a.material, b.material) &&
        isSameMiuixColors(a.miuix, b.miuix)
}

private fun isSameColorScheme(a: ColorScheme, b: ColorScheme): Boolean {
    if (a === b) return true
    return a.primary == b.primary &&
        a.onPrimary == b.onPrimary &&
        a.primaryContainer == b.primaryContainer &&
        a.onPrimaryContainer == b.onPrimaryContainer &&
        a.inversePrimary == b.inversePrimary &&
        a.secondary == b.secondary &&
        a.onSecondary == b.onSecondary &&
        a.secondaryContainer == b.secondaryContainer &&
        a.onSecondaryContainer == b.onSecondaryContainer &&
        a.tertiary == b.tertiary &&
        a.onTertiary == b.onTertiary &&
        a.tertiaryContainer == b.tertiaryContainer &&
        a.onTertiaryContainer == b.onTertiaryContainer &&
        a.background == b.background &&
        a.onBackground == b.onBackground &&
        a.surface == b.surface &&
        a.onSurface == b.onSurface &&
        a.surfaceVariant == b.surfaceVariant &&
        a.onSurfaceVariant == b.onSurfaceVariant &&
        a.surfaceTint == b.surfaceTint &&
        a.inverseSurface == b.inverseSurface &&
        a.inverseOnSurface == b.inverseOnSurface &&
        a.error == b.error &&
        a.onError == b.onError &&
        a.errorContainer == b.errorContainer &&
        a.onErrorContainer == b.onErrorContainer &&
        a.outline == b.outline &&
        a.outlineVariant == b.outlineVariant &&
        a.scrim == b.scrim &&
        a.surfaceBright == b.surfaceBright &&
        a.surfaceDim == b.surfaceDim &&
        a.surfaceContainer == b.surfaceContainer &&
        a.surfaceContainerHigh == b.surfaceContainerHigh &&
        a.surfaceContainerHighest == b.surfaceContainerHighest &&
        a.surfaceContainerLow == b.surfaceContainerLow &&
        a.surfaceContainerLowest == b.surfaceContainerLowest &&
        a.primaryFixed == b.primaryFixed &&
        a.primaryFixedDim == b.primaryFixedDim &&
        a.onPrimaryFixed == b.onPrimaryFixed &&
        a.onPrimaryFixedVariant == b.onPrimaryFixedVariant &&
        a.secondaryFixed == b.secondaryFixed &&
        a.secondaryFixedDim == b.secondaryFixedDim &&
        a.onSecondaryFixed == b.onSecondaryFixed &&
        a.onSecondaryFixedVariant == b.onSecondaryFixedVariant &&
        a.tertiaryFixed == b.tertiaryFixed &&
        a.tertiaryFixedDim == b.tertiaryFixedDim &&
        a.onTertiaryFixed == b.onTertiaryFixed &&
        a.onTertiaryFixedVariant == b.onTertiaryFixedVariant
}

private fun isSameMiuixColors(a: Colors, b: Colors): Boolean {
    if (a === b) return true
    return a.primary == b.primary &&
        a.onPrimary == b.onPrimary &&
        a.primaryVariant == b.primaryVariant &&
        a.onPrimaryVariant == b.onPrimaryVariant &&
        a.error == b.error &&
        a.onError == b.onError &&
        a.errorContainer == b.errorContainer &&
        a.onErrorContainer == b.onErrorContainer &&
        a.disabledPrimary == b.disabledPrimary &&
        a.disabledOnPrimary == b.disabledOnPrimary &&
        a.disabledPrimaryButton == b.disabledPrimaryButton &&
        a.disabledOnPrimaryButton == b.disabledOnPrimaryButton &&
        a.disabledPrimarySlider == b.disabledPrimarySlider &&
        a.primaryContainer == b.primaryContainer &&
        a.onPrimaryContainer == b.onPrimaryContainer &&
        a.secondary == b.secondary &&
        a.onSecondary == b.onSecondary &&
        a.secondaryVariant == b.secondaryVariant &&
        a.onSecondaryVariant == b.onSecondaryVariant &&
        a.disabledSecondary == b.disabledSecondary &&
        a.disabledOnSecondary == b.disabledOnSecondary &&
        a.disabledSecondaryVariant == b.disabledSecondaryVariant &&
        a.disabledOnSecondaryVariant == b.disabledOnSecondaryVariant &&
        a.secondaryContainer == b.secondaryContainer &&
        a.onSecondaryContainer == b.onSecondaryContainer &&
        a.secondaryContainerVariant == b.secondaryContainerVariant &&
        a.onSecondaryContainerVariant == b.onSecondaryContainerVariant &&
        a.tertiaryContainer == b.tertiaryContainer &&
        a.onTertiaryContainer == b.onTertiaryContainer &&
        a.tertiaryContainerVariant == b.tertiaryContainerVariant &&
        a.background == b.background &&
        a.onBackground == b.onBackground &&
        a.onBackgroundVariant == b.onBackgroundVariant &&
        a.surface == b.surface &&
        a.onSurface == b.onSurface &&
        a.surfaceVariant == b.surfaceVariant &&
        a.onSurfaceSecondary == b.onSurfaceSecondary &&
        a.onSurfaceVariantSummary == b.onSurfaceVariantSummary &&
        a.onSurfaceVariantActions == b.onSurfaceVariantActions &&
        a.disabledOnSurface == b.disabledOnSurface &&
        a.surfaceContainer == b.surfaceContainer &&
        a.onSurfaceContainer == b.onSurfaceContainer &&
        a.onSurfaceContainerVariant == b.onSurfaceContainerVariant &&
        a.surfaceContainerHigh == b.surfaceContainerHigh &&
        a.onSurfaceContainerHigh == b.onSurfaceContainerHigh &&
        a.surfaceContainerHighest == b.surfaceContainerHighest &&
        a.onSurfaceContainerHighest == b.onSurfaceContainerHighest &&
        a.outline == b.outline &&
        a.dividerLine == b.dividerLine &&
        a.windowDimming == b.windowDimming &&
        a.sliderKeyPoint == b.sliderKeyPoint &&
        a.sliderKeyPointForeground == b.sliderKeyPointForeground &&
        a.sliderBackground == b.sliderBackground
}
