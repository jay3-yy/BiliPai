package com.android.purebilibili.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.theme.AdaptiveAccentColors
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.resolveFilledSelectionAccentColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 根主题记录的材质边界，不受页面为使用原生组件而临时关闭玻璃的局部覆盖影响。 */
internal val LocalAppNativeInteractionColorsEnabled = staticCompositionLocalOf<Boolean?> { null }

@Composable
@ReadOnlyComposable
internal fun nativeInteractionColorsEnabled(): Boolean =
    LocalAppNativeInteractionColorsEnabled.current ?: isMiuixNonGlassEnabled()

enum class AppNavigationColorRole { TABS, BAR }

@Immutable
data class AppNavigationStateColors(
    val selectedContainer: Color,
    val selectedContent: Color,
    val unselectedContent: Color,
)

@Immutable
data class AppActionStateColors(val container: Color, val content: Color)

/** 主题组件共享的交互色。 */
object AppInteractionColors {
    /** 原生 MD3 的胶囊标签、筛选芯片和底栏选中态遵循官方 secondaryContainer / onSecondaryContainer 角色。 */
    @Composable
    fun rememberMaterialNavigationSelection(): AdaptiveAccentColors {
        val scheme = MaterialTheme.colorScheme
        return remember(scheme) { resolveFilledSelectionAccentColors(scheme) }
    }

    /** 内容选中态在两种材质下使用相同的容器及前景。 */
    @Composable
    @ReadOnlyComposable
    fun miuixContentSelection(): AdaptiveAccentColors? {
        if (LocalAppUiStyle.current != AppUiStyle.MIUIX) return null
        val scheme = MiuixTheme.colorScheme
        return AdaptiveAccentColors(scheme.primaryContainer, scheme.onPrimaryContainer)
    }

    @Composable
    fun nativeNavigation(role: AppNavigationColorRole = AppNavigationColorRole.TABS): AppNavigationStateColors? {
        if (!nativeInteractionColorsEnabled()) return null
        val scheme = MiuixTheme.colorScheme
        return when (role) {
            AppNavigationColorRole.TABS -> AppNavigationStateColors(
                selectedContainer = scheme.primaryContainer,
                selectedContent = scheme.onPrimaryContainer,
                unselectedContent = scheme.onSurfaceVariantSummary,
            )
            AppNavigationColorRole.BAR -> AppNavigationStateColors(
                selectedContainer = miuixNavigationBarIndicatorColor(),
                selectedContent = scheme.onSurfaceContainer,
                unselectedContent = scheme.onSurfaceContainer.copy(alpha = 0.4f),
            )
        }
    }

    @Composable
    @ReadOnlyComposable
    fun miuixNavigationBarIndicatorColor(): Color =
        if (MiuixTheme.colorScheme.onSurfaceContainer.luminance() > 0.5f) {
            Color.White.copy(alpha = 0.13f)
        } else {
            Color.Black.copy(alpha = 0.075f)
        }

    /** 关注、已关注及个人资料编辑均为中性关系操作；状态由文案表达，禁用态沿用原生配色。 */
    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun nativeFollow(isFollowing: Boolean, isOwner: Boolean = false, enabled: Boolean = true): AppActionStateColors? {
        if (!nativeInteractionColorsEnabled()) return null
        val colors = ButtonDefaults.buttonColors()
        return if (enabled) AppActionStateColors(colors.color, colors.contentColor)
            else AppActionStateColors(colors.disabledColor, colors.disabledContentColor)
    }

    /** 对比度计算使用当前原生表面与前景；应用画布仍沿用已有的桥接背景。 */
    @Composable
    @ReadOnlyComposable
    internal fun nativeContentScheme(forceMiuix: Boolean = false): ColorScheme? {
        if (!forceMiuix && !nativeInteractionColorsEnabled()) return null
        val native = MiuixTheme.colorScheme
        return MaterialTheme.colorScheme.copy(
            primary = native.primary,
            onPrimary = native.onPrimary,
            primaryContainer = native.primaryContainer,
            onPrimaryContainer = native.onPrimaryContainer,
            secondaryContainer = native.secondaryContainer,
            onSecondaryContainer = native.onSecondaryContainer,
            surface = native.surface,
            onSurface = native.onSurface,
            surfaceVariant = native.surfaceContainerHigh,
            onSurfaceVariant = native.onSurfaceVariantSummary,
            surfaceContainerLowest = native.surface,
            surfaceContainerLow = native.surface,
            surfaceContainer = native.surfaceContainer,
            surfaceContainerHigh = native.surfaceContainerHigh,
            surfaceContainerHighest = native.surfaceContainerHighest,
            surfaceBright = native.surfaceContainerHigh,
            surfaceDim = native.surface,
        )
    }
}
