// 文件路径: core/theme/Theme.kt
package com.android.purebilibili.core.theme

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.LocalAppIconStyle
import com.android.purebilibili.core.ui.LocalAppListItemStyle
import com.android.purebilibili.core.ui.findHostActivity
import com.android.purebilibili.core.ui.resolveAppIconStyle
import com.android.purebilibili.core.ui.resolveAppListItemStyle
import com.android.purebilibili.core.store.ThemeRoleOverrides
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.settings.normalizeMd3CustomColorHex
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

// --- 扩展颜色定义 ---
private val LightSurfaceVariant = Color(0xFFF1F2F3)

//  [优化] 根据主题色索引生成配色方案
private fun createDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.3f), //  Container derived from primary
    onPrimaryContainer = primaryColor.copy(alpha = 1f), // Stronger primary for content
    secondary = primaryColor.copy(alpha = 0.85f),
    secondaryContainer = primaryColor.copy(alpha = 0.2f), //  Container derived from primary
    onSecondaryContainer = primaryColor.copy(alpha = 0.9f),
    background = DarkBackground, // iOS User Interface Black
    surface = DarkSurface, // iOS System Gray 6 (Dark)
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkSurfaceElevated, // iOS System Gray 5 (Dark)
    outline = iOSSystemGray3Dark,
    outlineVariant = iOSSystemGray4Dark,
    surfaceTint = Color.Transparent
)

private fun createAmoledDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.32f),
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.9f),
    secondaryContainer = primaryColor.copy(alpha = 0.22f),
    onSecondaryContainer = primaryColor,
    background = Black,
    surface = Black,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF050505),
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = Color(0xFF090909),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1A1A1A),
    surfaceTint = Color.Transparent
)

internal fun resolveEffectiveDynamicColorEnabled(
    dynamicColorEnabled: Boolean,
    amoledDarkTheme: Boolean,
    uiStyle: AppUiStyle
): Boolean = dynamicColorEnabled

internal fun createIosColorScheme(
    primaryColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean
): ColorScheme = when {
    darkTheme && amoledDarkTheme -> createAmoledDarkColorScheme(primaryColor)
    darkTheme -> createDarkColorScheme(primaryColor)
    else -> createLightColorScheme(primaryColor)
}

internal fun alignIosColorSchemeWithDynamicAccent(
    baseScheme: ColorScheme,
    dynamicAccentScheme: ColorScheme
): ColorScheme = baseScheme.copy(
    primary = dynamicAccentScheme.primary,
    onPrimary = dynamicAccentScheme.onPrimary,
    primaryContainer = dynamicAccentScheme.primaryContainer,
    onPrimaryContainer = dynamicAccentScheme.onPrimaryContainer,
    secondary = dynamicAccentScheme.secondary,
    onSecondary = dynamicAccentScheme.onSecondary,
    secondaryContainer = dynamicAccentScheme.secondaryContainer,
    onSecondaryContainer = dynamicAccentScheme.onSecondaryContainer,
    tertiary = dynamicAccentScheme.tertiary,
    onTertiary = dynamicAccentScheme.onTertiary,
    tertiaryContainer = dynamicAccentScheme.tertiaryContainer,
    onTertiaryContainer = dynamicAccentScheme.onTertiaryContainer
)

internal fun shouldObserveSystemWallpaperForDynamicColor(
    dynamicColorActive: Boolean,
    sdkInt: Int
): Boolean {
    return dynamicColorActive && sdkInt >= Build.VERSION_CODES.S
}

internal fun resolveMd3DynamicColorEnabled(
    source: Md3ColorSource,
    sdkInt: Int
): Boolean {
    return source != Md3ColorSource.CUSTOM && sdkInt >= Build.VERSION_CODES.S
}

internal fun resolveMd3ThemeSeedColor(
    source: Md3ColorSource,
    customColorHex: String,
    themeColorIndex: Int,
    wallpaperSeed: Color? = null,
): Color {
    return when (source) {
        Md3ColorSource.FOLLOW_WALLPAPER -> ThemeColors.getOrElse(
            normalizeThemeColorIndex(themeColorIndex)
        ) { iOSSystemBlue }
        Md3ColorSource.CUSTOM -> parseMd3CustomColorHex(customColorHex)
        Md3ColorSource.WALLPAPER_RETONED -> wallpaperSeed ?: ThemeColors.getOrElse(
            normalizeThemeColorIndex(themeColorIndex)
        ) { iOSSystemBlue }
    }
}

internal fun parseMd3CustomColorHex(
    rawValue: String
): Color {
    val normalized = normalizeMd3CustomColorHex(rawValue)
    val rgb = normalized.removePrefix("#").toLong(radix = 16)
    return Color(0xFF000000 or rgb)
}

internal fun formatMd3CustomColorHex(color: Color): String {
    val rgb = color.toArgb() and 0x00FFFFFF
    return "#${rgb.toString(16).uppercase().padStart(6, '0')}"
}

internal fun resolveMiuixColorSchemeMode(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean
): ColorSchemeMode {
    // AndroidX resolves wallpaper palettes and MaterialKolor resolves custom seed
    // palettes before the Miuix bridge, so keep Miuix on the explicit bridged colors.
    return when (themeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
    }
}

internal fun shouldUseNativeMiuixPalette(uiStyle: AppUiStyle): Boolean =
    uiStyle == AppUiStyle.MIUIX

internal fun resolvePaletteStylePreference(rawValue: String?): PaletteStyle {
    return runCatching {
        rawValue?.let(PaletteStyle::valueOf)
    }.getOrNull() ?: PaletteStyle.TonalSpot
}

internal fun resolveColorSpecPreference(rawValue: String?): ColorSpec.SpecVersion {
    return runCatching {
        rawValue?.let(ColorSpec.SpecVersion::valueOf)
    }.getOrNull() ?: ColorSpec.SpecVersion.SPEC_2025
}

internal data class MiuixMaterialBridge(
    val primary: Color,
    val onPrimary: Color,
    val primaryFixed: Color,
    val onPrimaryFixed: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color
)

internal fun createMiuixMaterialBridge(colorScheme: ColorScheme): MiuixMaterialBridge {
    return MiuixMaterialBridge(
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        primaryFixed = colorScheme.primaryFixed,
        onPrimaryFixed = colorScheme.onPrimaryFixed,
        primaryContainer = colorScheme.primaryContainer,
        onPrimaryContainer = colorScheme.onPrimaryContainer,
        secondary = colorScheme.secondary,
        onSecondary = colorScheme.onSecondary,
        secondaryContainer = colorScheme.secondaryContainer,
        onSecondaryContainer = colorScheme.onSecondaryContainer,
        tertiary = colorScheme.tertiary,
        onTertiary = colorScheme.onTertiary,
        tertiaryContainer = colorScheme.tertiaryContainer,
        onTertiaryContainer = colorScheme.onTertiaryContainer,
        error = colorScheme.error,
        onError = colorScheme.onError,
        errorContainer = colorScheme.errorContainer,
        onErrorContainer = colorScheme.onErrorContainer,
        background = colorScheme.background,
        onBackground = colorScheme.onBackground,
        surface = colorScheme.surface,
        onSurface = colorScheme.onSurface,
        surfaceVariant = colorScheme.surfaceVariant,
        onSurfaceVariant = colorScheme.onSurfaceVariant,
        surfaceContainer = colorScheme.surfaceContainer,
        surfaceContainerHigh = colorScheme.surfaceContainerHigh,
        surfaceContainerHighest = colorScheme.surfaceContainerHighest,
        outline = colorScheme.outline,
        outlineVariant = colorScheme.outlineVariant
    )
}

internal fun resolveMaterialColorSchemeFromMiuixBridge(
    bridge: MiuixMaterialBridge,
    amoledDarkTheme: Boolean
): ColorScheme {
    val baseScheme = if (bridge.background.luminance() < 0.5f) {
        darkColorScheme(
            primary = bridge.primary,
            onPrimary = bridge.onPrimary,
            primaryContainer = bridge.primaryContainer,
            onPrimaryContainer = bridge.onPrimaryContainer,
            secondary = bridge.secondary,
            onSecondary = bridge.onSecondary,
            secondaryContainer = bridge.secondaryContainer,
            onSecondaryContainer = bridge.onSecondaryContainer,
            tertiary = bridge.tertiary,
            onTertiary = bridge.onTertiary,
            tertiaryContainer = bridge.tertiaryContainer,
            onTertiaryContainer = bridge.onTertiaryContainer,
            error = bridge.error,
            onError = bridge.onError,
            background = bridge.background,
            onBackground = bridge.onBackground,
            surface = bridge.surface,
            onSurface = bridge.onSurface,
            surfaceVariant = bridge.surfaceVariant,
            onSurfaceVariant = bridge.onSurfaceVariant,
            surfaceContainer = bridge.surfaceContainer,
            surfaceContainerHigh = bridge.surfaceContainerHigh,
            outline = bridge.outline,
            outlineVariant = bridge.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = bridge.primary,
            onPrimary = bridge.onPrimary,
            primaryContainer = bridge.primaryContainer,
            onPrimaryContainer = bridge.onPrimaryContainer,
            secondary = bridge.secondary,
            onSecondary = bridge.onSecondary,
            secondaryContainer = bridge.secondaryContainer,
            onSecondaryContainer = bridge.onSecondaryContainer,
            tertiary = bridge.tertiary,
            onTertiary = bridge.onTertiary,
            tertiaryContainer = bridge.tertiaryContainer,
            onTertiaryContainer = bridge.onTertiaryContainer,
            error = bridge.error,
            onError = bridge.onError,
            background = bridge.background,
            onBackground = bridge.onBackground,
            surface = bridge.surface,
            onSurface = bridge.onSurface,
            surfaceVariant = bridge.surfaceVariant,
            onSurfaceVariant = bridge.onSurfaceVariant,
            surfaceContainer = bridge.surfaceContainer,
            surfaceContainerHigh = bridge.surfaceContainerHigh,
            outline = bridge.outline,
            outlineVariant = bridge.outlineVariant
        )
    }
    return if (amoledDarkTheme) {
        applyAmoledSurfaceOverrides(baseScheme)
    } else {
        baseScheme
    }
}

/**
 * Keep upstream neutral/control roles; only the user's accent is adapted from Material.
 * Dark canvas roles honor the ordinary/AMOLED preference; light colors and native
 * control roles retain Miuix defaults. Both theme APIs consume this resolved palette.
 */
internal fun resolveNativeMiuixColors(
    scheme: ColorScheme,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean = false,
): top.yukonga.miuix.kmp.theme.Colors {
    val upstream = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()
    // Upstream's dark surface is already pure black. Give ordinary dark mode a
    // charcoal canvas, and apply AMOLED roles before bridging both theme APIs.
    val base = when {
        !darkTheme -> upstream
        amoledDarkTheme -> {
            val amoled = applyAmoledSurfaceOverrides(scheme)
            upstream.copy(
                background = amoled.background,
                surface = amoled.surface,
                surfaceVariant = amoled.surfaceVariant,
                surfaceContainer = amoled.surfaceContainer,
                surfaceContainerHigh = amoled.surfaceContainerHigh,
                surfaceContainerHighest = amoled.surfaceContainerHighest,
                outline = amoled.outline,
                dividerLine = amoled.outlineVariant,
            )
        }
        else -> upstream.copy(background = Color(0xFF121212), surface = Color(0xFF121212))
    }
    val accentScheme = scheme.copy(
        surface = base.surface,
        primaryFixed = scheme.primaryFixed,
        onPrimaryFixed = scheme.onPrimaryFixed,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
    )
    val accent = resolveMiuixColorsFromMaterialBridge(createMiuixMaterialBridge(accentScheme), darkTheme)
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryVariant = accent.primaryVariant,
        onPrimaryVariant = accent.onPrimaryVariant,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        tertiaryContainer = scheme.tertiaryContainer,
        onTertiaryContainer = scheme.onTertiaryContainer,
        sliderKeyPoint = scheme.primary.copy(alpha = base.sliderKeyPoint.alpha),
        sliderKeyPointForeground = scheme.primary,
        // Native neutral containers also carry readable text, including search and quality rows.
        onSecondary = base.onSurface,
        error = scheme.error,
        onError = scheme.onError,
        errorContainer = scheme.errorContainer,
        onErrorContainer = scheme.onErrorContainer,
        onSecondaryContainer = base.onSurface,
        onSecondaryContainerVariant = base.onSurface,
        onSurfaceContainerHigh = base.onSurface,
        onSurfaceContainerVariant = base.onSurface,
    )
}

/** Material-backed content consumes the same semantic palette as native Miuix components.
 *  The Material canvas maps to the upstream `surface` role so chrome, lists, and the top
 *  bar share one tone while cards stay on `surfaceContainer`. */
internal fun alignMaterialSurfacesWithMiuix(
    scheme: ColorScheme,
    colors: top.yukonga.miuix.kmp.theme.Colors,
    preserveMaterialAccentRoles: Boolean = false,
): ColorScheme {
    val isDark = colors.background.luminance() < 0.5f
    return scheme.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryFixed = colors.primaryVariant,
        onPrimaryFixed = colors.onPrimaryVariant,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = if (preserveMaterialAccentRoles) scheme.secondary else colors.secondary,
        onSecondary = if (preserveMaterialAccentRoles) scheme.onSecondary else colors.onSecondary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = if (preserveMaterialAccentRoles) scheme.tertiary else colors.primary,
        onTertiary = if (preserveMaterialAccentRoles) scheme.onTertiary else colors.onPrimary,
        tertiaryContainer = colors.tertiaryContainer,
        onTertiaryContainer = colors.onTertiaryContainer,
        error = colors.error,
        onError = colors.onError,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
        background = colors.surface,
        onBackground = colors.onSurface,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.onSurfaceVariantSummary,
        surfaceTint = Color.Transparent,
        inversePrimary = colors.primaryVariant,
        inverseSurface = colors.onSurface,
        inverseOnSurface = colors.surface,
        outline = colors.outline,
        outlineVariant = colors.dividerLine,
        scrim = colors.windowDimming,
        surfaceBright = if (isDark) colors.surfaceContainerHighest else colors.surface,
        surfaceDim = if (isDark) colors.surface else colors.surfaceContainerHighest,
        surfaceContainerLowest = if (isDark) colors.surface else colors.surfaceContainer,
        surfaceContainerLow = colors.surfaceContainer,
        surfaceContainer = colors.surfaceContainer,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHighest,
    )
}

internal fun resolveMiuixColorsFromMaterialBridge(
    bridge: MiuixMaterialBridge,
    darkTheme: Boolean
): top.yukonga.miuix.kmp.theme.Colors {
    val base = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()
    val disabledPrimary = opaqueCompositeOver(bridge.primary.copy(alpha = 0.38f), bridge.surface)
    val disabledOnPrimary = opaqueCompositeOver(bridge.onPrimary.copy(alpha = 0.38f), disabledPrimary)
    val disabledPrimaryButton = opaqueCompositeOver(bridge.primary.copy(alpha = 0.38f), bridge.surface)
    val disabledOnPrimaryButton = opaqueCompositeOver(
        bridge.onPrimary.copy(alpha = 0.6f),
        disabledPrimaryButton,
    )
    val disabledSecondary = opaqueCompositeOver(bridge.outlineVariant.copy(alpha = 0.5f), bridge.surface)
    val disabledOnSecondary = opaqueCompositeOver(bridge.onSurface.copy(alpha = 0.38f), disabledSecondary)
    val disabledSecondaryVariant = opaqueCompositeOver(
        bridge.surfaceContainerHigh.copy(alpha = 0.6f),
        bridge.surface,
    )
    val disabledOnSecondaryVariant = opaqueCompositeOver(
        bridge.onSurface.copy(alpha = 0.38f),
        disabledSecondaryVariant,
    )
    return base.copy(
        primary = bridge.primary,
        onPrimary = bridge.onPrimary,
        primaryVariant = bridge.primaryFixed,
        onPrimaryVariant = bridge.onPrimaryFixed,
        errorContainer = bridge.errorContainer,
        onErrorContainer = bridge.onErrorContainer,
        disabledPrimary = disabledPrimary,
        disabledOnPrimary = disabledOnPrimary,
        disabledPrimaryButton = disabledPrimaryButton,
        disabledOnPrimaryButton = disabledOnPrimaryButton,
        disabledPrimarySlider = disabledPrimary,
        primaryContainer = bridge.primaryContainer,
        onPrimaryContainer = bridge.onPrimaryContainer,
        secondary = bridge.outlineVariant,
        onSecondary = resolveReadableTextColor(
            candidate = bridge.outline,
            background = bridge.outlineVariant,
            fallback = bridge.onSurface,
            minimumContrast = ACCESSIBLE_TEXT_MIN_CONTRAST,
        ),
        secondaryVariant = bridge.surfaceContainerHigh,
        onSecondaryVariant = bridge.onSurface,
        disabledSecondary = disabledSecondary,
        disabledOnSecondary = disabledOnSecondary,
        disabledSecondaryVariant = disabledSecondaryVariant,
        disabledOnSecondaryVariant = disabledOnSecondaryVariant,
        secondaryContainer = bridge.secondaryContainer,
        onSecondaryContainer = bridge.onSecondaryContainer,
        secondaryContainerVariant = bridge.surfaceContainerHighest,
        onSecondaryContainerVariant = bridge.onSurfaceVariant,
        tertiaryContainer = bridge.tertiaryContainer,
        onTertiaryContainer = bridge.onTertiaryContainer,
        tertiaryContainerVariant = bridge.onTertiaryContainer,
        error = bridge.error,
        onError = bridge.onError,
        background = bridge.background,
        onBackground = bridge.onBackground,
        onBackgroundVariant = bridge.onSurfaceVariant,
        surface = bridge.surface,
        onSurface = bridge.onSurface,
        surfaceVariant = bridge.surfaceVariant,
        onSurfaceSecondary = opaqueCompositeOver(bridge.onSurface.copy(alpha = 0.8f), bridge.surface),
        onSurfaceVariantSummary = bridge.onSurfaceVariant,
        onSurfaceVariantActions = bridge.onSurfaceVariant,
        disabledOnSurface = bridge.onSurface,
        surfaceContainer = bridge.surfaceContainer,
        onSurfaceContainer = bridge.onSurface,
        onSurfaceContainerVariant = bridge.onSurfaceVariant,
        surfaceContainerHigh = bridge.surfaceContainerHigh,
        onSurfaceContainerHigh = opaqueCompositeOver(
            bridge.onSurface.copy(alpha = 0.8f),
            bridge.surfaceContainerHigh,
        ),
        surfaceContainerHighest = bridge.surfaceContainerHighest,
        onSurfaceContainerHighest = bridge.onSurface,
        outline = bridge.outline,
        dividerLine = bridge.outlineVariant,
        windowDimming = Color.Black.copy(alpha = if (darkTheme) 0.6f else 0.3f),
        sliderKeyPoint = bridge.primary,
        sliderKeyPointForeground = bridge.surfaceContainerHigh,
        sliderBackground = opaqueCompositeOver(bridge.primary.copy(alpha = 0.2f), bridge.surface),
    )
}

internal fun applyAmoledSurfaceOverrides(
    baseScheme: ColorScheme
): ColorScheme = baseScheme.copy(
    background = Black,
    surface = Black,
    surfaceVariant = Color(0xFF050505),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF090909),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    surfaceBright = Color(0xFF0D0D0D),
    surfaceDim = Black,
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1A1A1A)
)

private fun createLightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    onPrimary = White,
    primaryContainer = primaryColor.copy(alpha = 0.15f), //  Container derived from primary (ligther for light mode)
    onPrimaryContainer = primaryColor,
    secondary = primaryColor.copy(alpha = 0.8f),
    secondaryContainer = primaryColor.copy(alpha = 0.1f), //  Container derived from primary
    onSecondaryContainer = primaryColor,
    background = iOSSystemGray6, // Use iOS System Gray 6 for main background (grouped table view style)
    surface = White, // iOS cards are usually white
    onSurface = TextPrimary,
    surfaceVariant = iOSSystemGray5, // Separators / Higher groupings
    onSurfaceVariant = TextSecondary,
    surfaceContainer = iOSSystemGray5, // iOS System Gray 5 (Light)
    outline = iOSSystemGray3,
    outlineVariant = iOSSystemGray4,
    surfaceTint = Color.Transparent
)

// 保留默认配色作为后备 (使用 iOS 系统蓝)
private val DarkColorScheme = createDarkColorScheme(iOSSystemBlue)
private val LightColorScheme = createLightColorScheme(iOSSystemBlue)

@Composable
@Suppress("DEPRECATION") // Broadcast is retained as an OEM fallback for wallpaper palette delivery.
private fun rememberSystemWallpaperRefreshToken(
    dynamicColorActive: Boolean
): Int {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var token by remember { mutableIntStateOf(0) }
    val shouldObserve = shouldObserveSystemWallpaperForDynamicColor(
        dynamicColorActive = dynamicColorActive,
        sdkInt = Build.VERSION.SDK_INT
    )

    DisposableEffect(context, lifecycleOwner, shouldObserve) {
        if (!shouldObserve || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return@DisposableEffect onDispose { }
        }
        val wallpaperManager = WallpaperManager.getInstance(context)
        val handler = Handler(Looper.getMainLooper())
        var lastPaletteRefreshAtMs: Long? = null
        val markPaletteRefreshed = {
            lastPaletteRefreshAtMs = SystemClock.uptimeMillis()
            token += 1
        }
        val settledPaletteRefresh = Runnable {
            markPaletteRefreshed()
        }
        // 只有真实壁纸变更才需要「落定后重读」：OEM 壁纸选择器可能在回调之后才应用新的
        // 运行时资源覆盖层。ON_RESUME 的兜底刷新不追加这一步，否则每次回前台都会多算一次
        // 配色方案，让整棵树重新取色。
        val requestPaletteRefreshFromWallpaperChange = {
            markPaletteRefreshed()
            handler.removeCallbacks(settledPaletteRefresh)
            handler.postDelayed(
                settledPaletteRefresh,
                SYSTEM_WALLPAPER_PALETTE_SETTLE_DELAY_MS
            )
        }
        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            requestPaletteRefreshFromWallpaperChange()
        }
        val wallpaperChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                    requestPaletteRefreshFromWallpaperChange()
                }
            }
        }
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                shouldRefreshSystemWallpaperPaletteOnResume(
                    lastRefreshAtMs = lastPaletteRefreshAtMs,
                    nowMs = SystemClock.uptimeMillis()
                )
            ) {
                // Some OEM wallpaper pickers update the runtime resource overlay after their
                // wallpaper callback. Re-read once when returning to the app as a reliable
                // fallback for callbacks that arrived early or while composition was paused.
                //
                // 但每次回前台都重算 dynamic color scheme 会让整棵树重新取色，正好和用户
                // 回前台后的第一次切页抢主线程，因此只在距上次刷新超过阈值时才重读。
                markPaletteRefreshed()
            }
        }
        wallpaperManager.addOnColorsChangedListener(
            listener,
            handler
        )
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        ContextCompat.registerReceiver(
            context,
            wallpaperChangedReceiver,
            IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            handler.removeCallbacks(settledPaletteRefresh)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            wallpaperManager.removeOnColorsChangedListener(listener)
            context.unregisterReceiver(wallpaperChangedReceiver)
        }
    }

    return token
}

private const val SYSTEM_WALLPAPER_PALETTE_SETTLE_DELAY_MS = 200L

/**
 * ON_RESUME 兜底重读系统壁纸配色的最小间隔。
 *
 * 重读会重算 dynamic color scheme 并让整棵树重新取色，成本不低。短时间内的多次前后台切换
 * （看通知、切一下别的应用再回来）不该每次都付这笔钱，因此加一个节流窗口。
 */
internal const val SYSTEM_WALLPAPER_PALETTE_RESUME_MIN_INTERVAL_MS = 10_000L

internal fun shouldRefreshSystemWallpaperPaletteOnResume(
    lastRefreshAtMs: Long?,
    nowMs: Long,
    minIntervalMs: Long = SYSTEM_WALLPAPER_PALETTE_RESUME_MIN_INTERVAL_MS,
): Boolean {
    val last = lastRefreshAtMs ?: return true
    return nowMs - last >= minIntervalMs
}

internal fun createMiuixAlignedColorScheme(
    primaryColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean
): ColorScheme {
    return if (darkTheme) {
        if (amoledDarkTheme) {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = White,
                primaryContainer = primaryColor.copy(alpha = 0.32f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor.copy(alpha = 0.9f),
                secondaryContainer = primaryColor.copy(alpha = 0.22f),
                onSecondaryContainer = primaryColor,
                background = Black,
                surface = Black,
                onSurface = Color(0xFFF2F2F2),
                surfaceVariant = Color(0xFF121212),
                onSurfaceVariant = Color(0xFF98989D),
                surfaceContainer = Color(0xFF0D0D0D),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF242424),
                outline = Color(0xFF48484A),
                outlineVariant = Color(0xFF262626),
                // 显式透明：tonal elevation 表面不得被未调和的种子 primary 染色
                surfaceTint = Color.Transparent
            )
        } else {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = White,
                primaryContainer = primaryColor.copy(alpha = 0.3f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor.copy(alpha = 0.85f),
                secondaryContainer = primaryColor.copy(alpha = 0.2f),
                onSecondaryContainer = primaryColor,
                background = Color(0xFF0D0D0D),
                surface = Color(0xFF121212),
                onSurface = Color(0xFFF2F2F2),
                surfaceVariant = Color(0xFF242424),
                onSurfaceVariant = Color(0xFF98989D),
                surfaceContainer = Color(0xFF242424),
                surfaceContainerHigh = Color(0xFF2C2C2E),
                surfaceContainerHighest = Color(0xFF383838),
                outline = Color(0xFF48484A),
                outlineVariant = Color(0xFF3A3A3C),
                surfaceTint = Color.Transparent
            )
        }
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = White,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            secondaryContainer = primaryColor.copy(alpha = 0.1f),
            onSecondaryContainer = primaryColor,
            background = Color(0xFFF7F7F7),
            surface = White,
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF6C6C70),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            outline = Color(0xFFD1D1D6),
            outlineVariant = Color(0xFFE5E5EA),
            surfaceTint = Color.Transparent
        )
    }
}

@Composable
private fun rememberBiliPaiStyleColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    dynamicBaseScheme: ColorScheme? = null
): ColorScheme = remember(
    seedColor,
    darkTheme,
    amoledDarkTheme,
    paletteStyle,
    colorSpec,
    uiStyle,
    dynamicBaseScheme,
) {
    createBiliPaiStyleColorScheme(
        seedColor = seedColor,
        darkTheme = darkTheme,
        amoledDarkTheme = amoledDarkTheme,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
        uiStyle = uiStyle,
        dynamicBaseScheme = dynamicBaseScheme,
    )
}

internal fun createBiliPaiStyleColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    amoledDarkTheme: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    dynamicBaseScheme: ColorScheme? = null,
): ColorScheme {
    // AndroidX already returns the user's final wallpaper-derived light/dark scheme.
    // Re-generating it from resolved roles changes the palette selected in system settings.
    if (dynamicBaseScheme != null) {
        // Wallpaper dynamic colors supply accents, but their surface roles must not
        // replace the globally selected AMOLED black surfaces.
        return if (darkTheme && amoledDarkTheme) {
            applyAmoledSurfaceOverrides(dynamicBaseScheme)
        } else {
            dynamicBaseScheme
        }
    }

    val scheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = amoledDarkTheme,
        style = paletteStyle,
        specVersion = colorSpec
    )

    val readableScheme = enforceDynamicTextContrast(scheme)
    // 不再把原始种子 hex 盖进 surfaceTint：tonal elevation 表面（弹窗、菜单等）
    // 会把 surfaceTint 混入容器色，未调和的亮种子会将其染成过饱和色。保留
    // materialkolor 原生调和的 surfaceTint，与壁纸取色路径行为一致。
    return readableScheme
}

@Composable
fun PureBiliBiliTheme(
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDarkTheme: Boolean = false,
    themeColorIndex: Int = 0, //  默认 0 = iOS 蓝色
    md3ColorSource: Md3ColorSource = if (dynamicColor) {
        Md3ColorSource.FOLLOW_WALLPAPER
    } else {
        Md3ColorSource.CUSTOM
    },
    md3CustomColorHex: String = "#007AFF",
    themeRoleOverrides: ThemeRoleOverrides = ThemeRoleOverrides(),
    wallpaperPaletteOverrides: com.android.purebilibili.core.store.WallpaperPaletteOverrides =
        com.android.purebilibili.core.store.WallpaperPaletteOverrides(),
    colorStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    fontSizePreset: AppFontSizePreset = AppFontSizePreset.DEFAULT,
    appFontWeightPreset: AppFontWeightPreset = AppFontWeightPreset.FOLLOW_THEME,
    appFontFileName: String = "",
    appIconStyle: AppIconStyle = AppIconStyle.AUTO,
    appListItemStyle: AppListItemStyle = AppListItemStyle.AUTO,
    liquidGlassEnabled: Boolean = true,
    themeTransitionEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val isDynamicColorActive = resolveEffectiveDynamicColorEnabled(
        dynamicColorEnabled = resolveMd3DynamicColorEnabled(
            source = md3ColorSource,
            sdkInt = Build.VERSION.SDK_INT
        ),
        amoledDarkTheme = amoledDarkTheme,
        uiStyle = uiStyle
    )
    val shapes = resolveMaterialShapes(uiStyle)
    val appFontFamily = remember(context, appFontFileName) {
        loadStoredAppFontFamily(context, appFontFileName)
    }
    val materialTypography = resolveMaterialTypography(uiStyle, liquidGlassEnabled)
        .scaled(fontSizePreset.multiplier)
        .withFontFamily(appFontFamily)
        .withFontWeight(appFontWeightPreset.fontWeight)
    val materialMotionScheme = remember(uiStyle) {
        resolveMaterialMotionScheme(uiStyle)
    }
    val miuixTextStyles = remember(uiStyle, liquidGlassEnabled, fontSizePreset, appFontFamily, appFontWeightPreset) {
        resolveMaterialTypography(uiStyle, liquidGlassEnabled)
            .toMiuixTextStyles()
            .scaled(fontSizePreset.multiplier)
            .withFontFamily(appFontFamily)
            .withFontWeight(appFontWeightPreset.fontWeight)
    }
    val systemWallpaperRefreshToken = rememberSystemWallpaperRefreshToken(isDynamicColorActive)
    val customPrimaryColor = remember(
        context, md3ColorSource, md3CustomColorHex, themeColorIndex, systemWallpaperRefreshToken,
    ) {
        resolveMd3ThemeSeedColor(
            source = md3ColorSource,
            customColorHex = md3CustomColorHex,
            themeColorIndex = themeColorIndex,
            wallpaperSeed = if (md3ColorSource == Md3ColorSource.WALLPAPER_RETONED) {
                readSystemWallpaperSeed(context)
            } else null,
        )
    }
    val dynamicLightBaseScheme = if (
        md3ColorSource == Md3ColorSource.FOLLOW_WALLPAPER &&
        isDynamicColorActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        // key() only groups composition; it does not cache these resource-backed
        // palettes. A new ColorScheme on each theme frame changes the transition
        // endpoint by identity and can keep the entire app recomposing indefinitely.
        remember(context, systemWallpaperRefreshToken) {
            dynamicLightColorScheme(context)
        }
    } else {
        null
    }
    val dynamicDarkBaseScheme = if (
        md3ColorSource == Md3ColorSource.FOLLOW_WALLPAPER &&
        isDynamicColorActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        remember(context, systemWallpaperRefreshToken) {
            dynamicDarkColorScheme(context)
        }
    } else {
        null
    }
    val lightMaterialScheme = rememberBiliPaiStyleColorScheme(
        seedColor = customPrimaryColor,
        darkTheme = false,
        amoledDarkTheme = false,
        paletteStyle = colorStyle,
        colorSpec = colorSpec,
        uiStyle = uiStyle,
        dynamicBaseScheme = dynamicLightBaseScheme
    )
    val darkMaterialScheme = rememberBiliPaiStyleColorScheme(
        seedColor = customPrimaryColor,
        darkTheme = true,
        amoledDarkTheme = amoledDarkTheme,
        paletteStyle = colorStyle,
        colorSpec = colorSpec,
        uiStyle = uiStyle,
        dynamicBaseScheme = dynamicDarkBaseScheme
    )

    val baseThemeRoleOverrides = remember(lightMaterialScheme, darkMaterialScheme) {
        themeRoleOverridesFromSchemes(lightMaterialScheme, darkMaterialScheme)
    }
    val effectiveThemeRoleOverrides = remember(md3ColorSource, themeRoleOverrides, uiStyle, baseThemeRoleOverrides, md3CustomColorHex) {
        val effective = resolveEffectiveThemeRoleOverrides(md3ColorSource, themeRoleOverrides, uiStyle)
        if (effective.enabled) {
            enableThemeRoleOverrides(effective, baseThemeRoleOverrides, md3CustomColorHex)
        } else effective
    }
    val resolvedLightMaterialScheme = remember(
        lightMaterialScheme, effectiveThemeRoleOverrides, colorStyle, colorSpec,
        liquidGlassEnabled, md3ColorSource, customPrimaryColor,
    ) {
        val overridden = applyThemeRoleOverrides(
            lightMaterialScheme, effectiveThemeRoleOverrides, darkTheme = false,
            paletteStyle = colorStyle, colorSpec = colorSpec,
        )
        alignLiquidAccentColorScheme(
            scheme = overridden,
            accent = resolveLiquidControlAccentColor(
                source = md3ColorSource,
                customSeed = customPrimaryColor,
                generatedAccent = overridden.primary,
                roleOverridesEnabled = effectiveThemeRoleOverrides.enabled,
                roleControlHex = effectiveThemeRoleOverrides.light.controlAccentHex,
            ),
            liquidGlassEnabled = liquidGlassEnabled,
            source = md3ColorSource,
        )
    }
    val resolvedDarkMaterialScheme = remember(
        darkMaterialScheme, effectiveThemeRoleOverrides, colorStyle, colorSpec,
        liquidGlassEnabled, md3ColorSource, customPrimaryColor,
    ) {
        val overridden = applyThemeRoleOverrides(
            darkMaterialScheme, effectiveThemeRoleOverrides, darkTheme = true,
            paletteStyle = colorStyle, colorSpec = colorSpec,
        )
        alignLiquidAccentColorScheme(
            scheme = overridden,
            accent = resolveLiquidControlAccentColor(
                source = md3ColorSource,
                customSeed = customPrimaryColor,
                generatedAccent = overridden.primary,
                roleOverridesEnabled = effectiveThemeRoleOverrides.enabled,
                roleControlHex = effectiveThemeRoleOverrides.dark.controlAccentHex,
            ),
            liquidGlassEnabled = liquidGlassEnabled,
            source = md3ColorSource,
        )
    }
    val lightEndpointPalette = remember(
        resolvedLightMaterialScheme, uiStyle, md3ColorSource, amoledDarkTheme,
        wallpaperPaletteOverrides, customPrimaryColor, effectiveThemeRoleOverrides,
    ) {
        resolveAppThemeEndpointPalette(
            base = resolvedLightMaterialScheme,
            uiStyle = uiStyle,
            source = md3ColorSource,
            darkTheme = false,
            amoledDarkTheme = false,
            wallpaperOverrides = wallpaperPaletteOverrides,
            customSeed = customPrimaryColor,
            roleOverrides = effectiveThemeRoleOverrides,
        )
    }
    val darkEndpointPalette = remember(
        resolvedDarkMaterialScheme, uiStyle, md3ColorSource, amoledDarkTheme,
        wallpaperPaletteOverrides, customPrimaryColor, effectiveThemeRoleOverrides,
    ) {
        resolveAppThemeEndpointPalette(
            base = resolvedDarkMaterialScheme,
            uiStyle = uiStyle,
            source = md3ColorSource,
            darkTheme = true,
            amoledDarkTheme = amoledDarkTheme,
            wallpaperOverrides = wallpaperPaletteOverrides,
            customSeed = customPrimaryColor,
            roleOverrides = effectiveThemeRoleOverrides,
        )
    }
    // Only immutable settings/resource endpoints enter the transition. Animated frame
    // palettes flow down to components and never become new generation inputs.
    val targetPalette = if (darkTheme) darkEndpointPalette else lightEndpointPalette
    val reduceMotion = com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()
    val animatedPalette = rememberAnimatedAppThemePalette(
        target = targetPalette,
        enabled = themeTransitionEnabled && !reduceMotion,
    )
    val materialColorScheme = animatedPalette.material
    val colorSchemeMode = resolveMiuixColorSchemeMode(themeMode, dynamicColor)
    val controller = remember(colorSchemeMode, animatedPalette.miuix, darkTheme) {
        // Both modes expose the already resolved active frame. Letting the controller
        // choose an unanimated endpoint would desynchronize native Miuix and Material.
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            lightColors = animatedPalette.miuix,
            darkColors = animatedPalette.miuix,
            isDark = darkTheme,
        )
    }

    //  [新增] 动态设置状态栏图标颜色
    val view = LocalView.current
    val themeDefaultLightBars = materialColorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        // Apply only when the brightness boundary changes. Reapplying on every color
        // frame would overwrite player/immersive pages that own their system-bar style.
        LaunchedEffect(view, themeDefaultLightBars) {
            val window = view.context.findHostActivity()?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // 设置状态栏图标颜色：
            // - 深色模式：使用浅色图标 (isAppearanceLightStatusBars = false)
            // - 浅色模式：使用深色图标 (isAppearanceLightStatusBars = true)
            // 这里是主题默认外观的兜底出口；有场景外观的页面（首页皮肤、视频沉浸等）
            // 会在自己的 effect 中覆盖。仅在明暗边界变化时更新主题默认值，
            // 避免逐帧配色更新与页面 effect 争抢状态栏外观。
            if (controller.isAppearanceLightStatusBars != themeDefaultLightBars) {
                controller.isAppearanceLightStatusBars = themeDefaultLightBars
            }
        }
    }

    CompositionLocalProvider(
        LocalAppUiStyle provides uiStyle,
        LocalDynamicColorActive provides isDynamicColorActive,
        LocalBaseThemeRoleOverrides provides baseThemeRoleOverrides,
        com.android.purebilibili.core.ui.LocalLiquidControlThemeColor provides animatedPalette.liquidAccent,
        LocalAppIconStyle provides resolveAppIconStyle(
            iconStyle = appIconStyle,
            uiStyle = uiStyle
        ),
        LocalAppListItemStyle provides resolveAppListItemStyle(
            style = appListItemStyle,
            uiStyle = uiStyle
        ),
        LocalCornerRadiusScale provides resolveCornerRadiusScale(uiStyle)
    ) {
        MiuixTheme(
            controller = controller,
            textStyles = miuixTextStyles
        ) {
            MaterialTheme(
                colorScheme = materialColorScheme,
                typography = materialTypography,
                shapes = shapes,
                motionScheme = materialMotionScheme,
                content = { com.android.purebilibili.core.ui.ProvideAppContentColors(content) }
            )
        }
    }
}
