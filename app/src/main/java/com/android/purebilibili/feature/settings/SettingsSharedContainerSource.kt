package com.android.purebilibili.feature.settings

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.NavigationReturnLanding
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.SettingsSharedContainerBounds
import kotlin.math.abs

internal class SettingsSharedContainerOrigin(
    val owner: BiliPaiNavKey,
    color: Color,
    val sourceIdentity: Any? = null,
) {
    var coordinates: LayoutCoordinates? = null
    var available by mutableStateOf(false)
    var color by mutableStateOf(color)
    var topRadius by mutableStateOf(0.dp)
    var bottomRadius by mutableStateOf(0.dp)
    var sharedBounds by mutableStateOf<SettingsSharedContainerBounds?>(null)
    var returnLandingHandledByOwner = false
    val returnLanding = NavigationReturnLanding()
    var radiusUpdater: ((LayoutCoordinates) -> Unit)? = null
    var capturedRectInHost: Rect? = null
    var capturedHostSize: IntSize = IntSize.Zero
    var resolveSourceRectInHost: (() -> Rect?)? = null
    var captureGeneration by mutableIntStateOf(0)
    var capturedSourceLayer by mutableStateOf<GraphicsLayer?>(null)
    var capturedSourceBitmap by mutableStateOf<ImageBitmap?>(null)
    var capturedSourceLayerWidth: Float = 0f
    var capturedSourceLayerHeight: Float = 0f
}

/**
 * 恢复来源节点在宿主坐标系下的静止未裁切矩形。
 * 被覆盖父页在过渡期间会围绕宿主中心缩放（> 1f），这里按实测缩放比反算静止落点，
 * 同时避免卡片按压缩放（< 1f）偏移左上角。
 */
internal fun resolveUnscaledSharedSourceRectInHost(
    coordinates: LayoutCoordinates,
    hostOrigin: Offset,
    hostSize: IntSize,
): Rect? {
    if (!coordinates.isAttached) return null
    val width = coordinates.size.width.toFloat()
    val height = coordinates.size.height.toFloat()
    if (width <= 1f || height <= 1f) return null
    val p0 = coordinates.localToRoot(Offset.Zero)
    val p1 = coordinates.localToRoot(Offset(width, height))
    val centerInRoot = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
    val scaleX = abs(p1.x - p0.x) / width
    val restingCenter = if (scaleX > 1.001f && hostSize.width > 1 && hostSize.height > 1) {
        val hostCenterX = hostOrigin.x + hostSize.width * 0.5f
        val hostCenterY = hostOrigin.y + hostSize.height * 0.5f
        Offset(
            (centerInRoot.x - hostCenterX) / scaleX + hostSize.width * 0.5f,
            (centerInRoot.y - hostCenterY) / scaleX + hostSize.height * 0.5f,
        )
    } else {
        centerInRoot - hostOrigin
    }
    return Rect(
        left = restingCenter.x - width * 0.5f,
        top = restingCenter.y - height * 0.5f,
        right = restingCenter.x + width * 0.5f,
        bottom = restingCenter.y + height * 0.5f,
    )
}

/**
 * 校验共享容器来源矩形在当前宿主窗口尺寸下是否仍有效：
 * - 有实时测量值时，要求矩形仍与当前宿主视口相交，避免已滚出屏幕的卡片把容器拉出视口；
 * - 实时节点已卸载时，仅允许在宿主尺寸未变化（未发生旋转、分屏拖拽或折叠形态切换）且仍在视口内时沿用捕获矩形，
 *   跨方向直退时返回 null，交由容器退化为干净原地淡出。
 */
internal fun resolveValidatedSharedSourceRectInHost(
    liveRect: Rect?,
    capturedRect: Rect?,
    capturedHostSize: IntSize,
    currentHostSize: IntSize,
): Rect? {
    if (currentHostSize.width <= 1 || currentHostSize.height <= 1) return null
    fun Rect.isVisibleInHost(): Boolean {
        if (width <= 1f || height <= 1f) return false
        val visibleWidth = minOf(right, currentHostSize.width.toFloat()) - maxOf(left, 0f)
        val visibleHeight = minOf(bottom, currentHostSize.height.toFloat()) - maxOf(top, 0f)
        return visibleWidth > 1f && visibleHeight > 1f
    }
    if (liveRect != null) {
        return liveRect.takeIf { it.isVisibleInHost() }
    }
    if (capturedRect == null || capturedHostSize != currentHostSize) return null
    return capturedRect.takeIf { it.isVisibleInHost() }
}

/**
 * 设置容器在父页 1.10x 景深放大期间，靠近顶/底边缘的设置行会被短暂推出根视口几像素。
 * 放大态下改为基于反算后的静止宿主矩形判定可见性，避免返回中途误判 available=false 导致 sharedBounds 闪跳；
 * 静止态保持完整可见边界校验不变。
 */
internal fun isSettingsSharedContainerAvailableInHost(
    boundsInRoot: Rect,
    rowSize: IntSize,
    hostOrigin: Offset,
    hostSize: IntSize,
    allowPartial: Boolean,
    unscaledRestingRectInHost: Rect?,
    isParentZoomed: Boolean,
): Boolean {
    if (rowSize.width <= 1 || rowSize.height <= 1 || hostSize.width <= 1 || hostSize.height <= 1) return false
    if (allowPartial) {
        val visible = boundsInRoot.translate(-hostOrigin)
        return minOf(visible.right, hostSize.width.toFloat()) - maxOf(visible.left, 0f) > 1f &&
            minOf(visible.bottom, hostSize.height.toFloat()) - maxOf(visible.top, 0f) > 1f
    }
    if (isParentZoomed && unscaledRestingRectInHost != null) {
        return unscaledRestingRectInHost.left >= -1f &&
            unscaledRestingRectInHost.top >= -1f &&
            unscaledRestingRectInHost.right <= hostSize.width + 1f &&
            unscaledRestingRectInHost.bottom <= hostSize.height + 1f
    }
    return resolveSettingsContainerCaptureBounds(
        boundsInRoot = boundsInRoot,
        rowSize = rowSize,
        hostOrigin = hostOrigin,
        hostSize = hostSize,
        allowPartial = false,
        visibleBoundsInRoot = boundsInRoot,
    ) != null
}

internal class SettingsSharedContainerSources(
    private val allowPartial: Boolean = false,
) {
    val sources = mutableStateMapOf<BiliPaiNavKey, SettingsSharedContainerOrigin>()
    var hostOrigin = Offset.Zero
    var hostSize = IntSize.Zero

    fun isAvailable(coordinates: LayoutCoordinates): Boolean {
        if (!coordinates.isAttached || coordinates.size.width <= 1 || coordinates.size.height <= 1 ||
            hostSize.width <= 1 || hostSize.height <= 1
        ) return false
        val visibleBounds = coordinates.boundsInRoot()
        val width = coordinates.size.width.toFloat()
        val p0 = coordinates.localToRoot(Offset.Zero)
        val p1 = coordinates.localToRoot(Offset(width, coordinates.size.height.toFloat()))
        val isParentZoomed = abs(p1.x - p0.x) / width > 1.001f
        val restingRect = if (!allowPartial && isParentZoomed) {
            resolveUnscaledSharedSourceRectInHost(coordinates, hostOrigin, hostSize)
        } else null
        return isSettingsSharedContainerAvailableInHost(
            boundsInRoot = visibleBounds,
            rowSize = coordinates.size,
            hostOrigin = hostOrigin,
            hostSize = hostSize,
            allowPartial = allowPartial,
            unscaledRestingRectInHost = restingRect,
            isParentZoomed = isParentZoomed,
        )
    }

    fun capture(target: BiliPaiNavKey, origin: SettingsSharedContainerOrigin) {
        origin.returnLanding.clear()
        val coordinates = origin.coordinates
        if (coordinates != null && coordinates.isAttached) {
            origin.radiusUpdater?.invoke(coordinates)
            origin.capturedRectInHost = resolveUnscaledSharedSourceRectInHost(coordinates, hostOrigin, hostSize)
            origin.capturedHostSize = hostSize
        } else {
            origin.capturedRectInHost = null
            origin.capturedHostSize = IntSize.Zero
        }
        origin.resolveSourceRectInHost = {
            val live = origin.coordinates?.let { resolveUnscaledSharedSourceRectInHost(it, hostOrigin, hostSize) }
            val validated = resolveValidatedSharedSourceRectInHost(
                liveRect = live,
                capturedRect = origin.capturedRectInHost,
                capturedHostSize = origin.capturedHostSize,
                currentHostSize = hostSize,
            )
            if (live != null && validated != null) {
                origin.capturedRectInHost = live
                origin.capturedHostSize = hostSize
            }
            validated
        }
        origin.available = coordinates != null && isAvailable(coordinates)
        if (origin.available) {
            origin.captureGeneration += 1
            sources[target] = origin
        } else {
            sources.remove(target)
        }
    }
}

internal val LocalSettingsContainerSources = staticCompositionLocalOf<SettingsSharedContainerSources?> { null }
internal val LocalSettingsContainerOwner = staticCompositionLocalOf<BiliPaiNavKey?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSettingsContainerSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
