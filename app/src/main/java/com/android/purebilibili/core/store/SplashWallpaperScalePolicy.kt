package com.android.purebilibili.core.store

const val SPLASH_WALLPAPER_SCALE_MIN = 0.2f
const val SPLASH_WALLPAPER_SCALE_MAX = 2f

fun sanitizeSplashWallpaperScale(value: Float): Float {
    if (!value.isFinite()) return 1f
    return value.coerceIn(SPLASH_WALLPAPER_SCALE_MIN, SPLASH_WALLPAPER_SCALE_MAX)
}

fun resolveSplashWallpaperScale(
    isTabletLayout: Boolean,
    mobileScale: Float,
    tabletScale: Float,
): Float = sanitizeSplashWallpaperScale(if (isTabletLayout) tabletScale else mobileScale)
