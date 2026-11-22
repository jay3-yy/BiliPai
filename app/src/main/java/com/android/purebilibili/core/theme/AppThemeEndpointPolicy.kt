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
        // 普通自定义色的选中容器共用主色容器；高级编辑和壁纸色板可独立指定角色。
        val palette = if (source == Md3ColorSource.CUSTOM && !roleOverrides.enabled && !preserveGeneratedPalette) {
            configured.copy(
                secondaryContainer = configured.primaryContainer,
                onSecondaryContainer = configured.onPrimaryContainer,
            )
        } else configured
        val generatedMiuix = resolveMiuixColorsFromMaterialBridge(
            createMiuixMaterialBridge(palette), darkTheme, useMonetMapping = true,
        )
        val miuix = if (source == Md3ColorSource.WALLPAPER_RETONED &&
            WallpaperPaletteRole.SECONDARY in wallpaperOverrides.forMode(darkTheme)
        ) generatedMiuix.copy(secondary = configured.secondary, onSecondary = configured.onSecondary)
            else generatedMiuix
        val material = alignMaterialSurfacesWithMiuix(
            palette, miuix, preserveMaterialAccentRoles = true,
        )
        return AppThemePalette(material, miuix, material.primary)
    }
    // 非玻璃 MD3 保留 MaterialKolor / 系统动态取色生成的完整 HCT 角色（primary /
    // primaryContainer / secondaryContainer / tertiary 分离）；液态玻璃开启时的
    // 精准主色同步已在 resolveConfiguredMaterialScheme 的 alignLiquidAccentColorScheme 完成。
    val bridgedMiuix = resolveMiuixColorsFromMaterialBridge(
        createMiuixMaterialBridge(configured),
        darkTheme,
    )
    // Both hosts can contain Miuix components. Apply explicit secondary edits after
    // the bridge, while unedited secondary controls retain their neutral role.
    val miuix = if (source == Md3ColorSource.WALLPAPER_RETONED &&
        WallpaperPaletteRole.SECONDARY in wallpaperOverrides.forMode(darkTheme)
    ) bridgedMiuix.copy(secondary = configured.secondary, onSecondary = configured.onSecondary)
        else bridgedMiuix
    return AppThemePalette(configured, miuix, configured.primary)
}
