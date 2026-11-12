package com.android.purebilibili.core.ui.popup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 按实际指针到静止面板边界的距离计算，列表能否继续滚动不改变越界拖拽。 */
internal fun popupPullBeyondBounds(pointer: Offset, bounds: Rect): Offset {
    if (bounds.isEmpty) return Offset.Zero
    return pointer - Offset(pointer.x.coerceIn(bounds.left, bounds.right), pointer.y.coerceIn(bounds.top, bounds.bottom))
}

/** 每轴独立橡皮筋阻力，位移渐近短边的 7/32，不因手指移得更远而无限拉长。 */
internal fun popupRubberBandPull(pull: Offset, shortSide: Float, maxPull: Float = shortSide * 7f / 32f): Offset {
    val limit = maxPull
    if (limit <= 0f) return Offset.Zero
    fun axis(value: Float): Float = limit * (1f - 1f / (kotlin.math.abs(value) * 0.55f / limit + 1f)) * kotlin.math.sign(value)
    return Offset(axis(pull.x), axis(pull.y))
}

/** pull 为阻尼后的位移；纵向拖动提供更明显的拉伸，松手后随位移回弹。 */
internal fun popupElasticFrame(frame: AppGlassPopupFrame, growth: Float, pull: Offset, density: Float, maxGrowthDp: Float = 16f, horizontalStretch: Float = 0.5f): AppGlassPopupFrame {
    if (frame.rect.isEmpty || (growth == 0f && pull == Offset.Zero)) return frame
    val swell = 1f + growth * minOf(0.25f, maxGrowthDp * density / maxOf(frame.rect.width, frame.rect.height))
    val scaleX = swell * (1f + kotlin.math.abs(pull.x) / frame.rect.width * horizontalStretch)
    val scaleY = swell * (1f + kotlin.math.abs(pull.y) / frame.rect.height)
    val center = frame.rect.center + pull
    val halfWidth = frame.rect.width * scaleX / 2f
    val halfHeight = frame.rect.height * scaleY / 2f
    return frame.copy(
        rect = Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight),
        radius = frame.radius * swell,
        contentScale = frame.contentScale * swell,
        contentStretchX = frame.contentStretchX * scaleX / swell,
        contentStretchY = frame.contentStretchY * scaleY / swell,
    )
}

internal data class PopupElasticVelocity(val growth: Float, val x: Float, val y: Float)

/** 面板内先以全局拖拽阻尼跟手，拖出边界后连续叠加橡皮筋位移。 */
internal fun popupMenuDragPull(pointer: Offset, origin: Offset, bounds: Rect, followInside: Boolean): Offset {
    if (bounds.isEmpty) return Offset.Zero
    val outside = popupPullBeyondBounds(pointer, bounds)
    val edgePull = popupRubberBandPull(outside, bounds.minDimension)
    if (!followInside) return edgePull
    // 内部跟手在边界位置停止累积，外部增量交给橡皮筋，越界前后没有跳步。
    val boundedPointer = pointer - outside
    val follow = com.android.purebilibili.core.ui.components.resolveAppElasticDragTranslation(
        bounds.size, boundedPointer - origin,
    )
    return follow + edgePull
}

/** 边界处优先保留跟手中心，以剩余空间收敛尺寸，避免把整个面板钉回静止位置。 */
internal fun fitPopupElasticFrame(frame: AppGlassPopupFrame, bounds: Rect): AppGlassPopupFrame {
    if (bounds.isEmpty || frame.rect.isEmpty) return frame
    // 极端越界或入口贴边时至少保留半幅尺寸，避免为追随屏外中心把面板压成细线。
    val insetX = minOf(frame.rect.width, bounds.width) * 0.25f
    val insetY = minOf(frame.rect.height, bounds.height) * 0.25f
    val center = Offset(
        frame.rect.center.x.coerceIn(bounds.left + insetX, bounds.right - insetX),
        frame.rect.center.y.coerceIn(bounds.top + insetY, bounds.bottom - insetY),
    )
    val roomWidth = 2f * minOf(center.x - bounds.left, bounds.right - center.x)
    val roomHeight = 2f * minOf(center.y - bounds.top, bounds.bottom - center.y)
    val scale = minOf(1f, roomWidth / frame.rect.width, roomHeight / frame.rect.height)
    val halfWidth = frame.rect.width * scale / 2f
    val halfHeight = frame.rect.height * scale / 2f
    return frame.copy(
        rect = Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight),
        radius = frame.radius * scale,
        contentScale = frame.contentScale * scale,
    )
}

private val PopupPressSpring = spring<Float>(dampingRatio = 0.593f, stiffness = 311f, visibilityThreshold = 0.001f)
private val PopupTrackSpring = spring<Float>(dampingRatio = 1f, stiffness = 2741.557f, visibilityThreshold = 0.001f)

internal class PopupElasticMotion {
    private val growth = Animatable(0f)
    private val x = Animatable(0f)
    private val y = Animatable(0f)
    var isTracking = false
    private var growthJob: Job? = null
    private var xJob: Job? = null
    private var yJob: Job? = null
    private var resetJob: Job? = null
    private var growthTarget: Float? = null
    private var pullTarget: Offset? = null
    private var trackingTarget: Boolean? = null
    private var draggedDuringTracking = false

    fun stop() {
        growthJob?.cancel(); xJob?.cancel(); yJob?.cancel(); resetJob?.cancel()
        growthJob = null; xJob = null; yJob = null; resetJob = null
        growthTarget = null; pullTarget = null; trackingTarget = null
        draggedDuringTracking = false
    }

    /** 仅改变需要追随的轴，移动指针不会重启按压放大；松手使用独立回弹。 */
    fun retarget(scope: CoroutineScope, pressed: Boolean, pull: Offset, reduced: Boolean) {
        if (reduced) {
            stop()
            growthTarget = null; pullTarget = null; trackingTarget = null
            resetJob = scope.launch { snapToRest() }
            return
        }
        resetJob?.cancel()
        if (isTracking && trackingTarget != true) draggedDuringTracking = false
        if (isTracking && pull != Offset.Zero) draggedDuringTracking = true
        val completePressLift = !draggedDuringTracking
        val grow = if (pressed) 1f else 0f
        if (growthTarget != grow) {
            val speed = growth.velocity
            val wasPressed = growthTarget == 1f
            growthJob?.cancel()
            growthTarget = grow
            growthJob = scope.launch {
                // 静止短按先完成抬升；真实拖拽松手直接从当前形变回弹，不再补一次放大。
                var releaseVelocity = speed
                if (!pressed && wasPressed && completePressLift && growth.value < 0.975f) {
                    coroutineScope {
                        val lifting = launch { growth.animateTo(1f, PopupPressSpring, initialVelocity = speed) }
                        snapshotFlow { growth.value }.first { it >= 0.975f }
                        releaseVelocity = growth.velocity
                        lifting.cancelAndJoin()
                    }
                }
                growth.animateTo(grow, PopupPressSpring, initialVelocity = releaseVelocity)
            }
        }
        val tracking = isTracking
        val modeChanged = trackingTarget != tracking
        if (pullTarget?.x != pull.x || modeChanged) {
            val speed = x.velocity
            xJob?.cancel()
            xJob = scope.launch {
                if (tracking) x.snapTo(pull.x)
                else x.animateTo(pull.x, PopupPressSpring, initialVelocity = speed)
            }
        }
        if (pullTarget?.y != pull.y || modeChanged) {
            val speed = y.velocity
            yJob?.cancel()
            yJob = scope.launch {
                if (tracking) y.snapTo(pull.y)
                else y.animateTo(pull.y, PopupPressSpring, initialVelocity = speed)
            }
        }
        pullTarget = pull
        trackingTarget = tracking
    }


    fun frame(base: AppGlassPopupFrame, density: Float, weight: Float = 1f, maxGrowthDp: Float = 16f, horizontalStretch: Float = 0.5f): AppGlassPopupFrame {
        val blend = weight.coerceIn(0f, 1f)
        val pull = Offset(x.value, y.value)
        return popupElasticFrame(base, growth.value * blend, pull * blend, density, maxGrowthDp, horizontalStretch)
    }

    fun velocity() = PopupElasticVelocity(growth.velocity, x.velocity, y.velocity)

    suspend fun snapToRest() {
        growth.snapTo(0f)
        x.snapTo(0f)
        y.snapTo(0f)
    }

    suspend fun animateTo(pressed: Boolean, pull: Offset, initialVelocity: PopupElasticVelocity = velocity()) = coroutineScope {
        val follow = if (pressed || pull != Offset.Zero) PopupTrackSpring else PopupPressSpring
        launch { growth.animateTo(if (pressed) 1f else 0f, PopupPressSpring, initialVelocity = initialVelocity.growth) }
        launch { x.animateTo(pull.x, follow, initialVelocity = initialVelocity.x) }
        launch { y.animateTo(pull.y, follow, initialVelocity = initialVelocity.y) }
    }
}

/** 让拖拽在展开过程中逐步接入，在展开落定前完成衔接，不补第二轮放大。 */
internal fun popupDragBlend(time: Float, openDuration: Float): Float {
    if (openDuration <= 0f) return 1f
    val t = ((time / openDuration - 0.2f) / 0.5f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
