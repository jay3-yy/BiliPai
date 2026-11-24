package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.components.AppSurface
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.ui.AppPopupSurfaceRenderer
import com.android.purebilibili.core.ui.AppPopupSurfaceType

object BiliPaiPopupSurfaceRenderer : AppPopupSurfaceRenderer {
    @Composable
    override fun Render(
        type: AppPopupSurfaceType,
        modifier: Modifier,
        shape: Shape,
        containerColor: Color,
        contentColor: Color,
        tonalElevation: Dp,
        content: @Composable () -> Unit,
    ) {
        val glass = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
            com.android.purebilibili.core.ui.LocalAppPopupWindowMaterialEnabled.current &&
            android.os.Build.VERSION.SDK_INT >= 33 && type != AppPopupSurfaceType.MENU
        val lowBudget = com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
        val dark = com.android.purebilibili.core.ui.isAppInDarkTheme()
        val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current ||
            !com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
        val feedback = com.android.purebilibili.core.ui.popup.rememberPopupDragFeedback(
            enabled = glass && !reduceMotion && !LocalAppGlassMenuActive.current,
            closing = com.android.purebilibili.core.ui.LocalAppDialogExitRunning.current,
        )
        val palette = if (glass) com.android.purebilibili.core.ui.popup.rememberGlassPopupPalette()
            else com.android.purebilibili.core.store.home.GlassPopupPalette.DOCK
        // 配色只作用于玻璃窗口，关闭玻璃时沿用调用方原生底色。
        val glassBase = com.android.purebilibili.feature.home.components.resolveBiliPaiBottomBarContainerColor(
            darkTheme = dark,
            liquidGlassTuning = com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig.current.tuning,
        )
        // 浅色模式文字为深色，窗口叠在深色内容上时需更高的白色不透明度保证可读性。
        val surfaceColor = when {
            !glass -> containerColor
            !lowBudget && palette == com.android.purebilibili.core.store.home.GlassPopupPalette.THEME_FROSTED ->
                com.android.purebilibili.core.ui.popup.resolveThemeFrostedPopupColor(
                    dark, androidx.compose.material3.MaterialTheme.colorScheme)
            else -> glassBase.copy(alpha = if (lowBudget) 0.96f else if (dark) 0.63f else 0.70f)
        }
        val glassModifier = if (glass) feedback.border(0.7.dp,
            (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.14f else 0.1f), shape) else Modifier
        // 玻璃底色叠任意背景时次要文字按最差背景校正对比，仅玻璃路径生效。
        val contentSlot: @Composable () -> Unit = if (glass) {
            { com.android.purebilibili.core.ui.ProvideAppGlassPopupContentColors(surfaceColor) { content() } }
        } else {
            content
        }
        // 面板内嵌的菜单宿主采样不到本表面及其窗口模糊，以表面色的不透明版本作为采样底。
        val backdropBase = if (type != AppPopupSurfaceType.MENU &&
            surfaceColor != Color.Unspecified && surfaceColor.alpha > 0f
        ) {
            surfaceColor.copy(alpha = 1f)
        } else {
            com.android.purebilibili.core.ui.LocalAppPopupBackdropBase.current
        }
        AppSurface(
            modifier = modifier.then(glassModifier),
            shape = shape,
            color = surfaceColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.android.purebilibili.core.ui.LocalAppPopupBackdropBase provides backdropBase,
                content = contentSlot,
            )
        }
    }
}
