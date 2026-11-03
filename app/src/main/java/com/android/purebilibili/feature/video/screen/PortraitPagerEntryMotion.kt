package com.android.purebilibili.feature.video.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlin.math.roundToInt

/** 入场起点使用进入前的窗口坐标，系统栏变化不会重新定位起点。 */
internal fun resolvePortraitPagerEntryBounds(
    sourceInWindow: Rect?,
    viewportOriginInWindow: Offset,
    viewportSize: IntSize,
    progress: Float,
): Rect {
    val target = Rect(0f, 0f, viewportSize.width.toFloat(), viewportSize.height.toFloat())
    if (sourceInWindow == null || sourceInWindow.width <= 0f || sourceInWindow.height <= 0f) return target
    return lerp(sourceInWindow.translate(-viewportOriginInWindow), target, progress.coerceIn(0f, 1f))
}

/** 只改变同一播放视口的边界，不缩放整页，也不淡出正在播放的画面。 */
@Composable
internal fun PortraitPagerEntryViewport(
    sourceBoundsInWindow: Rect?,
    durationMillis: Int,
    enabled: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (entering: Boolean) -> Unit,
) {
    val reducedMotion = rememberSystemReduceMotion()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val animate = enabled && !reducedMotion && LocalComponentMotionEnabled.current && focused &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // 每次挂载只捕获一次起点；换推荐数据、系统栏或控件重组都不重启动画。
    val source = remember { sourceBoundsInWindow }
    val progress = remember {
        Animatable(if (animate && source != null && !source.isEmpty) 0f else 1f)
    }
    val entering by remember { derivedStateOf { progress.value < 1f } }
    LaunchedEffect(animate, visible) {
        if (animate && !visible) {
            // 进入途中返回，从当前边界退出，不先跳到全屏终点。
            progress.stop()
        } else if (animate && progress.value < 1f) {
            progress.animateTo(1f, emphasizedEnterTween(durationMillis))
        } else {
            // 失焦、退到后台、小窗或减少动态效果时完成交接，恢复后不重播入场。
            progress.snapTo(1f)
        }
    }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Layout(
        modifier = modifier.clipToBounds().onPlaced { coordinates[0] = it },
        content = { content(entering) },
    ) { measurables, constraints ->
        val size = IntSize(constraints.maxWidth, constraints.maxHeight)
        // 逐帧读仅在测量和放置阶段，不让播放器随动画进度逐帧重组。
        val fraction = progress.value
        val bounds = resolvePortraitPagerEntryBounds(source, Offset.Zero, size, fraction)
        val childConstraints = Constraints.fixed(
            bounds.width.roundToInt().coerceIn(1, size.width.coerceAtLeast(1)),
            bounds.height.roundToInt().coerceIn(1, size.height.coerceAtLeast(1)),
        )
        val children = measurables.map { it.measure(childConstraints) }
        layout(size.width, size.height) {
            val origin = coordinates[0]?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero
            val placedBounds = resolvePortraitPagerEntryBounds(source, origin, size, fraction)
            children.forEach { it.place(placedBounds.left.roundToInt(), placedBounds.top.roundToInt()) }
        }
    }
}
