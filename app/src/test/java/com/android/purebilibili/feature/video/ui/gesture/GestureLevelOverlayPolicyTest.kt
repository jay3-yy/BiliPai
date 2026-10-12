package com.android.purebilibili.feature.video.ui.gesture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppTopTabPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GestureLevelOverlayPolicyTest {

    @Test
    fun overlayStyle_mapsChromePresentation() {
        assertEquals(
            GestureLevelOverlayStyle.Md3,
            resolveGestureLevelOverlayStyle(AppTopTabPresentation.MATERIAL_UNDERLINE)
        )
        assertEquals(
            GestureLevelOverlayStyle.Ios,
            resolveGestureLevelOverlayStyle(AppTopTabPresentation.MOVING_CAPSULE)
        )
        assertEquals(
            GestureLevelOverlayStyle.Miuix,
            resolveGestureLevelOverlayStyle(
                presentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
                uiStyle = AppUiStyle.MIUIX,
            )
        )
    }

    @Test
    fun overlaySpec_differsByThemeLayout() {
        val md3 = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Md3,
            kind = GestureLevelKind.Volume,
            percent = 0.5f
        )
        val ios = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Ios,
            kind = GestureLevelKind.Volume,
            percent = 0.5f
        )
        val iosBrightness = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Ios,
            kind = GestureLevelKind.Brightness,
            percent = 0.5f
        )
        val md3Brightness = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Md3,
            kind = GestureLevelKind.Brightness,
            percent = 0.5f
        )
        val miuixVolume = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Miuix,
            kind = GestureLevelKind.Volume,
            percent = 0.5f
        )
        val miuixBrightness = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Miuix,
            kind = GestureLevelKind.Brightness,
            percent = 0.5f
        )

        assertFalse(md3.verticalRail)
        assertFalse(ios.verticalRail)
        assertFalse(miuixVolume.verticalRail)
        assertEquals(Alignment.Center, md3.alignment)
        assertEquals(Alignment.Center, md3Brightness.alignment)
        assertEquals(Alignment.CenterEnd, ios.alignment)
        assertEquals(Alignment.CenterStart, iosBrightness.alignment)
        assertEquals(Alignment.TopCenter, miuixVolume.alignment)
        assertEquals(Alignment.TopCenter, miuixBrightness.alignment)
        assertEquals(186, miuixVolume.railWidthDp)
        assertEquals(40, miuixVolume.railHeightDp)
        assertEquals(32, miuixVolume.topInsetDp)
        assertEquals(32, miuixBrightness.topInsetDp)
        assertEquals(0, md3.topInsetDp)
        assertEquals(0, ios.topInsetDp)
        assertTrue(ios.showLabel)
        assertFalse(md3.showLabel)
        assertFalse(miuixVolume.showPercentText)
    }

    @Test
    fun md3Colors_keepBrightnessIndependentOfTheme() {
        val themes = listOf(
            lightColorScheme(primary = Color(0xFF6750A4)),
            darkColorScheme(primary = Color(0xFF80CBC4))
        )
        for (colors in themes) {
            for (kind in GestureLevelKind.entries) {
                val spec = resolveGestureLevelOverlaySpec(
                    brightnessColor = Color.Yellow,
                    style = GestureLevelOverlayStyle.Md3,
                    kind = kind,
                    percent = 0.5f,
                    colorScheme = colors
                )
                assertEquals(
                    if (kind == GestureLevelKind.Brightness) Color.Yellow
                    else androidx.compose.ui.graphics.lerp(colors.primary, Color.White, 0.30f),
                    spec.fillColor
                )
                assertEquals(Color.White, spec.iconTint)
                assertEquals(Color(0xFF16181D).copy(alpha = 0.70f), spec.containerColor)
                assertEquals(Color.White, spec.textColor)
            }
        }
    }

    @Test
    fun miuixColors_followNativeSemanticColors() {
        val lightContainer = Color(0xFFF2F2F2)
        val lightContent = Color(0xFF1A1A1A)
        val darkContainer = Color(0xFF2A2A2A)
        val darkContent = Color(0xFFF5F5F5)

        val light = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Miuix,
            kind = GestureLevelKind.Brightness,
            percent = 0.5f,
            miuixContainerColor = lightContainer,
            miuixContentColor = lightContent
        )
        val dark = resolveGestureLevelOverlaySpec(
            brightnessColor = Color.Yellow,
            style = GestureLevelOverlayStyle.Miuix,
            kind = GestureLevelKind.Volume,
            percent = 0.5f,
            miuixContainerColor = darkContainer,
            miuixContentColor = darkContent
        )

        assertEquals(lightContainer, light.containerColor)
        assertEquals(lightContent, light.iconTint)
        assertEquals(darkContainer, dark.containerColor)
        assertEquals(darkContent, dark.iconTint)
    }

    @Test
    fun icons_resolveByLevelForAllStyles() {
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeOff,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Md3, GestureLevelKind.Volume, 0f)
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeUp,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Md3, GestureLevelKind.Volume, 1f)
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeOff,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Ios, GestureLevelKind.Volume, 0f)
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeUp,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Ios, GestureLevelKind.Volume, 1f)
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeOff,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Miuix, GestureLevelKind.Volume, 0f)
        )
        assertEquals(
            Icons.AutoMirrored.Filled.VolumeUp,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Miuix, GestureLevelKind.Volume, 1f)
        )
        assertEquals(
            Icons.Filled.BrightnessLow,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Md3, GestureLevelKind.Brightness, 0.2f)
        )
        assertEquals(
            Icons.Filled.LightMode,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Ios, GestureLevelKind.Brightness, 0.5f)
        )
        assertEquals(
            Icons.Filled.BrightnessHigh,
            resolveGestureLevelIcon(GestureLevelOverlayStyle.Md3, GestureLevelKind.Brightness, 0.9f)
        )
    }
}
