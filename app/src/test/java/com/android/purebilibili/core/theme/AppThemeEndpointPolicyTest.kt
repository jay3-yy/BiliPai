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
    fun standardMiuixCustomColorsShareSelectionAndAccentAcrossMaterials() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(Color(0xFFF36595), Color(0xFF34C759), Color.White, Color.Black)) {
                val base = createBiliPaiStyleColorScheme(
                    seed, dark, false, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025,
                )
                val endpoint = resolveAppThemeEndpointPalette(
                    base, AppUiStyle.MIUIX, Md3ColorSource.CUSTOM, dark,
                    customSeed = seed, liquidGlassEnabled = false,
                )
                val glass = resolveAppThemeEndpointPalette(
                    base, AppUiStyle.MIUIX, Md3ColorSource.CUSTOM, dark,
                    customSeed = seed, liquidGlassEnabled = true,
                )
                val colors = endpoint.miuix
                assertEquals(base.primary, colors.primary)
                assertEquals(base.onPrimary, colors.onPrimary)
                assertEquals(base.primaryContainer, colors.primaryContainer)
                assertEquals(base.onPrimaryContainer, colors.onPrimaryContainer)
                assertEquals(colors.primaryContainer, colors.secondaryContainer)
                assertEquals(colors.onPrimaryContainer, colors.onSecondaryContainer)
                assertEquals(base.surface, colors.surface)
                assertEquals(base.surfaceContainer, colors.surfaceContainer)
                assertEquals(base.outlineVariant, colors.secondary)
                assertEquals(base.outline, colors.onSecondary)
                assertEquals(base.primary, colors.onBackgroundVariant)
                val disabled = opaqueCompositeOver(base.primary.copy(alpha = 0.38f), base.surface)
                assertEquals(disabled, colors.disabledPrimary)
                assertEquals(disabled, colors.disabledPrimaryButton)
                assertEquals(disabled, colors.disabledPrimarySlider)
                assertEquals(opaqueCompositeOver(base.onPrimary.copy(alpha = 0.6f), disabled), colors.disabledOnPrimaryButton)
                assertEquals(base.primary, colors.sliderKeyPoint)
                assertEquals(base.surfaceContainerHigh, colors.sliderKeyPointForeground)
                assertEquals(base.primary, endpoint.material.primary)
                assertEquals(base.onPrimary, endpoint.material.onPrimary)
                assertEquals(colors.secondaryContainer, endpoint.material.secondaryContainer)
                assertEquals(colors.onSecondaryContainer, endpoint.material.onSecondaryContainer)
                assertEquals(base.primary, endpoint.liquidAccent)
                assertEquals(endpoint.liquidAccent, glass.liquidAccent)
                assertEquals(colors.primaryContainer, glass.miuix.primaryContainer)
                assertEquals(colors.onPrimaryContainer, glass.miuix.onPrimaryContainer)
                assertEquals(colors.secondaryContainer, glass.material.secondaryContainer)
                assertEquals(colors.onSecondaryContainer, glass.material.onSecondaryContainer)
            }
        }
    }

    @Test
    fun materialCustomSelectionPreservesOfficialMd3HctRolesWhenGlassIsOff() {
        for (dark in listOf(false, true)) {
            val seed = Color(0xFF007AFF)
            val base = createBiliPaiStyleColorScheme(
                seed, dark, false, PaletteStyle.Expressive, ColorSpec.SpecVersion.SPEC_2025,
            )
            val endpoint = resolveAppThemeEndpointPalette(
                base, AppUiStyle.MATERIAL3, Md3ColorSource.CUSTOM, dark,
                customSeed = seed, liquidGlassEnabled = false,
            )
            assertEquals(base.primary, endpoint.material.primary)
            assertEquals(base.onPrimary, endpoint.material.onPrimary)
            assertEquals(base.primaryContainer, endpoint.material.primaryContainer)
            assertEquals(base.onPrimaryContainer, endpoint.material.onPrimaryContainer)
            assertEquals(base.secondaryContainer, endpoint.material.secondaryContainer)
            assertEquals(base.onSecondaryContainer, endpoint.material.onSecondaryContainer)
            assertEquals(base.tertiary, endpoint.material.tertiary)
            assertTrue(
                calculateContrastRatio(
                    endpoint.material.onSecondaryContainer,
                    endpoint.material.secondaryContainer,
                ) >= ACCESSIBLE_TEXT_MIN_CONTRAST
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
            assertEquals(seed, glass.material.primary)
            assertEquals(glassConfigured.secondaryContainer, glass.material.secondaryContainer)
            assertEquals(base.surface, glass.material.surface)
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
