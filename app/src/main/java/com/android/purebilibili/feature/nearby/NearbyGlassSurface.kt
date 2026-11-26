package com.android.purebilibili.feature.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.feature.home.components.BottomBarMatchedLiquidDock
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 设备接力统一液态玻璃胶囊表面：
 * - 开启液态玻璃且具备 [backdrop] 时，接入真实 Backdrop 采样模糊与壳体折射；
 * - 开启液态玻璃但位于玻璃弹窗内部（无独立 backdrop）时，使用通透玻璃叠层与高光描边，避免自采样；
 * - 关闭液态玻璃时，完整保留原生 MD3 / Miuix 表面与颜色。
 */
@Composable
internal fun NearbyGlassPill(
    nativeContainerColor: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    shape: Shape = RoundedCornerShape(999.dp),
    glassContainerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 0.8.dp,
    blurRadius: Dp = 14.dp,
    shellLensIntensity: Float = 0.68f,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val isDark = AppSurfaceTokens.background().luminance() < 0.5f
    val blurAllowed = glassEnabled &&
        backdrop != null &&
        !rememberSystemReduceMotion() &&
        !isLowBlurBudgetForced()
    val resolvedGlassFill = glassContainerColor ?: if (isDark) {
        Color.Black.copy(alpha = 0.30f)
    } else {
        Color.White.copy(alpha = 0.56f)
    }
    val resolvedGlassBorder = borderColor ?: Color.White.copy(alpha = if (isDark) 0.22f else 0.58f)

    if (blurAllowed) {
        val tuning = LocalLiquidGlassRenderConfig.current.tuning
        BottomBarMatchedLiquidDock(
            backdrop = backdrop,
            containerColor = resolvedGlassFill,
            shape = shape,
            blurEnabled = true,
            glassEnabled = true,
            drawShellLens = true,
            shellLensIntensity = shellLensIntensity,
            blurRadius = blurRadius,
            liquidGlassTuning = tuning,
            modifier = modifier.border(borderWidth, resolvedGlassBorder, shape),
            content = content,
        )
    } else if (glassEnabled) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(resolvedGlassFill)
                .border(borderWidth, resolvedGlassBorder, shape),
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(nativeContainerColor)
                .then(
                    if (borderColor != null) Modifier.border(borderWidth, borderColor, shape)
                    else Modifier
                ),
            content = content,
        )
    }
}

/**
 * 设备接力玻璃底部弹窗内的统一子卡片容器：
 * - 开启液态玻璃时使用半透明玻璃叠层与柔和镜面描边，让外层弹窗的实时模糊自然透出；
 * - 关闭液态玻璃时保持原生 [AppSurface] 实底卡片，不影响 MD3 / Miuix 原生路径。
 */
@Composable
internal fun NearbySheetCardSurface(
    nativeColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.container(ContainerLevel.Card),
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    if (!glassEnabled) {
        AppSurface(
            shape = shape,
            color = nativeColor,
            modifier = modifier,
            content = content,
        )
        return
    }
    val isDark = AppSurfaceTokens.background().luminance() < 0.5f
    val glassFill = when {
        isDark && elevated -> Color.White.copy(alpha = 0.11f)
        isDark -> Color.White.copy(alpha = 0.08f)
        elevated -> Color.White.copy(alpha = 0.72f)
        else -> Color.White.copy(alpha = 0.56f)
    }
    val rimColor = Color.White.copy(alpha = if (isDark) 0.14f else 0.68f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(glassFill)
            .border(0.75.dp, rimColor, shape),
    ) {
        content()
    }
}
