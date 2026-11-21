// 文件路径: feature/home/components/HomeOverlayPillButton.kt
package com.android.purebilibili.feature.home.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.LocalAppGlassContainer
import com.android.purebilibili.core.ui.blur.resolveHomeChromeLiquidGlassEnabled
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.Backdrop

@Composable
internal fun refreshOverlayGlassEnabled(): Boolean =
    resolveHomeChromeLiquidGlassEnabled(LocalAppThemeConfig.current.liquidGlassEnabled, Build.VERSION.SDK_INT) &&
        !isLowBlurBudgetForced()

/** [backdrop] 只接收独立的内容录制层。任何包含本胶囊的录制层都会造成自采样。 */
@Composable
internal fun HomeOverlayPillButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = backdrop != null && refreshOverlayGlassEnabled()
    val shape = AppShapes.container(ContainerLevel.Pill)
    val containerColor = AppSurfaceTokens.cardContainer()
    // 玻璃只采样调用方显式提供的内容源；实色路径保留 MD3 和 Miuix 原生按钮。
    CompositionLocalProvider(
        LocalAppGlassContainer provides if (glass) null else LocalAppGlassContainer.current,
    ) {
        AppButton(
            onClick = onClick,
            modifier = if (glass) {
                modifier.biliPaiFloatingDockShell(
                    backdrop = backdrop,
                    containerColor = containerColor,
                    pressProgress = 0f,
                    shape = shape,
                    lensIntensity = resolveFloatingDockGeometryScale(40f),
                    liquidGlassTuning = LocalLiquidGlassRenderConfig.current.tuning,
                )
            } else modifier,
            shape = shape,
            containerColor = if (glass) Color.Transparent else containerColor,
            contentColor = AppContentColors.primaryText(),
            border = if (glass) null else
                BorderStroke(AppSpacingTokens.Micro * 0.4f, Color.White.copy(alpha = 0.12f)),
            defaultElevation = if (glass) 0.dp else AppSpacingTokens.ExtraSmall,
            pressedElevation = if (glass) 0.dp else AppSpacingTokens.Micro,
            contentPadding = PaddingValues(
                horizontal = AppSpacingTokens.Large,
                vertical = AppSpacingTokens.Small + AppSpacingTokens.Micro,
            ),
            content = content,
        )
    }
}
