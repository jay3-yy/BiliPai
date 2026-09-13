package com.android.purebilibili.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource

internal val LocalBaseThemeRoleOverrides = staticCompositionLocalOf {
    ThemeRoleOverrides()
}

internal fun themeRoleOverridesFromSchemes(
    lightScheme: ColorScheme,
    darkScheme: ColorScheme
): ThemeRoleOverrides {
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
        light = lightScheme.toRoles(),
        dark = darkScheme.toRoles()
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

internal fun resolveEffectiveThemeRoleOverrides(
    source: Md3ColorSource,
    overrides: ThemeRoleOverrides,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
): ThemeRoleOverrides {
    return if (source == Md3ColorSource.CUSTOM && uiStyle == AppUiStyle.MATERIAL3) {
        overrides
    } else {
        overrides.copy(enabled = false)
    }
}

internal fun applyThemeRoleOverrides(
    scheme: ColorScheme,
    overrides: ThemeRoleOverrides,
    darkTheme: Boolean
): ColorScheme {
    if (!overrides.enabled) return scheme

    val roles = if (darkTheme) overrides.dark else overrides.light
    val background = parseThemeRoleColor(roles.backgroundHex, scheme.background)
    val primaryText = parseThemeRoleColor(roles.primaryTextHex, scheme.onBackground)
    val secondaryText = parseThemeRoleColor(roles.secondaryTextHex, scheme.onSurfaceVariant)
    val accent = parseThemeRoleColor(roles.controlAccentHex, scheme.primary)
    val onAccent = resolveReadableForeground(accent)
    val surfaceLow = lerp(background, primaryText, if (darkTheme) 0.08f else 0.035f)
    val surface = lerp(background, primaryText, if (darkTheme) 0.12f else 0.055f)
    val surfaceHigh = lerp(background, primaryText, if (darkTheme) 0.18f else 0.09f)

    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(background, accent, if (darkTheme) 0.34f else 0.18f),
        onPrimaryContainer = primaryText,
        secondary = accent,
        onSecondary = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
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
    )
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
        themeRoleContrastRatio(roles.secondaryTextHex, roles.backgroundHex) < 3f
}

private fun parseThemeRoleColor(hex: String, fallback: Color): Color {
    val value = hex.removePrefix("#").toLongOrNull(16) ?: return fallback
    return if (hex.removePrefix("#").length == 6) {
        Color(0xFF000000 or value)
    } else {
        fallback
    }
}

private fun resolveReadableForeground(background: Color): Color {
    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
