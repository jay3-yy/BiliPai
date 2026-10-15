package com.android.purebilibili.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.android.purebilibili.core.store.WallpaperPaletteOverrides
import com.android.purebilibili.core.store.WallpaperPaletteRole
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.settings.isValidMd3CustomColorHex

/** Exact role edits apply only to wallpaper re-toning, independently in light and dark modes.
 * Unedited roles and native neutral surfaces keep the generated/system defaults. */
internal fun applyWallpaperPaletteOverrides(
    scheme: ColorScheme,
    overrides: WallpaperPaletteOverrides,
    source: Md3ColorSource,
    darkTheme: Boolean,
): ColorScheme {
    if (source != Md3ColorSource.WALLPAPER_RETONED) return scheme
    var result = scheme
    overrides.forMode(darkTheme).forEach { (role, hex) ->
        if (!isValidMd3CustomColorHex(hex)) return@forEach
        val color = parseMd3CustomColorHex(hex)
        val content = resolveReadableForeground(color)
        val fixed = lerp(Color.White, color, 0.18f)
        val fixedDim = lerp(Color.White, color, 0.32f)
        val fixedContent = resolveReadableForeground(fixedDim)
        result = when (role) {
            WallpaperPaletteRole.PRIMARY -> result.copy(
                primary = color, onPrimary = content,
                primaryFixed = fixed, primaryFixedDim = fixedDim,
                onPrimaryFixed = fixedContent, onPrimaryFixedVariant = fixedContent,
                inversePrimary = if (calculateContrastRatio(color, result.inverseSurface) >= ACCESSIBLE_TEXT_MIN_CONTRAST) {
                    color
                } else resolveReadableForeground(result.inverseSurface),
            )
            WallpaperPaletteRole.SECONDARY -> result.copy(
                secondary = color, onSecondary = content,
                secondaryFixed = fixed, secondaryFixedDim = fixedDim,
                onSecondaryFixed = fixedContent, onSecondaryFixedVariant = fixedContent,
            )
            WallpaperPaletteRole.TERTIARY -> result.copy(
                tertiary = color, onTertiary = content,
                tertiaryFixed = fixed, tertiaryFixedDim = fixedDim,
                onTertiaryFixed = fixedContent, onTertiaryFixedVariant = fixedContent,
            )
            WallpaperPaletteRole.CONTAINER -> result.copy(
                primaryContainer = color, onPrimaryContainer = content,
            )
        }
    }
    return result
}
