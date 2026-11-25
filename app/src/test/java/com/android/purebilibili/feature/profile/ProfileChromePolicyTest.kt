package com.android.purebilibili.feature.profile

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileChromePolicyTest {

    @Test
    fun wallpaperHero_usesWhiteTextAndNoDistortedScrim() {
        val lightHero = resolveProfileHeroChrome(
            hasWallpaper = true,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray
        )
        val darkHero = resolveProfileHeroChrome(
            hasWallpaper = true,
            isDarkTheme = true,
            onSurfaceColor = Color.White,
            onSurfaceVariantColor = Color.Gray
        )

        assertEquals(Color.White, lightHero.textColor)
        assertEquals(0f, lightHero.scrimBottomAlpha)
        assertEquals(0f, darkHero.scrimBottomAlpha)
        assertFalse(lightHero.useLightStatusBarIcons)
    }

    @Test
    fun fallbackHero_usesThemeTextAndLightStatusBarOnLightTheme() {
        val hero = resolveProfileHeroChrome(
            hasWallpaper = false,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.DarkGray
        )

        assertEquals(Color.Black, hero.textColor)
        assertTrue(hero.useLightStatusBarIcons)
        assertEquals(0f, hero.scrimBottomAlpha)
    }

    @Test
    fun contentChrome_usesTintedSheetGradientAndCoverDominantCardLayers() {
        val lightChrome = resolveProfileContentChrome(
            surfaceColor = Color.White,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray,
            primaryColor = Color.Blue,
            surfaceContainerLowColor = Color(0xFFF7F2F4),
            surfaceContainerHighColor = Color(0xFFECECEC),
            surfaceContainerHighestColor = Color(0xFFE6E1E3),
            outlineVariantColor = Color(0xFFCAC4D0),
            isDarkTheme = false
        )
        val darkChrome = resolveProfileContentChrome(
            surfaceColor = Color(0xFF121212),
            onSurfaceColor = Color.White,
            onSurfaceVariantColor = Color.LightGray,
            primaryColor = Color.Cyan,
            surfaceContainerLowColor = Color(0xFF1D1A1C),
            surfaceContainerHighColor = Color(0xFF1E1E1E),
            surfaceContainerHighestColor = Color(0xFF262426),
            outlineVariantColor = Color(0xFF49454F),
            isDarkTheme = true
        )

        assertEquals(Color(0xFFF7F2F4), lightChrome.sheetGradientTopColor)
        assertEquals(Color.White, lightChrome.sheetGradientBottomColor)
        assertEquals(Color(0xFFE6E1E3), lightChrome.cardMetadataColor)
        assertEquals(1, lightChrome.cardShadowElevationDp)
        assertEquals(2, lightChrome.sheetShadowElevationDp)
        assertEquals(0, darkChrome.cardShadowElevationDp)
        assertTrue(resolveProfileContentUsesOpaqueSurface(hasWallpaper = true))
    }

    @Test
    fun tabletHeaderSurfaceChrome_keepsDarkTextBelowWallpaperOnLightTheme() {
        val surfaceChrome = resolveProfileTabletHeaderSurfaceChrome(
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.DarkGray,
        )

        assertEquals(Color.Black, surfaceChrome.textColor)
        assertEquals(Color.DarkGray, surfaceChrome.secondaryTextColor)
        assertEquals(Color.Black, surfaceChrome.actionButtonContentColor)
    }

}
