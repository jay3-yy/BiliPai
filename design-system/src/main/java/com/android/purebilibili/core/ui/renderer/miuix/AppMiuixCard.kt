package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppCardColors
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.appLongClickWithFeedback
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun AppMiuixCard(
    modifier: Modifier,
    shape: AppCardShape?,
    colors: AppCardColors?,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedColors = colors?.let {
        CardDefaults.defaultColors(
            color = it.containerColor,
            contentColor = it.contentColor.takeOrElse {
                miuixCardContentColorFor(it.containerColor)
            },
        )
    } ?: CardDefaults.defaultColors()
    val resolvedCornerRadius = shape?.toMiuixCornerRadius() ?: CardDefaults.CornerRadius

    if (onClick != null || onLongClick != null) {
        val contentHaptic = LocalHapticFeedback.current
        val longClick = onLongClick?.let { appLongClickWithFeedback(it) }
        // 长按由共享操作边界派发；内容区恢复自身场景，不继承卡片的静默策略。
        ProvideAppMiuixHapticFeedback(enabled = false) {
            Card(
                modifier = modifier,
                cornerRadius = resolvedCornerRadius,
                colors = resolvedColors,
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = onClick,
                onLongPress = longClick,
                content = {
                    CompositionLocalProvider(
                        LocalHapticFeedback provides contentHaptic,
                        androidx.compose.material3.LocalContentColor provides top.yukonga.miuix.kmp.theme.LocalContentColor.current,
                    ) {
                        content()
                    }
                },
            )
        }
    } else {
        Card(
            modifier = modifier,
            cornerRadius = resolvedCornerRadius,
            colors = resolvedColors,
            content = {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides top.yukonga.miuix.kmp.theme.LocalContentColor.current,
                ) {
                    content()
                }
            },
        )
    }
}

@Composable
private fun AppCardShape.toMiuixCornerRadius(): Dp = when (this) {
    is AppCardShape.Semantic -> AppShapes.resolveContainerCornerDp(
        level = level,
        uiStyle = AppUiStyle.MIUIX,
        liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
    )
    is AppCardShape.Uniform -> cornerRadius
}

@Composable
private fun miuixCardContentColorFor(backgroundColor: Color): Color {
    val colors = MiuixTheme.colorScheme
    return when (backgroundColor) {
        colors.surfaceContainer -> colors.onSurfaceContainer
        colors.surfaceContainerHigh -> colors.onSurfaceContainerHigh
        colors.surfaceContainerHighest -> colors.onSurfaceContainerHighest
        else -> miuixContentColorFor(backgroundColor)
    }
}
