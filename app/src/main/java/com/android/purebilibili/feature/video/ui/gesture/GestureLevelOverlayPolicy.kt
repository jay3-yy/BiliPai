package com.android.purebilibili.feature.video.ui.gesture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode

/**
 * Three fully distinct volume/brightness feedback skins.
 * Layout, placement, icon family and motion all differ by style.
 */
enum class GestureLevelOverlayStyle {
    /** Material 3: compact horizontal bar at the top of the player. */
    Md3,
    /** iOS: centered frosted capsule with SF-style glyphs. */
    Ios,
    /** MIUIX: native animated horizontal slider at the top of the player. */
    Miuix
}

enum class GestureLevelKind {
    Brightness,
    Volume
}

data class GestureLevelOverlaySpec(
    val style: GestureLevelOverlayStyle,
    val kind: GestureLevelKind,
    val alignment: Alignment,
    val showLabel: Boolean,
    val showPercentText: Boolean,
    val verticalRail: Boolean,
    val accentColor: Color,
    val trackColor: Color,
    val fillColor: Color,
    val containerColor: Color,
    val borderColor: Color,
    val iconTint: Color,
    val textColor: Color,
    val railWidthDp: Int,
    val railHeightDp: Int,
    val capsuleMinWidthDp: Int,
    val iconSizeDp: Int,
    val topInsetDp: Int
)

fun resolveGestureLevelOverlayStyle(
    presentation: AppTopTabPresentation,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
): GestureLevelOverlayStyle {
    if (uiStyle == AppUiStyle.MIUIX) return GestureLevelOverlayStyle.Miuix
    return when (presentation) {
        AppTopTabPresentation.TONAL_CAPSULE -> GestureLevelOverlayStyle.Miuix
        AppTopTabPresentation.MOVING_CAPSULE -> GestureLevelOverlayStyle.Ios
        AppTopTabPresentation.MATERIAL_UNDERLINE -> GestureLevelOverlayStyle.Md3
    }
}

@Composable
fun rememberGestureLevelOverlayStyle(
    presentation: AppTopTabPresentation,
): GestureLevelOverlayStyle {
    val uiStyle = LocalAppUiStyle.current
    return remember(uiStyle, presentation) {
        resolveGestureLevelOverlayStyle(
            presentation = presentation,
            uiStyle = uiStyle,
        )
    }
}

fun resolveGestureLevelKind(mode: VideoGestureMode): GestureLevelKind? {
    return when (mode) {
        VideoGestureMode.Brightness -> GestureLevelKind.Brightness
        VideoGestureMode.Volume -> GestureLevelKind.Volume
        else -> null
    }
}

fun resolveGestureLevelOverlaySpec(
    style: GestureLevelOverlayStyle,
    kind: GestureLevelKind,
    percent: Float,
    brightnessColor: Color,
    colorScheme: ColorScheme = darkColorScheme(),
    miuixContainerColor: Color = Color(0xE61A1A1A),
    miuixContentColor: Color = Color.White
): GestureLevelOverlaySpec {
    val progress = percent.coerceIn(0f, 1f)
    val isVolume = kind == GestureLevelKind.Volume
    return when (style) {
        GestureLevelOverlayStyle.Md3 -> GestureLevelOverlaySpec(
            style = style,
            kind = kind,
            alignment = Alignment.Center,
            showLabel = false,
            showPercentText = true,
            verticalRail = false,
            accentColor = if (isVolume) colorScheme.primary else brightnessColor,
            trackColor = Color.White.copy(alpha = 0.18f),
            fillColor = if (isVolume) lerp(colorScheme.primary, Color.White, 0.30f) else brightnessColor,
            containerColor = Color(0xFF16181D).copy(alpha = 0.70f),
            borderColor = colorScheme.outlineVariant,
            iconTint = Color.White,
            textColor = Color.White,
            railWidthDp = 0,
            railHeightDp = 0,
            capsuleMinWidthDp = 0,
            iconSizeDp = 26,
            topInsetDp = 0
        )
        GestureLevelOverlayStyle.Ios -> GestureLevelOverlaySpec(
            style = style,
            kind = kind,
            alignment = if (isVolume) Alignment.CenterEnd else Alignment.CenterStart,
            showLabel = true,
            showPercentText = true,
            verticalRail = false,
            accentColor = if (isVolume) Color(0xFF64D2FF) else brightnessColor,
            trackColor = Color.White.copy(alpha = 0.22f),
            fillColor = if (isVolume) Color(0xFF64D2FF) else brightnessColor,
            containerColor = Color.Black.copy(alpha = 0.52f + progress * 0.08f),
            borderColor = Color.White.copy(alpha = 0.28f),
            iconTint = Color.White,
            textColor = Color.White,
            railWidthDp = 0,
            railHeightDp = 0,
            capsuleMinWidthDp = 148,
            iconSizeDp = 34,
            topInsetDp = 0
        )
        GestureLevelOverlayStyle.Miuix -> GestureLevelOverlaySpec(
            style = style,
            kind = kind,
            alignment = Alignment.TopCenter,
            showLabel = false,
            showPercentText = false,
            verticalRail = false,
            accentColor = if (isVolume) Color(0xFF0D84FF) else brightnessColor,
            trackColor = Color.White.copy(alpha = 0.18f),
            fillColor = if (isVolume) Color(0xFF0D84FF) else brightnessColor,
            containerColor = miuixContainerColor,
            borderColor = Color.White.copy(alpha = 0.06f),
            iconTint = miuixContentColor,
            textColor = miuixContentColor,
            railWidthDp = 186,
            railHeightDp = 40,
            capsuleMinWidthDp = 40,
            iconSizeDp = 22,
            topInsetDp = 32
        )
    }
}

fun resolveGestureLevelIcon(
    style: GestureLevelOverlayStyle,
    kind: GestureLevelKind,
    percent: Float
): ImageVector {
    val p = percent.coerceIn(0f, 1f)
    return when (kind) {
        GestureLevelKind.Volume -> when (style) {
            GestureLevelOverlayStyle.Md3 -> when {
                p < 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                p < 0.34f -> Icons.AutoMirrored.Filled.VolumeMute
                p < 0.67f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            GestureLevelOverlayStyle.Ios -> when {
                p < 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                p < 0.45f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            GestureLevelOverlayStyle.Miuix -> when {
                p < 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                p < 0.34f -> Icons.AutoMirrored.Filled.VolumeMute
                p < 0.67f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
        }
        GestureLevelKind.Brightness -> when (style) {
            GestureLevelOverlayStyle.Md3 -> when {
                p < 0.34f -> Icons.Filled.BrightnessLow
                p < 0.67f -> Icons.Filled.BrightnessMedium
                else -> Icons.Filled.BrightnessHigh
            }
            GestureLevelOverlayStyle.Ios -> when {
                p < 0.34f -> Icons.Filled.BrightnessLow
                p < 0.67f -> Icons.Filled.LightMode
                else -> Icons.Filled.BrightnessHigh
            }
            GestureLevelOverlayStyle.Miuix -> when {
                p < 0.20f -> Icons.Filled.Brightness4
                p < 0.40f -> Icons.Filled.Brightness5
                p < 0.70f -> Icons.Filled.Brightness6
                else -> Icons.Filled.Brightness7
            }
        }
    }
}

fun resolveGestureLevelLabel(kind: GestureLevelKind): String {
    return when (kind) {
        GestureLevelKind.Brightness -> "亮度"
        GestureLevelKind.Volume -> "音量"
    }
}
