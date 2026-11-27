package com.android.purebilibili.feature.video.player

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.theme.createSeedColorScheme
import com.android.purebilibili.core.theme.readSeedWallpaperColorScheme
import com.android.purebilibili.core.theme.darkenSeedColorScheme
import com.android.purebilibili.core.theme.createBiliPaiStyleColorScheme
import com.android.purebilibili.core.theme.resolveLiquidControlAccentColor
import com.android.purebilibili.core.theme.resolveMd3ThemeSeedColor
import com.android.purebilibili.core.theme.readSystemWallpaperSeed
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.settings.isScheduledDarkTheme
import com.android.purebilibili.feature.settings.resolveThemePreferenceState
import java.util.Calendar

/** A notification is a primary playback accent, independent of native component palette tones. */
internal fun resolvePlaybackNotificationAccentColor(
    settings: AppThemeSettings,
    darkTheme: Boolean,
    wallpaperAccent: Color? = null,
    wallpaperSeed: Color? = null,
): Color {
    val seed = resolveMd3ThemeSeedColor(
        settings.md3ColorSource, settings.md3CustomColorHex, settings.themeColorIndex, wallpaperSeed,
    )
    if (settings.seedPaletteStyle.enabled) {
        val pureBlack = darkTheme && settings.darkThemeStyle == com.android.purebilibili.feature.settings.DarkThemeStyle.AMOLED
        if (settings.md3ColorSource == Md3ColorSource.FOLLOW_WALLPAPER && wallpaperAccent != null) {
            // Wallpaper input has already received the selected dark-mode adjustment.
            return wallpaperAccent
        }
        val scheme = createSeedColorScheme(seed, darkTheme, settings.seedPaletteStyle, pureBlack, settings.colorSpec)
        return com.android.purebilibili.core.theme.resolveAppThemeEndpointPalette(
            base = scheme,
            uiStyle = settings.uiStyle,
            source = settings.md3ColorSource,
            darkTheme = darkTheme,
            amoledDarkTheme = pureBlack,
            wallpaperOverrides = settings.wallpaperPaletteOverrides,
            preserveGeneratedPalette = true,
        ).material.primary
    }
    if (settings.md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
        val amoled = darkTheme &&
            settings.darkThemeStyle == com.android.purebilibili.feature.settings.DarkThemeStyle.AMOLED
        val scheme = createBiliPaiStyleColorScheme(
            seedColor = seed,
            darkTheme = darkTheme,
            amoledDarkTheme = amoled,
            paletteStyle = settings.colorStyle,
            colorSpec = settings.colorSpec,
            uiStyle = settings.uiStyle,
            colorContrast = settings.colorContrast,
        )
        return com.android.purebilibili.core.theme.resolveAppThemeEndpointPalette(
            base = scheme,
            uiStyle = settings.uiStyle,
            source = settings.md3ColorSource,
            darkTheme = darkTheme,
            amoledDarkTheme = amoled,
            wallpaperOverrides = settings.wallpaperPaletteOverrides,
        ).liquidAccent
    }
    return resolveLiquidControlAccentColor(
        source = settings.md3ColorSource,
        customSeed = seed,
        generatedAccent = wallpaperAccent ?: seed,
        roleOverridesEnabled = settings.themeRoleOverrides.enabled && settings.md3ColorSource == Md3ColorSource.CUSTOM,
        roleControlHex = if (darkTheme) settings.themeRoleOverrides.dark.controlAccentHex
            else settings.themeRoleOverrides.light.controlAccentHex,
    )
}

/** Resolve on notification refresh, including current timezone and scheduled appearance. */
internal fun resolvePlaybackNotificationColor(context: Context, settings: AppThemeSettings): Int {
    val clock = Calendar.getInstance()
    val minute = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE)
    val appearance = resolveThemePreferenceState(
        themeMode = settings.themeMode,
        darkThemeStyle = settings.darkThemeStyle,
        systemInDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
        scheduledDarkTheme = isScheduledDarkTheme(settings.darkThemeSchedule, minute)
            .takeIf { settings.darkThemeSchedule.enabled },
    )
    val wallpaperAccent = if (settings.md3ColorSource == Md3ColorSource.FOLLOW_WALLPAPER) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val compatibilitySeed = com.android.purebilibili.core.theme.latestWallpaperColorCompatibility.seed
            if (compatibilitySeed != null) {
                createBiliPaiStyleColorScheme(
                    seedColor = compatibilitySeed,
                    darkTheme = appearance.useDarkTheme,
                    amoledDarkTheme = appearance.useAmoledDarkTheme,
                    paletteStyle = settings.colorStyle,
                    colorSpec = settings.colorSpec,
                    colorContrast = settings.colorContrast,
                    seedPaletteStyle = settings.seedPaletteStyle,
                ).primary
            } else if (settings.seedPaletteStyle.enabled) {
                val scheme = readSeedWallpaperColorScheme(context, appearance.useDarkTheme)
                if (appearance.useAmoledDarkTheme) darkenSeedColorScheme(scheme).primary else scheme.primary
            } else if (appearance.useDarkTheme) dynamicDarkColorScheme(context).primary
            else dynamicLightColorScheme(context).primary
        } else {
            createBiliPaiStyleColorScheme(
                seedColor = resolveMd3ThemeSeedColor(settings.md3ColorSource, settings.md3CustomColorHex, settings.themeColorIndex),
                darkTheme = appearance.useDarkTheme,
                amoledDarkTheme = appearance.useAmoledDarkTheme,
                paletteStyle = settings.colorStyle,
                colorSpec = settings.colorSpec,
                uiStyle = settings.uiStyle,
                colorContrast = settings.colorContrast,
                seedPaletteStyle = settings.seedPaletteStyle,
            ).primary
        }
    } else null
    return resolvePlaybackNotificationAccentColor(
        settings, appearance.useDarkTheme, wallpaperAccent,
        wallpaperSeed = if (settings.md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
            com.android.purebilibili.core.theme.latestWallpaperColorCompatibility.seed ?: readSystemWallpaperSeed(context)
        } else null,
    ).toArgb()
}
