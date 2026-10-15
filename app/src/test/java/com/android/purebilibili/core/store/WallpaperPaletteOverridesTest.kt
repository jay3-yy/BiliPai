package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WallpaperPaletteOverridesTest {
    @Test
    fun persistenceKeepsModesIndependentAndIgnoresInvalidColors() {
        val preferences = mutablePreferencesOf()
        preferences[wallpaperPalettePreferenceKeys.getValue(false to WallpaperPaletteRole.PRIMARY)] = "#ff5722"
        preferences[wallpaperPalettePreferenceKeys.getValue(true to WallpaperPaletteRole.PRIMARY)] = "#007AFF"
        preferences[wallpaperPalettePreferenceKeys.getValue(false to WallpaperPaletteRole.SECONDARY)] = "invalid"
        val read = readWallpaperPaletteOverrides(preferences)
        assertEquals(mapOf(WallpaperPaletteRole.PRIMARY to "#FF5722"), read.light)
        assertEquals(mapOf(WallpaperPaletteRole.PRIMARY to "#007AFF"), read.dark)
        assertEquals(read, mapAppThemeSettingsFromPreferences(preferences).wallpaperPaletteOverrides)
    }

    @Test
    fun removingOneRoleRestoresAutomaticColorWithoutRemovingOtherEdits() {
        val preferences = mutablePreferencesOf()
        val primaryKey = wallpaperPalettePreferenceKeys.getValue(false to WallpaperPaletteRole.PRIMARY)
        val secondaryKey = wallpaperPalettePreferenceKeys.getValue(false to WallpaperPaletteRole.SECONDARY)
        preferences[primaryKey] = "#FF5722"
        preferences[secondaryKey] = "#007AFF"
        preferences.remove(primaryKey)
        val read = readWallpaperPaletteOverrides(preferences)
        assertFalse(WallpaperPaletteRole.PRIMARY in read.light)
        assertEquals("#007AFF", read.light[WallpaperPaletteRole.SECONDARY])
        assertEquals(emptyMap(), read.dark)
    }
}
