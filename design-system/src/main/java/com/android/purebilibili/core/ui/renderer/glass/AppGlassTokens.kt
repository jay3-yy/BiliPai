package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.MiuixContainerShape
import com.android.purebilibili.core.ui.isAppInDarkTheme

/**
 * 独立液态玻璃渲染线的 iOS Liquid Glass 几何与高光 Token。
 * 严格隔离于 renderer/glass/ 内，绝不影响关闭液态玻璃后的 MD3 / Miuix 原生路径。
 */
internal object AppGlassTokens {
    val CardCornerRadius: Dp = 20.dp
    val DialogCornerRadius: Dp = 28.dp
    val SheetCornerRadius: Dp = 28.dp
    val CompactCornerRadius: Dp = 14.dp
    val SpecularBorderWidth: Dp = 0.7.dp
    val ListRowMinHeight: Dp = 48.dp
    val ListRowHorizontalPadding: Dp = 16.dp
    val ListRowVerticalPadding: Dp = 11.dp
    val DialogPadding: Dp = 20.dp
    val DialogActionHeight: Dp = 44.dp
    val DialogActionSpacing: Dp = 10.dp

    fun continuousShape(radius: Dp): Shape = MiuixContainerShape(radius)

    fun containerShape(level: ContainerLevel): Shape = when (level) {
        ContainerLevel.Card -> continuousShape(CardCornerRadius)
        ContainerLevel.ProminentCard -> continuousShape(22.dp)
        ContainerLevel.Dialog -> continuousShape(DialogCornerRadius)
        ContainerLevel.Sheet -> RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius)
        ContainerLevel.Chip -> continuousShape(CompactCornerRadius)
        ContainerLevel.Field -> continuousShape(CompactCornerRadius)
        ContainerLevel.MediaCover -> continuousShape(14.dp)
        ContainerLevel.Tag -> RoundedCornerShape(50)
        ContainerLevel.Pill -> RoundedCornerShape(50)
        ContainerLevel.Floating -> continuousShape(22.dp)
        ContainerLevel.Micro -> continuousShape(8.dp)
    }

    @Composable
    fun specularBorderBrush(darkTheme: Boolean = isAppInDarkTheme()): Brush {
        val topAlpha = if (darkTheme) 0.24f else 0.72f
        val bottomAlpha = if (darkTheme) 0.08f else 0.16f
        val edgeColor = Color.White
        return Brush.verticalGradient(
            colors = listOf(
                edgeColor.copy(alpha = topAlpha),
                edgeColor.copy(alpha = bottomAlpha),
            )
        )
    }

    @Composable
    fun cardRimColor(darkTheme: Boolean = isAppInDarkTheme()): Color =
        if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)

    @Composable
    fun neutralPillFill(darkTheme: Boolean = isAppInDarkTheme()): Color =
        if (darkTheme) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.065f)
}
