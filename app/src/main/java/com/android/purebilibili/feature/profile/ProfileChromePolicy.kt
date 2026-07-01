package com.android.purebilibili.feature.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

data class ProfileHeroChrome(
    val textColor: Color,
    val secondaryTextColor: Color,
    val scrimTopAlpha: Float,
    val scrimBottomAlpha: Float,
    val avatarBorderColor: Color,
    val actionButtonContentColor: Color,
    val actionButtonBorderAlpha: Float,
    val metaChipContainerColor: Color,
    val metaChipBorderColor: Color,
    val useLightStatusBarIcons: Boolean
)

data class ProfileContentChrome(
    val surfaceColor: Color,
    val onSurfaceColor: Color,
    val onSurfaceVariantColor: Color,
    val primaryColor: Color,
    val sheetGradientTopColor: Color,
    val sheetGradientBottomColor: Color,
    val cardContainerColor: Color,
    val cardMetadataColor: Color,
    val cardBorderColor: Color,
    val cardShadowElevationDp: Int,
    val sheetShadowElevationDp: Int,
    val sheetBorderColor: Color
)

data class ProfileHeroFallbackGradient(
    val topColor: Color,
    val bottomColor: Color
)

fun resolveProfileHeroChrome(
    hasWallpaper: Boolean,
    isDarkTheme: Boolean,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
): ProfileHeroChrome {
    if (hasWallpaper) {
        val scrimBottomAlpha = if (isDarkTheme) 0.65f else 0.55f
        return ProfileHeroChrome(
            textColor = Color.White,
            secondaryTextColor = Color.White.copy(alpha = 0.72f),
            scrimTopAlpha = 0f,
            scrimBottomAlpha = scrimBottomAlpha,
            avatarBorderColor = Color.White.copy(alpha = 0.88f),
            actionButtonContentColor = Color.White,
            actionButtonBorderAlpha = 0.42f,
            metaChipContainerColor = Color.Black.copy(alpha = 0.22f),
            metaChipBorderColor = Color.White.copy(alpha = 0.22f),
            useLightStatusBarIcons = false
        )
    }
    return ProfileHeroChrome(
        textColor = onSurfaceColor,
        secondaryTextColor = onSurfaceVariantColor,
        scrimTopAlpha = 0f,
        scrimBottomAlpha = 0f,
        avatarBorderColor = onSurfaceColor.copy(alpha = 0.16f),
        actionButtonContentColor = onSurfaceColor,
        actionButtonBorderAlpha = 0.28f,
        metaChipContainerColor = onSurfaceColor.copy(alpha = 0.06f),
        metaChipBorderColor = onSurfaceColor.copy(alpha = 0.12f),
        useLightStatusBarIcons = !isDarkTheme
    )
}

fun resolveProfileContentChrome(
    surfaceColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color,
    surfaceContainerLowColor: Color,
    surfaceContainerHighColor: Color,
    surfaceContainerHighestColor: Color,
    outlineVariantColor: Color,
    isDarkTheme: Boolean
): ProfileContentChrome {
    return ProfileContentChrome(
        surfaceColor = surfaceColor,
        onSurfaceColor = onSurfaceColor,
        onSurfaceVariantColor = onSurfaceVariantColor,
        primaryColor = primaryColor,
        sheetGradientTopColor = surfaceContainerLowColor,
        sheetGradientBottomColor = surfaceColor,
        cardContainerColor = surfaceContainerHighColor,
        cardMetadataColor = surfaceContainerHighestColor,
        cardBorderColor = outlineVariantColor.copy(alpha = if (isDarkTheme) 0.28f else 0.22f),
        cardShadowElevationDp = if (isDarkTheme) 0 else 1,
        sheetShadowElevationDp = if (isDarkTheme) 0 else 2,
        sheetBorderColor = onSurfaceColor.copy(alpha = if (isDarkTheme) 0.08f else 0.04f)
    )
}

fun resolveProfileHeroFallbackGradient(
    hasWallpaper: Boolean,
    isDarkTheme: Boolean,
    surfaceColor: Color,
    surfaceVariantColor: Color,
    primaryContainerColor: Color
): ProfileHeroFallbackGradient? {
    if (hasWallpaper) return null
    return if (isDarkTheme) {
        ProfileHeroFallbackGradient(
            topColor = surfaceVariantColor.copy(alpha = 0.92f),
            bottomColor = surfaceColor
        )
    } else {
        ProfileHeroFallbackGradient(
            topColor = primaryContainerColor.copy(alpha = 0.55f),
            bottomColor = surfaceColor
        )
    }
}

fun resolveProfileContentUsesOpaqueSurface(hasWallpaper: Boolean): Boolean = true

fun resolveProfileHeroUsesWallpaperBackground(hasWallpaper: Boolean): Boolean = hasWallpaper

/**
 * Maps the pull-down overscroll distance (px) to a 0..1 inversion fraction.
 * 0 = header rests on the wallpaper (text keeps hero chrome); 1 = header pulled
 * fully into the surface area below the wallpaper (text inverted toward onSurface).
 */
fun resolveProfileHeroPullInvertFraction(
    pullPx: Float,
    fullInvertPullPx: Float
): Float {
    if (fullInvertPullPx <= 0f) return 0f
    if (pullPx <= 0f) return 0f
    return (pullPx / fullInvertPullPx).coerceIn(0f, 1f)
}

/**
 * Blends the wallpaper hero chrome's foreground colors (text, secondary text,
 * action button) toward the surface's onSurface/onSurfaceVariant colors by
 * [invertFraction], so white hero text stays legible when pulled down into the
 * surface (white) area. No-op when [invertFraction] <= 0. Blending toward
 * onSurface keeps dark-theme text light (no-op) since onSurface is light there.
 */
fun resolveProfileHeroInvertedChrome(
    heroChrome: ProfileHeroChrome,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    invertFraction: Float
): ProfileHeroChrome {
    if (invertFraction <= 0f) return heroChrome
    val fraction = invertFraction.coerceIn(0f, 1f)
    return heroChrome.copy(
        textColor = lerp(heroChrome.textColor, onSurfaceColor, fraction),
        secondaryTextColor = lerp(heroChrome.secondaryTextColor, onSurfaceVariantColor, fraction),
        actionButtonContentColor = lerp(heroChrome.actionButtonContentColor, onSurfaceColor, fraction)
    )
}