package com.android.purebilibili.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.core.ui.transition.sameVideoCardBounds
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.navigation3.BiliPaiNavKey

/** 坐标只由布局回调更新，不让测量结果反向触发页面组合。 */
internal class SettingsContainerAnchor(val allowPartial: Boolean = false) {
    var coordinates: LayoutCoordinates? = null
    var recorded = false
    var boundsInRoot: Rect? = null
    var visibleBoundsInRoot: Rect? = null

    fun update(value: LayoutCoordinates) {
        coordinates = value
        visibleBoundsInRoot = if (value.isAttached) value.boundsInRoot() else null
        boundsInRoot = if (value.isAttached) {
            if (allowPartial) value.videoCardSourceBoundsInRoot() else visibleBoundsInRoot
        } else null
    }

    fun detach() {
        coordinates = null
        boundsInRoot = null
        visibleBoundsInRoot = null
        recorded = false
    }
}

/** 圆形等主容器之外、仍需随来源一起移动的局部内容。 */
internal data class SettingsContainerCompanion(val layer: GraphicsLayer, val bounds: Rect)

internal class SettingsContainerSource(
    val anchor: SettingsContainerAnchor,
    var layer: GraphicsLayer,
    val owner: BiliPaiNavKey,
    val bounds: Rect,
    val hostSize: IntSize,
    val originInRoot: Offset,
    color: Color,
    val topRadius: Float,
    val bottomRadius: Float,
    // 头像只作短程内容交接，容器仍展开到页面；设置行与直播封面保留整块缩放。
    val avatarSource: Boolean = false,
) {
    // 来源图层随主题重录，底色也必须同步，不能保留点击时的配色。
    var color by mutableStateOf(color)
    // 当次捕获拥有开合状态，主题或嵌套入口更新转场对象时不能重置。
    var previewImageUrl: String? = null
    var hasOpened = false
    var companions: List<SettingsContainerCompanion> = emptyList()
    var returnInFlight: () -> Boolean = { false }

    fun isUsable(size: IntSize, expectedSourceScale: Float = 1f): Boolean {
        val liveBounds = anchor.boundsInRoot?.translate(-originInRoot) ?: return false
        return anchor.coordinates?.isAttached == true && anchor.recorded && size == hostSize &&
            bounds.width > 1f && bounds.height > 1f &&
            matchesSettingsContainerSourceBounds(liveBounds, bounds, hostSize, expectedSourceScale)
    }
}

/** 图层景深可能更新全局坐标，也可能只更新绘制；两种合法坐标都不能误判为列表滚动。 */
internal fun matchesSettingsContainerSourceBounds(
    liveBounds: Rect,
    capturedBounds: Rect,
    hostSize: IntSize,
    expectedSourceScale: Float,
): Boolean {
    fun matches(expected: Rect): Boolean =
        kotlin.math.abs(liveBounds.left - expected.left) < 1f &&
            kotlin.math.abs(liveBounds.top - expected.top) < 1f &&
            kotlin.math.abs(liveBounds.width - expected.width) < 1f &&
            kotlin.math.abs(liveBounds.height - expected.height) < 1f
    if (matches(capturedBounds)) return true
    if (!expectedSourceScale.isFinite() || expectedSourceScale <= 0f) return false
    val center = Offset(hostSize.width / 2f, hostSize.height / 2f)
    return matches(Rect(
        center + (capturedBounds.topLeft - center) * expectedSourceScale,
        center + (capturedBounds.bottomRight - center) * expectedSourceScale,
    ))
}

internal class SettingsContainerSources {
    val sources = mutableStateMapOf<BiliPaiNavKey, SettingsContainerSource>()
    var hostOrigin = Offset.Zero
    var hostSize = IntSize.Zero

    fun capture(
        target: BiliPaiNavKey,
        owner: BiliPaiNavKey,
        anchor: SettingsContainerAnchor,
        layer: GraphicsLayer,
        color: Color,
        topRadius: Float,
        bottomRadius: Float,
        avatarSource: Boolean = false,
    ) {
        val coordinates = anchor.coordinates ?: return
        if (!coordinates.isAttached || !anchor.recorded || hostSize == IntSize.Zero) return
        // 点击时再采集一次，与布局回调共用同一份边界，避免缺失实时边界而误降级。
        anchor.update(coordinates)
        val hasCompletePixels = !layer.isReleased && layer.size == coordinates.size
        val bounds = resolveSettingsContainerCaptureBounds(
            boundsInRoot = anchor.boundsInRoot,
            rowSize = coordinates.size,
            hostOrigin = hostOrigin,
            hostSize = hostSize,
            allowPartial = anchor.allowPartial && hasCompletePixels,
            visibleBoundsInRoot = anchor.visibleBoundsInRoot,
        ) ?: return
        sources[target] = SettingsContainerSource(
            anchor, layer, owner, bounds, hostSize, hostOrigin, color, topRadius, bottomRadius,
            avatarSource = avatarSource,
        )
    }
}

internal val LocalSettingsContainerSources = staticCompositionLocalOf<SettingsContainerSources?> { null }
internal val LocalSettingsContainerOwner = staticCompositionLocalOf<BiliPaiNavKey?> { null }

/** 内容卡片有完整录制像素时允许部分可见；设置行保留完整可见要求。 */
internal fun resolveSettingsContainerCaptureBounds(
    boundsInRoot: Rect?,
    rowSize: IntSize,
    hostOrigin: Offset,
    hostSize: IntSize,
    allowPartial: Boolean = false,
    visibleBoundsInRoot: Rect? = boundsInRoot,
): Rect? {
    if (boundsInRoot == null || rowSize.width <= 1 || rowSize.height <= 1 ||
        hostSize.width <= 1 || hostSize.height <= 1) return null
    val bounds = boundsInRoot.translate(-hostOrigin)
    if (bounds.width < rowSize.width - 1f || bounds.height < rowSize.height - 1f) return null
    if (allowPartial) {
        val visible = visibleBoundsInRoot?.translate(-hostOrigin) ?: return null
        if (minOf(visible.right, hostSize.width.toFloat()) - maxOf(visible.left, 0f) <= 1f ||
            minOf(visible.bottom, hostSize.height.toFloat()) - maxOf(visible.top, 0f) <= 1f) return null
    } else if (visibleBoundsInRoot == null || !sameVideoCardBounds(boundsInRoot, visibleBoundsInRoot) ||
        bounds.left < 0f || bounds.top < 0f ||
        bounds.right > hostSize.width || bounds.bottom > hostSize.height) return null
    return bounds
}
