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
    val pickedAccent = resolveLiquidControlAccentColor(
        source = source,
        customSeed = customSeed,
        generatedAccent = configured.primary,
        roleOverridesEnabled = roleOverrides.enabled,
        roleControlHex = if (darkTheme) roleOverrides.dark.controlAccentHex else roleOverrides.light.controlAccentHex,
    )
    val selectedAccent = pickedAccent
    val exactPrimary = if (!preserveGeneratedPalette && source == Md3ColorSource.CUSTOM) configured.copy(
        primary = selectedAccent,
        onPrimary = resolveReadableForeground(selectedAccent),
    ) else configured
    val nativeMiuix = shouldUseNativeMiuixPalette(uiStyle)
    val nativeMiuixColors = if (nativeMiuix) {
        resolveNativeMiuixColors(
            exactPrimary, darkTheme, amoledDarkTheme,
            customRolesEnabled = source == Md3ColorSource.CUSTOM && roleOverrides.enabled && !preserveGeneratedPalette,
        )
    } else null
    // 原生导航指示器、tonal 按钮及筛选胶囊消费 secondaryContainer，而非 primary。
    // 自定义色在关闭玻璃后也必须覆盖这个选中角色；只桥接颜色，不改原生布局和动效。
    // 玻璃分支沿用 alignLiquidAccentColorScheme，壁纸配色保留其独立角色。
    val useNativeCustomSelection = !preserveGeneratedPalette && !liquidGlassEnabled && source == Md3ColorSource.CUSTOM
    val material = if (useNativeCustomSelection) {
        val selectionSurface = nativeMiuixColors?.surface ?: exactPrimary.surface
        val selection = resolveTintedSelectionAccentColors(selectedAccent, selectionSurface)
        // Standard custom colors share one accent family across tonal actions and tabs.
        // Glass and generated/wallpaper palettes retain their independent role contracts.
        exactPrimary.copy(
            primaryContainer = selection.backgroundColor,
            onPrimaryContainer = selection.contentColor,
            secondaryContainer = selection.backgroundColor,
            onSecondaryContainer = selection.contentColor,
        )
    } else exactPrimary
    val bridgedMiuix = nativeMiuixColors
        ?: resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(material), darkTheme)
    // Both hosts can contain Miuix components. Apply explicit secondary edits after
    // either bridge, while unedited secondary controls retain their native neutral role.
    val selectionMiuix = if (useNativeCustomSelection) bridgedMiuix.copy(
        primaryContainer = material.primaryContainer,
        onPrimaryContainer = material.onPrimaryContainer,
        secondaryContainer = material.secondaryContainer,
        onSecondaryContainer = material.onSecondaryContainer,
    ) else bridgedMiuix
    val miuix = if (source == Md3ColorSource.WALLPAPER_RETONED &&
        WallpaperPaletteRole.SECONDARY in wallpaperOverrides.forMode(darkTheme)
    ) selectionMiuix.copy(secondary = material.secondary, onSecondary = material.onSecondary)
        else selectionMiuix
    val aligned = if (nativeMiuix) {
        alignMaterialSurfacesWithMiuix(
            material, miuix, preserveMaterialAccentRoles = preserveGeneratedPalette || source == Md3ColorSource.WALLPAPER_RETONED,
        )
    } else material
    return AppThemePalette(aligned, miuix, aligned.primary)
}
