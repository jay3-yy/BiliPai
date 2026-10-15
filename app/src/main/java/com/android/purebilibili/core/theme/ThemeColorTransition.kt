package com.android.purebilibili.core.theme

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors

internal const val APP_THEME_COLOR_TRANSITION_DURATION_MS = 240

/** Resolved endpoint palettes: no seed generation or contrast correction runs per animation frame. */
internal data class AppThemePalette(
    val material: ColorScheme,
    val miuix: Colors,
    val liquidAccent: Color,
)

/**
 * One clock owns all Material, native Miuix and exact glass-accent roles. Each child color
 * retargets from its current value when the user reverses a switch. Initial composition
 * already uses the selected palette; disabling motion exposes the exact endpoint immediately.
 * This changes colors only: both glass and non-glass keep their own layout and final palette.
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

    val transitionState = remember { MutableTransitionState(target) }
    SideEffect { transitionState.targetState = target }
    val transition = rememberTransition(transitionState, label = "appThemePalette")
    val spec: FiniteAnimationSpec<Color> =
        tween(APP_THEME_COLOR_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
    val material = transition.animatedMaterialColors(spec)
    val miuix = transition.animatedMiuixColors(spec)
    val liquidAccent = transition.themeColor("liquidAccent", spec) { it.liquidAccent }
    return AppThemePalette(material, miuix, liquidAccent)
}

@Composable
private fun Transition<AppThemePalette>.themeColor(
    role: String,
    spec: FiniteAnimationSpec<Color>,
    value: @Composable (AppThemePalette) -> Color,
): Color = animateColor(transitionSpec = { spec }, label = role, targetValueByState = value).value

@Composable
private fun Transition<AppThemePalette>.animatedMaterialColors(
    spec: FiniteAnimationSpec<Color>,
): ColorScheme = targetState.material.copy(
    primary = themeColor("material.primary", spec) { it.material.primary },
    onPrimary = themeColor("material.onPrimary", spec) { it.material.onPrimary },
    primaryContainer = themeColor("material.primaryContainer", spec) { it.material.primaryContainer },
    onPrimaryContainer = themeColor("material.onPrimaryContainer", spec) { it.material.onPrimaryContainer },
    inversePrimary = themeColor("material.inversePrimary", spec) { it.material.inversePrimary },
    secondary = themeColor("material.secondary", spec) { it.material.secondary },
    onSecondary = themeColor("material.onSecondary", spec) { it.material.onSecondary },
    secondaryContainer = themeColor("material.secondaryContainer", spec) { it.material.secondaryContainer },
    onSecondaryContainer = themeColor("material.onSecondaryContainer", spec) { it.material.onSecondaryContainer },
    tertiary = themeColor("material.tertiary", spec) { it.material.tertiary },
    onTertiary = themeColor("material.onTertiary", spec) { it.material.onTertiary },
    tertiaryContainer = themeColor("material.tertiaryContainer", spec) { it.material.tertiaryContainer },
    onTertiaryContainer = themeColor("material.onTertiaryContainer", spec) { it.material.onTertiaryContainer },
    background = themeColor("material.background", spec) { it.material.background },
    onBackground = themeColor("material.onBackground", spec) { it.material.onBackground },
    surface = themeColor("material.surface", spec) { it.material.surface },
    onSurface = themeColor("material.onSurface", spec) { it.material.onSurface },
    surfaceVariant = themeColor("material.surfaceVariant", spec) { it.material.surfaceVariant },
    onSurfaceVariant = themeColor("material.onSurfaceVariant", spec) { it.material.onSurfaceVariant },
    surfaceTint = themeColor("material.surfaceTint", spec) { it.material.surfaceTint },
    inverseSurface = themeColor("material.inverseSurface", spec) { it.material.inverseSurface },
    inverseOnSurface = themeColor("material.inverseOnSurface", spec) { it.material.inverseOnSurface },
    error = themeColor("material.error", spec) { it.material.error },
    onError = themeColor("material.onError", spec) { it.material.onError },
    errorContainer = themeColor("material.errorContainer", spec) { it.material.errorContainer },
    onErrorContainer = themeColor("material.onErrorContainer", spec) { it.material.onErrorContainer },
    outline = themeColor("material.outline", spec) { it.material.outline },
    outlineVariant = themeColor("material.outlineVariant", spec) { it.material.outlineVariant },
    scrim = themeColor("material.scrim", spec) { it.material.scrim },
    surfaceBright = themeColor("material.surfaceBright", spec) { it.material.surfaceBright },
    surfaceDim = themeColor("material.surfaceDim", spec) { it.material.surfaceDim },
    surfaceContainer = themeColor("material.surfaceContainer", spec) { it.material.surfaceContainer },
    surfaceContainerHigh = themeColor("material.surfaceContainerHigh", spec) { it.material.surfaceContainerHigh },
    surfaceContainerHighest = themeColor("material.surfaceContainerHighest", spec) { it.material.surfaceContainerHighest },
    surfaceContainerLow = themeColor("material.surfaceContainerLow", spec) { it.material.surfaceContainerLow },
    surfaceContainerLowest = themeColor("material.surfaceContainerLowest", spec) { it.material.surfaceContainerLowest },
    primaryFixed = themeColor("material.primaryFixed", spec) { it.material.primaryFixed },
    primaryFixedDim = themeColor("material.primaryFixedDim", spec) { it.material.primaryFixedDim },
    onPrimaryFixed = themeColor("material.onPrimaryFixed", spec) { it.material.onPrimaryFixed },
    onPrimaryFixedVariant = themeColor("material.onPrimaryFixedVariant", spec) { it.material.onPrimaryFixedVariant },
    secondaryFixed = themeColor("material.secondaryFixed", spec) { it.material.secondaryFixed },
    secondaryFixedDim = themeColor("material.secondaryFixedDim", spec) { it.material.secondaryFixedDim },
    onSecondaryFixed = themeColor("material.onSecondaryFixed", spec) { it.material.onSecondaryFixed },
    onSecondaryFixedVariant = themeColor("material.onSecondaryFixedVariant", spec) { it.material.onSecondaryFixedVariant },
    tertiaryFixed = themeColor("material.tertiaryFixed", spec) { it.material.tertiaryFixed },
    tertiaryFixedDim = themeColor("material.tertiaryFixedDim", spec) { it.material.tertiaryFixedDim },
    onTertiaryFixed = themeColor("material.onTertiaryFixed", spec) { it.material.onTertiaryFixed },
    onTertiaryFixedVariant = themeColor("material.onTertiaryFixedVariant", spec) { it.material.onTertiaryFixedVariant },
)

@Composable
private fun Transition<AppThemePalette>.animatedMiuixColors(
    spec: FiniteAnimationSpec<Color>,
): Colors = targetState.miuix.copy(
    primary = themeColor("miuix.primary", spec) { it.miuix.primary },
    onPrimary = themeColor("miuix.onPrimary", spec) { it.miuix.onPrimary },
    primaryVariant = themeColor("miuix.primaryVariant", spec) { it.miuix.primaryVariant },
    onPrimaryVariant = themeColor("miuix.onPrimaryVariant", spec) { it.miuix.onPrimaryVariant },
    error = themeColor("miuix.error", spec) { it.miuix.error },
    onError = themeColor("miuix.onError", spec) { it.miuix.onError },
    errorContainer = themeColor("miuix.errorContainer", spec) { it.miuix.errorContainer },
    onErrorContainer = themeColor("miuix.onErrorContainer", spec) { it.miuix.onErrorContainer },
    disabledPrimary = themeColor("miuix.disabledPrimary", spec) { it.miuix.disabledPrimary },
    disabledOnPrimary = themeColor("miuix.disabledOnPrimary", spec) { it.miuix.disabledOnPrimary },
    disabledPrimaryButton = themeColor("miuix.disabledPrimaryButton", spec) { it.miuix.disabledPrimaryButton },
    disabledOnPrimaryButton = themeColor("miuix.disabledOnPrimaryButton", spec) { it.miuix.disabledOnPrimaryButton },
    disabledPrimarySlider = themeColor("miuix.disabledPrimarySlider", spec) { it.miuix.disabledPrimarySlider },
    primaryContainer = themeColor("miuix.primaryContainer", spec) { it.miuix.primaryContainer },
    onPrimaryContainer = themeColor("miuix.onPrimaryContainer", spec) { it.miuix.onPrimaryContainer },
    secondary = themeColor("miuix.secondary", spec) { it.miuix.secondary },
    onSecondary = themeColor("miuix.onSecondary", spec) { it.miuix.onSecondary },
    secondaryVariant = themeColor("miuix.secondaryVariant", spec) { it.miuix.secondaryVariant },
    onSecondaryVariant = themeColor("miuix.onSecondaryVariant", spec) { it.miuix.onSecondaryVariant },
    disabledSecondary = themeColor("miuix.disabledSecondary", spec) { it.miuix.disabledSecondary },
    disabledOnSecondary = themeColor("miuix.disabledOnSecondary", spec) { it.miuix.disabledOnSecondary },
    disabledSecondaryVariant = themeColor("miuix.disabledSecondaryVariant", spec) { it.miuix.disabledSecondaryVariant },
    disabledOnSecondaryVariant = themeColor("miuix.disabledOnSecondaryVariant", spec) { it.miuix.disabledOnSecondaryVariant },
    secondaryContainer = themeColor("miuix.secondaryContainer", spec) { it.miuix.secondaryContainer },
    onSecondaryContainer = themeColor("miuix.onSecondaryContainer", spec) { it.miuix.onSecondaryContainer },
    secondaryContainerVariant = themeColor("miuix.secondaryContainerVariant", spec) { it.miuix.secondaryContainerVariant },
    onSecondaryContainerVariant = themeColor("miuix.onSecondaryContainerVariant", spec) { it.miuix.onSecondaryContainerVariant },
    tertiaryContainer = themeColor("miuix.tertiaryContainer", spec) { it.miuix.tertiaryContainer },
    onTertiaryContainer = themeColor("miuix.onTertiaryContainer", spec) { it.miuix.onTertiaryContainer },
    tertiaryContainerVariant = themeColor("miuix.tertiaryContainerVariant", spec) { it.miuix.tertiaryContainerVariant },
    background = themeColor("miuix.background", spec) { it.miuix.background },
    onBackground = themeColor("miuix.onBackground", spec) { it.miuix.onBackground },
    onBackgroundVariant = themeColor("miuix.onBackgroundVariant", spec) { it.miuix.onBackgroundVariant },
    surface = themeColor("miuix.surface", spec) { it.miuix.surface },
    onSurface = themeColor("miuix.onSurface", spec) { it.miuix.onSurface },
    surfaceVariant = themeColor("miuix.surfaceVariant", spec) { it.miuix.surfaceVariant },
    onSurfaceSecondary = themeColor("miuix.onSurfaceSecondary", spec) { it.miuix.onSurfaceSecondary },
    onSurfaceVariantSummary = themeColor("miuix.onSurfaceVariantSummary", spec) { it.miuix.onSurfaceVariantSummary },
    onSurfaceVariantActions = themeColor("miuix.onSurfaceVariantActions", spec) { it.miuix.onSurfaceVariantActions },
    disabledOnSurface = themeColor("miuix.disabledOnSurface", spec) { it.miuix.disabledOnSurface },
    surfaceContainer = themeColor("miuix.surfaceContainer", spec) { it.miuix.surfaceContainer },
    onSurfaceContainer = themeColor("miuix.onSurfaceContainer", spec) { it.miuix.onSurfaceContainer },
    onSurfaceContainerVariant = themeColor("miuix.onSurfaceContainerVariant", spec) { it.miuix.onSurfaceContainerVariant },
    surfaceContainerHigh = themeColor("miuix.surfaceContainerHigh", spec) { it.miuix.surfaceContainerHigh },
    onSurfaceContainerHigh = themeColor("miuix.onSurfaceContainerHigh", spec) { it.miuix.onSurfaceContainerHigh },
    surfaceContainerHighest = themeColor("miuix.surfaceContainerHighest", spec) { it.miuix.surfaceContainerHighest },
    onSurfaceContainerHighest = themeColor("miuix.onSurfaceContainerHighest", spec) { it.miuix.onSurfaceContainerHighest },
    outline = themeColor("miuix.outline", spec) { it.miuix.outline },
    dividerLine = themeColor("miuix.dividerLine", spec) { it.miuix.dividerLine },
    windowDimming = themeColor("miuix.windowDimming", spec) { it.miuix.windowDimming },
    sliderKeyPoint = themeColor("miuix.sliderKeyPoint", spec) { it.miuix.sliderKeyPoint },
    sliderKeyPointForeground = themeColor("miuix.sliderKeyPointForeground", spec) { it.miuix.sliderKeyPointForeground },
    sliderBackground = themeColor("miuix.sliderBackground", spec) { it.miuix.sliderBackground },
)
