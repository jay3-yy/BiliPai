package com.android.purebilibili.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

internal val LocalBaseThemeRoleOverrides = staticCompositionLocalOf {
    ThemeRoleOverrides()
}

internal fun themeRoleOverridesFromSchemes(
    lightScheme: ColorScheme,
    darkScheme: ColorScheme,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    amoledDarkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") liquidGlassEnabled: Boolean = true,
): ThemeRoleOverrides {
    fun ColorScheme.nativeStartingScheme(dark: Boolean): ColorScheme =
        if (shouldUseNativeMiuixPalette(uiStyle)) {
            val colors = resolveNativeMiuixColors(this, dark, amoledDarkTheme)
            alignMaterialSurfacesWithMiuix(this, colors)
        } else this

    fun ColorScheme.toRoles(): ThemeModeRoleOverrides {
        return ThemeModeRoleOverrides(
            backgroundHex = formatMd3CustomColorHex(background),
            primaryTextHex = formatMd3CustomColorHex(onBackground),
            secondaryTextHex = formatMd3CustomColorHex(onSurfaceVariant),
            controlAccentHex = formatMd3CustomColorHex(primary)
        )
    }

    return ThemeRoleOverrides(
        enabled = false,
        light = lightScheme.nativeStartingScheme(false).toRoles(),
        dark = darkScheme.nativeStartingScheme(true).toRoles()
    )
}

internal fun syncThemeRoleControlAccent(
    overrides: ThemeRoleOverrides,
    customColorHex: String
): ThemeRoleOverrides {
    if (!overrides.enabled) return overrides
    return overrides.copy(
        light = overrides.light.copy(controlAccentHex = customColorHex),
        dark = overrides.dark.copy(controlAccentHex = customColorHex)
    )
}

/** Enabling advanced surfaces must not turn generated accent roles into new seeds. */
internal fun enableThemeRoleOverrides(
    current: ThemeRoleOverrides,
    base: ThemeRoleOverrides,
    customColorHex: String,
): ThemeRoleOverrides {
    val unconfigured = current.copy(enabled = false) == ThemeRoleOverrides()
    val inheritedGeneratedAccents = current.light.controlAccentHex == base.light.controlAccentHex &&
        current.dark.controlAccentHex == base.dark.controlAccentHex
    val starting = if (unconfigured) base else current
    val enabled = starting.copy(enabled = true)
    return if (unconfigured || inheritedGeneratedAccents) {
        syncThemeRoleControlAccent(enabled, customColorHex)
    } else {
        enabled
    }
}

internal fun resolveEffectiveThemeRoleOverrides(
    source: Md3ColorSource,
    overrides: ThemeRoleOverrides,
): ThemeRoleOverrides {
    return if (source == Md3ColorSource.CUSTOM) {
        overrides
    } else {
        overrides.copy(enabled = false)
    }
}

internal fun applyThemeRoleOverrides(
    scheme: ColorScheme,
    overrides: ThemeRoleOverrides,
    darkTheme: Boolean,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    colorContrast: Contrast = Contrast.Default,
): ColorScheme {
    if (!overrides.enabled) return scheme

    val roles = if (darkTheme) overrides.dark else overrides.light
    val background = parseThemeRoleColor(roles.backgroundHex, scheme.background)
    val primaryText = parseThemeRoleColor(roles.primaryTextHex, scheme.onBackground)
    val secondaryText = parseThemeRoleColor(roles.secondaryTextHex, scheme.onSurfaceVariant)
    val accent = parseThemeRoleColor(roles.controlAccentHex, scheme.primary)
    // A custom control color is a seed, not a replacement for both light/dark roles.
    // Keep Material's generated role pairs, including the fixed roles, together.
    val accentScheme = dynamicColorScheme(
        seedColor = accent,
        isDark = darkTheme,
        style = paletteStyle,
        specVersion = resolveMaterialPaletteSpec(paletteStyle, colorSpec),
        contrastLevel = colorContrast.value,
    )
    val surfaceLow = lerp(background, primaryText, if (darkTheme) 0.08f else 0.035f)
    val surface = lerp(background, primaryText, if (darkTheme) 0.12f else 0.055f)
    val surfaceHigh = lerp(background, primaryText, if (darkTheme) 0.18f else 0.09f)

    return enforceDynamicTextContrast(scheme.copy(
        primary = accentScheme.primary,
        onPrimary = accentScheme.onPrimary,
        primaryContainer = accentScheme.primaryContainer,
        onPrimaryContainer = accentScheme.onPrimaryContainer,
        primaryFixed = accentScheme.primaryFixed,
        primaryFixedDim = accentScheme.primaryFixedDim,
        onPrimaryFixed = accentScheme.onPrimaryFixed,
        onPrimaryFixedVariant = accentScheme.onPrimaryFixedVariant,
        secondary = accentScheme.secondary,
        onSecondary = accentScheme.onSecondary,
        secondaryContainer = accentScheme.secondaryContainer,
        onSecondaryContainer = accentScheme.onSecondaryContainer,
        secondaryFixed = accentScheme.secondaryFixed,
        secondaryFixedDim = accentScheme.secondaryFixedDim,
        onSecondaryFixed = accentScheme.onSecondaryFixed,
        onSecondaryFixedVariant = accentScheme.onSecondaryFixedVariant,
        tertiary = accentScheme.tertiary,
        onTertiary = accentScheme.onTertiary,
        tertiaryContainer = accentScheme.tertiaryContainer,
        onTertiaryContainer = accentScheme.onTertiaryContainer,
        tertiaryFixed = accentScheme.tertiaryFixed,
        tertiaryFixedDim = accentScheme.tertiaryFixedDim,
        onTertiaryFixed = accentScheme.onTertiaryFixed,
        onTertiaryFixedVariant = accentScheme.onTertiaryFixedVariant,
        surfaceTint = accentScheme.surfaceTint,
        inversePrimary = accentScheme.inversePrimary,
        background = background,
        onBackground = primaryText,
        surface = background,
        onSurface = primaryText,
        surfaceVariant = surface,
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = background,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = lerp(background, primaryText, if (darkTheme) 0.24f else 0.13f),
        outline = secondaryText.copy(alpha = 0.72f),
        outlineVariant = secondaryText.copy(alpha = 0.36f)
    ))
}

internal fun themeRoleContrastRatio(foregroundHex: String, backgroundHex: String): Float {
    val foreground = parseThemeRoleColor(foregroundHex, Color.Black)
    val background = parseThemeRoleColor(backgroundHex, Color.White)
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun hasThemeRoleContrastWarning(roles: ThemeModeRoleOverrides): Boolean {
    return themeRoleContrastRatio(roles.primaryTextHex, roles.backgroundHex) < 4.5f ||
        themeRoleContrastRatio(roles.secondaryTextHex, roles.backgroundHex) < ACCESSIBLE_TEXT_MIN_CONTRAST
}

private fun parseThemeRoleColor(hex: String, fallback: Color): Color {
    val value = hex.removePrefix("#").toLongOrNull(16) ?: return fallback
    return if (hex.removePrefix("#").length == 6) {
        Color(0xFF000000 or value)
    } else {
        fallback
    }
}

internal fun resolveReadableForeground(background: Color): Color {
    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun resolveLiquidControlAccentColor(
    source: Md3ColorSource,
    customSeed: Color,
    generatedAccent: Color,
    roleOverridesEnabled: Boolean,
    roleControlHex: String? = null,
): Color = if (source == Md3ColorSource.CUSTOM) {
    if (roleOverridesEnabled && roleControlHex != null) {
        parseThemeRoleColor(roleControlHex, customSeed)
    } else if (roleOverridesEnabled) {
        generatedAccent
    } else {
        customSeed
    }
} else {
    generatedAccent
}

/** Shared finalization for app endpoints and settings previews. Glass-only accent changes
 * remain explicitly gated here; native layouts and defaults are not changed. */
internal fun resolveConfiguredMaterialScheme(
    base: ColorScheme,
    overrides: ThemeRoleOverrides,
    darkTheme: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    colorContrast: Contrast,
    seedPaletteStyle: SeedPaletteStyle,
    source: Md3ColorSource,
    customSeed: Color,
    liquidGlassEnabled: Boolean,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
): ColorScheme {
    val overridden = applyThemeRoleOverrides(base, overrides, darkTheme, paletteStyle, colorSpec, colorContrast)
    if (seedPaletteStyle.enabled || source != Md3ColorSource.CUSTOM) return overridden
    // Standard custom colors display the exact picked accent in both Miuix and Material 3
    // regardless of whether liquid glass is enabled or which ColorSpec/PaletteStyle/Contrast
    // is selected for surfaces. Advanced role overrides keep generated role pairs unless
    // liquid glass aligns Material 3 controls to the edited control color.
    if (overrides.enabled && !(liquidGlassEnabled && uiStyle != AppUiStyle.MIUIX)) {
        return overridden
    }
    val accent = resolveLiquidControlAccentColor(
        source, customSeed, overridden.primary, overrides.enabled,
        if (darkTheme) overrides.dark.controlAccentHex else overrides.light.controlAccentHex,
    )
    return alignCustomAccentColorScheme(overridden, accent)
}
