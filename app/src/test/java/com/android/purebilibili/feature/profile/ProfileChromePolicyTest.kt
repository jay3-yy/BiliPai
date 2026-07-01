package com.android.purebilibili.feature.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileChromePolicyTest {

    @Test
    fun wallpaperHero_usesWhiteTextAndStrongerDarkThemeScrim() {
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
        assertEquals(0.55f, lightHero.scrimBottomAlpha)
        assertEquals(0.65f, darkHero.scrimBottomAlpha)
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
    fun fallbackGradient_onlyAppliesWithoutWallpaper() {
        assertNull(
            resolveProfileHeroFallbackGradient(
                hasWallpaper = true,
                isDarkTheme = false,
                surfaceColor = Color.White,
                surfaceVariantColor = Color.LightGray,
                primaryContainerColor = Color.Blue
            )
        )
        val gradient = resolveProfileHeroFallbackGradient(
            hasWallpaper = false,
            isDarkTheme = true,
            surfaceColor = Color.Black,
            surfaceVariantColor = Color.DarkGray,
            primaryContainerColor = Color.Blue
        )
        assertEquals(Color.DarkGray.copy(alpha = 0.92f), gradient?.topColor)
        assertEquals(Color.Black, gradient?.bottomColor)
    }

    @Test
    fun pullInvertFraction_mapsPullDistanceAndClamps() {
        assertEquals(0f, resolveProfileHeroPullInvertFraction(pullPx = 0f, fullInvertPullPx = 100f))
        assertEquals(0f, resolveProfileHeroPullInvertFraction(pullPx = -20f, fullInvertPullPx = 100f))
        assertEquals(0.5f, resolveProfileHeroPullInvertFraction(pullPx = 50f, fullInvertPullPx = 100f))
        assertEquals(1f, resolveProfileHeroPullInvertFraction(pullPx = 100f, fullInvertPullPx = 100f))
        assertEquals(1f, resolveProfileHeroPullInvertFraction(pullPx = 250f, fullInvertPullPx = 100f))
    }

    @Test
    fun pullInvertFraction_disabledWhenThresholdNotPositive() {
        assertEquals(0f, resolveProfileHeroPullInvertFraction(pullPx = 80f, fullInvertPullPx = 0f))
        assertEquals(0f, resolveProfileHeroPullInvertFraction(pullPx = 80f, fullInvertPullPx = -1f))
    }

    @Test
    fun invertedChrome_noOpAtZeroFraction() {
        val hero = resolveProfileHeroChrome(
            hasWallpaper = true,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray
        )

        val inverted = resolveProfileHeroInvertedChrome(
            heroChrome = hero,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray,
            invertFraction = 0f
        )

        assertEquals(hero, inverted)
    }

    @Test
    fun invertedChrome_blendsTowardOnSurfaceAtFullFraction() {
        val hero = resolveProfileHeroChrome(
            hasWallpaper = true,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray
        )

        val inverted = resolveProfileHeroInvertedChrome(
            heroChrome = hero,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray,
            invertFraction = 1f
        )

        assertEquals(Color.Black, inverted.textColor)
        assertEquals(Color.Gray, inverted.secondaryTextColor)
        assertEquals(Color.Black, inverted.actionButtonContentColor)
    }

    @Test
    fun invertedChrome_clampsFractionAboveOne() {
        val hero = resolveProfileHeroChrome(
            hasWallpaper = true,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray
        )

        val inverted = resolveProfileHeroInvertedChrome(
            heroChrome = hero,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray,
            invertFraction = 5f
        )

        assertEquals(Color.Black, inverted.textColor)
    }

    @Test
    fun invertedChrome_midpointIsBlend() {
        val hero = resolveProfileHeroChrome(
            hasWallpaper = true,
            isDarkTheme = false,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray
        )
        val expectedText = lerp(Color.White, Color.Black, 0.5f)

        val inverted = resolveProfileHeroInvertedChrome(
            heroChrome = hero,
            onSurfaceColor = Color.Black,
            onSurfaceVariantColor = Color.Gray,
            invertFraction = 0.5f
        )

        assertEquals(expectedText, inverted.textColor)
    }
}