package com.android.purebilibili.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.theme.ACCESSIBLE_TEXT_MIN_CONTRAST
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.theme.opaqueCompositeOver

/** Content roles shared by both renderers. Neutral text never doubles as disabled text. */
object AppContentColors {
    @Composable
    @ReadOnlyComposable
    fun primaryText(): Color = AppSurfaceTokens.onSurface()

    @Composable
    @ReadOnlyComposable
    fun secondaryText(): Color = LocalAppContentPalette.current?.secondary
        ?: resolveReadableNeutralContent(AppSurfaceTokens.onSurfaceVariantSummary(), MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun neutralAction(): Color = primaryText()

    @Composable
    @ReadOnlyComposable
    fun disabled(): Color = AppSurfaceTokens.disabledContent()

    @Composable
    @ReadOnlyComposable
    fun accent(): Color = AppSurfaceTokens.primary()

    /** Glass selection keeps the picked accent; native MD3/Miuix retain their palette roles. */
    @Composable
    @ReadOnlyComposable
    fun selectionAccent(liquidGlassEnabled: Boolean): Color = resolveSelectionAccentColor(
        liquidGlassEnabled = liquidGlassEnabled,
        nativeAccent = accent(),
        glassAccent = LocalLiquidControlThemeColor.current,
    )

    @Composable
    @ReadOnlyComposable
    fun onAccent(): Color = MaterialTheme.colorScheme.onPrimary

    @Composable
    @ReadOnlyComposable
    fun error(): Color = AppSurfaceTokens.error()

    @Composable
    @ReadOnlyComposable
    fun link(): Color = LocalAppContentPalette.current?.link
        ?: resolveReadableNeutralContent(accent(), MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun success(): Color = LocalAppContentPalette.current?.success
        ?: resolveStatusContentColor(AppContentStatus.SUCCESS, MaterialTheme.colorScheme)

    @Composable
    @ReadOnlyComposable
    fun warning(): Color = LocalAppContentPalette.current?.warning
        ?: resolveStatusContentColor(AppContentStatus.WARNING, MaterialTheme.colorScheme)

    /** Scores use a readable warm hue; the role is informational, not a warning state. */
    @Composable
    @ReadOnlyComposable
    fun rating(): Color = warning()
}

private data class AppContentPalette(val secondary: Color, val link: Color, val success: Color, val warning: Color)
private val LocalAppContentPalette = staticCompositionLocalOf<AppContentPalette?> { null }

/** Palette work runs once per theme change; root text and icons share the native foreground. */
@Composable
fun ProvideAppContentColors(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val secondary = AppSurfaceTokens.onSurfaceVariantSummary()
    val foreground = AppSurfaceTokens.onSurface()
    val palette = remember(scheme, secondary) {
        AppContentPalette(
            secondary = resolveReadableNeutralContent(secondary, scheme),
            link = resolveReadableNeutralContent(scheme.primary, scheme),
            success = resolveStatusContentColor(AppContentStatus.SUCCESS, scheme),
            warning = resolveStatusContentColor(AppContentStatus.WARNING, scheme),
        )
    }
    CompositionLocalProvider(
        LocalAppContentPalette provides palette,
        androidx.compose.material3.LocalContentColor provides foreground,
        top.yukonga.miuix.kmp.theme.LocalContentColor provides foreground,
        content = content,
    )
}

enum class AppContentStatus { SUCCESS, WARNING }

/** Keep status hues independent of the user's accent and readable on ordinary app surfaces. */
fun resolveStatusContentColor(status: AppContentStatus, scheme: ColorScheme): Color {
    val dark = scheme.surface.luminance() < 0.5f
    val candidate = when (status) {
        AppContentStatus.SUCCESS -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        AppContentStatus.WARNING -> if (dark) Color(0xFFFFCC80) else Color(0xFF885200)
    }
    return resolveReadableNeutralContent(candidate, scheme)
}

/** Resolve opaque text against canvas and card roles instead of reducing text opacity. */
fun resolveReadableNeutralContent(candidate: Color, scheme: ColorScheme): Color {
    val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHigh)
    fun readable(color: Color) = surfaces.all {
        calculateContrastRatio(opaqueCompositeOver(color, it), it) >= ACCESSIBLE_TEXT_MIN_CONTRAST
    }
    if (readable(candidate)) return candidate
    // Preserve the candidate's hue as far as contrast permits; custom role overrides may
    // require full onSurface, so do not assume a fixed grey works in every theme.
    for (step in 1..16) {
        val adjusted = lerp(candidate.copy(alpha = 1f), scheme.onSurface, step / 16f)
        if (readable(adjusted)) return adjusted
    }
    return scheme.onSurface
}

internal fun resolveSelectionAccentColor(
    liquidGlassEnabled: Boolean,
    nativeAccent: Color,
    glassAccent: Color,
): Color = if (liquidGlassEnabled && glassAccent != Color.Unspecified) glassAccent else nativeAccent
