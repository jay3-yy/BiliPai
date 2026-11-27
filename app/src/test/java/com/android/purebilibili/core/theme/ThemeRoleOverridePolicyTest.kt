package com.android.purebilibili.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeRoleOverridePolicyTest {

    @Test
    fun enablingAdvancedPalette_keepsCustomSeedAndDoesNotRegenerateFromResolvedAccents() {
        val base = themeRoleOverridesFromSchemes(lightColorScheme(), darkColorScheme())
        val result = enableThemeRoleOverrides(ThemeRoleOverrides(), base, "#007AFF")
        assertTrue(result.enabled)
        assertEquals("#007AFF", result.light.controlAccentHex)
        assertEquals("#007AFF", result.dark.controlAccentHex)
        assertEquals(base.light.backgroundHex, result.light.backgroundHex)
        val legacy = enableThemeRoleOverrides(base.copy(enabled = true), base, "#007AFF")
        assertEquals(result, legacy)
    }

    @Test
    fun reenablingAdvancedPalette_preservesEditedControlSeedsAndSurfaces() {
        val edited = ThemeRoleOverrides(
            light = ThemeModeRoleOverrides("#FFF000", "#111111", "#555555", "#34C759"),
            dark = ThemeModeRoleOverrides("#121212", "#EEEEEE", "#AAAAAA", "#FF9500"),
        )
        val base = themeRoleOverridesFromSchemes(lightColorScheme(), darkColorScheme())
        assertEquals(edited.copy(enabled = true), enableThemeRoleOverrides(edited, base, "#007AFF"))
    }

    @Test
    fun wallpaperSource_disablesAppRoleOverridesWithoutDeletingThem() {
        val overrides = ThemeRoleOverrides(enabled = true)

        val wallpaperResult = resolveEffectiveThemeRoleOverrides(
            Md3ColorSource.FOLLOW_WALLPAPER,
            overrides
        )
        val customResult = resolveEffectiveThemeRoleOverrides(Md3ColorSource.CUSTOM, overrides)

        assertFalse(wallpaperResult.enabled)
        assertEquals(overrides.light, wallpaperResult.light)
        assertEquals(overrides.dark, wallpaperResult.dark)
        assertEquals(overrides, customResult)
    }

    @Test
    fun customSource_keepsAdvancedRoleOverrides() {
        val overrides = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#F4F0E8", "#201A17", "#655D57", "#FFF000"),
        )

        val result = resolveEffectiveThemeRoleOverrides(
            source = Md3ColorSource.CUSTOM,
            overrides = overrides,
        )

        assertTrue(result.enabled)
        assertEquals(overrides.light, result.light)
        assertEquals(overrides.dark, result.dark)
    }

    @Test
    fun miuixAdvancedStartingValues_useNativeCanvasAndTextIncludingAmoled() {
        val light = lightColorScheme(background = Color.Red, onBackground = Color.Green)
        val dark = darkColorScheme(background = Color.Blue, onBackground = Color.Yellow)
        for (amoled in listOf(false, true)) {
            val result = themeRoleOverridesFromSchemes(light, dark, AppUiStyle.MIUIX, amoled)
            val nativeLight = resolveNativeMiuixColors(light, false, amoled)
            val nativeDark = resolveNativeMiuixColors(dark, true, amoled)
            assertFalse(result.enabled)
            assertEquals(formatMd3CustomColorHex(nativeLight.surface), result.light.backgroundHex)
            assertEquals(formatMd3CustomColorHex(nativeLight.onSurface), result.light.primaryTextHex)
            assertEquals(formatMd3CustomColorHex(nativeLight.onSurfaceVariantSummary), result.light.secondaryTextHex)
            assertEquals(formatMd3CustomColorHex(nativeDark.surface), result.dark.backgroundHex)
            assertEquals(formatMd3CustomColorHex(nativeDark.onSurface), result.dark.primaryTextHex)
            assertEquals(formatMd3CustomColorHex(nativeDark.onSurfaceVariantSummary), result.dark.secondaryTextHex)
        }
    }

    @Test
    fun standardCustomColor_alignsExactSeedAcrossStylesAndGlassStates() {
        val seed = Color(0xFF007AFF)
        for (uiStyle in AppUiStyle.entries) {
            for (dark in listOf(false, true)) {
                val base = if (dark) darkColorScheme(primary = Color(0xFFA8C7FA)) else lightColorScheme(primary = Color(0xFF005AC1))
                for (glass in listOf(false, true)) {
                    val configured = resolveConfiguredMaterialScheme(
                        base = base,
                        overrides = ThemeRoleOverrides(),
                        darkTheme = dark,
                        paletteStyle = com.materialkolor.PaletteStyle.TonalSpot,
                        colorSpec = com.materialkolor.dynamiccolor.ColorSpec.SpecVersion.SPEC_2025,
                        colorContrast = com.materialkolor.Contrast.Default,
                        seedPaletteStyle = SeedPaletteStyle.Standard,
                        source = Md3ColorSource.CUSTOM,
                        customSeed = seed,
                        liquidGlassEnabled = glass,
                        uiStyle = uiStyle,
                    )
                    assertEquals(seed, configured.primary)
                }
            }
        }
    }

    @Test
    fun enabledOverrides_replaceSurfacesButGenerateControlRolePairs() {
        val overrides = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#F4F0E8", "#201A17", "#655D57", "#FFF000"),
            dark = ThemeRoleOverrides().dark
        )

        val result = applyThemeRoleOverrides(lightColorScheme(), overrides, darkTheme = false)

        assertEquals(Color(0xFFF4F0E8), result.background)
        assertEquals(Color(0xFF201A17), result.onBackground)
        assertEquals(Color(0xFF655D57), result.onSurfaceVariant)
        assertEquals(Color.White, result.onPrimary)
        assertTrue(result.primary.luminance() < result.onPrimary.luminance())
        assertTrue(themeRoleContrastRatio(
            formatMd3CustomColorHex(result.onPrimary), formatMd3CustomColorHex(result.primary)
        ) >= 3f)
    }

    @Test
    fun blueControlSeed_usesDifferentLightAndDarkRolePairsInsteadOfBlackWhiteSelection() {
        val overrides = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#FFFFFF", "#111111", "#555555", "#007AFF"),
            dark = ThemeModeRoleOverrides("#121212", "#EEEEEE", "#AAAAAA", "#007AFF"),
        )
        val light = applyThemeRoleOverrides(lightColorScheme(), overrides, darkTheme = false)
        val dark = applyThemeRoleOverrides(darkColorScheme(), overrides, darkTheme = true)

        assertEquals(Color.White, light.onPrimary)
        assertTrue(light.primary.luminance() < light.onPrimary.luminance())
        assertTrue(dark.primary.luminance() > dark.onPrimary.luminance())
        assertTrue(dark.onPrimary != Color.Black)
        assertTrue(light.primary != dark.primary)
        assertEquals(light.primaryFixed, dark.primaryFixed)
        for (scheme in listOf(light, dark)) {
            assertTrue(themeRoleContrastRatio(
                formatMd3CustomColorHex(scheme.onPrimary), formatMd3CustomColorHex(scheme.primary)
            ) >= 3f)
        }
    }

    @Test
    fun contrastWarning_allowsGoodContrastAndFlagsWeakText() {
        assertFalse(
            hasThemeRoleContrastWarning(
                ThemeModeRoleOverrides("#FFFFFF", "#111111", "#555555", "#0061A4")
            )
        )
        assertTrue(
            hasThemeRoleContrastWarning(
                ThemeModeRoleOverrides("#FFFFFF", "#EEEEEE", "#F2F2F2", "#0061A4")
            )
        )
    }

    @Test
    fun currentSchemes_becomeAdvancedColorStartingValues() {
        val result = themeRoleOverridesFromSchemes(
            lightScheme = lightColorScheme(
                primary = Color(0xFF00BCD4),
                background = Color(0xFFF8FDFF),
                onBackground = Color(0xFF102023),
                onSurfaceVariant = Color(0xFF405C61)
            ),
            darkScheme = darkColorScheme(
                primary = Color(0xFF4FD8EA),
                background = Color(0xFF071416),
                onBackground = Color(0xFFD8F6FA),
                onSurfaceVariant = Color(0xFFA8C9CE)
            )
        )

        assertFalse(result.enabled)
        assertEquals("#F8FDFF", result.light.backgroundHex)
        assertEquals("#102023", result.light.primaryTextHex)
        assertEquals("#405C61", result.light.secondaryTextHex)
        assertEquals("#00BCD4", result.light.controlAccentHex)
        assertEquals("#071416", result.dark.backgroundHex)
        assertEquals("#D8F6FA", result.dark.primaryTextHex)
        assertEquals("#A8C9CE", result.dark.secondaryTextHex)
        assertEquals("#4FD8EA", result.dark.controlAccentHex)
    }

    @Test
    fun customThemeColor_updatesEnabledAdvancedControlColors() {
        val overrides = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#FFFFFF", "#111111", "#555555", "#0061A4"),
            dark = ThemeModeRoleOverrides("#000000", "#EEEEEE", "#AAAAAA", "#4FD8EA")
        )

        val result = syncThemeRoleControlAccent(overrides, "#FA7298")

        assertEquals("#FA7298", result.light.controlAccentHex)
        assertEquals("#FA7298", result.dark.controlAccentHex)
        assertEquals("#FFFFFF", result.light.backgroundHex)
        assertEquals("#000000", result.dark.backgroundHex)
        assertEquals(
            ThemeRoleOverrides(),
            syncThemeRoleControlAccent(ThemeRoleOverrides(), "#FA7298")
        )
    }
}
