// 文件路径: core/theme/Theme.kt
package com.android.purebilibili.core.theme

import android.app.Activity
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.LocalAppIconStyle
import com.android.purebilibili.core.ui.LocalAppListItemStyle
import com.android.purebilibili.core.ui.resolveAppIconStyle
import com.android.purebilibili.core.ui.resolveAppListItemStyle
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.settings.normalizeMd3CustomColorHex
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

// --- 扩展颜色定义 ---
private val LightSurfaceVariant = Color(0xFFF1F2F3)

//  [优化] 根据主题色索引生成配色方案
private fun createDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.3f), //  Container derived from primary
    onPrimaryContainer = primaryColor.copy(alpha = 1f), // Stronger primary for content
    secondary = primaryColor.copy(alpha = 0.85f),
    secondaryContainer = primaryColor.copy(alpha = 0.2f), //  Container derived from primary
    onSecondaryContainer = primaryColor.copy(alpha = 0.9f),
    background = DarkBackground, // iOS User Interface Black
    surface = DarkSurface, // iOS System Gray 6 (Dark)
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkSurfaceElevated, // iOS System Gray 5 (Dark)
    outline = iOSSystemGray3Dark,
    outlineVariant = iOSSystemGray4Dark
)

private fun createAmoledDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.32f),
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.9f),
    secondaryContainer = primaryColor.copy(alpha = 0.22f),
    onSecondaryContainer = primaryColor,
    background = Black,
    surface = Black,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF050505),
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = Color(0xFF090909),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1A1A1A)
)

internal fun resolveEffectiveDynamicColorEnabled(
    dynamicColorEnabled: Boolean,
    amoledDarkTheme: Boolean,
    uiStyle: AppUiStyle
): Boolean = dynamicColorEnabled

internal fun createIosColorScheme(
    primaryColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean
): ColorScheme = when {
    darkTheme && amoledDarkTheme -> createAmoledDarkColorScheme(primaryColor)
    darkTheme -> createDarkColorScheme(primaryColor)
    else -> createLightColorScheme(primaryColor)
}

internal fun alignIosColorSchemeWithDynamicAccent(
    baseScheme: ColorScheme,
    dynamicAccentScheme: ColorScheme
): ColorScheme = baseScheme.copy(
    primary = dynamicAccentScheme.primary,
    onPrimary = dynamicAccentScheme.onPrimary,
    primaryContainer = dynamicAccentScheme.primaryContainer,
    onPrimaryContainer = dynamicAccentScheme.onPrimaryContainer,
    secondary = dynamicAccentScheme.secondary,
    onSecondary = dynamicAccentScheme.onSecondary,
    secondaryContainer = dynamicAccentScheme.secondaryContainer,
    onSecondaryContainer = dynamicAccentScheme.onSecondaryContainer,
    tertiary = dynamicAccentScheme.tertiary,
    onTertiary = dynamicAccentScheme.onTertiary,
    tertiaryContainer = dynamicAccentScheme.tertiaryContainer,
    onTertiaryContainer = dynamicAccentScheme.onTertiaryContainer
)

internal fun shouldObserveSystemWallpaperForDynamicColor(
    dynamicColorActive: Boolean,
    sdkInt: Int
): Boolean {
    return dynamicColorActive && sdkInt >= Build.VERSION_CODES.S
}

internal fun resolveMd3DynamicColorEnabled(
    source: Md3ColorSource,
    sdkInt: Int
): Boolean {
    return source == Md3ColorSource.FOLLOW_WALLPAPER && sdkInt >= Build.VERSION_CODES.S
}

internal fun resolveMd3ThemeSeedColor(
    source: Md3ColorSource,
    customColorHex: String,
    themeColorIndex: Int
): Color {
    return when (source) {
        Md3ColorSource.FOLLOW_WALLPAPER -> ThemeColors.getOrElse(
            normalizeThemeColorIndex(themeColorIndex)
        ) { iOSSystemBlue }
        Md3ColorSource.CUSTOM -> parseMd3CustomColorHex(customColorHex)
    }
}

internal fun parseMd3CustomColorHex(
    rawValue: String
): Color {
    val normalized = normalizeMd3CustomColorHex(rawValue)
    val rgb = normalized.removePrefix("#").toLong(radix = 16)
    return Color(0xFF000000 or rgb)
}

internal fun formatMd3CustomColorHex(color: Color): String {
    val rgb = color.toArgb() and 0x00FFFFFF
    return "#${rgb.toString(16).uppercase().padStart(6, '0')}"
}

internal fun resolveMiuixColorSchemeMode(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean
): ColorSchemeMode {
    // AndroidX resolves wallpaper palettes and MaterialKolor resolves custom seed
    // palettes before the Miuix bridge, so keep Miuix on the explicit bridged colors.
    return when (themeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
    }
}

internal fun shouldUseNativeMiuixPalette(uiStyle: AppUiStyle): Boolean =
    uiStyle == AppUiStyle.MIUIX

internal fun resolvePaletteStylePreference(rawValue: String?): PaletteStyle {
    return runCatching {
        rawValue?.let(PaletteStyle::valueOf)
    }.getOrNull() ?: PaletteStyle.TonalSpot
}

internal fun resolveColorSpecPreference(rawValue: String?): ColorSpec.SpecVersion {
    return runCatching {
        rawValue?.let(ColorSpec.SpecVersion::valueOf)
    }.getOrNull() ?: ColorSpec.SpecVersion.SPEC_2021
}

internal data class MiuixMaterialBridge(
    val primary: Color,
    val onPrimary: Color,
    val primaryFixed: Color,
    val onPrimaryFixed: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color
)

internal fun createMiuixMaterialBridge(colorScheme: ColorScheme): MiuixMaterialBridge {
    return MiuixMaterialBridge(
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        primaryFixed = colorScheme.primaryFixed,
        onPrimaryFixed = colorScheme.onPrimaryFixed,
        primaryContainer = colorScheme.primaryContainer,
        onPrimaryContainer = colorScheme.onPrimaryContainer,
        secondary = colorScheme.secondary,
        onSecondary = colorScheme.onSecondary,
        secondaryContainer = colorScheme.secondaryContainer,
        onSecondaryContainer = colorScheme.onSecondaryContainer,
        tertiary = colorScheme.tertiary,
        onTertiary = colorScheme.onTertiary,
        tertiaryContainer = colorScheme.tertiaryContainer,
        onTertiaryContainer = colorScheme.onTertiaryContainer,
        error = colorScheme.error,
        onError = colorScheme.onError,
        errorContainer = colorScheme.errorContainer,
        onErrorContainer = colorScheme.onErrorContainer,
        background = colorScheme.background,
        onBackground = colorScheme.onBackground,
        surface = colorScheme.surface,
        onSurface = colorScheme.onSurface,
        surfaceVariant = colorScheme.surfaceVariant,
        onSurfaceVariant = colorScheme.onSurfaceVariant,
        surfaceContainer = colorScheme.surfaceContainer,
        surfaceContainerHigh = colorScheme.surfaceContainerHigh,
        surfaceContainerHighest = colorScheme.surfaceContainerHighest,
        outline = colorScheme.outline,
        outlineVariant = colorScheme.outlineVariant
    )
}

internal fun resolveMaterialColorSchemeFromMiuixBridge(
    bridge: MiuixMaterialBridge,
    amoledDarkTheme: Boolean
): ColorScheme {
    val baseScheme = if (bridge.background.luminance() < 0.5f) {
        darkColorScheme(
            primary = bridge.primary,
            onPrimary = bridge.onPrimary,
            primaryContainer = bridge.primaryContainer,
            onPrimaryContainer = bridge.onPrimaryContainer,
            secondary = bridge.secondary,
            onSecondary = bridge.onSecondary,
            secondaryContainer = bridge.secondaryContainer,
            onSecondaryContainer = bridge.onSecondaryContainer,
            tertiary = bridge.tertiary,
            onTertiary = bridge.onTertiary,
            tertiaryContainer = bridge.tertiaryContainer,
            onTertiaryContainer = bridge.onTertiaryContainer,
            error = bridge.error,
            onError = bridge.onError,
            background = bridge.background,
            onBackground = bridge.onBackground,
            surface = bridge.surface,
            onSurface = bridge.onSurface,
            surfaceVariant = bridge.surfaceVariant,
            onSurfaceVariant = bridge.onSurfaceVariant,
            surfaceContainer = bridge.surfaceContainer,
            surfaceContainerHigh = bridge.surfaceContainerHigh,
            outline = bridge.outline,
            outlineVariant = bridge.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = bridge.primary,
            onPrimary = bridge.onPrimary,
            primaryContainer = bridge.primaryContainer,
            onPrimaryContainer = bridge.onPrimaryContainer,
            secondary = bridge.secondary,
            onSecondary = bridge.onSecondary,
            secondaryContainer = bridge.secondaryContainer,
            onSecondaryContainer = bridge.onSecondaryContainer,
            tertiary = bridge.tertiary,
            onTertiary = bridge.onTertiary,
            tertiaryContainer = bridge.tertiaryContainer,
            onTertiaryContainer = bridge.onTertiaryContainer,
            error = bridge.error,
            onError = bridge.onError,
            background = bridge.background,
            onBackground = bridge.onBackground,
            surface = bridge.surface,
            onSurface = bridge.onSurface,
            surfaceVariant = bridge.surfaceVariant,
            onSurfaceVariant = bridge.onSurfaceVariant,
            surfaceContainer = bridge.surfaceContainer,
            surfaceContainerHigh = bridge.surfaceContainerHigh,
            outline = bridge.outline,
            outlineVariant = bridge.outlineVariant
        )
    }
    return if (amoledDarkTheme) {
        applyAmoledSurfaceOverrides(baseScheme)
    } else {
        baseScheme
    }
}

/** Keep upstream neutral/control roles; only the user's accent is adapted from Material. */
internal fun resolveNativeMiuixColors(
    scheme: ColorScheme,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean = false,
): top.yukonga.miuix.kmp.theme.Colors {
    val base = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()
    val accentContainer = opaqueCompositeOver(scheme.primary.copy(alpha = 0.2f), base.surface)
    val accentScheme = scheme.copy(
        surface = base.surface,
        primaryFixed = accentContainer,
        onPrimaryFixed = scheme.primary,
        primaryContainer = scheme.primary,
        onPrimaryContainer = scheme.onPrimary,
    )
    val accent = resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(accentScheme), darkTheme)
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryVariant = accent.primaryVariant,
        onPrimaryVariant = accent.onPrimaryVariant,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = scheme.primary,
        onBackgroundVariant = scheme.primary,
        sliderKeyPoint = scheme.primary.copy(alpha = base.sliderKeyPoint.alpha),
        sliderKeyPointForeground = scheme.primary,
        background = if (darkTheme && amoledDarkTheme) Color.Black else base.background,
    )
}

/** Material-backed content consumes the same semantic palette as native Miuix components. */
internal fun alignMaterialSurfacesWithMiuix(
    scheme: ColorScheme,
    colors: top.yukonga.miuix.kmp.theme.Colors,
): ColorScheme {
    val isDark = colors.background.luminance() < 0.5f
    return scheme.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryFixed = colors.primaryVariant,
        onPrimaryFixed = colors.onPrimaryVariant,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = colors.primary,
        onTertiary = colors.onPrimary,
        tertiaryContainer = colors.tertiaryContainer,
        onTertiaryContainer = colors.onTertiaryContainer,
        error = colors.error,
        onError = colors.onError,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.onSurfaceVariantSummary,
        surfaceTint = Color.Transparent,
        inversePrimary = colors.primaryVariant,
        inverseSurface = colors.onSurface,
        inverseOnSurface = colors.surface,
        outline = colors.outline,
        outlineVariant = colors.dividerLine,
        scrim = colors.windowDimming,
        surfaceBright = if (isDark) colors.surfaceContainerHighest else colors.surface,
        surfaceDim = if (isDark) colors.surface else colors.surfaceContainerHighest,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surfaceContainer,
        surfaceContainer = colors.surfaceContainer,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHighest,
    )
}

internal fun resolveMiuixColorsFromMaterialBridge(
    bridge: MiuixMaterialBridge,
    darkTheme: Boolean
): top.yukonga.miuix.kmp.theme.Colors {
    val base = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()
    val disabledPrimary = opaqueCompositeOver(bridge.primary.copy(alpha = 0.38f), bridge.surface)
    val disabledOnPrimary = opaqueCompositeOver(bridge.onPrimary.copy(alpha = 0.38f), disabledPrimary)
    val disabledPrimaryButton = opaqueCompositeOver(bridge.primary.copy(alpha = 0.38f), bridge.surface)
    val disabledOnPrimaryButton = opaqueCompositeOver(
        bridge.onPrimary.copy(alpha = 0.6f),
        disabledPrimaryButton,
    )
    val disabledSecondary = opaqueCompositeOver(bridge.outlineVariant.copy(alpha = 0.5f), bridge.surface)
    val disabledOnSecondary = opaqueCompositeOver(bridge.onSurface.copy(alpha = 0.38f), disabledSecondary)
    val disabledSecondaryVariant = opaqueCompositeOver(
        bridge.surfaceContainerHigh.copy(alpha = 0.6f),
        bridge.surface,
    )
    val disabledOnSecondaryVariant = opaqueCompositeOver(
        bridge.onSurface.copy(alpha = 0.38f),
        disabledSecondaryVariant,
    )
    return base.copy(
        primary = bridge.primary,
        onPrimary = bridge.onPrimary,
        primaryVariant = bridge.primaryFixed,
        onPrimaryVariant = bridge.onPrimaryFixed,
        errorContainer = bridge.errorContainer,
        onErrorContainer = bridge.onErrorContainer,
        disabledPrimary = disabledPrimary,
        disabledOnPrimary = disabledOnPrimary,
        disabledPrimaryButton = disabledPrimaryButton,
        disabledOnPrimaryButton = disabledOnPrimaryButton,
        disabledPrimarySlider = disabledPrimary,
        primaryContainer = bridge.primaryContainer,
        onPrimaryContainer = bridge.onPrimaryContainer,
        secondary = bridge.outlineVariant,
        onSecondary = resolveReadableTextColor(
            candidate = bridge.outline,
            background = bridge.outlineVariant,
            fallback = bridge.onSurface,
            minimumContrast = ACCESSIBLE_UI_MIN_CONTRAST,
        ),
        secondaryVariant = bridge.surfaceContainerHigh,
        onSecondaryVariant = bridge.onSurface,
        disabledSecondary = disabledSecondary,
        disabledOnSecondary = disabledOnSecondary,
        disabledSecondaryVariant = disabledSecondaryVariant,
        disabledOnSecondaryVariant = disabledOnSecondaryVariant,
        secondaryContainer = bridge.secondaryContainer,
        onSecondaryContainer = bridge.onSecondaryContainer,
        secondaryContainerVariant = bridge.surfaceContainerHighest,
        onSecondaryContainerVariant = bridge.onSurfaceVariant,
        tertiaryContainer = bridge.tertiaryContainer,
        onTertiaryContainer = bridge.onTertiaryContainer,
        tertiaryContainerVariant = bridge.onTertiaryContainer,
        error = bridge.error,
        onError = bridge.onError,
        background = bridge.background,
        onBackground = bridge.onBackground,
        onBackgroundVariant = bridge.primary,
        surface = bridge.surface,
        onSurface = bridge.onSurface,
        surfaceVariant = bridge.surfaceVariant,
        onSurfaceSecondary = opaqueCompositeOver(bridge.onSurface.copy(alpha = 0.8f), bridge.surface),
        onSurfaceVariantSummary = bridge.onSurfaceVariant,
        onSurfaceVariantActions = bridge.onSurfaceVariant,
        disabledOnSurface = bridge.onSurface,
        surfaceContainer = bridge.surfaceContainer,
        onSurfaceContainer = bridge.onSurface,
        onSurfaceContainerVariant = bridge.onSurfaceVariant,
        surfaceContainerHigh = bridge.surfaceContainerHigh,
        onSurfaceContainerHigh = opaqueCompositeOver(
            bridge.onSurface.copy(alpha = 0.8f),
            bridge.surfaceContainerHigh,
        ),
        surfaceContainerHighest = bridge.surfaceContainerHighest,
        onSurfaceContainerHighest = bridge.onSurface,
        outline = bridge.outline,
        dividerLine = bridge.outlineVariant,
        windowDimming = Color.Black.copy(alpha = if (darkTheme) 0.6f else 0.3f),
        sliderKeyPoint = bridge.primary,
        sliderKeyPointForeground = bridge.surfaceContainerHigh,
        sliderBackground = opaqueCompositeOver(bridge.primary.copy(alpha = 0.2f), bridge.surface),
    )
}

internal fun applyAmoledSurfaceOverrides(
    baseScheme: ColorScheme
): ColorScheme = baseScheme.copy(
    background = Black,
    surface = Black,
    surfaceVariant = Color(0xFF050505),
    surfaceContainer = Color(0xFF090909),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1A1A1A)
)

// 官方 MD3 baseline error 角色(不随种子色变化)
private val Md3LightError = Color(0xFFB3261E)
private val Md3LightOnError = Color(0xFFFFFFFF)
private val Md3LightErrorContainer = Color(0xFFF9DEDC)
private val Md3LightOnErrorContainer = Color(0xFF410E0B)
private val Md3DarkError = Color(0xFFF2B8B5)
private val Md3DarkOnError = Color(0xFF601410)
private val Md3DarkErrorContainer = Color(0xFF8C1D18)
private val Md3DarkOnErrorContainer = Color(0xFFF9DEDC)

private fun createLightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.15f), //  Container derived from primary (ligther for light mode)
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.8f),
    secondaryContainer = primaryColor.copy(alpha = 0.1f), //  Container derived from primary
    onSecondaryContainer = primaryColor,
    background = iOSSystemGray6, // Use iOS System Gray 6 for main background (grouped table view style)
    surface = White, // iOS cards are usually white
    onSurface = TextPrimary,
    surfaceVariant = iOSSystemGray5, // Separators / Higher groupings
    onSurfaceVariant = TextSecondary,
    surfaceContainer = iOSSystemGray5, // iOS System Gray 5 (Light)
    outline = iOSSystemGray3,
    outlineVariant = iOSSystemGray4
)

// 保留默认配色作为后备 (使用 iOS 系统蓝)
private val DarkColorScheme = createDarkColorScheme(iOSSystemBlue)
private val LightColorScheme = createLightColorScheme(iOSSystemBlue)

private data class HslColorModel(
    val hue: Float,
    val saturation: Float,
    val lightness: Float
)

private fun Color.toHslColorModel(): HslColorModel {
    val red = red
    val green = green
    val blue = blue
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val lightness = (max + min) / 2f

    val saturation = if (delta == 0f) {
        0f
    } else {
        delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    }

    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * positiveModulo((green - blue) / delta, 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }

    return HslColorModel(
        hue = normalizeHue(hue),
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f)
    )
}

private fun normalizeHue(hue: Float): Float {
    val value = hue % 360f
    return if (value < 0f) value + 360f else value
}

private fun positiveModulo(value: Float, modulus: Float): Float {
    val result = value % modulus
    return if (result < 0f) result + modulus else result
}

private fun colorFromHsl(
    hue: Float,
    saturation: Float,
    lightness: Float
): Color {
    val normalizedHue = normalizeHue(hue)
    val normalizedSaturation = saturation.coerceIn(0f, 1f)
    val normalizedLightness = lightness.coerceIn(0f, 1f)
    val chroma = (1f - kotlin.math.abs(2f * normalizedLightness - 1f)) * normalizedSaturation
    val huePrime = normalizedHue / 60f
    val secondComponent = chroma * (1f - kotlin.math.abs(positiveModulo(huePrime, 2f) - 1f))
    val match = normalizedLightness - chroma / 2f

    val (redPrime, greenPrime, bluePrime) = when {
        huePrime < 1f -> Triple(chroma, secondComponent, 0f)
        huePrime < 2f -> Triple(secondComponent, chroma, 0f)
        huePrime < 3f -> Triple(0f, chroma, secondComponent)
        huePrime < 4f -> Triple(0f, secondComponent, chroma)
        huePrime < 5f -> Triple(secondComponent, 0f, chroma)
        else -> Triple(chroma, 0f, secondComponent)
    }

    return Color(
        redPrime + match,
        greenPrime + match,
        bluePrime + match,
        1f,
        ColorSpaces.Srgb
    )
}

private fun blendColors(
    background: Color,
    foreground: Color,
    foregroundRatio: Float
): Color {
    val ratio = foregroundRatio.coerceIn(0f, 1f)
    val inverse = 1f - ratio
    return Color(
        background.red * inverse + foreground.red * ratio,
        background.green * inverse + foreground.green * ratio,
        background.blue * inverse + foreground.blue * ratio,
        background.alpha * inverse + foreground.alpha * ratio,
        ColorSpaces.Srgb
    )
}

private fun chooseReadableOnColor(background: Color): Color {
    return if (calculateContrastRatio(White, background) >= calculateContrastRatio(Black, background)) {
        White
    } else {
        Black
    }
}

private fun deriveNeutralSurfaceColor(
    source: HslColorModel,
    lightness: Float,
    maxSaturation: Float
): Color {
    return colorFromHsl(
        hue = source.hue,
        saturation = minOf(source.saturation * 0.16f, maxSaturation),
        lightness = lightness
    )
}

private fun deriveAccentColor(
    source: HslColorModel,
    hueShift: Float,
    saturationScale: Float,
    lightness: Float,
    minimumSaturation: Float = 0.18f
): Color {
    return colorFromHsl(
        hue = source.hue + hueShift,
        saturation = maxOf(minimumSaturation, source.saturation * saturationScale),
        lightness = lightness
    )
}

internal fun createStaticMd3ColorScheme(
    primaryColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean
): ColorScheme {
    val source = primaryColor.toHslColorModel()

    val scheme = if (darkTheme) {
        val primary = primaryColor
        val secondary = deriveAccentColor(
            source = source,
            hueShift = 10f,
            saturationScale = 0.42f,
            lightness = 0.76f,
            minimumSaturation = 0.16f
        )
        val tertiary = deriveAccentColor(
            source = source,
            hueShift = 56f,
            saturationScale = 0.52f,
            lightness = 0.78f,
            minimumSaturation = 0.20f
        )
        val background = deriveNeutralSurfaceColor(source, lightness = 0.075f, maxSaturation = 0.05f)
        val surface = deriveNeutralSurfaceColor(source, lightness = 0.10f, maxSaturation = 0.06f)
        val surfaceVariant = deriveNeutralSurfaceColor(source, lightness = 0.18f, maxSaturation = 0.09f)
        val surfaceContainer = deriveNeutralSurfaceColor(source, lightness = 0.14f, maxSaturation = 0.07f)
        val surfaceContainerHigh = deriveNeutralSurfaceColor(source, lightness = 0.17f, maxSaturation = 0.08f)
        val surfaceContainerHighest = deriveNeutralSurfaceColor(source, lightness = 0.20f, maxSaturation = 0.09f)
        val outline = deriveNeutralSurfaceColor(source, lightness = 0.54f, maxSaturation = 0.08f)
        val outlineVariant = deriveNeutralSurfaceColor(source, lightness = 0.33f, maxSaturation = 0.07f)
        val primaryContainer = blendColors(background = background, foreground = primary, foregroundRatio = 0.34f)
        val secondaryContainer = blendColors(background = background, foreground = secondary, foregroundRatio = 0.28f)
        val tertiaryContainer = blendColors(background = background, foreground = tertiary, foregroundRatio = 0.28f)
        val onSurfaceVariant = resolveReadableTextColor(
            candidate = deriveNeutralSurfaceColor(source, lightness = 0.78f, maxSaturation = 0.08f),
            background = surfaceVariant,
            fallback = chooseReadableOnColor(surfaceVariant),
            minimumContrast = 3.0f
        )

        darkColorScheme(
            primary = primary,
            onPrimary = chooseReadableOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = chooseReadableOnColor(primaryContainer),
            secondary = secondary,
            onSecondary = chooseReadableOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = chooseReadableOnColor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = chooseReadableOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = chooseReadableOnColor(tertiaryContainer),
            error = Md3DarkError,
            onError = Md3DarkOnError,
            errorContainer = Md3DarkErrorContainer,
            onErrorContainer = Md3DarkOnErrorContainer,
            background = background,
            onBackground = chooseReadableOnColor(background),
            surface = surface,
            onSurface = chooseReadableOnColor(surface),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inversePrimary = deriveAccentColor(source = source, hueShift = 0f, saturationScale = 1f, lightness = 0.80f),
            inverseSurface = deriveNeutralSurfaceColor(source, lightness = 0.92f, maxSaturation = 0.05f),
            inverseOnSurface = deriveNeutralSurfaceColor(source, lightness = 0.10f, maxSaturation = 0.06f),
            surfaceContainerLowest = deriveNeutralSurfaceColor(source, lightness = 0.06f, maxSaturation = 0.05f),
            surfaceContainerLow = deriveNeutralSurfaceColor(source, lightness = 0.12f, maxSaturation = 0.06f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceBright = surfaceContainerHighest,
            surfaceDim = background,
            scrim = Black,
            outline = outline,
            outlineVariant = outlineVariant
        )
    } else {
        val primary = primaryColor
        val secondary = deriveAccentColor(
            source = source,
            hueShift = 10f,
            saturationScale = 0.42f,
            lightness = source.lightness.coerceIn(0.34f, 0.46f),
            minimumSaturation = 0.15f
        )
        val tertiary = deriveAccentColor(
            source = source,
            hueShift = 56f,
            saturationScale = 0.55f,
            lightness = 0.42f,
            minimumSaturation = 0.18f
        )
        val background = deriveNeutralSurfaceColor(source, lightness = 0.98f, maxSaturation = 0.12f)
        val surface = deriveNeutralSurfaceColor(source, lightness = 0.99f, maxSaturation = 0.04f)
        val surfaceVariant = deriveNeutralSurfaceColor(source, lightness = 0.90f, maxSaturation = 0.08f)
        val surfaceContainer = deriveNeutralSurfaceColor(source, lightness = 0.95f, maxSaturation = 0.06f)
        val surfaceContainerHigh = deriveNeutralSurfaceColor(source, lightness = 0.92f, maxSaturation = 0.07f)
        val surfaceContainerHighest = deriveNeutralSurfaceColor(source, lightness = 0.88f, maxSaturation = 0.08f)
        val outline = deriveNeutralSurfaceColor(source, lightness = 0.55f, maxSaturation = 0.08f)
        val outlineVariant = deriveNeutralSurfaceColor(source, lightness = 0.82f, maxSaturation = 0.06f)
        val primaryContainer = blendColors(background = background, foreground = primary, foregroundRatio = 0.18f)
        val secondaryContainer = blendColors(background = background, foreground = secondary, foregroundRatio = 0.16f)
        val tertiaryContainer = blendColors(background = background, foreground = tertiary, foregroundRatio = 0.16f)
        val onSurfaceVariant = resolveReadableTextColor(
            candidate = deriveNeutralSurfaceColor(source, lightness = 0.36f, maxSaturation = 0.08f),
            background = surfaceVariant,
            fallback = chooseReadableOnColor(surfaceVariant),
            minimumContrast = 3.0f
        )

        lightColorScheme(
            primary = primary,
            onPrimary = chooseReadableOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = chooseReadableOnColor(primaryContainer),
            secondary = secondary,
            onSecondary = chooseReadableOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = chooseReadableOnColor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = chooseReadableOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = chooseReadableOnColor(tertiaryContainer),
            error = Md3LightError,
            onError = Md3LightOnError,
            errorContainer = Md3LightErrorContainer,
            onErrorContainer = Md3LightOnErrorContainer,
            background = background,
            onBackground = chooseReadableOnColor(background),
            surface = surface,
            onSurface = chooseReadableOnColor(surface),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inversePrimary = deriveAccentColor(source = source, hueShift = 0f, saturationScale = 1f, lightness = 0.40f),
            inverseSurface = deriveNeutralSurfaceColor(source, lightness = 0.10f, maxSaturation = 0.06f),
            inverseOnSurface = deriveNeutralSurfaceColor(source, lightness = 0.92f, maxSaturation = 0.05f),
            surfaceContainerLowest = deriveNeutralSurfaceColor(source, lightness = 1.0f, maxSaturation = 0.04f),
            surfaceContainerLow = deriveNeutralSurfaceColor(source, lightness = 0.97f, maxSaturation = 0.05f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceBright = surface,
            surfaceDim = deriveNeutralSurfaceColor(source, lightness = 0.86f, maxSaturation = 0.08f),
            scrim = Black,
            outline = outline,
            outlineVariant = outlineVariant
        )
    }

    return if (darkTheme && amoledDarkTheme) {
        applyAmoledSurfaceOverrides(scheme)
    } else {
        scheme
    }
}

/**
 * Align a MaterialKolor-generated scheme with the user-picked seed.
 *
 * Official wallpaper MD3 keeps HCT tone-mapped roles (Switch / FilterChip / buttons).
 * Custom seed previously forced the raw hex into [ColorScheme.primary], which made
 * bright seeds produce black onPrimary and neon tracks in light mode — while wallpaper
 * dynamic color (no force-align) looked correct.
 *
 * MaterialKolor already maps [themePrimaryColor] into proper primary / onPrimary /
 * primaryContainer roles. Only stamp the seed onto [ColorScheme.surfaceTint] so brand
 * identity remains without breaking control colors.
 */
internal fun alignStaticColorSchemeWithThemePrimary(
    scheme: ColorScheme,
    themePrimaryColor: Color,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean
): ColorScheme {
    return scheme.copy(surfaceTint = themePrimaryColor)
}

private fun createMd3DarkColorScheme(primaryColor: Color) = createStaticMd3ColorScheme(
    primaryColor = primaryColor,
    darkTheme = true,
    amoledDarkTheme = false
)

private fun createMd3LightColorScheme(primaryColor: Color) = createStaticMd3ColorScheme(
    primaryColor = primaryColor,
    darkTheme = false,
    amoledDarkTheme = false
)

@Composable
@Suppress("DEPRECATION") // Broadcast is retained as an OEM fallback for wallpaper palette delivery.
private fun rememberSystemWallpaperRefreshToken(
    dynamicColorActive: Boolean
): Int {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var token by remember { mutableIntStateOf(0) }
    val shouldObserve = shouldObserveSystemWallpaperForDynamicColor(
        dynamicColorActive = dynamicColorActive,
        sdkInt = Build.VERSION.SDK_INT
    )

    DisposableEffect(context, lifecycleOwner, shouldObserve) {
        if (!shouldObserve || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return@DisposableEffect onDispose { }
        }
        val wallpaperManager = WallpaperManager.getInstance(context)
        val handler = Handler(Looper.getMainLooper())
        val settledPaletteRefresh = Runnable {
            token += 1
        }
        val requestPaletteRefresh = {
            token += 1
            // The wallpaper callback can arrive before the framework has finished
            // applying the new Monet resource overlay. Refresh once more after it
            // settles so an already-open app picks up the new palette immediately.
            handler.removeCallbacks(settledPaletteRefresh)
            handler.postDelayed(
                settledPaletteRefresh,
                SYSTEM_WALLPAPER_PALETTE_SETTLE_DELAY_MS
            )
        }
        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            requestPaletteRefresh()
        }
        val wallpaperChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                    requestPaletteRefresh()
                }
            }
        }
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Some OEM wallpaper pickers update the runtime resource overlay after their
                // wallpaper callback. Re-read once when returning to the app as a reliable
                // fallback for callbacks that arrived early or while composition was paused.
                requestPaletteRefresh()
            }
        }
        wallpaperManager.addOnColorsChangedListener(
            listener,
            handler
        )
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        ContextCompat.registerReceiver(
            context,
            wallpaperChangedReceiver,
            IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            handler.removeCallbacks(settledPaletteRefresh)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            wallpaperManager.removeOnColorsChangedListener(listener)
            context.unregisterReceiver(wallpaperChangedReceiver)
        }
    }

    return token
}

private const val SYSTEM_WALLPAPER_PALETTE_SETTLE_DELAY_MS = 200L

internal fun createMiuixAlignedColorScheme(
    primaryColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean
): ColorScheme {
    return if (darkTheme) {
        if (amoledDarkTheme) {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = White,
                primaryContainer = primaryColor.copy(alpha = 0.32f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor.copy(alpha = 0.9f),
                secondaryContainer = primaryColor.copy(alpha = 0.22f),
                onSecondaryContainer = primaryColor,
                background = Black,
                surface = Black,
                onSurface = Color(0xFFF2F2F2),
                surfaceVariant = Color(0xFF121212),
                onSurfaceVariant = Color(0xFF98989D),
                surfaceContainer = Color(0xFF0D0D0D),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF242424),
                outline = Color(0xFF48484A),
                outlineVariant = Color(0xFF262626)
            )
        } else {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = White,
                primaryContainer = primaryColor.copy(alpha = 0.3f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor.copy(alpha = 0.85f),
                secondaryContainer = primaryColor.copy(alpha = 0.2f),
                onSecondaryContainer = primaryColor,
                background = Color(0xFF0D0D0D),
                surface = Color(0xFF121212),
                onSurface = Color(0xFFF2F2F2),
                surfaceVariant = Color(0xFF242424),
                onSurfaceVariant = Color(0xFF98989D),
                surfaceContainer = Color(0xFF242424),
                surfaceContainerHigh = Color(0xFF2C2C2E),
                surfaceContainerHighest = Color(0xFF383838),
                outline = Color(0xFF48484A),
                outlineVariant = Color(0xFF3A3A3C)
            )
        }
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = White,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            secondaryContainer = primaryColor.copy(alpha = 0.1f),
            onSecondaryContainer = primaryColor,
            background = Color(0xFFF7F7F7),
            surface = White,
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF6C6C70),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            outline = Color(0xFFD1D1D6),
            outlineVariant = Color(0xFFE5E5EA)
        )
    }
}

@Composable
private fun rememberBiliPaiStyleColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    dynamicBaseScheme: ColorScheme? = null
): ColorScheme = remember(
    seedColor,
    darkTheme,
    amoledDarkTheme,
    paletteStyle,
    colorSpec,
    uiStyle,
    dynamicBaseScheme,
) {
    createBiliPaiStyleColorScheme(
        seedColor = seedColor,
        darkTheme = darkTheme,
        amoledDarkTheme = amoledDarkTheme,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
        uiStyle = uiStyle,
        dynamicBaseScheme = dynamicBaseScheme,
    )
}

internal fun createBiliPaiStyleColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    dynamicBaseScheme: ColorScheme? = null,
): ColorScheme {
    // AndroidX already returns the user's final wallpaper-derived light/dark scheme.
    // Re-generating it from resolved roles changes the palette selected in system settings.
    if (dynamicBaseScheme != null) return dynamicBaseScheme

    if (uiStyle == AppUiStyle.MIUIX) {
        return createMiuixAlignedColorScheme(
            primaryColor = seedColor,
            darkTheme = darkTheme,
            amoledDarkTheme = amoledDarkTheme
        )
    }

    val scheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = amoledDarkTheme,
        style = paletteStyle,
        specVersion = colorSpec
    )

    val readableScheme = if (!darkTheme) {
        enforceDynamicLightTextContrast(scheme)
    } else {
        scheme
    }
    return alignStaticColorSchemeWithThemePrimary(
        scheme = readableScheme,
        themePrimaryColor = seedColor,
        darkTheme = darkTheme
    )
}

@Composable
fun PureBiliBiliTheme(
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDarkTheme: Boolean = false,
    themeColorIndex: Int = 0, //  默认 0 = iOS 蓝色
    md3ColorSource: Md3ColorSource = if (dynamicColor) {
        Md3ColorSource.FOLLOW_WALLPAPER
    } else {
        Md3ColorSource.CUSTOM
    },
    md3CustomColorHex: String = "#007AFF",
    themeRoleOverrides: ThemeRoleOverrides = ThemeRoleOverrides(),
    colorStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
    fontSizePreset: AppFontSizePreset = AppFontSizePreset.DEFAULT,
    appFontFileName: String = "",
    appIconStyle: AppIconStyle = AppIconStyle.AUTO,
    appListItemStyle: AppListItemStyle = AppListItemStyle.AUTO,
    liquidGlassEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    //  获取 MD3 主题种子色。跟随壁纸时仍保留旧预设色作为非 S 设备后备。
    val customPrimaryColor = resolveMd3ThemeSeedColor(
        source = md3ColorSource,
        customColorHex = md3CustomColorHex,
        themeColorIndex = themeColorIndex
    )

    val isDynamicColorActive = resolveEffectiveDynamicColorEnabled(
        dynamicColorEnabled = resolveMd3DynamicColorEnabled(
            source = md3ColorSource,
            sdkInt = Build.VERSION.SDK_INT
        ),
        amoledDarkTheme = amoledDarkTheme,
        uiStyle = uiStyle
    )
    val shapes = resolveMaterialShapes(uiStyle)
    val appFontFamily = remember(context, appFontFileName) {
        loadStoredAppFontFamily(context, appFontFileName)
    }
    val materialTypography = resolveMaterialTypography(uiStyle, liquidGlassEnabled)
        .scaled(fontSizePreset.multiplier)
        .withFontFamily(appFontFamily)
    val materialMotionScheme = remember(uiStyle) {
        resolveMaterialMotionScheme(uiStyle)
    }
    val miuixTextStyles = remember(materialTypography) {
        materialTypography.toMiuixTextStyles()
    }
    val systemWallpaperRefreshToken = rememberSystemWallpaperRefreshToken(isDynamicColorActive)
    val dynamicLightBaseScheme = if (
        isDynamicColorActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        key(systemWallpaperRefreshToken) {
            dynamicLightColorScheme(context)
        }
    } else {
        null
    }
    val dynamicDarkBaseScheme = if (
        isDynamicColorActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        key(systemWallpaperRefreshToken) {
            dynamicDarkColorScheme(context)
        }
    } else {
        null
    }
    val lightMaterialScheme = rememberBiliPaiStyleColorScheme(
        seedColor = customPrimaryColor,
        darkTheme = false,
        amoledDarkTheme = false,
        paletteStyle = colorStyle,
        colorSpec = colorSpec,
        uiStyle = uiStyle,
        dynamicBaseScheme = dynamicLightBaseScheme
    )
    val darkMaterialScheme = rememberBiliPaiStyleColorScheme(
        seedColor = customPrimaryColor,
        darkTheme = true,
        amoledDarkTheme = amoledDarkTheme,
        paletteStyle = colorStyle,
        colorSpec = colorSpec,
        uiStyle = uiStyle,
        dynamicBaseScheme = dynamicDarkBaseScheme
    )

    val effectiveThemeRoleOverrides = remember(md3ColorSource, themeRoleOverrides, uiStyle) {
        resolveEffectiveThemeRoleOverrides(md3ColorSource, themeRoleOverrides, uiStyle)
    }
    val resolvedLightMaterialScheme = remember(lightMaterialScheme, effectiveThemeRoleOverrides) {
        applyThemeRoleOverrides(lightMaterialScheme, effectiveThemeRoleOverrides, darkTheme = false)
    }
    val resolvedDarkMaterialScheme = remember(darkMaterialScheme, effectiveThemeRoleOverrides) {
        applyThemeRoleOverrides(darkMaterialScheme, effectiveThemeRoleOverrides, darkTheme = true)
    }
    val baseThemeRoleOverrides = remember(lightMaterialScheme, darkMaterialScheme) {
        themeRoleOverridesFromSchemes(
            lightScheme = lightMaterialScheme,
            darkScheme = darkMaterialScheme
        )
    }
    val staticMaterialScheme = if (darkTheme) resolvedDarkMaterialScheme else resolvedLightMaterialScheme
    // Liquid glass changes chrome rendering, not the app's base palette. Keep Miuix's
    // native light-gray surfaces stable when the effect is toggled on or off.
    val useNativeMiuix = shouldUseNativeMiuixPalette(uiStyle)
    val miuixLightColors = remember(resolvedLightMaterialScheme, useNativeMiuix) {
        if (useNativeMiuix) {
            resolveNativeMiuixColors(
                resolvedLightMaterialScheme,
                darkTheme = false,
            )
        } else {
            resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(resolvedLightMaterialScheme), false)
        }
    }
    val miuixDarkColors = remember(
        resolvedDarkMaterialScheme, useNativeMiuix, amoledDarkTheme,
    ) {
        if (useNativeMiuix) {
            resolveNativeMiuixColors(
                resolvedDarkMaterialScheme,
                darkTheme = true,
                amoledDarkTheme = amoledDarkTheme,
            )
        } else {
            resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(resolvedDarkMaterialScheme), true)
        }
    }
    val controller = remember(
        themeMode,
        dynamicColor,
        darkTheme,
        customPrimaryColor,
        themeRoleOverrides,
        amoledDarkTheme,
        colorStyle,
        colorSpec,
        systemWallpaperRefreshToken,
        miuixLightColors,
        miuixDarkColors
    ) {
        ThemeController(
            colorSchemeMode = resolveMiuixColorSchemeMode(
                themeMode = themeMode,
                dynamicColorEnabled = dynamicColor
            ),
            lightColors = miuixLightColors,
            darkColors = miuixDarkColors,
            isDark = darkTheme
        )
    }
    val activeMiuixColors = if (darkTheme) miuixDarkColors else miuixLightColors
    val materialColorScheme = remember(staticMaterialScheme, useNativeMiuix, activeMiuixColors) {
        if (useNativeMiuix) alignMaterialSurfacesWithMiuix(staticMaterialScheme, activeMiuixColors)
        else staticMaterialScheme
    }

    //  [新增] 动态设置状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏图标颜色：
            // - 深色模式：使用浅色图标 (isAppearanceLightStatusBars = false)
            // - 浅色模式：使用深色图标 (isAppearanceLightStatusBars = true)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAppUiStyle provides uiStyle,
        LocalDynamicColorActive provides isDynamicColorActive,
        LocalBaseThemeRoleOverrides provides baseThemeRoleOverrides,
        LocalAppIconStyle provides resolveAppIconStyle(
            iconStyle = appIconStyle,
            uiStyle = uiStyle
        ),
        LocalAppListItemStyle provides resolveAppListItemStyle(
            style = appListItemStyle,
            uiStyle = uiStyle
        ),
        LocalCornerRadiusScale provides resolveCornerRadiusScale(uiStyle)
    ) {
        MiuixTheme(
            controller = controller,
            textStyles = miuixTextStyles
        ) {
            MaterialTheme(
                colorScheme = materialColorScheme,
                typography = materialTypography,
                shapes = shapes,
                motionScheme = materialMotionScheme,
                content = content
            )
        }
    }
}
