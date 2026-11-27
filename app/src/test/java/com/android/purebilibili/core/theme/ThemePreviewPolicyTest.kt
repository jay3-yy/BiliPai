package com.android.purebilibili.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemePreviewPolicyTest {
    @Test
    fun wallpaperModePreviewsUseActualLightAndDarkSchemesInsteadOfFallbackSeed() {
        val light = lightColorScheme(primary = Color(0xFF006A60), background = Color(0xFFF4FBF8))
        val dark = darkColorScheme(primary = Color(0xFF80D5C8), background = Color(0xFF101815))
        val wallpaper = ThemeWallpaperPreviewSchemes(light, dark)
        for (glass in listOf(false, true)) for (isDark in listOf(false, true)) {
            for (amoled in listOf(false, true)) {
                val configuration = ThemePreviewConfiguration(
                    source = Md3ColorSource.FOLLOW_WALLPAPER,
                    liquidGlassEnabled = glass,
                    amoledDarkTheme = amoled,
                    wallpaperSchemes = wallpaper,
                )
                val preview = resolveThemePreviewPalette(Color(0xFFFF4081), isDark,
                    PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025, Contrast.Default,
                    SeedPaletteStyle.Standard, configuration).material
                val actual = if (isDark) dark else light
                assertEquals(actual.primary, preview.primary)
                assertEquals(if (isDark && amoled) Color.Black else actual.background, preview.background)
            }
        }
    }

    @Test
    fun customColorCandidatesIgnoreWallpaperPreviewSchemes() {
        val configuration = ThemePreviewConfiguration(source = Md3ColorSource.CUSTOM)
        for (dark in listOf(false, true)) {
            val expected = resolveThemePreviewPalette(seed, dark, PaletteStyle.TonalSpot,
                ColorSpec.SpecVersion.SPEC_2025, Contrast.Default, SeedPaletteStyle.Standard, configuration)
            val actual = resolveThemePreviewPalette(seed, dark, PaletteStyle.TonalSpot,
                ColorSpec.SpecVersion.SPEC_2025, Contrast.Default, SeedPaletteStyle.Standard,
                configuration.copy(wallpaperSchemes = ThemeWallpaperPreviewSchemes(
                    lightColorScheme(primary = Color.Red), darkColorScheme(primary = Color.Blue),
                )))
            assertEquals(expected.material.primary, actual.material.primary)
            assertEquals(expected.material.background, actual.material.background)
        }
    }

    private val seed = Color(0xFF34C759)
    private val overrides = ThemeRoleOverrides(
        enabled = true,
        light = ThemeModeRoleOverrides("#FFF4E8", "#EEEEEE", "#DDDDDD", "#8800AA"),
        dark = ThemeModeRoleOverrides("#130F19", "#222222", "#333333", "#8800AA"),
    )

    @Test
    fun previewMatchesFinalAppEndpointsAcrossBothNativeStylesGlassAndDarkModes() {
        for (uiStyle in AppUiStyle.entries) for (dark in listOf(false, true)) {
            for (glass in listOf(false, true)) for (seedStyle in listOf(SeedPaletteStyle.Standard, SeedPaletteStyle.CandyPop)) {
                val configuration = ThemePreviewConfiguration(
                    uiStyle = uiStyle, source = Md3ColorSource.CUSTOM,
                    liquidGlassEnabled = glass, amoledDarkTheme = true, roleOverrides = overrides,
                )
                val effective = resolveEffectiveThemeRoleOverrides(Md3ColorSource.CUSTOM, overrides)
                    .let { if (seedStyle.enabled) it.copy(enabled = false) else it }
                val base = createBiliPaiStyleColorScheme(
                    seed, dark, true, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
                    uiStyle = uiStyle, seedPaletteStyle = seedStyle,
                )
                val edited = applyThemeRoleOverrides(base, effective, dark)
                val configured = if (seedStyle.enabled) edited else alignLiquidAccentColorScheme(
                    edited, resolveLiquidControlAccentColor(
                        Md3ColorSource.CUSTOM, seed, edited.primary, effective.enabled,
                        if (dark) effective.dark.controlAccentHex else effective.light.controlAccentHex,
                    ), glass && uiStyle != AppUiStyle.MIUIX, Md3ColorSource.CUSTOM,
                )
                val expected = resolveAppThemeEndpointPalette(
                    configured, uiStyle, Md3ColorSource.CUSTOM, dark, amoledDarkTheme = true,
                    customSeed = seed, roleOverrides = effective, liquidGlassEnabled = glass,
                    preserveGeneratedPalette = seedStyle.enabled,
                )
                val preview = resolveThemePreviewPalette(seed, dark, PaletteStyle.TonalSpot,
                    ColorSpec.SpecVersion.SPEC_2025, Contrast.Default, seedStyle, configuration)
                assertEquals(expected.material.primary, preview.material.primary)
                assertEquals(expected.material.background, preview.material.background)
                assertEquals(expected.material.onSurface, preview.material.onSurface)
                assertEquals(expected.material.onSurfaceVariant, preview.material.onSurfaceVariant)
                assertEquals(expected.material.secondaryContainer, preview.material.secondaryContainer)
                assertEquals(expected.material.onSecondaryContainer, preview.material.onSecondaryContainer)
                assertEquals(expected.miuix.primary, preview.miuix.primary)
                assertEquals(expected.miuix.onSecondaryContainer, preview.miuix.onSecondaryContainer)
                assertEquals(assessThemeContrast(expected.material), assessThemeContrast(preview.material))
            }
        }
    }

    @Test
    fun miuixAdvancedColorsReachBothRenderersAndDisablingRestoresNativePalette() {
        val edits = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#FFF4E8", "#201A17", "#655D57", "#8800AA"),
            dark = ThemeModeRoleOverrides("#130F19", "#F6E8FF", "#BEAFCE", "#C880F0"),
        )
        for (dark in listOf(false, true)) for (glass in listOf(false, true)) {
            for (amoled in listOf(false, true)) {
                val configuration = ThemePreviewConfiguration(
                    uiStyle = AppUiStyle.MIUIX, liquidGlassEnabled = glass,
                    amoledDarkTheme = amoled, roleOverrides = edits,
                )
                fun preview(config: ThemePreviewConfiguration, style: SeedPaletteStyle = SeedPaletteStyle.Standard) =
                    resolveThemePreviewPalette(seed, dark, PaletteStyle.TonalSpot,
                        ColorSpec.SpecVersion.SPEC_2025, Contrast.Default, style, config)

                val edited = preview(configuration)
                val roles = if (dark) edits.dark else edits.light
                assertEquals(parseMd3CustomColorHex(roles.backgroundHex), edited.material.background)
                assertEquals(parseMd3CustomColorHex(roles.primaryTextHex), edited.material.onSurface)
                assertEquals(parseMd3CustomColorHex(roles.secondaryTextHex), edited.material.onSurfaceVariant)
                val base = createBiliPaiStyleColorScheme(
                    seed, dark, amoled, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
                    uiStyle = AppUiStyle.MIUIX,
                )
                assertEquals(applyThemeRoleOverrides(base, edits, dark).primary, edited.material.primary)
                assertEquals(edited.material.background, edited.miuix.surface)
                assertEquals(edited.material.onSurface, edited.miuix.onSurface)
                assertEquals(edited.material.onSurfaceVariant, edited.miuix.onSurfaceVariantSummary)
                assertEquals(edited.material.surfaceContainer, edited.miuix.surfaceContainer)
                assertEquals(edited.material.surfaceContainerHigh, edited.miuix.surfaceContainerHigh)
                assertEquals(edited.material.primary, edited.miuix.primary)
                assertEquals(edited.material.onSecondaryContainer, edited.miuix.onSecondaryContainer)

                val disabled = configuration.copy(roleOverrides = edits.copy(enabled = false))
                val restored = preview(disabled)
                val defaults = preview(configuration.copy(roleOverrides = ThemeRoleOverrides()))
                assertEquals(createMiuixMaterialBridge(defaults.material), createMiuixMaterialBridge(restored.material))
                assertEquals(defaults.miuix.surfaceContainer, restored.miuix.surfaceContainer)
                assertNotEquals(restored.material.background, edited.material.background)
                assertNotEquals(restored.miuix.surfaceContainer, edited.miuix.surfaceContainer)
                // 扩展色板仍自行管理角色，保留高级配置但暂不应用。
                val extended = preview(configuration, SeedPaletteStyle.CandyPop)
                val extendedWithoutEdits = preview(disabled, SeedPaletteStyle.CandyPop)
                assertEquals(createMiuixMaterialBridge(extendedWithoutEdits.material), createMiuixMaterialBridge(extended.material))
            }
        }
    }

    @Test
    fun miuixContentColorsStayTheSameWhenGlassChanges() {
        for (dark in listOf(false, true)) for (amoled in listOf(false, true)) {
            for (source in Md3ColorSource.entries) for (style in listOf(SeedPaletteStyle.Standard, SeedPaletteStyle.CandyPop)) {
                for (roles in listOf(ThemeRoleOverrides(), overrides)) {
                    val config = ThemePreviewConfiguration(
                        uiStyle = AppUiStyle.MIUIX, source = source,
                        amoledDarkTheme = amoled, roleOverrides = roles,
                    )
                    fun palette(glass: Boolean) = resolveThemePreviewPalette(
                        seed, dark, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
                        Contrast.Default, style, config.copy(liquidGlassEnabled = glass),
                    )
                    val native = palette(false)
                    val glass = palette(true)
                    assertEquals(createMiuixMaterialBridge(native.material), createMiuixMaterialBridge(glass.material))
                    assertEquals(native.miuix.surface, glass.miuix.surface)
                    assertEquals(native.miuix.surfaceContainer, glass.miuix.surfaceContainer)
                    assertEquals(native.miuix.onSurfaceVariantSummary, glass.miuix.onSurfaceVariantSummary)
                    assertEquals(native.miuix.primary, glass.miuix.primary)
                }
            }
        }
    }

    @Test
    fun savingSeedSynchronizesAdvancedControlColorButEditingRolesPreservesIt() {
        for (dark in listOf(false, true)) {
            val base = createBiliPaiStyleColorScheme(
                seed, dark, false, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
            )
            val syncedOverrides = syncThemeRoleControlAccent(overrides, formatMd3CustomColorHex(seed))
            val configuration = ThemePreviewConfiguration(roleOverrides = overrides, syncControlAccentToSeed = true)
            val preview = resolveThemePreviewPalette(seed, dark, PaletteStyle.TonalSpot,
                ColorSpec.SpecVersion.SPEC_2025, Contrast.Default, SeedPaletteStyle.Standard, configuration)
            assertEquals(applyThemeRoleOverrides(base, syncedOverrides, dark).primary, preview.material.primary)
            val swatch = resolveThemePresetPaletteSwatch(seed, dark, configuration = configuration)
            assertEquals(preview.material.primary, swatch.primary)
            assertEquals(preview.material.tertiary, swatch.tertiary)
            assertEquals(preview.material.primaryContainer, swatch.primaryContainer)
            val rolePreview = resolveThemePreviewPalette(seed, dark, PaletteStyle.TonalSpot,
                ColorSpec.SpecVersion.SPEC_2025, Contrast.Default, SeedPaletteStyle.Standard,
                configuration.copy(syncControlAccentToSeed = false))
            assertEquals(applyThemeRoleOverrides(base, overrides, dark).primary, rolePreview.material.primary)
            assertNotEquals(preview.material.primary, rolePreview.material.primary)
        }
    }

    @Test
    fun standardCustomSeedStaysExactAcrossPaletteStylesWhileSeedPalettesGenerateDistinctRoles() {
        val blue = Color(0xFF007AFF)
        for (uiStyle in AppUiStyle.entries) {
            for (dark in listOf(false, true)) {
                val config = ThemePreviewConfiguration(
                    uiStyle = uiStyle,
                    source = Md3ColorSource.CUSTOM,
                    liquidGlassEnabled = true,
                )
                val defaultPreview = resolveThemePreviewPalette(
                    seed = blue,
                    darkTheme = dark,
                    style = PaletteStyle.TonalSpot,
                    spec = ColorSpec.SpecVersion.SPEC_2025,
                    contrast = Contrast.Default,
                    seedStyle = SeedPaletteStyle.Standard,
                    configuration = config,
                )
                val cmfPreview = resolveThemePreviewPalette(
                    seed = blue,
                    darkTheme = dark,
                    style = PaletteStyle.Cmf(),
                    spec = ColorSpec.SpecVersion.SPEC_2026,
                    contrast = Contrast.High,
                    seedStyle = SeedPaletteStyle.Standard,
                    configuration = config,
                )
                val monochromePreview = resolveThemePreviewPalette(
                    seed = blue,
                    darkTheme = dark,
                    style = PaletteStyle.TonalSpot,
                    spec = ColorSpec.SpecVersion.SPEC_2025,
                    contrast = Contrast.Default,
                    seedStyle = SeedPaletteStyle.Monochrome,
                    configuration = config,
                )
                val expressivePreview = resolveThemePreviewPalette(
                    seed = blue,
                    darkTheme = dark,
                    style = PaletteStyle.TonalSpot,
                    spec = ColorSpec.SpecVersion.SPEC_2025,
                    contrast = Contrast.Default,
                    seedStyle = SeedPaletteStyle.Expressive,
                    configuration = config,
                )
                assertEquals(blue, defaultPreview.material.primary)
                assertEquals(blue, defaultPreview.liquidAccent)
                assertEquals(blue, cmfPreview.material.primary)
                assertEquals(blue, cmfPreview.liquidAccent)
                assertNotEquals(blue, monochromePreview.material.primary)
                assertNotEquals(blue, expressivePreview.material.primary)
                assertNotEquals(monochromePreview.material.primary, expressivePreview.material.primary)
            }
        }
    }
}
