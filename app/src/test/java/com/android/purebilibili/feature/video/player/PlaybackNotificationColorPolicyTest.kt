package com.android.purebilibili.feature.video.player

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.Md3ColorSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackNotificationColorPolicyTest {
    @Test
    fun manuallyEditedWallpaperPrimaryIsUsedByPlaybackNotifications() {
        val settings = AppThemeSettings(
            md3ColorSource = Md3ColorSource.WALLPAPER_RETONED,
            wallpaperPaletteOverrides = com.android.purebilibili.core.store.WallpaperPaletteOverrides(
                light = mapOf(com.android.purebilibili.core.store.WallpaperPaletteRole.PRIMARY to "#FF5722"),
                dark = mapOf(com.android.purebilibili.core.store.WallpaperPaletteRole.PRIMARY to "#007AFF"),
            ),
        )
        assertEquals(Color(0xFFFF5722), resolvePlaybackNotificationAccentColor(settings, false, wallpaperSeed = Color.Green))
        assertEquals(Color(0xFF007AFF), resolvePlaybackNotificationAccentColor(settings, true, wallpaperSeed = Color.Green))
    }
    @Test
    fun customNotificationUsesPickedColorInsteadOfMaterialToneInBothThemes() {
        val settings = AppThemeSettings(md3ColorSource = Md3ColorSource.CUSTOM, md3CustomColorHex = "#007AFF")
        for (dark in listOf(false, true)) {
            assertEquals(Color(0xFF007AFF), resolvePlaybackNotificationAccentColor(settings, dark, Color(0xFF4A608A)))
        }
    }

    @Test
    fun advancedPaletteUsesTheActiveLightOrDarkControlOverride() {
        val defaults = ThemeRoleOverrides()
        val settings = AppThemeSettings(
            md3ColorSource = Md3ColorSource.CUSTOM,
            md3CustomColorHex = "#007AFF",
            themeRoleOverrides = defaults.copy(
                enabled = true,
                light = defaults.light.copy(controlAccentHex = "#00A1D6"),
                dark = defaults.dark.copy(controlAccentHex = "#34C759"),
            ),
        )
        assertEquals(Color(0xFF00A1D6), resolvePlaybackNotificationAccentColor(settings, false))
        assertEquals(Color(0xFF34C759), resolvePlaybackNotificationAccentColor(settings, true))
    }

    @Test
    fun wallpaperNotificationKeepsResolvedWallpaperAccentInsteadOfCustomSeed() {
        val wallpaper = Color(0xFF906EB3)
        val settings = AppThemeSettings(
            md3ColorSource = Md3ColorSource.FOLLOW_WALLPAPER,
            md3CustomColorHex = "#007AFF",
            themeRoleOverrides = ThemeRoleOverrides(enabled = true),
        )
        assertEquals(wallpaper, resolvePlaybackNotificationAccentColor(settings, true, wallpaper))
    }

    @Test
    fun retoneNotificationUsesTheGeneratedWallpaperPaletteInBothThemes() {
        val seed = Color(0xFF8054A8)
        val settings = AppThemeSettings(
            md3ColorSource = Md3ColorSource.WALLPAPER_RETONED,
            md3CustomColorHex = "#FF5722",
            colorStyle = com.materialkolor.PaletteStyle.Expressive,
            themeRoleOverrides = ThemeRoleOverrides(enabled = true),
        )
        for (dark in listOf(false, true)) {
            val scheme = com.android.purebilibili.core.theme.createBiliPaiStyleColorScheme(
                seedColor = seed,
                darkTheme = dark,
                amoledDarkTheme = false,
                paletteStyle = settings.colorStyle,
                colorSpec = settings.colorSpec,
                uiStyle = settings.uiStyle,
            )
            assertEquals(scheme.primary, resolvePlaybackNotificationAccentColor(
                settings, dark, wallpaperAccent = Color.Green, wallpaperSeed = seed,
            ))
        }
    }
}
