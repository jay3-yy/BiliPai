package com.android.purebilibili.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.LocalDynamicColorActive

enum class AppSemanticIconFamily {
    MATERIAL,
    MIUIX,
}

/**
 * Miuix 的 Search 字形几乎占满矢量视口，Material Outlined.Search 自带约 27% 视口边距，
 * 同一 dp 框内 Miuix 搜索字形视觉偏大约 25%；按字形家族提供统一光学缩放补偿。
 */
fun resolveAppSearchGlyphOpticalScale(family: AppSemanticIconFamily): Float = when (family) {
    AppSemanticIconFamily.MIUIX -> 0.8f
    AppSemanticIconFamily.MATERIAL -> 1f
}

/**
 * 全局图标呈现样式(用户可切换的两套方案)。
 * - [AUTO]:跟随运行时主题 —— MIUIX 设置使用柔和彩色，MATERIAL3 解析为官方推荐。
 * - [THEME_CONTAINER]:主题色容器 —— 图标置于主题色(primaryContainer)
 *   圆角容器内,图标用 onPrimaryContainer,对齐官方 Settings 容器图标规范。
 * - [MD3_STANDARD]:MD3 官方推荐 —— onSurfaceVariant 单色图标、无容器。
 * - [SOFT_COLORFUL] / [VIVID_COLORFUL]:共享偏好入口使用柔和或鲜明的多色容器。
 */
enum class AppIconStyle {
    AUTO,
    THEME_CONTAINER,
    MD3_STANDARD,
    SOFT_COLORFUL,
    VIVID_COLORFUL,
}

/**
 * AUTO 跟随原生主题的图标形状：Miuix 的普通设置入口沿用七彩图标色，
 * Material 3 使用中性色，仅 MATERIAL3 预设解析为官方推荐样式。
 */
fun resolveAppIconStyle(
    iconStyle: AppIconStyle,
    uiStyle: AppUiStyle,
): AppIconStyle = when (iconStyle) {
    AppIconStyle.AUTO -> when (uiStyle) {
        AppUiStyle.MATERIAL3 -> AppIconStyle.MD3_STANDARD
        AppUiStyle.MIUIX -> AppIconStyle.AUTO
    }
    else -> iconStyle
}

/** 从持久化字符串解析 [AppIconStyle],非法或缺失值回退 [AppIconStyle.AUTO]。 */
fun resolveAppIconStylePreference(rawValue: String?): AppIconStyle {
    return runCatching {
        rawValue?.let(AppIconStyle::valueOf)
    }.getOrNull() ?: AppIconStyle.AUTO
}

/**
 * 全局图标呈现样式 CompositionLocal。
 * 默认 [AppIconStyle.AUTO] 由 UI 预设推导;主题层提供用户显式选择后全局生效。
 */
val LocalAppIconStyle = staticCompositionLocalOf {
    AppIconStyle.AUTO
}

/** 解析当前生效的图标呈现样式(处理 AUTO 跟随运行时主题)。 */
@Composable
fun rememberResolvedAppIconStyle(): AppIconStyle {
    val iconStyle = LocalAppIconStyle.current
    val uiStyle = LocalAppUiStyle.current
    return remember(iconStyle, uiStyle) {
        resolveAppIconStyle(iconStyle, uiStyle)
    }
}

enum class AppSemanticAccentRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
}

data class AppSemanticAccentPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color,
)

data class AppSemanticVisualPolicy(
    val iconFamily: AppSemanticIconFamily,
    val iconStyle: AppIconStyle = AppIconStyle.AUTO,
    val accentPalette: AppSemanticAccentPalette?,
    val prefersNativeChrome: Boolean,
    val supportsIndependentLiquidGlass: Boolean,
    val prefersGroupedListCards: Boolean = false,
) {
    /**
     * MD3 官方推荐样式强制使用 Material 官方字形(运行时字形已收敛为单值)。
     */
    val effectiveIconFamily: AppSemanticIconFamily
        get() = if (iconStyle == AppIconStyle.MD3_STANDARD) {
            AppSemanticIconFamily.MATERIAL
        } else {
            iconFamily
        }

    fun resolveAccent(role: AppSemanticAccentRole, fallback: Color): Color {
        val palette = accentPalette ?: return fallback
        return when (role) {
            AppSemanticAccentRole.PRIMARY -> palette.primary
            AppSemanticAccentRole.SECONDARY -> palette.secondary
            AppSemanticAccentRole.TERTIARY -> palette.tertiary
            AppSemanticAccentRole.ERROR -> palette.error
        }
    }

    companion object {
        fun material(palette: AppSemanticAccentPalette) = AppSemanticVisualPolicy(
            iconFamily = AppSemanticIconFamily.MATERIAL,
            accentPalette = palette,
            prefersNativeChrome = true,
            supportsIndependentLiquidGlass = false,
        )
    }
}

fun resolveAppSemanticAccentPalette(
    colorScheme: ColorScheme,
    useSemanticAccentRoles: Boolean,
): AppSemanticAccentPalette = AppSemanticAccentPalette(
    primary = colorScheme.primary,
    secondary = if (useSemanticAccentRoles) colorScheme.secondary else colorScheme.primary,
    tertiary = if (useSemanticAccentRoles) colorScheme.tertiary else colorScheme.primary,
    error = colorScheme.error,
)

fun resolveAppSemanticVisualPolicy(
    uiStyle: AppUiStyle,
    materialPalette: AppSemanticAccentPalette,
    iconStyle: AppIconStyle = AppIconStyle.AUTO,
): AppSemanticVisualPolicy = when (uiStyle) {
    AppUiStyle.MATERIAL3 -> AppSemanticVisualPolicy.material(materialPalette).copy(iconStyle = iconStyle)
    AppUiStyle.MIUIX -> AppSemanticVisualPolicy.material(materialPalette).copy(
        iconFamily = AppSemanticIconFamily.MIUIX,
        prefersGroupedListCards = true,
        iconStyle = iconStyle,
    )
}

@Composable
fun rememberAppSemanticVisualPolicy(): AppSemanticVisualPolicy {
    val uiStyle = LocalAppUiStyle.current
    val dynamicColorActive = LocalDynamicColorActive.current
    val colorScheme = MaterialTheme.colorScheme
    val iconStyle = rememberResolvedAppIconStyle()
    return remember(uiStyle, dynamicColorActive, colorScheme, iconStyle) {
        resolveAppSemanticVisualPolicy(
            uiStyle = uiStyle,
            materialPalette = resolveAppSemanticAccentPalette(
                colorScheme = colorScheme,
                useSemanticAccentRoles = dynamicColorActive,
            ),
            iconStyle = iconStyle,
        )
    }
}

fun resolveAppChromeLiquidGlassEnabled(
    androidNativeEnabled: Boolean,
): Boolean = androidNativeEnabled

@Composable
fun rememberAppChromeLiquidGlassEnabled(
    androidNativeEnabled: Boolean,
): Boolean = remember(androidNativeEnabled) {
    resolveAppChromeLiquidGlassEnabled(androidNativeEnabled = androidNativeEnabled)
}
