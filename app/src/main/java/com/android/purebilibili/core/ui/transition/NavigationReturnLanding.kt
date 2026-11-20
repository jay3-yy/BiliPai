package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.rememberVisualWorkActive

internal enum class NavigationReturnLandingStyle(val edgeTravelDp: Float) {
    CONTROL(1.5f),
    VIDEO_CARD(3f),
}

/** 只通知本次导航的实际来源。新的返回可以替换尚未播放的旧事件。 */
internal class NavigationReturnLanding {
    var event by mutableStateOf<Any?>(null)
        private set

    fun request() {
        event = Any()
    }

    fun clear() {
        event = null
    }

    fun complete(completed: Any) {
        if (event === completed) clear()
    }
}

internal fun LayoutCoordinates?.isVisibleReturnLanding(): Boolean {
    val visible = this?.takeIf { it.isAttached }?.boundsInWindow() ?: return false
    return visible.width > 1f && visible.height > 1f
}

internal fun resolveNavigationReturnLandingScale(
    longestEdgePx: Float,
    edgeTravelLimitPx: Float,
    pressure: Float,
): Float {
    if (longestEdgePx <= 0f) return 1f
    val compression = minOf(0.06f, 2f * edgeTravelLimitPx / longestEdgePx)
    return 1f - compression * pressure
}

/** 一次收紧，一次微小释放。释放结束后直接归位，没有第二轮振荡。 */
internal fun navigationReturnLandingPressureSpec(): KeyframesSpec<Float> = keyframes {
    durationMillis = 270
    0f at 0 using AppMotionEasing.M1Standard
    1f at 68 using AppMotionEasing.M1Standard
    -0.09f at 180 using AppMotionEasing.M1Standard
    0f at 270
}

/** 只在落位时创建动画，变换整块绘制内容且不改点击区域、快照尺寸或来源坐标。 */
@Composable
internal fun Modifier.navigationReturnLanding(
    landing: NavigationReturnLanding?,
    sharedBoundsReleased: Boolean = true,
    style: NavigationReturnLandingStyle = NavigationReturnLandingStyle.CONTROL,
    isSourceVisible: () -> Boolean,
): Modifier {
    val state = landing ?: return this
    val event = state.event ?: return this
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val entry = LocalVideoDetailNavigationTransitionScope.current
    val sceneSettled = entry?.isRunning != true && sharedBoundsReleased
    val sourceIsTop = entry?.isTop != false
    val ready = rememberVisualWorkActive() && sceneSettled && sourceIsTop
    val sourceVisible by rememberUpdatedState(isSourceVisible)
    val pressure = remember(state, event) { Animatable(0f) }
    LaunchedEffect(state, event, motionEnabled, sceneSettled, sourceIsTop, ready) {
        if (!motionEnabled || (sceneSettled && !sourceIsTop)) {
            state.complete(event)
        } else if (ready) {
            var completed = false
            try {
                // 先让返回后的原位内容以正常比例完成一帧，再接住落位冲量。
                withFrameNanos { }
                if (sourceVisible()) pressure.animateTo(0f, navigationReturnLandingPressureSpec())
                completed = true
            } finally {
                // 焦点/共享边界中断时保留事件；可见后继续，不能丢失未完成的落位反馈。
                if (completed) state.complete(event)
            }
        }
    }
    if (!motionEnabled || !ready) return this
    return drawWithContent {
        val amount = pressure.value
        if (amount == 0f || !sourceVisible()) {
            drawContent()
        } else {
            val scaleFactor = resolveNavigationReturnLandingScale(size.maxDimension, style.edgeTravelDp.dp.toPx(), amount)
            scale(scaleFactor, pivot = center) { this@drawWithContent.drawContent() }
        }
    }
}
