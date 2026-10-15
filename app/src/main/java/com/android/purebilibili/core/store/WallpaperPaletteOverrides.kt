package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.feature.settings.isValidMd3CustomColorHex
import com.android.purebilibili.feature.settings.normalizeMd3CustomColorHex

enum class WallpaperPaletteRole { PRIMARY, SECONDARY, TERTIARY, CONTAINER }

/** Only explicitly edited roles are stored; absent roles continue following the wallpaper. */
data class WallpaperPaletteOverrides(
    val light: Map<WallpaperPaletteRole, String> = emptyMap(),
    val dark: Map<WallpaperPaletteRole, String> = emptyMap(),
) {
    fun forMode(darkTheme: Boolean): Map<WallpaperPaletteRole, String> = if (darkTheme) dark else light
}

internal val wallpaperPalettePreferenceKeys = listOf(false, true).flatMap { dark ->
    WallpaperPaletteRole.entries.map { role ->
        (dark to role) to stringPreferencesKey(
            "wallpaper_palette_${if (dark) "dark" else "light"}_${role.name.lowercase()}"
        )
    }
}.toMap()

internal fun readWallpaperPaletteOverrides(preferences: Preferences): WallpaperPaletteOverrides {
    fun read(dark: Boolean) = WallpaperPaletteRole.entries.mapNotNull { role ->
        preferences[wallpaperPalettePreferenceKeys.getValue(dark to role)]
            ?.takeIf(::isValidMd3CustomColorHex)
            ?.let { role to normalizeMd3CustomColorHex(it) }
    }.toMap()
    return WallpaperPaletteOverrides(light = read(false), dark = read(true))
}
