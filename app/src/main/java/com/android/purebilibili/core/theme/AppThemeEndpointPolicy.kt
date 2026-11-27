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
    liquidGlassEnabled: Boolean = false,
    preserveGeneratedPalette: Boolean = false,
): AppThemePalette {
    val overridden = applyWallpaperPaletteOverrides(base, wallpaperOverrides, source, darkTheme)
    // Wallpaper edits can change a role's backdrop. Repair only extended foregrounds
    // before either native bridge; glass on/off consumes the same corrected palette.
    val configured = if (preserveGeneratedPalette) enforceSeedTextContrast(overridden) else overridden
    // Miuix 的原生控件和玻璃控件共用当前明暗模式解析后的主色。
    if (uiStyle == AppUiStyle.MIUIX) {
        if (source == Md3ColorSource.CUSTOM && !roleOverrides.enabled && !preserveGeneratedPalette) {
            // 普通自定义色在 Miuix 下保留原生中性画布与卡片层次（亮色 #F7F7F7 / 纯白，
            // 深色 #121212 / AMOLED 纯黑），并将所选主色直接用于高亮与液态玻璃控件。
            val nativeBase = resolveNativeMiuixColors(
                configured, darkTheme, amoledDarkTheme, customRolesEnabled = false,
            )
            val accentAligned = alignCustomAccentColorScheme(
                configured.copy(surface = nativeBase.surface),
                customSeed,
            )
            val nativeMiuix = resolveNativeMiuixColors(
                accentAligned, darkTheme, amoledDarkTheme, customRolesEnabled = false,
            )
            val miuix = nativeMiuix.copy(
                secondaryContainer = nativeMiuix.primaryContainer,
                onSecondaryContainer = nativeMiuix.onPrimaryContainer,
            )
            val material = alignMaterialSurfacesWithMiuix(
                accentAligned, miuix, preserveMaterialAccentRoles = true,
            )
            return AppThemePalette(material, miuix, material.primary)
        }
        val generatedMiuix = resolveMiuixColorsFromMaterialBridge(
            createMiuixMaterialBridge(configured), darkTheme, useMonetMapping = true,
        )
        val miuix = if (source == Md3ColorSource.WALLPAPER_RETONED &&
            WallpaperPaletteRole.SECONDARY in wallpaperOverrides.forMode(darkTheme)
        ) generatedMiuix.copy(secondary = configured.secondary, onSecondary = configured.onSecondary)
            else generatedMiuix
        val material = alignMaterialSurfacesWithMiuix(
            configured, miuix, preserveMaterialAccentRoles = true,
        )
        return AppThemePalette(material, miuix, material.primary)
    }
    // 标准自定义色在 MD3 下（无论是否开启液态玻璃）均保留 Material 表面层级并直出所选原色；
    // 扩展调色板、高级自定义与壁纸取色继续保留各自的 HCT 生成角色。
    val materialScheme = if (
        source == Md3ColorSource.CUSTOM && !roleOverrides.enabled && !preserveGeneratedPalette
    ) {
        alignCustomAccentColorScheme(configured, customSeed)
    } else {
        configured
    }
    val bridgedMiuix = resolveMiuixColorsFromMaterialBridge(
        createMiuixMaterialBridge(materialScheme),
        darkTheme,
    )
    // Both hosts can contain Miuix components. Apply explicit secondary edits after
    // the bridge, while unedited secondary controls retain their neutral role.
    val miuix = if (source == Md3ColorSource.WALLPAPER_RETONED &&
        WallpaperPaletteRole.SECONDARY in wallpaperOverrides.forMode(darkTheme)
    ) bridgedMiuix.copy(secondary = materialScheme.secondary, onSecondary = materialScheme.onSecondary)
        else bridgedMiuix
    return AppThemePalette(materialScheme, miuix, materialScheme.primary)
}
