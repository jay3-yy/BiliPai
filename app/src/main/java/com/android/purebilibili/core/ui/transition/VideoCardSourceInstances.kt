package com.android.purebilibili.core.ui.transition

import android.graphics.Rect as AndroidRect
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.purebilibili.core.ui.AppShapes
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalVideoCardSourceTrackingEnabled = compositionLocalOf { false }

/** 只跟随实际卡片组合存活；返回时重新测量同一实例，不按视频编号认领新卡片。 */
internal class VideoCardSourceInstance(
    val bvid: String,
    val view: View,
    val shape: Shape,
    val density: Density,
    val layoutDirection: LayoutDirection,
) {
    var coordinates: LayoutCoordinates? = null

    fun boundsInRoot(): Rect? = coordinates?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()

    fun bounds(windowCoordinates: Boolean, allowPartial: Boolean = false): AndroidRect? {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return null
        if (!view.isAttachedToWindow) return null
        val origin = coordinates.localToWindow(Offset.Zero)
        val topRight = coordinates.localToWindow(Offset(coordinates.size.width.toFloat(), 0f))
        val bottomLeft = coordinates.localToWindow(Offset(0f, coordinates.size.height.toFloat()))
        val bottom = coordinates.localToWindow(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        // 窗口协议只接收轴对齐的原尺寸截图，不能用对角线包围盒代替旋转或缩放后的卡片。
        if (!sameVideoCardBounds(
                Rect(origin, bottom),
                Rect(origin, origin + Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())),
            ) || abs(topRight.y - origin.y) > 1f || abs(bottomLeft.x - origin.x) > 1f ||
            abs(topRight.x - bottom.x) > 1f || abs(bottomLeft.y - bottom.y) > 1f) return null
        val full = Rect(origin, bottom)
        val visible = coordinates.boundsInWindow()
        // 有完整录制像素时可从部分可见的整卡展开；窗口截图仍要求完整可见。
        if (full.width <= 1f || full.height <= 1f || visible.width <= 1f || visible.height <= 1f) return null
        if (!allowPartial && !sameVideoCardBounds(full, visible)) return null
        val offset = IntArray(2)
        if (!windowCoordinates) {
            val screen = IntArray(2)
            view.getLocationOnScreen(screen)
            view.getLocationInWindow(offset)
            offset[0] = screen[0] - offset[0]
            offset[1] = screen[1] - offset[1]
        }
        return AndroidRect(
            (full.left + offset[0]).roundToInt(), (full.top + offset[1]).roundToInt(),
            (full.right + offset[0]).roundToInt(), (full.bottom + offset[1]).roundToInt(),
        )
    }

    fun radiusPx(): Float = AppShapes.transitionCornerRadiusPx(
        shape, Size(coordinates?.size?.width?.toFloat() ?: 0f, coordinates?.size?.height?.toFloat() ?: 0f), layoutDirection, density,
    )
}

internal fun sameVideoCardBounds(first: Rect, second: Rect): Boolean =
    abs(first.left - second.left) <= 1f && abs(first.top - second.top) <= 1f &&
        abs(first.right - second.right) <= 1f && abs(first.bottom - second.bottom) <= 1f

/** 返回只测量打开时的实例；同视频的新组合不能接替落点。 */
internal class VideoCardSourceAnchor(source: VideoCardSourceInstance) {
    private val reference = WeakReference(source)
    fun belongsToWindow(decor: View): Boolean = reference.get()?.let {
        VideoCardSourceInstances.isAttached(it) && it.view.rootView === decor.rootView
    } == true
    fun bounds(windowCoordinates: Boolean, allowPartial: Boolean = false): AndroidRect? = reference.get()
        ?.takeIf { VideoCardSourceInstances.isAttached(it) }?.bounds(windowCoordinates, allowPartial)

    fun radiusPx(): Float? = reference.get()
        ?.takeIf { VideoCardSourceInstances.isAttached(it) }?.radiusPx()

    fun cornerRadiusDp(): Int? = reference.get()?.takeIf { VideoCardSourceInstances.isAttached(it) }
        ?.let { (it.radiusPx() / it.density.density).roundToInt() }

    fun boundsInRoot(allowPartial: Boolean = false): Rect? = reference.get()?.takeIf {
        VideoCardSourceInstances.isAttached(it) && it.bounds(windowCoordinates = true, allowPartial = allowPartial) != null
    }?.boundsInRoot()
}

/** 主线程访问；返回锚点只弱引用来源，Lazy 项回收后不保活页面。 */
internal object VideoCardSourceInstances {
    private val sources = mutableSetOf<VideoCardSourceInstance>()
    private var clickedSource: WeakReference<VideoCardSourceInstance>? = null

    fun attach(source: VideoCardSourceInstance) { sources.add(source) }
    fun detach(source: VideoCardSourceInstance) {
        sources.remove(source)
        source.coordinates = null
    }

    fun recordClick(bvid: String, bounds: Rect) {
        clickedSource = sources.singleOrNull {
            it.bvid == bvid && it.view.hasWindowFocus() &&
                it.boundsInRoot()?.let { actual -> sameVideoCardBounds(actual, bounds) } == true
        }?.let { WeakReference(it) }
    }

    fun takeAnchor(bvid: String): VideoCardSourceAnchor? {
        val source = clickedSource?.get()
        clickedSource = null
        return source?.takeIf { it.bvid == bvid && it in sources }?.let(::VideoCardSourceAnchor)
    }

    fun isAttached(source: VideoCardSourceInstance): Boolean = source in sources
}

/** 只登记卡片实例和实测位置；像素复用既有卡片录制层，不重复捕获。 */
@Composable
internal fun Modifier.videoCardSourceAnchor(
    bvid: String,
    shape: Shape,
    // 导航内容外的横条显式启用；页面卡片仍沿用原有导航作用域开关。
    enabled: Boolean = LocalVideoCardSourceTrackingEnabled.current,
): Modifier {
    if (!enabled || bvid.isBlank()) return this
    val view = LocalView.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val source = remember(bvid, view, shape, density, direction) {
        VideoCardSourceInstance(bvid, view, shape, density, direction)
    }
    DisposableEffect(source) {
        VideoCardSourceInstances.attach(source)
        onDispose { VideoCardSourceInstances.detach(source) }
    }
    return onPlaced { source.coordinates = it }
}
