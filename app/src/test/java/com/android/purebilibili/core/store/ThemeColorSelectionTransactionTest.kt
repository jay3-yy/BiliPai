package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.ThemeColors
import com.android.purebilibili.core.theme.createBiliPaiStyleColorScheme
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.theme.resolveAppThemeEndpointPalette
import com.android.purebilibili.core.theme.resolveMd3ThemeSeedColor
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ThemeColorSelectionTransactionTest {
    private fun wallpaperPreferences() = mutablePreferencesOf(
        stringPreferencesKey("md3_color_source") to Md3ColorSource.WALLPAPER_RETONED.name,
        booleanPreferencesKey("dynamic_color") to true,
        stringPreferencesKey("md3_custom_color_hex") to "#FF5722",
        wallpaperPalettePreferenceKeys.getValue(false to WallpaperPaletteRole.PRIMARY) to "#00FF00",
    )

    @Test
    fun sourceSelectorRestoresSavedManualSeedAndStopsApplyingWallpaperEdits() {
        val preferences = wallpaperPreferences()
        SettingsManager.updateMd3ColorSelection(preferences, Md3ColorSource.CUSTOM)
        val selected = mapAppThemeSettingsFromPreferences(preferences)
        assertEquals(Md3ColorSource.CUSTOM, selected.md3ColorSource)
        assertFalse(preferences[booleanPreferencesKey("dynamic_color")]!!)
        val seed = resolveMd3ThemeSeedColor(selected.md3ColorSource, selected.md3CustomColorHex, 0, Color.Green)
        assertEquals(Color(0xFFFF5722), seed)
        val base = createBiliPaiStyleColorScheme(seed, false, false, selected.colorStyle, selected.colorSpec)
        val endpoint = resolveAppThemeEndpointPalette(
            base, selected.uiStyle, selected.md3ColorSource, false,
            wallpaperOverrides = selected.wallpaperPaletteOverrides, customSeed = seed,
        )
        assertEquals(seed, endpoint.material.primary)
        assertEquals(seed, endpoint.liquidAccent)
        // Returning to re-toning restores that mode's edits without overwriting manual colors.
        SettingsManager.updateMd3ColorSelection(preferences, Md3ColorSource.WALLPAPER_RETONED)
        val restored = mapAppThemeSettingsFromPreferences(preferences)
        assertEquals("#00FF00", restored.wallpaperPaletteOverrides.light[WallpaperPaletteRole.PRIMARY])
        assertEquals("#FF5722", restored.md3CustomColorHex)
    }

    @Test
    fun pickerSaveUpdatesSourceSeedAndEnabledAdvancedAccentsInOneSnapshot() {
        val preferences = wallpaperPreferences()
        preferences[booleanPreferencesKey("theme_role_overrides_enabled")] = true
        preferences[stringPreferencesKey("theme_light_control_accent")] = "#00FF00"
        preferences[stringPreferencesKey("theme_dark_control_accent")] = "#00FF00"
        SettingsManager.updateMd3ColorSelection(preferences, Md3ColorSource.CUSTOM, customHex = "#af52de")
        val selected = mapAppThemeSettingsFromPreferences(preferences)
        assertEquals(Md3ColorSource.CUSTOM, selected.md3ColorSource)
        assertFalse(preferences[booleanPreferencesKey("dynamic_color")]!!)
        assertEquals("#AF52DE", selected.md3CustomColorHex)
        assertEquals("#AF52DE", selected.themeRoleOverrides.light.controlAccentHex)
        assertEquals("#AF52DE", selected.themeRoleOverrides.dark.controlAccentHex)
    }

    @Test
    fun presetSelectionSwitchesToCustomAndKeepsIndexAndHexConsistent() {
        val preferences = wallpaperPreferences()
        SettingsManager.updateMd3ColorSelection(preferences, Md3ColorSource.CUSTOM, presetIndex = 3)
        val selected = mapAppThemeSettingsFromPreferences(preferences)
        assertEquals(Md3ColorSource.CUSTOM, selected.md3ColorSource)
        assertFalse(preferences[booleanPreferencesKey("dynamic_color")]!!)
        assertEquals(3, selected.themeColorIndex)
        assertEquals(formatMd3CustomColorHex(ThemeColors[3]), selected.md3CustomColorHex)
    }
}
