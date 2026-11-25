package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AppUiStyle
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.ui.OpticalContrastPalette
import com.android.purebilibili.core.ui.blur.BlurIntensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarSurfaceColorPolicyTest {

    @Test
    fun `floating skin label preserves resolved contrast tint over sampled tail artwork`() {
        val glacierTeal = Color(0xFF42707F)
        val snowBackdrop = resolveEffectiveSkinBackdropReferenceColor(
            skinTrimTint = Color(0xFFAAE8FC),
            sampledTailLuminance = 0.92f,
        )
        val snowColors = resolveBottomBarSkinContentColors(
            selectedColor = Color(0xFF103B49),
            unselectedColor = glacierTeal,
            skinTrimTint = snowBackdrop,
        )

        assertEquals(
            glacierTeal,
            resolveFloatingBottomBarLabelColor(snowColors.unselectedColor, hasSkinArtwork = true),
        )
        assertEquals(
            Color(0xFF103B49),
            resolveFloatingBottomBarLabelColor(snowColors.selectedColor, hasSkinArtwork = false),
        )
    }

    @Test
    fun `blur enabled follows blur style alpha`() {
        val color = resolveBottomBarSurfaceColor(
            surfaceColor = Color.White,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(0.4f, color.alpha, 0.001f)
    }

    @Test
    fun `blur disabled keeps light theme surface color`() {
        val color = resolveBottomBarSurfaceColor(
            surfaceColor = Color.White,
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(Color.White, color)
    }

    @Test
    fun `blur disabled keeps dark theme surface color`() {
        val color = resolveBottomBarSurfaceColor(
            surfaceColor = Color(0xFF121212),
            blurEnabled = false,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(Color(0xFF121212), color)
    }

    @Test
    fun `android native floating shell is opaque when blur is disabled`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = false,
            darkTheme = false
        )

        assertEquals(1f, tuning.shellSurfaceAlpha, 0.001f)
    }

    @Test
    fun `android native floating shell is translucent when ordinary blur is enabled`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = true,
            darkTheme = false
        )
        val color = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.White,
            tuning = tuning,
            glassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN
        )

        assertEquals(0.4f, tuning.shellSurfaceAlpha, 0.001f)
        assertEquals(0.4f, color.alpha, 0.001f)
    }

    @Test
    fun `android native floating shell follows ordinary blur intensity when glass is off`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = true,
            darkTheme = false
        )
        val color = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.White,
            tuning = tuning,
            glassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.APPLE_DOCK
        )

        assertEquals(0.6f, color.alpha, 0.001f)
    }

    @Test
    fun `global wallpaper raises floating shell alpha floor`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = true,
            darkTheme = false
        )
        val color = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.White,
            tuning = tuning,
            glassEnabled = false,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN,
            globalWallpaperVisible = true
        )

        assertTrue(color.alpha >= 0.67f)
    }

    @Test
    fun `android native glass stays enabled when liquid glass is on even if blur toggle is off`() {
        assertTrue(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = true,
                blurEnabled = false,
                sdkInt = 33
            )
        )
        assertFalse(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = false,
                blurEnabled = false,
                sdkInt = 33
            )
        )
    }

    @Test
    fun `android native liquid glass stays enabled when blur toggle is also on`() {
        assertFalse(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = false,
                blurEnabled = true,
                sdkInt = 33
            )
        )
        assertTrue(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = true,
                blurEnabled = true,
                sdkInt = 33
            )
        )
    }

    @Test
    fun `android 12 downgrades liquid glass to ordinary frosted blur`() {
        assertFalse(
            resolveAndroidNativeBottomBarGlassEnabled(
                liquidGlassEnabled = true,
                blurEnabled = true,
                sdkInt = 31
            )
        )
        assertTrue(
            shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = true,
                glassEnabled = false,
                hasHazeState = true,
                sdkInt = 31
            )
        )
    }

    @Test
    fun `ios26 liquid glass preset keeps BiliPai shell translucency`() {
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = true,
            darkTheme = true
        )
        val bilipai = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.Black,
            tuning = tuning,
            glassEnabled = true,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN,
            liquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED
        )
        val ios26 = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = Color.Black,
            tuning = tuning,
            glassEnabled = true,
            blurEnabled = true,
            blurIntensity = BlurIntensity.THIN,
            liquidGlassPreset = BottomBarLiquidGlassPreset.IOS26_REFINED
        )

        assertEquals(0.40f, bilipai.alpha, 0.003f)
        assertEquals(0.40f, ios26.alpha, 0.003f)
        assertEquals(ios26.alpha, bilipai.alpha, 0.003f)
    }

    @Test
    fun `android native floating blur uses haze when available`() {
        assertTrue(
            shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = true,
                glassEnabled = false,
                hasHazeState = true,
                sdkInt = 33
            )
        )
        assertFalse(
            shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = true,
                glassEnabled = true,
                hasHazeState = true,
                sdkInt = 33
            )
        )
    }

    @Test
    fun `android native floating blur avoids haze before runtime shader support`() {
        assertFalse(
            shouldUseAndroidNativeFloatingHazeBlur(
                blurEnabled = true,
                glassEnabled = false,
                hasHazeState = true,
                sdkInt = 29
            )
        )
    }

    @Test
    fun `android native indicator keeps capsule static when idle`() {
        val spec = resolveAndroidNativeIndicatorSpec(isMoving = false)

        assertFalse(spec.usesLens)
        assertFalse(spec.captureTintedContentLayer)
    }

    @Test
    fun `android native indicator enables lens and tinted export while moving`() {
        val spec = resolveAndroidNativeIndicatorSpec(isMoving = true)

        assertTrue(spec.usesLens)
        assertTrue(spec.captureTintedContentLayer)
    }

    @Test
    fun `android native indicator color softens primary tint in light theme`() {
        val color = resolveAndroidNativeIndicatorColor(
            themeColor = Color(0xFF4F7CFF),
            darkTheme = false
        )

        assertTrue(color.alpha > 0.7f)
        assertTrue(color.red > 0.7f)
        assertTrue(color.green > 0.8f)
    }

    @Test
    fun `settled segmented indicator uses bottom bar surface tint`() {
        val color = resolveIosFloatingBottomIndicatorColor(
            isDarkTheme = true,
            visualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = false,
                shouldRefract = false,
                useNeutralTint = false
            ),
            liquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
        )

        assertEquals(resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = true).red, color.red, 0.001f)
        assertEquals(resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = true).green, color.green, 0.001f)
        assertEquals(resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = true).blue, color.blue, 0.001f)
        assertTrue(color.alpha < 1f)
    }

    @Test
    fun `light segmented indicator uses gray white bottom bar surface tint`() {
        val color = resolveIosFloatingBottomIndicatorColor(
            isDarkTheme = false,
            visualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = false,
                shouldRefract = false,
                useNeutralTint = false
            ),
            liquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
        )

        assertEquals(resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = false).red, color.red, 0.001f)
        assertEquals(resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = false).green, color.green, 0.001f)
        assertEquals(resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = false).blue, color.blue, 0.001f)
    }

    @Test
    fun `moving segmented indicator uses bottom bar moving alpha floor`() {
        val visualPolicy = BottomBarIndicatorVisualPolicy(
            isInMotion = true,
            shouldRefract = true,
            useNeutralTint = true
        )
        val alpha = resolveIosFloatingBottomIndicatorTintAlpha(
            visualPolicy = visualPolicy,
            isDarkTheme = false,
            liquidGlassProgress = 0.5f,
            configuredAlpha = 0.12f
        )

        assertTrue(alpha >= 0.4f)
    }

    @Test
    fun `segmented controls route non glass indicator by ui theme`() {
        assertEquals(
            SegmentedControlChromeStyle.ANDROID_NATIVE_UNDERLINE,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MATERIAL3,
                prefersNativeChrome = true,
                androidNativeLiquidGlassEnabled = false
            )
        )

        assertEquals(
            SegmentedControlChromeStyle.LIQUID_PILL,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MATERIAL3,
                prefersNativeChrome = true,
                androidNativeLiquidGlassEnabled = true
            )
        )

        assertEquals(
            SegmentedControlChromeStyle.LIQUID_PILL,
            resolveSegmentedControlChromeStyle(
                uiStyle = AppUiStyle.MIUIX,
                prefersNativeChrome = false,
                androidNativeLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun `android native export layer tint keeps theme hue in dark theme`() {
        val color = resolveAndroidNativeExportTintColor(
            themeColor = Color(0xFF4F7CFF),
            darkTheme = true
        )

        assertTrue(color.alpha > 0.2f)
        assertTrue(color.blue >= color.red)
    }

    @Test
    fun `android native export layer tint preserves theme brightness in light theme`() {
        val themeColor = Color(0xFFE67A73)
        val color = resolveAndroidNativeExportTintColor(
            themeColor = themeColor,
            darkTheme = false
        )

        assertEquals(themeColor.red, color.red, 0.001f)
        assertEquals(themeColor.green, color.green, 0.001f)
        assertEquals(themeColor.blue, color.blue, 0.001f)
        assertEquals(themeColor.alpha, color.alpha, 0.001f)
    }

    @Test
    fun `android native glass export tint preserves theme hue`() {
        val themeColor = Color(0xFF4F7CFF)
        val containerColor = Color(0xFFE8ECEF)
        val color = resolveAndroidNativeExportTintColor(
            themeColor = themeColor,
            darkTheme = false,
            containerColor = containerColor,
            glassEnabled = true
        )

        assertEquals(themeColor.red, color.red, 0.001f)
        assertEquals(themeColor.green, color.green, 0.001f)
        assertEquals(themeColor.blue, color.blue, 0.001f)
        assertEquals(themeColor.alpha, color.alpha, 0.001f)
    }

    @Test
    fun `android native glass visible selected item stays themed while indicator is idle`() {
        val unselected = Color(0xFF202124)
        val selected = Color(0xFF00A1D6)
        val color = resolveBottomBarGlassVisibleContentColor(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 1f,
            glassEnabled = true,
            indicatorProgress = 0f
        )

        assertEquals(selected.red, color.red, 0.001f)
        assertEquals(selected.green, color.green, 0.001f)
        assertEquals(selected.blue, color.blue, 0.001f)
    }

    @Test
    fun `android native glass visible layer turns neutral only while indicator refracts`() {
        val unselected = Color(0xFF202124)
        val selected = Color(0xFF00A1D6)
        val color = resolveBottomBarGlassVisibleContentColor(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 1f,
            glassEnabled = true,
            indicatorProgress = 1f
        )

        assertEquals(unselected.red, color.red, 0.001f)
        assertEquals(unselected.green, color.green, 0.001f)
        assertEquals(unselected.blue, color.blue, 0.001f)
    }

    @Test
    fun `android native glass visible selected item stays themed when indicator backdrop is disabled`() {
        val unselected = Color(0xFF202124)
        val selected = Color(0xFF00A1D6)
        val color = resolveBottomBarGlassVisibleContentColor(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 1f,
            glassEnabled = true,
            indicatorProgress = 1f,
            indicatorBackdropEnabled = false
        )

        assertEquals(selected.red, color.red, 0.001f)
        assertEquals(selected.green, color.green, 0.001f)
        assertEquals(selected.blue, color.blue, 0.001f)
    }

    @Test
    fun `light skin trim replaces low contrast themed foreground`() {
        val themedUnselectedColor = Color.White.copy(alpha = 0.78f)
        val colors = resolveBottomBarSkinContentColors(
            selectedColor = Color(0xFFFFA000),
            unselectedColor = themedUnselectedColor,
            skinTrimTint = Color(0xFFF3CF87)
        )

        assertEquals(OpticalContrastPalette.Shadow, colors.selectedColor)
        assertEquals(OpticalContrastPalette.Shadow, colors.unselectedColor)
        assertEquals(OpticalContrastPalette.Highlight, colors.labelScrimColor)
        assertEquals(0f, colors.labelScrimAlpha, 0.0001f)
    }

    @Test
    fun `dark skin trim keeps readable foreground without a label capsule`() {
        val colors = resolveBottomBarSkinContentColors(
            selectedColor = Color(0xFFFFA000),
            unselectedColor = Color.White,
            skinTrimTint = Color(0xFF241E17)
        )

        assertEquals(Color.White, colors.unselectedColor)
        assertEquals(OpticalContrastPalette.Shadow, colors.labelScrimColor)
        assertEquals(0f, colors.labelScrimAlpha, 0.0001f)
    }

    @Test
    fun `android native glass export content keeps neutral base while partially covered`() {
        val unselected = Color(0xFF202124)
        val selected = Color(0xFF00A1D6)
        val color = resolveBottomBarGlassExportContentColor(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 0.42f,
            glassEnabled = true
        )

        assertEquals(unselected.red, color.red, 0.001f)
        assertEquals(unselected.green, color.green, 0.001f)
        assertEquals(unselected.blue, color.blue, 0.001f)
    }

    @Test
    fun `android native glass export content keeps uncovered item neutral`() {
        val unselected = Color(0xFF202124)
        val selected = Color(0xFF00A1D6)
        val color = resolveBottomBarGlassExportContentColor(
            unselectedColor = unselected,
            selectedColor = selected,
            themeWeight = 0f,
            glassEnabled = true
        )

        assertEquals(unselected.red, color.red, 0.001f)
        assertEquals(unselected.green, color.green, 0.001f)
        assertEquals(unselected.blue, color.blue, 0.001f)
    }

    @Test
    fun `android native idle glass indicator uses BiliPai neutral overlay in light mode`() {
        val themeIndicator = resolveAndroidNativeIndicatorColor(
            themeColor = Color(0xFF00A1D6),
            darkTheme = false
        )
        val idleIndicator = resolveAndroidNativeIdleIndicatorSurfaceColor(
            darkTheme = false
        )

        assertEquals(Color.Black.red, idleIndicator.red, 0.001f)
        assertEquals(Color.Black.green, idleIndicator.green, 0.001f)
        assertEquals(Color.Black.blue, idleIndicator.blue, 0.001f)
        assertEquals(0.1f, idleIndicator.alpha, 0.003f)
        assertFalse(
            idleIndicator.red == themeIndicator.red &&
                idleIndicator.green == themeIndicator.green &&
                idleIndicator.blue == themeIndicator.blue
        )
    }

    @Test
    fun `BiliPai light glass shell uses native white surface container`() {
        val color = resolveBiliPaiBottomBarContainerColor(darkTheme = false)

        assertEquals(Color.White.red, color.red, 0.001f)
        assertEquals(Color.White.green, color.green, 0.001f)
        assertEquals(Color.White.blue, color.blue, 0.001f)
        assertEquals(0.4f, color.alpha, 0.003f)
    }

    @Test
    fun `BiliPai dark glass shell uses native 242424 surface container`() {
        val color = resolveBiliPaiBottomBarContainerColor(darkTheme = true)
        val expected = Color(0xFF242424)

        assertEquals(expected.red, color.red, 0.001f)
        assertEquals(expected.green, color.green, 0.001f)
        assertEquals(expected.blue, color.blue, 0.001f)
        assertEquals(0.4f, color.alpha, 0.003f)
    }

    @Test
    fun `BiliPai blur only shell keeps configured blur surface alpha`() {
        val themeSurface = Color(0x66352F2A)
        val color = resolveBiliPaiBottomBarShellColor(
            containerColor = themeSurface,
            liquidGlassEnabled = false,
            darkTheme = true
        )

        assertEquals(themeSurface, color)
        assertTrue(shouldBlurBiliPaiBottomBarShell(blurEnabled = true))
        assertFalse(shouldBlurBiliPaiBottomBarShell(blurEnabled = false))
    }

    @Test
    fun `BiliPai liquid glass shell keeps glass material and shell blur`() {
        val color = resolveBiliPaiBottomBarShellColor(
            containerColor = Color.Red,
            liquidGlassEnabled = true,
            darkTheme = true
        )

        assertEquals(Color.Red.copy(alpha = 0.4f), color)
        assertTrue(shouldBlurBiliPaiBottomBarShell(blurEnabled = true))
    }

    @Test
    fun `bottom bar theme follows app background instead of system theme`() {
        assertFalse(resolveBottomBarDarkTheme(Color(0xFFF7F7F7)))
        assertTrue(resolveBottomBarDarkTheme(Color(0xFF090909)))
    }

    @Test
    fun `android native idle glass indicator uses BiliPai neutral overlay in dark mode`() {
        val idleIndicator = resolveAndroidNativeIdleIndicatorSurfaceColor(
            darkTheme = true
        )

        assertEquals(Color.White.red, idleIndicator.red, 0.001f)
        assertEquals(Color.White.green, idleIndicator.green, 0.001f)
        assertEquals(Color.White.blue, idleIndicator.blue, 0.001f)
        assertEquals(0.1f, idleIndicator.alpha, 0.003f)
    }

    @Test
    fun `ios26 idle glass indicator uses same BiliPai overlay in dark mode`() {
        val tunedDark = resolveBottomBarIdleIndicatorSurfaceColor(
            preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
            darkTheme = true
        )
        val ios26Dark = resolveBottomBarIdleIndicatorSurfaceColor(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            darkTheme = true
        )

        assertEquals(tunedDark.red, ios26Dark.red, 0.001f)
        assertEquals(tunedDark.green, ios26Dark.green, 0.001f)
        assertEquals(tunedDark.blue, ios26Dark.blue, 0.001f)
        assertEquals(tunedDark.alpha, ios26Dark.alpha, 0.001f)
    }

    @Test
    fun `ios26 idle glass indicator keeps BiliPai low alpha overlay in light mode`() {
        val tunedLight = resolveBottomBarIdleIndicatorSurfaceColor(
            preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
            darkTheme = false
        )
        val ios26Light = resolveBottomBarIdleIndicatorSurfaceColor(
            preset = BottomBarLiquidGlassPreset.IOS26_REFINED,
            darkTheme = false
        )

        assertEquals(tunedLight.red, ios26Light.red, 0.001f)
        assertEquals(tunedLight.green, ios26Light.green, 0.001f)
        assertEquals(tunedLight.blue, ios26Light.blue, 0.001f)
        assertEquals(tunedLight.alpha, ios26Light.alpha, 0.001f)
    }

    @Test
    fun `moving floating bottom bar staggers shell and indicator refraction offsets`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 1.35f,
            velocity = 900f,
            isDragging = true
        )

        assertTrue(profile.progress > 0f)
        assertTrue(profile.exportPanelOffsetFraction > 0f)
        assertTrue(profile.indicatorPanelOffsetFraction > 0f)
        assertTrue(profile.visiblePanelOffsetFraction > 0f)
        assertTrue(profile.exportPanelOffsetFraction < profile.indicatorPanelOffsetFraction)
        assertTrue(profile.visiblePanelOffsetFraction < profile.indicatorPanelOffsetFraction)
        assertTrue(profile.visibleSelectionEmphasis < 1f)
        assertTrue(profile.exportSelectionEmphasis < 1f)
    }

    @Test
    fun `idle floating bottom bar refraction profile stays neutral`() {
        val profile = resolveBottomBarRefractionMotionProfile(
            position = 2f,
            velocity = 0f,
            isDragging = false
        )

        assertEquals(0f, profile.progress, 0.001f)
        assertEquals(0f, profile.exportPanelOffsetFraction, 0.001f)
        assertEquals(0f, profile.indicatorPanelOffsetFraction, 0.001f)
        assertEquals(0f, profile.visiblePanelOffsetFraction, 0.001f)
        assertEquals(1f, profile.visibleSelectionEmphasis, 0.001f)
        assertEquals(1f, profile.exportSelectionEmphasis, 0.001f)
    }

    @Test
    fun `moving floating indicator uses combined backdrop when tinted layer is exported`() {
        val policy = resolveBottomBarRefractionLayerPolicy(
            isFloating = true,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = true,
                shouldRefract = true,
                useNeutralTint = false
            )
        )

        assertTrue(policy.captureTintedContentLayer)
        assertTrue(policy.useCombinedBackdrop)
    }

    @Test
    fun `idle floating indicator does not export tinted layer or combined backdrop`() {
        val policy = resolveBottomBarRefractionLayerPolicy(
            isFloating = true,
            isLiquidGlassEnabled = true,
            indicatorVisualPolicy = BottomBarIndicatorVisualPolicy(
                isInMotion = false,
                shouldRefract = false,
                useNeutralTint = false
            )
        )

        assertFalse(policy.captureTintedContentLayer)
        assertFalse(policy.useCombinedBackdrop)
    }
}
