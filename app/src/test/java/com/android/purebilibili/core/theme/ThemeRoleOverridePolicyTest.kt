package com.android.purebilibili.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.ThemeModeRoleOverrides
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeRoleOverridePolicyTest {

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
    fun miuixStyle_disablesAdvancedRoleOverridesWithoutDeletingThem() {
        val overrides = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#F4F0E8", "#201A17", "#655D57", "#FFF000"),
        )

        val result = resolveEffectiveThemeRoleOverrides(
            source = Md3ColorSource.CUSTOM,
            overrides = overrides,
            uiStyle = AppUiStyle.MIUIX,
        )

        assertFalse(result.enabled)
        assertEquals(overrides.light, result.light)
        assertEquals(overrides.dark, result.dark)
    }

    @Test
    fun enabledOverrides_replaceMaterialRolesAndChooseReadableButtonText() {
        val overrides = ThemeRoleOverrides(
            enabled = true,
            light = ThemeModeRoleOverrides("#F4F0E8", "#201A17", "#655D57", "#FFF000"),
            dark = ThemeRoleOverrides().dark
        )

        val result = applyThemeRoleOverrides(lightColorScheme(), overrides, darkTheme = false)

        assertEquals(Color(0xFFF4F0E8), result.background)
        assertEquals(Color(0xFF201A17), result.onBackground)
        assertEquals(Color(0xFF655D57), result.onSurfaceVariant)
        assertEquals(Color(0xFFFFF000), result.primary)
        assertEquals(Color.Black, result.onPrimary)
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
