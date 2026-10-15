package com.android.purebilibili.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.core.store.WallpaperPaletteOverrides
import com.android.purebilibili.core.store.WallpaperPaletteRole
import com.android.purebilibili.feature.settings.Md3ColorSource

/** One endpoint resolver for app content, both component renderers, glass and notifications.
 * Call before animation and cache by settings/base palette, never by interpolated frame colors.
 * Each wallpaper role is overridden once. Native neutral surfaces retain their style. */
internal fun resolveAppThemeEndpointPalette(
    base: ColorScheme,
    uiStyle: AppUiStyle,
    source: Md3ColorSource,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean = false,
    wallpaperOverrides: WallpaperPaletteOverrides = WallpaperPaletteOverrides(),
    customSeed: Color = base.primary,
    roleOverrides: ThemeRoleOverrides = ThemeRoleOverrides(),
): AppThemePalette {
    val configured = applyWallpaperPaletteOverrides(base, wallpaperOverrides, source, darkTheme)
    val selectedAccent = resolveLiquidControlAccentColor(
        source = source,
        customSeed = customSeed,
        generatedAccent = configured.primary,
        roleOverridesEnabled = roleOverrides.enabled,
        roleControlHex = if (darkTheme) roleOverrides.dark.controlAccentHex else roleOverrides.light.controlAccentHex,
    )
    // Exact manual primary is a color-source contract, independent of glass rendering.
    // Preserve native/generated containers, secondary/tertiary roles and neutral surfaces;
    // only glass-specific container treatment belongs in alignLiquidAccentColorScheme.
    val material = if (source == Md3ColorSource.CUSTOM) configured.copy(
        primary = selectedAccent,
        onPrimary = resolveReadableForeground(selectedAccent),
    ) else configured
    val nativeMiuix = shouldUseNativeMiuixPalette(uiStyle)
    val bridgedMiuix = if (nativeMiuix) {
        resolveNativeMiuixColors(material, darkTheme, amoledDarkTheme)
    } else resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(material), darkTheme)
    // Both hosts can contain Miuix components. Apply explicit secondary edits after
    // either bridge, while unedited secondary controls retain their native neutral role.
    val miuix = if (source == Md3ColorSource.WALLPAPER_RETONED &&
        WallpaperPaletteRole.SECONDARY in wallpaperOverrides.forMode(darkTheme)
    ) bridgedMiuix.copy(secondary = material.secondary, onSecondary = material.onSecondary)
        else bridgedMiuix
    val aligned = if (nativeMiuix) {
        alignMaterialSurfacesWithMiuix(
            material, miuix, preserveMaterialAccentRoles = source == Md3ColorSource.WALLPAPER_RETONED,
        )
    } else material
    return AppThemePalette(aligned, miuix, aligned.primary)
}
