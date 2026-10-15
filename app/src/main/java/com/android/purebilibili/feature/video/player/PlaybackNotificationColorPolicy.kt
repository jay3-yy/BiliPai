package com.android.purebilibili.feature.video.player

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.store.AppThemeSettings
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
    val generatedAccent = if (settings.md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
        val scheme = createBiliPaiStyleColorScheme(
            seedColor = seed,
            darkTheme = darkTheme,
            amoledDarkTheme = settings.darkThemeStyle == com.android.purebilibili.feature.settings.DarkThemeStyle.AMOLED,
            paletteStyle = settings.colorStyle,
            colorSpec = settings.colorSpec,
            uiStyle = settings.uiStyle,
        )
        com.android.purebilibili.core.theme.resolveAppThemeEndpointPalette(
            base = scheme,
            uiStyle = settings.uiStyle,
            source = settings.md3ColorSource,
            darkTheme = darkTheme,
            amoledDarkTheme = settings.darkThemeStyle == com.android.purebilibili.feature.settings.DarkThemeStyle.AMOLED,
            wallpaperOverrides = settings.wallpaperPaletteOverrides,
        ).liquidAccent
    } else wallpaperAccent ?: seed
    return resolveLiquidControlAccentColor(
        source = settings.md3ColorSource,
        customSeed = seed,
        generatedAccent = generatedAccent,
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
            if (appearance.useDarkTheme) dynamicDarkColorScheme(context).primary
            else dynamicLightColorScheme(context).primary
        } else {
            createBiliPaiStyleColorScheme(
                seedColor = resolveMd3ThemeSeedColor(settings.md3ColorSource, settings.md3CustomColorHex, settings.themeColorIndex),
                darkTheme = appearance.useDarkTheme,
                amoledDarkTheme = appearance.useAmoledDarkTheme,
                paletteStyle = settings.colorStyle,
                colorSpec = settings.colorSpec,
                uiStyle = settings.uiStyle,
            ).primary
        }
    } else null
    return resolvePlaybackNotificationAccentColor(
        settings, appearance.useDarkTheme, wallpaperAccent,
        wallpaperSeed = if (settings.md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
            readSystemWallpaperSeed(context)
        } else null,
    ).toArgb()
}
