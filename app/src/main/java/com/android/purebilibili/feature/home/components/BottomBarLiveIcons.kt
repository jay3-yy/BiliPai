package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 现代直播信号波纹底栏图标（24dp 网格、2dp 光学边距、对称圆头双层信号弧 + 中心圆点）。
 * 未选中态保持轻盈线性，选中态增强中心圆点与波纹分量，与相邻 Material 底栏图标视觉权重一致。
 */
internal val BottomBarLiveIconOutlined: ImageVector by lazy {
    buildBottomBarLiveIcon(
        name = "BottomBarLiveOutlined",
        dotRadius = 2.0f,
        strokeWidth = 1.95f,
    )
}

internal val BottomBarLiveIconFilled: ImageVector by lazy {
    buildBottomBarLiveIcon(
        name = "BottomBarLiveFilled",
        dotRadius = 2.65f,
        strokeWidth = 2.35f,
    )
}

private fun buildBottomBarLiveIcon(
    name: String,
    dotRadius: Float,
    strokeWidth: Float,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // 中心实心圆点 (cx = 12, cy = 12)
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f - dotRadius, 12f)
        arcTo(
            horizontalEllipseRadius = dotRadius,
            verticalEllipseRadius = dotRadius,
            theta = 0f,
            isMoreThanHalf = true,
            isPositiveArc = true,
            x1 = 12f + dotRadius,
            y1 = 12f,
        )
        arcTo(
            horizontalEllipseRadius = dotRadius,
            verticalEllipseRadius = dotRadius,
            theta = 0f,
            isMoreThanHalf = true,
            isPositiveArc = true,
            x1 = 12f - dotRadius,
            y1 = 12f,
        )
        close()
    }
    // 内层左右对称信号圆弧 (r = 5.2, ±42°)
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15.86f, 8.52f)
        arcTo(5.2f, 5.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.86f, 15.48f)
        moveTo(8.14f, 15.48f)
        arcTo(5.2f, 5.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.14f, 8.52f)
    }
    // 外层左右对称信号圆弧 (r = 8.8, ±44°)
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(18.33f, 5.89f)
        arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.33f, 18.11f)
        moveTo(5.67f, 18.11f)
        arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.67f, 5.89f)
    }
}.build()
