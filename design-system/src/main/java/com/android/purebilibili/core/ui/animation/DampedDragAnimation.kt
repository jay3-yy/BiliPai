// 文件路径: core/ui/animation/DampedDragAnimation.kt
package com.android.purebilibili.core.ui.animation

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import com.android.purebilibili.core.ui.motion.BottomBarMotionSpec
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal fun resolveDampedDragVelocityItemsPerSecond(
    velocityPxPerSecond: Float,
    itemWidthPx: Float
): Float {
    if (itemWidthPx <= 0f) return 0f
    return velocityPxPerSecond / itemWidthPx
}

internal const val HORIZONTAL_DRAG_DOMINANCE_RATIO = 1.25f
internal const val HORIZONTAL_DRAG_MIN_DISTANCE_PX = 8f

/** 指示器拖拽的目标跟随方式；BiliPai 模式不做飞掷投影或超滚。 */
enum class DampedDragTrackingMode {
    PROJECTED_SNAP,
    BILIPAI_SPRING,
}

fun shouldEngageHorizontalDrag(
    horizontalDeltaPx: Float,
    verticalDeltaPx: Float,
    dominanceRatio: Float = HORIZONTAL_DRAG_DOMINANCE_RATIO,
    minimumHorizontalDistancePx: Float = HORIZONTAL_DRAG_MIN_DISTANCE_PX,
): Boolean {
    val horizontalDistance = abs(horizontalDeltaPx)
    val verticalDistance = abs(verticalDeltaPx)
    val safeRatio = dominanceRatio.coerceAtLeast(1f)
    val safeMinimum = minimumHorizontalDistancePx.coerceAtLeast(0f)
    return horizontalDistance >= safeMinimum &&
        horizontalDistance >= verticalDistance * safeRatio
}

/**
 * 根据拖拽速度和位置计算释放后吸附的目标索引（9.0.0 飞掷投影）。
 */
internal fun resolveDampedDragReleaseTargetIndex(
    currentValue: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    motionSpec: BottomBarMotionSpec
): Int {
    if (itemCount <= 0) return 0
    val velocityItems = resolveDampedDragVelocityItemsPerSecond(
        velocityPxPerSecond = velocityPxPerSecond,
        itemWidthPx = itemWidthPx
    )
    val projectedValue = currentValue + velocityItems * motionSpec.drag.flingProjectionTimeSeconds
    var nextIndex = projectedValue.roundToInt()
    val baseIndex = currentValue.roundToInt()
    val maxReleaseStep = motionSpec.drag.maxReleaseStepCount.coerceAtLeast(1)
    if (abs(nextIndex - baseIndex) > maxReleaseStep) {
        nextIndex = baseIndex + (nextIndex - baseIndex).sign * maxReleaseStep
    }
    return nextIndex.coerceIn(0, itemCount - 1)
}

/**
 * 共享的 BiliPai 指示器拖拽动画状态。
 *
 * 实现参考上游 FloatingBottomBar / DampedDragAnimation / DragGestureInspector，
 * commit 778fb38bbf0c43f168b8bbd7d9e369d6fb46754b。
 * 底栏、顶部标签、分段控件、分区侧栏共用此内核，避免各自维护一套速度形变。
 *
 * 默认交互逻辑以 9.0.0 发行版为准（跟手 snapTo + 速度飞掷投影）；
 * 首页底栏可切换 BiliPai 的边界/吸附策略。两种模式都保留 BiliPai 的按压与速度形变，
 * 且拖拽阶段一律逐帧跟手，避免指示器在手指停住时仍落后于已切换的页面。
 */
class DampedDragAnimationState internal constructor(
    initialIndex: Int,
    private val itemCount: Int,
    private val scope: CoroutineScope,
    private val onIndexChanged: (Int) -> Unit,
    private val motionSpec: BottomBarMotionSpec,
    private val pressedScale: Float,
    private val trackingMode: DampedDragTrackingMode,
    private val notifyIndexChangedOnReleaseStart: Boolean = false,
    @Suppress("UNUSED_PARAMETER") private val holdPressUntilReleaseTargetSettles: Boolean = false
) {
    private val valueAnimationSpec = LiquidIndicatorMotion.positionSpec()
    private val pressProgressAnimationSpec = LiquidIndicatorMotion.pressSpec()
    // Motion tuning copied from HyperIsland's LiquidGlassNavigationBar.
    private val scaleXAnimationSpec = LiquidIndicatorMotion.scaleXSpec()
    private val scaleYAnimationSpec = LiquidIndicatorMotion.scaleYSpec()

    private val valueAnimation = Animatable(initialIndex.toFloat(), 0.001f)
    private val velocityAnimation = LiquidIndicatorVelocity(scope)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val offsetAnimation = Animatable(0f)
    private val mutatorMutex = MutatorMutex()
    private val deformationVelocityTracker = VelocityTracker()

    private var motionGeneration = 0
    private var valueJob: Job? = null
    private var releaseJob: Job? = null
    private var pressJob: Job? = null
    private var offsetJob: Job? = null

    /** 9.0.0 风格的拖拽期望位置（允许超滚，不受边界限制） */
    private var desiredValue = initialIndex.toFloat()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val velocity: Float get() = velocityAnimation.value
    val deformationVelocityItemsPerSecond: Float get() = velocityAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val scale: Float get() = maxOf(scaleX, scaleY)
    val dragOffset: Float get() = offsetAnimation.value
    val isRunning: Boolean get() = valueAnimation.isRunning

    var velocityPxPerSecond by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var targetIndex = initialIndex
        private set

    var settledReleaseCount by mutableIntStateOf(0)
        private set

    var settledSelectionCount by mutableIntStateOf(0)
        private set

    private fun startNewMotion(): Int {
        motionGeneration += 1
        return motionGeneration
    }

    fun press() {
        deformationVelocityTracker.resetTracking()
        releaseJob?.cancel()
        pressJob?.cancel()
        pressJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release(onSettled: (() -> Unit)? = null) {
        releaseJob?.cancel()
        releaseJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (pressJob?.isActive == true || pressProgressAnimation.value > 0f) {
                withTimeoutOrNull(LiquidIndicatorMotion.BloomWaitMillis) {
                    snapshotFlow { minOf(scaleXAnimation.value, scaleYAnimation.value) }
                        .first { it >= pressedScale }
                }
            }
            val settled = withTimeoutOrNull(LiquidIndicatorMotion.SettleWaitMillis) {
                awaitFrame()
                val threshold = ((itemCount - 1).toFloat() * LiquidIndicatorMotion.SettleTolerance)
                    .coerceAtLeast(0.001f)
                snapshotFlow { abs(valueAnimation.value - targetValue) }
                    .first { it <= threshold }
                true
            }
            if (settled == null) {
                valueJob?.cancel()
                valueAnimation.snapTo(targetValue)
            }
            pressJob?.cancel()
            onSettled?.invoke()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
            // 速度形变是非对称的，不归零会留下椭圆残影。
            velocityAnimation.updateTarget(0f)
        }
    }

    private fun updateDeformationVelocity(value: Float) {
        val valueRange = (itemCount - 1).toFloat().coerceAtLeast(1f)
        deformationVelocityTracker.addPosition(
            SystemClock.uptimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = deformationVelocityTracker.calculateVelocity().x / valueRange
        velocityAnimation.updateTarget(targetVelocity)
    }

    fun snapTo(targetValue: Float) {
        val generation = startNewMotion()
        valueJob?.cancel()
        desiredValue = targetValue
        targetIndex = targetValue.roundToInt().coerceIn(0, itemCount - 1)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (generation != motionGeneration) return@launch
            valueAnimation.stop()
            valueAnimation.snapTo(targetValue)
            velocityAnimation.snapTo(0f)
        }
    }

    fun animateToValue(
        value: Float,
        onSettled: (() -> Unit)? = null,
        animatePress: Boolean = true,
        initialVelocity: Float = valueAnimation.velocity,
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutatorMutex.mutate {
                if (animatePress) press()
                val nextTarget = value.fastCoerceIn(0f, (itemCount - 1).toFloat())
                targetIndex = nextTarget.roundToInt().coerceIn(0, itemCount - 1)
                valueJob?.cancel()
                valueJob = launch {
                    valueAnimation.animateTo(
                        targetValue = nextTarget,
                        animationSpec = valueAnimationSpec,
                        initialVelocity = initialVelocity,
                    ) {
                        updateDeformationVelocity(this.value)
                    }
                }
                if (velocity != 0f) {
                    velocityAnimation.updateTarget(0f)
                }
                release(onSettled = onSettled)
            }
        }
    }

    /**
     * 处理拖拽事件（9.0.0 跟手逻辑 + BiliPai 压按形变）。
     *
     * 使用 snapTo 确保指示器位置完全跟手，
     * 同时通过 desiredValue 记录超滚状态，
     * 手势速度只用于释放投影；形变速度对齐 BiliPai，从指示器 value 轨迹平滑估算。
     */
    fun onDrag(
        dragAmountPx: Float,
        itemWidthPx: Float,
        gestureVelocityPxPerSecond: Float = 0f
    ) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        if (!isDragging) {
            isDragging = true
            startNewMotion()
            valueJob?.cancel()
            offsetJob?.cancel()
            desiredValue = valueAnimation.value
            velocityPxPerSecond = 0f
            velocityAnimation.snapTo(0f)
            press()
        }
        velocityPxPerSecond = gestureVelocityPxPerSecond

        when (trackingMode) {
            DampedDragTrackingMode.BILIPAI_SPRING -> {
                // BiliPai FloatingBottomBar: spring-follow via animateTo (updateValue),
                // bounded to [0, itemCount-1], with velocity deformation sampling.
                desiredValue = (desiredValue + dragAmountPx / itemWidthPx)
                    .fastCoerceIn(0f, (itemCount - 1).toFloat())
                valueJob?.cancel()
                valueJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (isDragging) {
                        valueAnimation.snapTo(desiredValue)
                    } else {
                        valueAnimation.animateTo(desiredValue, valueAnimationSpec) {
                            updateDeformationVelocity(this.value)
                        }
                    }
                    updateDeformationVelocity(desiredValue)
                }
            }

            DampedDragTrackingMode.PROJECTED_SNAP -> {
                // 9.0.0 风格：带阻力和超滚约束的期望位置跟踪。
                val currentValue = desiredValue
                val isOverscrolling = currentValue < 0f || currentValue > (itemCount - 1).toFloat()
                val baseResistance = motionSpec.drag.baseResistance
                val overscrollResistance = motionSpec.drag.overscrollResistance
                val newDesiredValue = desiredValue + (dragAmountPx / itemWidthPx) *
                    if (isOverscrolling) overscrollResistance else baseResistance
                desiredValue = newDesiredValue.fastCoerceIn(
                    -motionSpec.drag.overscrollLimitItems,
                    (itemCount - 1).toFloat() + motionSpec.drag.overscrollLimitItems
                )
                val clampedValue = desiredValue.fastCoerceIn(0f, (itemCount - 1).toFloat())
                valueJob?.cancel()
                valueJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    valueAnimation.snapTo(clampedValue)
                    updateDeformationVelocity(clampedValue)
                }
            }
        }

        // 面板偏移累计
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimation.snapTo(offsetAnimation.value + dragAmountPx)
        }
    }

    fun setPressed(pressed: Boolean) {
        if (pressed) {
            press()
        } else if (!isDragging) {
            release()
        }
    }

    /** 处理拖拽结束：按 trackingMode 选择就近吸附或速度投影，再执行 BiliPai 落位形变。 */
    fun onDragEnd(
        velocityX: Float,
        itemWidthPx: Float,
        settleIndex: Int? = null,
        notifyIndexChanged: Boolean = true
    ) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        isDragging = false
        val generation = motionGeneration
        velocityPxPerSecond = velocityX

        val releaseTargetIndex = settleIndex?.coerceIn(0, itemCount - 1) ?: when (trackingMode) {
            DampedDragTrackingMode.BILIPAI_SPRING ->
                desiredValue.roundToInt().coerceIn(0, itemCount - 1)
            DampedDragTrackingMode.PROJECTED_SNAP -> resolveDampedDragReleaseTargetIndex(
                currentValue = desiredValue,
                velocityPxPerSecond = velocityX,
                itemWidthPx = itemWidthPx,
                itemCount = itemCount,
                motionSpec = motionSpec
            )
        }
        targetIndex = releaseTargetIndex
        desiredValue = releaseTargetIndex.toFloat()
        if (notifyIndexChanged && notifyIndexChangedOnReleaseStart) {
            onIndexChanged(releaseTargetIndex)
        }
        val releaseVelocityItemsPerSecond = resolveDampedDragVelocityItemsPerSecond(
            velocityPxPerSecond = velocityX,
            itemWidthPx = itemWidthPx,
        )
        animateToValue(
            value = releaseTargetIndex.toFloat(),
            onSettled = {
                if (generation == motionGeneration) {
                    velocityPxPerSecond = 0f
                    settledReleaseCount += 1
                    if (notifyIndexChanged && !notifyIndexChangedOnReleaseStart) {
                        onIndexChanged(releaseTargetIndex)
                    }
                }
            },
            animatePress = false,
            initialVelocity = releaseVelocityItemsPerSecond,
        )
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
        }
    }

    fun updateIndex(index: Int) {
        if (isDragging || itemCount <= 0) return
        val safeIndex = index.coerceIn(0, itemCount - 1)
        if (
            safeIndex == targetIndex &&
            (
                isRunning ||
                    abs(value - safeIndex.toFloat()) < 0.005f ||
                    abs(targetValue - safeIndex.toFloat()) < 0.005f
                )
        ) return
        startNewMotion()
        targetIndex = safeIndex
        desiredValue = safeIndex.toFloat()
        velocityPxPerSecond = 0f
        animateToValue(
            value = safeIndex.toFloat(),
            onSettled = { settledSelectionCount += 1 },
        )
    }
}

private const val BILIPAI_PRESSED_SCALE =
    com.android.purebilibili.core.ui.BottomBarReferencePressedScale

@Composable
fun rememberDampedDragAnimationState(
    initialIndex: Int,
    itemCount: Int,
    onIndexChanged: (Int) -> Unit,
    motionSpec: BottomBarMotionSpec = resolveBottomBarMotionSpec(),
    pressedScale: Float = BILIPAI_PRESSED_SCALE,
    trackingMode: DampedDragTrackingMode = DampedDragTrackingMode.PROJECTED_SNAP,
    notifyIndexChangedOnReleaseStart: Boolean = false,
    holdPressUntilReleaseTargetSettles: Boolean = false
): DampedDragAnimationState {
    val scope = rememberCoroutineScope()
    val currentOnIndexChanged by rememberUpdatedState(onIndexChanged)

    return remember(
        itemCount,
        motionSpec,
        pressedScale,
        trackingMode,
        notifyIndexChangedOnReleaseStart,
        holdPressUntilReleaseTargetSettles
    ) {
        DampedDragAnimationState(
            initialIndex = initialIndex,
            itemCount = itemCount,
            scope = scope,
            onIndexChanged = { currentOnIndexChanged(it) },
            motionSpec = motionSpec,
            pressedScale = pressedScale.coerceAtLeast(1f),
            trackingMode = trackingMode,
            notifyIndexChangedOnReleaseStart = notifyIndexChangedOnReleaseStart,
            holdPressUntilReleaseTargetSettles = holdPressUntilReleaseTargetSettles
        )
    }
}

/**
 * 水平拖拽手势 Modifier（9.0.0 发行版实现，带速度追踪）。
 *
 * 使用标准 Compose Foundation 手势 API 确保可靠的触摸检测：
 * - awaitFirstDown(requireUnconsumed = false)：保证被点击层消费后仍能触发拖拽
 * - awaitHorizontalTouchSlopOrCancellation：等待触摸斜率阈值后才激活拖拽（避免误触为点击）
 * - horizontalDrag：标准的 Compose 拖拽事件循环
 * - VelocityTracker：跟踪帧间速度用于飞掷投影和形变
 */
fun Modifier.horizontalDragGesture(
    dragState: DampedDragAnimationState,
    itemWidthPx: Float,
    consumePointerChanges: Boolean = true,
    settleIndex: Int? = null,
    notifyIndexChanged: Boolean = true
): Modifier = this.pointerInput(
    dragState,
    itemWidthPx,
    consumePointerChanges,
    settleIndex,
    notifyIndexChanged
) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        val down = awaitFirstDown(requireUnconsumed = false)
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var horizontalDragEngaged = false
        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            val totalDelta = change.position - down.position
            if (!shouldEngageHorizontalDrag(totalDelta.x, totalDelta.y)) {
                return@awaitHorizontalTouchSlopOrCancellation
            }
            horizontalDragEngaged = true
            if (consumePointerChanges) {
                change.consume()
            }
            dragState.onDrag(over, itemWidthPx)
        }

        if (dragStart != null && horizontalDragEngaged) {
            velocityTracker.addPosition(dragStart.uptimeMillis, dragStart.position)
            var isCanceled = false

            try {
                horizontalDrag(dragStart.id) { change ->
                    if (consumePointerChanges) {
                        change.consume()
                    }
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val dragAmount = change.position.x - change.previousPosition.x
                    val velocity = velocityTracker.calculateVelocity()
                    dragState.onDrag(dragAmount, itemWidthPx, velocity.x)
                }
            } catch (_: Exception) {
                isCanceled = true
            }

            if (!isCanceled) {
                val velocity = velocityTracker.calculateVelocity()
                dragState.onDragEnd(
                    velocityX = velocity.x,
                    itemWidthPx = itemWidthPx,
                    settleIndex = settleIndex,
                    notifyIndexChanged = notifyIndexChanged
                )
            } else {
                dragState.onDragEnd(
                    velocityX = 0f,
                    itemWidthPx = itemWidthPx,
                    settleIndex = settleIndex,
                    notifyIndexChanged = notifyIndexChanged
                )
            }
        }
    }
}
