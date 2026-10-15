package com.android.purebilibili.core.theme

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color

/**
 * Use the system-selected wallpaper accent at a fixed tone, independent of app light/dark
 * mode. This reads public palette resources and needs no wallpaper/image permission.
 * Re-toning intentionally follows the system's selected color family, not wallpaper pixels.
 */
internal fun readSystemWallpaperSeed(context: Context): Color? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_500))
    } else null
