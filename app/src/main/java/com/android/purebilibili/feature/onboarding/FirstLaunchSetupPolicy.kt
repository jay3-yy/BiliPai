package com.android.purebilibili.feature.onboarding

import android.os.Build
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.feature.settings.AppThemeMode

enum class FirstLaunchSetupStep {
    THEME_STYLE,
    APPEARANCE_COLOR,
    LIQUID_GLASS,
    HOME_LAYOUT,
}

data class FirstLaunchSetupDraft(
    val uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = false,
    val themeColorIndex: Int = 0,
    val liquidGlassEnabled: Boolean = true,
    /** 0 = 多列网格, 1 = 单列大图（与 DisplayMode.value 一致）。 */
    val displayMode: Int = 0,
    val bottomBarFloating: Boolean = true,
    /** 0 = 图标+文字, 1 = 仅图标, 2 = 仅文字。 */
    val bottomBarLabelMode: Int = 0,
)

/** 精选 6 个代表性主题强调色索引（经典蓝、樱花粉、天空蓝、薄荷绿、梦幻紫、活力橙）。 */
internal val FIRST_LAUNCH_ACCENT_PRESET_INDICES = listOf(0, 1, 2, 3, 4, 5)

/**
 * Resolves the ordered setup steps for the current Android version.
 * Liquid Glass is only offered on Android 13 (API 33) and above where runtime shaders
 * and backdrop refraction are supported.
 */
internal fun resolveFirstLaunchSetupSteps(sdkInt: Int): List<FirstLaunchSetupStep> = buildList {
    add(FirstLaunchSetupStep.THEME_STYLE)
    add(FirstLaunchSetupStep.APPEARANCE_COLOR)
    if (shouldAllowHomeChromeLiquidGlass(sdkInt)) {
        add(FirstLaunchSetupStep.LIQUID_GLASS)
    }
    add(FirstLaunchSetupStep.HOME_LAYOUT)
}

internal fun resolveNextFirstLaunchStep(
    current: FirstLaunchSetupStep,
    steps: List<FirstLaunchSetupStep>,
): FirstLaunchSetupStep? {
    val index = steps.indexOf(current)
    return if (index in 0 until steps.lastIndex) steps[index + 1] else null
}

internal fun resolvePreviousFirstLaunchStep(
    current: FirstLaunchSetupStep,
    steps: List<FirstLaunchSetupStep>,
): FirstLaunchSetupStep? {
    val index = steps.indexOf(current)
    return if (index > 0) steps[index - 1] else null
}

internal fun resolveInitialFirstLaunchSetupDraft(
    currentUiStyle: AppUiStyle,
    currentLiquidGlassEnabled: Boolean,
    sdkInt: Int,
    currentThemeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    currentDynamicColor: Boolean = false,
    currentThemeColorIndex: Int = 0,
    currentDisplayMode: Int = 0,
    currentBottomBarFloating: Boolean = true,
    currentBottomBarLabelMode: Int = 0,
): FirstLaunchSetupDraft = FirstLaunchSetupDraft(
    uiStyle = currentUiStyle,
    themeMode = currentThemeMode,
    dynamicColor = if (shouldOfferDynamicColorOption(sdkInt)) currentDynamicColor else false,
    themeColorIndex = currentThemeColorIndex.coerceAtLeast(0),
    liquidGlassEnabled = if (shouldAllowHomeChromeLiquidGlass(sdkInt)) currentLiquidGlassEnabled else false,
    displayMode = if (currentDisplayMode == 1) 1 else 0,
    bottomBarFloating = currentBottomBarFloating,
    bottomBarLabelMode = currentBottomBarLabelMode.coerceIn(0, 2),
)

internal fun shouldOfferDynamicColorOption(sdkInt: Int): Boolean =
    sdkInt >= Build.VERSION_CODES.S

internal fun shouldPersistLiquidGlassChoice(sdkInt: Int): Boolean =
    shouldAllowHomeChromeLiquidGlass(sdkInt)

internal fun resolveFirstLaunchSetupDarkTheme(
    themeMode: AppThemeMode,
    systemInDarkTheme: Boolean,
): Boolean = when (themeMode) {
    AppThemeMode.FOLLOW_SYSTEM -> systemInDarkTheme
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}

/**
 * Step 1 / 2 / 4 follow the selected UI style; Step 3 keeps the Miuix ambient backdrop when
 * Miuix is selected or Liquid Glass is actively enabled, so MD3 + native material stays in MD3.
 */
internal fun resolveFirstLaunchBackgroundUsesMiuixAmbient(
    uiStyle: AppUiStyle,
    currentStep: FirstLaunchSetupStep,
    liquidGlassEnabled: Boolean,
): Boolean = uiStyle == AppUiStyle.MIUIX ||
    (currentStep == FirstLaunchSetupStep.LIQUID_GLASS && liquidGlassEnabled)

/**
 * Only the selected Miuix device mockup runs the continuous ambient gradient loop; the full-screen
 * backdrop behind Haze stays static so Haze does not re-blur every frame.
 */
internal fun resolveFirstLaunchMockupAmbientMotion(
    style: AppUiStyle,
    selected: Boolean,
    reduceMotion: Boolean,
): Boolean = style == AppUiStyle.MIUIX && selected && !reduceMotion

/**
 * Runs a single, interruptible tab showcase on the Liquid Glass stage when motion is allowed
 * and the user has not yet interacted with the preview bar.
 */
internal fun shouldRunLiquidGlassPreviewAutoDemo(
    liquidGlassEnabled: Boolean,
    reduceMotion: Boolean,
    userInteracted: Boolean,
    gateActive: Boolean,
): Boolean = liquidGlassEnabled && !reduceMotion && !userInteracted && gateActive
