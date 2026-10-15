package com.android.purebilibili.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.android.purebilibili.feature.settings.Md3ColorSource

/**
 * Glass chrome uses the picked color, so ordinary content beside it must use the same
 * accent family. Keep this boundary at the theme entry: disabling glass or following
 * wallpaper preserves the complete native MD3/Miuix palette and its default roles.
 * Neutral surfaces, status/error colors and identity badges remain independent.
 */
internal fun alignLiquidAccentColorScheme(
    scheme: ColorScheme,
    accent: Color,
    liquidGlassEnabled: Boolean,
    source: Md3ColorSource,
): ColorScheme {
    if (!liquidGlassEnabled || source != Md3ColorSource.CUSTOM) return scheme

    fun readableAccent(background: Color): Color {
        val target = resolveReadableForeground(background)
        for (step in 0..32) {
            val candidate = lerp(accent, target, step / 32f)
            if (calculateContrastRatio(candidate, background) >= ACCESSIBLE_TEXT_MIN_CONTRAST) {
                return candidate
            }
        }
        return target
    }

    // Opaque containers keep the picked hue without depending on the surface below.
    val container = lerp(scheme.surface, accent, 0.18f)
    val onContainer = readableAccent(container)
    val fixed = lerp(Color.White, accent, 0.18f)
    val fixedDim = lerp(Color.White, accent, 0.32f)
    val onFixed = readableAccent(fixedDim)
    val onAccent = resolveReadableForeground(accent)
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        primaryFixed = fixed,
        primaryFixedDim = fixedDim,
        onPrimaryFixed = onFixed,
        onPrimaryFixedVariant = onFixed,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        secondaryFixed = fixed,
        secondaryFixedDim = fixedDim,
        onSecondaryFixed = onFixed,
        onSecondaryFixedVariant = onFixed,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        tertiaryFixed = fixed,
        tertiaryFixedDim = fixedDim,
        onTertiaryFixed = onFixed,
        onTertiaryFixedVariant = onFixed,
        inversePrimary = readableAccent(scheme.inverseSurface),
    )
}
