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

    fun bounds(windowCoordinates: Boolean): AndroidRect? {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return null
        if (!view.isAttachedToWindow) return null
        val origin = coordinates.localToWindow(Offset.Zero)
        val bottom = coordinates.localToWindow(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        val full = Rect(origin, bottom)
        val visible = coordinates.boundsInWindow()
        // 裁切的卡片不能将裁掉的尺寸冒充整卡截图的尺寸。
        if (full.width <= 1f || full.height <= 1f || !sameVideoCardBounds(full, visible)) return null
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
    fun bounds(windowCoordinates: Boolean): AndroidRect? = reference.get()
        ?.takeIf { VideoCardSourceInstances.isAttached(it) }?.bounds(windowCoordinates)

    fun radiusPx(): Float? = reference.get()
        ?.takeIf { VideoCardSourceInstances.isAttached(it) }?.radiusPx()

    fun cornerRadiusDp(): Int? = reference.get()?.takeIf { VideoCardSourceInstances.isAttached(it) }
        ?.let { (it.radiusPx() / it.density.density).roundToInt() }

    fun boundsInRoot(): Rect? = reference.get()?.takeIf {
        VideoCardSourceInstances.isAttached(it) && it.bounds(windowCoordinates = true) != null
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
internal fun Modifier.videoCardSourceAnchor(bvid: String, shape: Shape): Modifier {
    if (!LocalVideoCardSourceTrackingEnabled.current || bvid.isBlank()) return this
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
