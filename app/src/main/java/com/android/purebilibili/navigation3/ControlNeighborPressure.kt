package com.android.purebilibili.navigation3

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.android.purebilibili.core.ui.motion.softLandingSpring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** 支点固定在远离入口的一侧，邻项的受力方向不随展开/返回阶段翻转。 */
internal fun controlNeighborPressurePivot(direction: Float): Float = when {
    direction > 0f -> 1f
    direction < 0f -> 0f
    else -> 0.5f
}

internal fun controlNeighborPressureDisplacement(pressure: Float, axisSize: Float, maxTravel: Float): Float =
    pressure * min(axisSize * 0.08f, maxTravel)

/** 玻璃、MD3 和 Miuix 使用同一受力模型，不改变组件槽位与系统来源几何。 */
internal fun GraphicsLayerScope.applyControlNeighborPressure(pressure: Float, direction: Offset) {
    val landing = direction == Offset.Zero
    if (landing) {
        // 落点自身短暂收紧，保持中心与返回落点对齐。
        scaleX = 1f - pressure * 0.025f
        scaleY = scaleX
        translationX = 0f
        translationY = 0f
        transformOrigin = TransformOrigin.Center
        return
    }
    val horizontal = direction.x != 0f
    val axisSize = if (horizontal) size.width else size.height
    // 长搜索框不按整条宽度压扁文字；最大压缩量与让位距离都以 dp 限定。
    val compression = min(0.08f, 8.dp.toPx() / axisSize)
    val axialScale = 1f - pressure * compression
    val transverseScale = 1f + pressure * compression * 0.45f
    scaleX = if (horizontal) axialScale else transverseScale
    scaleY = if (horizontal) transverseScale else axialScale
    val displacement = controlNeighborPressureDisplacement(pressure, axisSize, 6.dp.toPx())
    translationX = direction.x * displacement
    translationY = direction.y * displacement
    transformOrigin = TransformOrigin(
        controlNeighborPressurePivot(direction.x),
        controlNeighborPressurePivot(direction.y),
    )
}

/** 加载受力时不越过目标；释放时允许轻微反向形变，再衰减回到平衡位置。 */
internal fun controlNeighborPressureSpring(loading: Boolean): SpringSpec<Float> =
    if (loading) spring(dampingRatio = 1f, stiffness = 700f) else softLandingSpring()
