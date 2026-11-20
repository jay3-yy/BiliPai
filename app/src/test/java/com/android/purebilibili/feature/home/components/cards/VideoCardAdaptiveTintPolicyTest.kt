package com.android.purebilibili.feature.home.components.cards

import com.android.purebilibili.feature.home.HomeCardWallpaperSurfaceMode
import com.android.purebilibili.feature.home.resolveHomeCardWallpaperSurfaceMode
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.resolveHomeCardFrostedGlassEnabled
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoCardAdaptiveTintPolicyTest {

    @Test
    fun cardEffectsAreOffByDefault() {
        assertEquals(false, HomeSettings().homeCardDynamicTintEnabled)
        assertEquals(false, HomeSettings().homeCardFrostedGlassEnabled)
    }

    @Test
    fun explicitLegacyCardSettingIsPreservedUntilGlassGetsItsOwnValue() {
        assertEquals(false, resolveHomeCardFrostedGlassEnabled(null, null))
        assertEquals(true, resolveHomeCardFrostedGlassEnabled(null, true))
        assertEquals(false, resolveHomeCardFrostedGlassEnabled(false, true))
        assertEquals(true, resolveHomeCardFrostedGlassEnabled(true, false))
    }

    @Test
    fun resolveCoverBottomSamplingRegion_targetsWholeCover() {
        val region = resolveCoverBottomSamplingRegion(width = 800, height = 600)
        assertEquals(0, region.left)
        assertEquals(0, region.top)
        assertEquals(800, region.right)
        assertEquals(600, region.bottom)
    }

    @Test
    fun resolveCoverBottomSamplingRegion_handlesEdgeDimensions() {
        val region = resolveCoverBottomSamplingRegion(width = 0, height = 0)
        assertEquals(0, region.left)
        assertEquals(0, region.top)
        assertEquals(1, region.right)
        assertEquals(1, region.bottom)
    }

    @Test
    fun interpolateWallpaperColor_interpolatesAccurately() {
        val palette = WallpaperPalette(
            topColor = Color(0xFF0000FF), // Pure Blue
            bottomColor = Color(0xFFFF0000) // Pure Red
        )

        val atTop = interpolateWallpaperColor(palette, yFraction = 0f)
        assertEquals(palette.topColor, atTop)

        val atBottom = interpolateWallpaperColor(palette, yFraction = 1f)
        assertEquals(palette.bottomColor, atBottom)

        // Clamping check
        val belowZero = interpolateWallpaperColor(palette, yFraction = -0.5f)
        assertEquals(palette.topColor, belowZero)

        val aboveOne = interpolateWallpaperColor(palette, yFraction = 1.5f)
        assertEquals(palette.bottomColor, aboveOne)
    }

    @Test
    fun interpolateWallpaperColor_multiStops_interpolatesContinuously() {
        val stops = listOf(
            Color(0xFFFF0000), // Red at 0.0
            Color(0xFF00FF00), // Green at 0.5
            Color(0xFF0000FF)  // Blue at 1.0
        )
        val palette = WallpaperPalette(
            topColor = stops.first(),
            bottomColor = stops.last(),
            stops = stops
        )

        val atZero = interpolateWallpaperColor(palette, 0f)
        assertEquals(Color(0xFFFF0000), atZero)

        val atHalf = interpolateWallpaperColor(palette, 0.5f)
        assertEquals(Color(0xFF00FF00), atHalf)

        val atOne = interpolateWallpaperColor(palette, 1f)
        assertEquals(Color(0xFF0000FF), atOne)
    }

    @Test
    fun resolveVideoCardAmbientDrawSpec_wallpaperTakesExclusivePrecedenceOverCoverTint() {
        val palette = WallpaperPalette(
            topColor = Color.Blue,
            bottomColor = Color.Red
        )
        val coverTint = Color.Green
        val defaultContainer = Color.Black
        val defaultBorder = Color.Gray

        val spec = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = palette,
            yFraction = 0.5f,
            coverTint = coverTint,
            wallpaperTintEnabled = true,
            isDarkTheme = true,
            defaultContainerColor = defaultContainer,
            defaultBorderColor = defaultBorder
        )

        assertEquals(0f, spec.coverGlowAlpha)
        // Container color comes from the interpolated wallpaper, not the cover.
        assertTrue(spec.containerColor != defaultContainer)
        assertTrue(spec.borderColor != coverTint)
    }

    @Test
    fun shouldUseCoverTintForCard_onlyUsesCoverWhenWallpaperIsDisabled() {
        assertTrue(shouldUseCoverTintForCard(false, Color.Green))
        assertEquals(false, shouldUseCoverTintForCard(true, Color.Green))
        assertEquals(false, shouldUseCoverTintForCard(false, null))
    }

    @Test
    fun representativeSwatch_prefersLargestUsefulRegionOverSmallBrightSubtitle() {
        val subtitleRed = Palette.Swatch(0xFFFF0000.toInt(), 12)
        val coverBlue = Palette.Swatch(0xFF336699.toInt(), 160)
        val whiteBackground = Palette.Swatch(0xFFFFFFFF.toInt(), 400)

        assertEquals(coverBlue, VideoCardCoverColorStore.resolveRepresentativeSwatch(
            listOf(subtitleRed, coverBlue, whiteBackground)
        ))
    }

    @Test
    fun representativeSwatch_keeps_grayscale_fallback_when_no_colorful_region_exists() {
        val black = Palette.Swatch(0xFF111111.toInt(), 120)
        val white = Palette.Swatch(0xFFF2F2F2.toInt(), 250)

        assertEquals(white, VideoCardCoverColorStore.resolveRepresentativeSwatch(listOf(black, white)))
    }

    @Test
    fun grayscaleCover_keepsItsNeutralToneDespiteSmallSaturatedCaption() {
        val gray = Palette.Swatch(0xFF7F7F7F.toInt(), 140)
        val otherGrays = listOf(0xFF282828, 0xFF525252, 0xFFA5A5A5, 0xFFC8C8C8, 0xFFF2F2F2)
            .map { Palette.Swatch(it.toInt(), 130) }
        val caption = Palette.Swatch(0xFFFF0000.toInt(), 150)

        assertEquals(gray, VideoCardCoverColorStore.resolveRepresentativeSwatch(listOf(caption, gray) + otherGrays))
    }

    @Test
    fun relatedCoverShadesJointlyOutweighAnIsolatedColor() {
        val foregroundBlue = Palette.Swatch(0xFF34679A.toInt(), 95)
        val shadowBlue = Palette.Swatch(0xFF244E76.toInt(), 80)
        val neutral = Palette.Swatch(0xFFE6E6E6.toInt(), 550)
        val orange = Palette.Swatch(0xFFD05A20.toInt(), 125)

        assertEquals(foregroundBlue, VideoCardCoverColorStore.resolveRepresentativeSwatch(
            listOf(neutral, orange, shadowBlue, foregroundBlue),
        ))
    }

    @Test
    fun coverPlateKeepsThemeTextReadableAndPreservesCoverHue() {
        for (dark in listOf(false, true)) {
            val base = if (dark) Color(0xFF1C1B1F) else Color(0xFFF7F2FA)
            val title = if (dark) Color(0xFFE6E1E5) else Color(0xFF1D1B20)
            val subtitle = if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F)
            for (cover in listOf(Color(0xFF174282), Color(0xFFD65116), Color.Gray)) {
                val plate = resolveCoverCardTint(cover, base, title, subtitle)
                val source = Hct.fromInt(cover.toArgb())
                val toned = Hct.fromInt(plate.toArgb())
                if (source.chroma >= 12) {
                    val hueDistance = abs(source.hue - toned.hue)
                    assertTrue(minOf(hueDistance, 360.0 - hueDistance) < 12.0)
                    assertTrue(toned.chroma < source.chroma)
                } else {
                    assertTrue(toned.chroma < 6.0)
                }
                for (text in listOf(title, subtitle)) {
                    val bright = maxOf(text.luminance(), plate.luminance())
                    val dim = minOf(text.luminance(), plate.luminance())
                    assertTrue((bright + 0.05f) / (dim + 0.05f) >= 4.5f)
                }
            }
        }
    }

    @Test
    fun resolveVideoCardAmbientDrawSpec_fallsBackWhenDisabled() {
        val defaultContainer = Color(0xFF1E1E1E)
        val defaultBorder = Color(0xFF333333)

        val spec = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = null,
            yFraction = 0.5f,
            coverTint = null,
            wallpaperTintEnabled = false,
            isDarkTheme = true,
            defaultContainerColor = defaultContainer,
            defaultBorderColor = defaultBorder
        )

        assertEquals(defaultContainer, spec.containerColor)
        assertEquals(0f, spec.coverGlowAlpha)
        assertEquals(defaultBorder, spec.borderColor)
    }

    @Test
    fun resolveVideoCardAmbientDrawSpec_producesTranslucentFrostedGlassWhenWallpaperAbsent() {
        val defaultContainer = Color.White
        val defaultBorder = Color.LightGray

        val spec = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = null,
            yFraction = 0.5f,
            coverTint = null,
            wallpaperTintEnabled = true,
            isDarkTheme = false,
            defaultContainerColor = defaultContainer,
            defaultBorderColor = defaultBorder,
            frostedGlassEnabled = true
        )

        // Must be translucent (alpha < 1.0f), never opaque white
        assertTrue(spec.containerColor.alpha < 1.0f)
        assertTrue(spec.containerColor.alpha > 0.05f)
    }

    @Test
    fun resolveHomeCardWallpaperSurfaceMode_usesRealtimeFrostedForReadyStaticWallpaper() {
        assertEquals(
            HomeCardWallpaperSurfaceMode.REALTIME_FROSTED,
            resolveHomeCardWallpaperSurfaceMode(
                dynamicTintEnabled = true,
                frostedGlassEnabled = true,
                wallpaperVisible = true,
                wallpaperIsStatic = true,
                backdropReady = true,
                blurEnabled = true,
                isDataSaverActive = false,
                lowBlurBudgetForced = false,
                sdkInt = 34,
            )
        )
    }

    @Test
    fun resolveHomeCardWallpaperSurfaceMode_fallsBackForAnimatedOrUnavailableWallpaper() {
        val animated = resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = true,
            frostedGlassEnabled = true,
            wallpaperVisible = true,
            wallpaperIsStatic = false,
            backdropReady = true,
            blurEnabled = true,
            isDataSaverActive = false,
            lowBlurBudgetForced = false,
            sdkInt = 34,
        )
        val notReady = resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = true,
            frostedGlassEnabled = true,
            wallpaperVisible = true,
            wallpaperIsStatic = true,
            backdropReady = false,
            blurEnabled = true,
            isDataSaverActive = false,
            lowBlurBudgetForced = false,
            sdkInt = 34,
        )
        assertEquals(HomeCardWallpaperSurfaceMode.LIGHTWEIGHT_TINT, animated)
        assertEquals(HomeCardWallpaperSurfaceMode.LIGHTWEIGHT_TINT, notReady)
    }

    @Test
    fun resolveHomeCardWallpaperSurfaceMode_disablesRealtimeForBudgetOrPlatform() {
        val dataSaver = resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = true,
            frostedGlassEnabled = true,
            wallpaperVisible = true,
            wallpaperIsStatic = true,
            backdropReady = true,
            blurEnabled = true,
            isDataSaverActive = true,
            lowBlurBudgetForced = false,
            sdkInt = 34,
        )
        val oldApi = resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = true,
            frostedGlassEnabled = true,
            wallpaperVisible = true,
            wallpaperIsStatic = true,
            backdropReady = true,
            blurEnabled = true,
            isDataSaverActive = false,
            lowBlurBudgetForced = false,
            sdkInt = 30,
        )
        assertEquals(HomeCardWallpaperSurfaceMode.LIGHTWEIGHT_TINT, dataSaver)
        assertEquals(HomeCardWallpaperSurfaceMode.LIGHTWEIGHT_TINT, oldApi)
    }

    @Test
    fun resolveHomeCardWallpaperSurfaceMode_usesStandardWhenDisabledOrNoWallpaper() {
        assertEquals(
            HomeCardWallpaperSurfaceMode.STANDARD,
            resolveHomeCardWallpaperSurfaceMode(
                dynamicTintEnabled = false,
                frostedGlassEnabled = false,
                wallpaperVisible = true,
                wallpaperIsStatic = true,
                backdropReady = true,
                blurEnabled = true,
                isDataSaverActive = false,
                lowBlurBudgetForced = false,
                sdkInt = 34,
            )
        )
        assertEquals(
            HomeCardWallpaperSurfaceMode.STANDARD,
            resolveHomeCardWallpaperSurfaceMode(
                dynamicTintEnabled = true,
                frostedGlassEnabled = true,
                wallpaperVisible = false,
                wallpaperIsStatic = true,
                backdropReady = true,
                blurEnabled = true,
                isDataSaverActive = false,
                lowBlurBudgetForced = false,
                sdkInt = 34,
            )
        )
    }

    @Test
    fun cardTintAndFrostedGlassCanBeUsedSeparately() {
        val tintOnly = resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = true,
            frostedGlassEnabled = false,
            wallpaperVisible = true,
            wallpaperIsStatic = true,
            backdropReady = true,
            blurEnabled = true,
            isDataSaverActive = false,
            lowBlurBudgetForced = false,
            sdkInt = 34,
        )
        val glassOnly = resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = false,
            frostedGlassEnabled = true,
            wallpaperVisible = true,
            wallpaperIsStatic = true,
            backdropReady = true,
            blurEnabled = true,
            isDataSaverActive = false,
            lowBlurBudgetForced = false,
            sdkInt = 34,
        )
        assertEquals(HomeCardWallpaperSurfaceMode.LIGHTWEIGHT_TINT, tintOnly)
        assertEquals(HomeCardWallpaperSurfaceMode.REALTIME_FROSTED, glassOnly)

        val tintedSurface = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = WallpaperPalette(Color.Blue, Color.Red),
            yFraction = 0.5f,
            coverTint = null,
            wallpaperTintEnabled = true,
            isDarkTheme = false,
            defaultContainerColor = Color.White,
            defaultBorderColor = Color.Gray,
            frostedGlassEnabled = false,
            dynamicTintEnabled = true,
        )
        val neutralGlass = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = WallpaperPalette(Color.Blue, Color.Red),
            yFraction = 0.5f,
            coverTint = null,
            wallpaperTintEnabled = true,
            isDarkTheme = false,
            defaultContainerColor = Color.White,
            defaultBorderColor = Color.Gray,
            frostedGlassEnabled = true,
            dynamicTintEnabled = false,
        )
        assertTrue(tintedSurface.containerColor.alpha > 0.8f)
        assertEquals(Color.White.copy(alpha = 0.34f), neutralGlass.containerColor)
    }

    @Test
    fun resolveVideoCardAdaptiveContentColors_usesWhiteTextForDarkCoverInLightMode() {
        val darkCover = Color(0xFF101828)
        val defaultOnSurface = Color(0xFF1D1B20)
        val defaultOnSurfaceVariant = Color(0xFF49454F)

        val colors = resolveVideoCardAdaptiveContentColors(
            wallpaperPalette = null,
            coverTint = darkCover,
            wallpaperTintEnabled = false,
            isDarkTheme = false,
            defaultOnSurface = defaultOnSurface,
            defaultOnSurfaceVariant = defaultOnSurfaceVariant,
            homeCardDynamicTintEnabled = true
        )

        assertEquals(Color.White, colors.titleColor)
        assertTrue(colors.isDarkSurface)
    }

    @Test
    fun resolveVideoCardAdaptiveContentColors_usesOnSurfaceForLightCoverInLightMode() {
        val lightCover = Color(0xFFE8F0FE)
        val defaultOnSurface = Color(0xFF1D1B20)
        val defaultOnSurfaceVariant = Color(0xFF49454F)

        val colors = resolveVideoCardAdaptiveContentColors(
            wallpaperPalette = null,
            coverTint = lightCover,
            wallpaperTintEnabled = false,
            isDarkTheme = false,
            defaultOnSurface = defaultOnSurface,
            defaultOnSurfaceVariant = defaultOnSurfaceVariant,
            homeCardDynamicTintEnabled = true
        )

        assertEquals(defaultOnSurface, colors.titleColor)
        assertEquals(defaultOnSurfaceVariant, colors.subtitleColor)
        assertEquals(false, colors.isDarkSurface)
    }

    @Test
    fun resolveVideoCardAdaptiveContentColors_usesWhiteTextForDarkWallpaperInLightMode() {
        val darkWallpaper = WallpaperPalette(
            topColor = Color(0xFF0F172A),
            bottomColor = Color(0xFF020617),
            dominantColor = Color(0xFF0F172A)
        )
        val defaultOnSurface = Color(0xFF1D1B20)
        val defaultOnSurfaceVariant = Color(0xFF49454F)

        val colors = resolveVideoCardAdaptiveContentColors(
            wallpaperPalette = darkWallpaper,
            coverTint = null,
            wallpaperTintEnabled = true,
            isDarkTheme = false,
            defaultOnSurface = defaultOnSurface,
            defaultOnSurfaceVariant = defaultOnSurfaceVariant,
            homeCardDynamicTintEnabled = true
        )

        assertEquals(Color.White, colors.titleColor)
        assertTrue(colors.isDarkSurface)
    }

    @Test
    fun resolveVideoCardAdaptiveContentColors_respectsDynamicTintDisabled() {
        val darkCover = Color(0xFF101828)
        val defaultOnSurface = Color(0xFF1D1B20)
        val defaultOnSurfaceVariant = Color(0xFF49454F)

        val colors = resolveVideoCardAdaptiveContentColors(
            wallpaperPalette = null,
            coverTint = darkCover,
            wallpaperTintEnabled = false,
            isDarkTheme = false,
            defaultOnSurface = defaultOnSurface,
            defaultOnSurfaceVariant = defaultOnSurfaceVariant,
            homeCardDynamicTintEnabled = false
        )

        assertEquals(defaultOnSurface, colors.titleColor)
        assertEquals(defaultOnSurfaceVariant, colors.subtitleColor)
        assertEquals(false, colors.isDarkSurface)
    }

    @Test
    fun wallpaperTintKeepsThemeTextReadableAcrossLightDarkAndGlassModes() {
        for (dark in listOf(false, true)) {
            val base = if (dark) Color(0xFF1C1B1F) else Color(0xFFF7F2FA)
            val title = if (dark) Color(0xFFE6E1E5) else Color(0xFF1D1B20)
            val subtitle = if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F)
            for (glass in listOf(false, true)) {
                for (wallpaper in listOf(Color.Black, Color.White, Color.Blue, Color.Cyan, Color.Gray, Color(0xFFFF8800))) {
                    val palette = WallpaperPalette(wallpaper, wallpaper)
                    val spec = resolveVideoCardAmbientDrawSpec(
                        wallpaperPalette = palette,
                        yFraction = 0.5f,
                        coverTint = Color.Red,
                        isDarkTheme = dark,
                        defaultContainerColor = base,
                        defaultBorderColor = Color.Gray,
                        frostedGlassEnabled = glass,
                    )
                    val surface = spec.containerColor.compositeOver(wallpaper)
                    val content = resolveVideoCardAdaptiveContentColors(
                        wallpaperPalette = palette,
                        coverTint = Color.Red,
                        wallpaperTintEnabled = true,
                        isDarkTheme = dark,
                        defaultOnSurface = title,
                        defaultOnSurfaceVariant = subtitle,
                        wallpaperSurfaceToned = true,
                    )
                    assertEquals(title, content.titleColor)
                    assertEquals(subtitle, content.subtitleColor)
                    for (text in listOf(content.titleColor, content.subtitleColor)) {
                        val bright = maxOf(text.luminance(), surface.luminance())
                        val dim = minOf(text.luminance(), surface.luminance())
                        assertTrue((bright + 0.05f) / (dim + 0.05f) >= 4.5f,
                            "Unreadable wallpaper tint: dark=$dark, glass=$glass, wallpaper=$wallpaper")
                    }
                    assertEquals(0f, spec.coverGlowAlpha)
                }
            }
        }
    }

    @Test
    fun pendingWallpaperPaletteUsesTheSameNeutralPlateAsTheLoadedSurface() {
        val base = Color(0xFFF7F2FA)
        val pending = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = null,
            yFraction = 0.5f,
            coverTint = Color.Red,
            wallpaperTintEnabled = true,
            isDarkTheme = false,
            defaultContainerColor = base,
            defaultBorderColor = Color.Gray,
        )
        val loaded = resolveVideoCardAmbientDrawSpec(
            wallpaperPalette = WallpaperPalette(base, base),
            yFraction = 0.5f,
            coverTint = Color.Red,
            wallpaperTintEnabled = true,
            isDarkTheme = false,
            defaultContainerColor = base,
            defaultBorderColor = Color.Gray,
        )
        assertEquals(loaded.containerColor, pending.containerColor)
        assertEquals(0f, pending.coverGlowAlpha)
    }

    @Test
    fun wallpaperSurfaceRetainsSourceHueWithoutColoringNeutralWallpaper() {
        for (base in listOf(Color(0xFFF7F2FA), Color(0xFF1C1B1F))) {
            for (wallpaper in listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color(0xFFFF8800))) {
                for (glass in listOf(false, true)) {
                    val surface = resolveWallpaperCardTint(wallpaper, base, glass)
                    val sourceHue = Hct.fromInt(wallpaper.toArgb()).hue
                    val surfaceHue = Hct.fromInt(surface.copy(alpha = 1f).toArgb()).hue
                    val delta = abs(sourceHue - surfaceHue)
                    assertTrue(minOf(delta, 360.0 - delta) < 5.0,
                        "Wallpaper hue changed: wallpaper=$wallpaper, surface=$surface")
                    if (!glass) assertEquals(1f, surface.alpha)
                }
            }
            val neutral = resolveWallpaperCardTint(Color.Gray, base, false)
            assertTrue(Hct.fromInt(neutral.toArgb()).chroma < 5.0)
        }
    }

    @Test
    fun glassOpacityRespondsToSubtitleContrastInsteadOfUsingOneFixedPlate() {
        val base = Color(0xFFF7F2FA)
        val standard = resolveWallpaperCardTint(
            wallpaperColor = Color.Blue,
            defaultContainerColor = base,
            frostedGlassEnabled = true,
            titleColor = Color.Black,
            subtitleColor = Color(0xFF49454F),
        )
        val demanding = resolveWallpaperCardTint(
            wallpaperColor = Color.Blue,
            defaultContainerColor = base,
            frostedGlassEnabled = true,
            titleColor = Color.Black,
            subtitleColor = Color(0xFF666666),
        )
        assertTrue(demanding.alpha > standard.alpha)
        for (backdrop in listOf(Color.Black, Color.White)) {
            val surface = demanding.compositeOver(backdrop)
            val contrast = (surface.luminance() + 0.05f) / (Color(0xFF666666).luminance() + 0.05f)
            assertTrue(contrast >= 4.5f)
        }
    }

    @Test
    fun precomputedWallpaperSurfacePalettePreservesScrollColorChangesAndReadableInterpolation() {
        val raw = WallpaperPalette(Color.Blue, Color.Red)
        val title = Color(0xFF1D1B20)
        val subtitle = Color(0xFF49454F)
        val surfacePalette = resolveWallpaperCardSurfacePalette(
            palette = raw,
            defaultContainerColor = Color(0xFFF7F2FA),
            titleColor = title,
            subtitleColor = subtitle,
            frostedGlassEnabled = true,
            isDataSaverActive = false,
        )
        assertTrue(surfacePalette.topColor != surfacePalette.bottomColor)
        assertEquals(surfacePalette.topColor, interpolateWallpaperColor(surfacePalette, 0f))
        assertEquals(surfacePalette.bottomColor, interpolateWallpaperColor(surfacePalette, 1f))
        for (position in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val plate = interpolateWallpaperColor(surfacePalette, position)
            for (backdrop in listOf(Color.Black, Color.White)) {
                val surface = plate.compositeOver(backdrop)
                for (text in listOf(title, subtitle)) {
                    val contrast = (surface.luminance() + 0.05f) / (text.luminance() + 0.05f)
                    assertTrue(contrast >= 4.5f)
                }
            }
        }
    }

    @Test
    fun darkWallpaperSurfacesUseSubduedColorInsteadOfDenseWarmPanels() {
        for (wallpaper in listOf(Color(0xFFFF8800), Color.Red, Color.Blue, Color.Green)) {
            for (glass in listOf(false, true)) {
                val light = resolveWallpaperCardTint(wallpaper, Color(0xFFF7F2FA), glass)
                val dark = resolveWallpaperCardTint(wallpaper, Color(0xFF1C1B1F), glass)
                val lightChroma = Hct.fromInt(light.copy(alpha = 1f).toArgb()).chroma
                val darkChroma = Hct.fromInt(dark.copy(alpha = 1f).toArgb()).chroma
                assertTrue(darkChroma < lightChroma,
                    "Dark wallpaper surface should be less saturated: wallpaper=$wallpaper, glass=$glass")
            }
        }
    }

}
