package com.android.purebilibili.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.blur.BlurIntensity

/**
 * UI 组件运行时需要的应用级配置。
 *
 * 数据读取由应用外壳负责，core/ui 只消费已解析的值，避免反向依赖设置存储。
 */
enum class LiquidSwitchThumbMode(val label: String) {
    THEME("跟随主题"), DARK("固定深色"), LIGHT("固定浅色")
}

data class AppThemeConfig(
    val blurIntensity: BlurIntensity = BlurIntensity.THIN,
    val headerBlurEnabled: Boolean = true,
    val bottomBarBlurEnabled: Boolean = false,
    val progressiveTopBlurEnabled: Boolean = true,
    val progressiveTopFadeEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val hapticSettings: AppHapticSettings = AppHapticSettings(),
    val globalTextTapCopyEnabled: Boolean = false,
    val uiEntranceAnimationEnabled: Boolean = true,
    val globalDragFeedbackEnabled: Boolean = true,
    val runtimeVisualGuardEnabled: Boolean = true,
    val nativeMiuixPopupsEnabled: Boolean = true,
    // Matches the persisted default; each application host supplies the observed preference.
    val liquidGlassEnabled: Boolean = false,
    // 独立场景由应用宿主解析总开关与偏好；场景容器只覆盖其内容的材质选择。
    val playerGlassEnabled: Boolean = liquidGlassEnabled,
    val popupGlassEnabled: Boolean = liquidGlassEnabled,
    val nowPlayingGlassEnabled: Boolean = liquidGlassEnabled,
    val liquidControlsUseThemeColor: Boolean = false,
    val liquidSwitchThumbMode: LiquidSwitchThumbMode = LiquidSwitchThumbMode.THEME,
)

fun isMiuixNonGlassEnabled(uiStyle: AppUiStyle, liquidGlassEnabled: Boolean): Boolean =
    uiStyle == AppUiStyle.MIUIX && !liquidGlassEnabled

@Composable
@ReadOnlyComposable
fun isMiuixNonGlassEnabled(): Boolean = isMiuixNonGlassEnabled(
    LocalAppUiStyle.current,
    LocalAppThemeConfig.current.liquidGlassEnabled,
)

enum class AppChromeMaterial { SOLID, BLUR, LIQUID_GLASS }

/** Callers resolve hardware/runtime capability before requesting glass or blur. */
fun resolveAppChromeMaterial(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean,
    blurAvailable: Boolean,
): AppChromeMaterial = when {
    liquidGlassEnabled -> AppChromeMaterial.LIQUID_GLASS
    blurEnabled && blurAvailable -> AppChromeMaterial.BLUR
    else -> AppChromeMaterial.SOLID
}

val LocalAppThemeConfig = staticCompositionLocalOf { AppThemeConfig() }

/** Exact custom control color; kept separate from generated Material palette tones. */
val LocalLiquidControlThemeColor = compositionLocalOf { Color.Unspecified }

@Composable
@ReadOnlyComposable
fun liquidControlThemeColor(): Color {
    val color = LocalLiquidControlThemeColor.current
    return if (color == Color.Unspecified) AppContentColors.accent() else color
}

/** Individual switches retain their scope; all disabled also stops component transitions. */
fun resolveComponentMotionEnabled(
    entranceEnabled: Boolean,
    cardEntranceEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    navigationEnabled: Boolean,
    systemReduceMotion: Boolean,
): Boolean = !systemReduceMotion &&
    (entranceEnabled || cardEntranceEnabled || cardTransitionEnabled || navigationEnabled)

val LocalComponentMotionEnabled = compositionLocalOf { true }

/** System accessibility preference, independent of card and navigation animation switches. */
val LocalSystemReduceMotion = compositionLocalOf { false }

@Composable
fun ProvideAppThemeConfig(
    config: AppThemeConfig,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppThemeConfig provides config) {
        val platformHaptic = LocalHapticFeedback.current
        val delegate = (platformHaptic as? AppPolicyHapticFeedback)?.delegate ?: platformHaptic
        val latestConfig = rememberUpdatedState(config)
        val sceneHaptic = remember(delegate) {
            AppPolicyHapticFeedback(delegate, { latestConfig.value })
        }
        CompositionLocalProvider(
            LocalHapticFeedback provides sceneHaptic,
            content = content,
        )
    }
}
