package com.android.purebilibili.core.theme

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.WallpaperPaletteOverrides
import com.android.purebilibili.core.store.WallpaperPaletteRole
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.video.player.resolvePlaybackNotificationAccentColor
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AppThemeEndpointPolicyTest {
    @Test
    fun standardMiuixCustomColorsUseNativeNeutralSurfacesAndExactAccentAcrossMaterials() {
        for (dark in listOf(false, true)) {
            for (amoled in if (dark) listOf(false, true) else listOf(false)) {
                for (seed in listOf(Color(0xFFF36595), Color(0xFF34C759), Color.White, Color.Black, Color(0xFF007AFF))) {
                    val base = createBiliPaiStyleColorScheme(
                        seed, dark, amoled, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
                    )
                    val endpoint = resolveAppThemeEndpointPalette(
                        base, AppUiStyle.MIUIX, Md3ColorSource.CUSTOM, dark,
                        amoledDarkTheme = amoled, customSeed = seed, liquidGlassEnabled = false,
                    )
                    val glass = resolveAppThemeEndpointPalette(
                        base, AppUiStyle.MIUIX, Md3ColorSource.CUSTOM, dark,
                        amoledDarkTheme = amoled, customSeed = seed, liquidGlassEnabled = true,
                    )
                    val expectedNative = resolveNativeMiuixColors(base, dark, amoled)
                    val colors = endpoint.miuix
                    assertEquals(seed, colors.primary)
                    assertEquals(resolveReadableForeground(seed), colors.onPrimary)
                    assertEquals(colors.primaryContainer, colors.secondaryContainer)
                    assertEquals(colors.onPrimaryContainer, colors.onSecondaryContainer)
                    assertEquals(expectedNative.surface, colors.surface)
                    assertEquals(expectedNative.background, colors.background)
                    assertEquals(expectedNative.surfaceContainer, colors.surfaceContainer)
                    assertEquals(expectedNative.secondary, colors.secondary)
                    assertEquals(expectedNative.onSecondary, colors.onSecondary)
                    assertEquals(seed, colors.sliderKeyPointForeground)
                    assertEquals(seed, endpoint.material.primary)
                    assertEquals(colors.onPrimary, endpoint.material.onPrimary)
                    assertEquals(colors.surface, endpoint.material.background)
                    assertEquals(colors.surface, endpoint.material.surface)
                    assertEquals(colors.surfaceContainer, endpoint.material.surfaceContainer)
                    assertEquals(colors.secondaryContainer, endpoint.material.secondaryContainer)
                    assertEquals(colors.onSecondaryContainer, endpoint.material.onSecondaryContainer)
                    assertEquals(seed, endpoint.liquidAccent)
                    assertEquals(endpoint.liquidAccent, glass.liquidAccent)
                    assertEquals(colors.primary, glass.miuix.primary)
                    assertEquals(colors.primaryContainer, glass.miuix.primaryContainer)
                    assertEquals(colors.onPrimaryContainer, glass.miuix.onPrimaryContainer)
                    assertEquals(colors.surface, glass.miuix.surface)
                    assertEquals(colors.secondaryContainer, glass.material.secondaryContainer)
                    assertEquals(colors.onSecondaryContainer, glass.material.onSecondaryContainer)
                }
            }
        }
    }

    @Test
    fun materialCustomSelectionUsesExactAccentInBothGlassStatesWhileKeepingMd3Surfaces() {
        for (dark in listOf(false, true)) {
            val seed = Color(0xFF007AFF)
            val base = createBiliPaiStyleColorScheme(
                seed, dark, false, PaletteStyle.Expressive, ColorSpec.SpecVersion.SPEC_2025,
            )
            val expected = alignCustomAccentColorScheme(base, seed)
            val endpoint = resolveAppThemeEndpointPalette(
                base, AppUiStyle.MATERIAL3, Md3ColorSource.CUSTOM, dark,
                customSeed = seed, liquidGlassEnabled = false,
            )
            val glassConfigured = alignLiquidAccentColorScheme(
                scheme = base,
                accent = seed,
                liquidGlassEnabled = true,
                source = Md3ColorSource.CUSTOM,
            )
            val glass = resolveAppThemeEndpointPalette(
                glassConfigured, AppUiStyle.MATERIAL3, Md3ColorSource.CUSTOM, dark,
                customSeed = seed, liquidGlassEnabled = true,
            )
            assertEquals(seed, endpoint.material.primary)
            assertEquals(seed, endpoint.liquidAccent)
            assertEquals(expected.onPrimary, endpoint.material.onPrimary)
            assertEquals(expected.primaryContainer, endpoint.material.primaryContainer)
            assertEquals(expected.secondaryContainer, endpoint.material.secondaryContainer)
            assertEquals(expected.onSecondaryContainer, endpoint.material.onSecondaryContainer)
            assertEquals(base.surface, endpoint.material.surface)
            assertEquals(base.surfaceContainer, endpoint.material.surfaceContainer)
            assertTrue(
                calculateContrastRatio(
                    endpoint.material.onSecondaryContainer,
                    endpoint.material.secondaryContainer,
                ) >= ACCESSIBLE_TEXT_MIN_CONTRAST
            )
            assertEquals(endpoint.material, glass.material)
            assertEquals(endpoint.miuix, glass.miuix)
            assertEquals(endpoint.liquidAccent, glass.liquidAccent)
        }
    }

    @Test
    fun editedWallpaperRolesAgreeAcrossMaterialMiuixGlassAndNotifications() {
        val seed = Color(0xFF8054A8)
        val edits = mapOf(
            WallpaperPaletteRole.PRIMARY to "#007AFF",
            WallpaperPaletteRole.SECONDARY to "#34C759",
            WallpaperPaletteRole.TERTIARY to "#AF52DE",
            WallpaperPaletteRole.CONTAINER to "#123456",
        )
        val overrides = WallpaperPaletteOverrides(edits, edits)
        for (style in AppUiStyle.entries) for (dark in listOf(false, true)) {
            val base = createBiliPaiStyleColorScheme(
                seed, dark, false, PaletteStyle.Expressive, ColorSpec.SpecVersion.SPEC_2025,
            )
            val endpoint = resolveAppThemeEndpointPalette(
                base, style, Md3ColorSource.WALLPAPER_RETONED, dark, wallpaperOverrides = overrides,
            )
            assertEquals(Color(0xFF007AFF), endpoint.material.primary)
            assertEquals(endpoint.material.primary, endpoint.miuix.primary)
            assertEquals(endpoint.material.primary, endpoint.liquidAccent)
            assertEquals(Color(0xFF34C759), endpoint.material.secondary)
            assertEquals(endpoint.material.secondary, endpoint.miuix.secondary)
            assertEquals(Color(0xFFAF52DE), endpoint.material.tertiary)
            assertEquals(Color(0xFF123456), endpoint.material.primaryContainer)
            assertEquals(endpoint.material.primaryContainer, endpoint.miuix.primaryContainer)
            assertEquals(endpoint.material.onPrimary, endpoint.miuix.onPrimary)
            val settings = AppThemeSettings(
                uiStyle = style,
                md3ColorSource = Md3ColorSource.WALLPAPER_RETONED,
                colorStyle = PaletteStyle.Expressive,
                wallpaperPaletteOverrides = overrides,
            )
            assertEquals(endpoint.material.primary, resolvePlaybackNotificationAccentColor(
                settings, dark, wallpaperSeed = seed,
            ))
        }
    }

    @Test
    fun restoringWallpaperRolesRestoresTheSameEndpointAsFreshSettings() {
        val base = createBiliPaiStyleColorScheme(
            Color(0xFF8054A8), false, false, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
        )
        for (style in AppUiStyle.entries) {
            val fresh = resolveAppThemeEndpointPalette(base, style, Md3ColorSource.WALLPAPER_RETONED, false)
            val reset = resolveAppThemeEndpointPalette(
                base, style, Md3ColorSource.WALLPAPER_RETONED, false,
                wallpaperOverrides = WallpaperPaletteOverrides(),
            )
            assertEquals(fresh.material.primary, reset.material.primary)
            assertEquals(fresh.material.secondary, reset.material.secondary)
            assertEquals(fresh.material.tertiary, reset.material.tertiary)
            assertEquals(fresh.material.primaryContainer, reset.material.primaryContainer)
            assertEquals(fresh.liquidAccent, reset.liquidAccent)
        }
    }
}
