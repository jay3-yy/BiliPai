package com.android.purebilibili.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.motion.EntranceMotionSpec
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.motion.resolveEffectiveEntranceMotionSpec
import com.android.purebilibili.core.ui.motion.resolveEntranceFrame
import com.android.purebilibili.core.ui.motion.resolveEntranceStaggerDelayMs
import com.android.purebilibili.core.util.LocalWindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.runtime.snapshotFlow

/**
 * 一组共享同一入场节奏的内容协调器。
 *
 * - [spec] 已合并设备档 + 开关 + 系统 reduce-motion 的最终参数。
 * - 自动按「首次挂载顺序」分配错峰序号,调用方不再手写 index。
 * - [start] 在导航转场落定后由 [EntranceGroup] 触发,避免与页面级转场叠成双重动画。
 */
class AppEntranceController internal constructor(
    val spec: EntranceMotionSpec
) {
    private val counter = AtomicInteger(0)
    internal var started by mutableStateOf(!spec.animate)
        private set

    internal fun allocateIndex(): Int = counter.getAndIncrement()

    internal fun start() {
        started = true
    }

    internal suspend fun awaitStart() {
        if (started) return
        snapshotFlow { started }.filter { it }.first()
    }
}

internal val LocalAppEntrance = staticCompositionLocalOf<AppEntranceController?> { null }
internal val LocalNavigationOwnsContentEntrance = staticCompositionLocalOf { false }

/**
 * 解析当前生效的入场参数:设备动效档 ⊕ App「界面入场动画」开关 ⊕ 系统 reduce-motion。
 */
@Composable
fun rememberEffectiveEntranceMotionSpec(): EntranceMotionSpec {
    val widthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val deviceTier = remember(widthSizeClass) {
        resolveDeviceUiProfile(widthSizeClass).motionTier
    }
    val appEnabled = LocalAppThemeConfig.current.uiEntranceAnimationEnabled
    val reduceMotion = rememberSystemReduceMotion()
    return remember(deviceTier, appEnabled, reduceMotion) {
        resolveEffectiveEntranceMotionSpec(
            deviceTier = deviceTier,
            appEntranceEnabled = appEnabled,
            systemReduceMotion = reduceMotion
        )
    }
}

/**
 * 包裹一段内容,使其中所有 [Modifier.entrance] 共享同一套入场节奏与错峰序号。
 *
 * @param startWhen 置 false 可推迟入场启动(例如等导航转场落定);恢复 true 后开始。
 */
@Composable
fun EntranceGroup(
    startWhen: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalNavigationOwnsContentEntrance.current) {
        CompositionLocalProvider(LocalAppEntrance provides null, content = content)
        return
    }
    val spec = rememberEffectiveEntranceMotionSpec()
    val controller = remember(spec) { AppEntranceController(spec) }
    val navigationReady = LocalAppNavigationEntranceReady.current
    LaunchedEffect(controller, startWhen, navigationReady) {
        if (startWhen && spec.animate) {
            snapshotFlow { navigationReady.value }.first { it }
            controller.start()
        }
    }
    CompositionLocalProvider(LocalAppEntrance provides controller) {
        content()
    }
}

/**
 * 让本元素以统一入场动效淡入浮现。需置于 [EntranceGroup] 内;组外则不动画(直接显示)。
 *
 * 用单个 spring 驱动的 progress 非线性派生 alpha/位移/缩放,只在 layer 阶段生效,零重组。
 */
fun Modifier.entrance(): Modifier = this then EntranceElement

private object EntranceElement : ModifierNodeElement<EntranceNode>() {
    override fun create(): EntranceNode = EntranceNode()
    override fun update(node: EntranceNode) {}
    override fun InspectorInfo.inspectableProperties() {
        name = "entrance"
    }
    override fun hashCode(): Int = "entrance".hashCode()
    override fun equals(other: Any?): Boolean = other === this
}

private class EntranceNode :
    Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {

    private val progress = Animatable(0f)
    private var controller: AppEntranceController? = null
    private var animationJob: Job? = null
    private var skipEntrance = false

    override fun onAttach() {
        observeController()
        val currentController = controller
        if (skipEntrance || currentController == null || !currentController.spec.animate) {
            // 已直接显示的内容不能在开关、窗口档位变化后重新退回透明的起始帧。
            skipEntrance = true
            return
        }
        val spec = currentController.spec
        val index = currentController.allocateIndex()
        val delayMs = resolveEntranceStaggerDelayMs(index, spec)
        animationJob = coroutineScope.launch {
            currentController.awaitStart()
            if (delayMs > 0) delay(delayMs.toLong())
            progress.animateTo(targetValue = 1f, animationSpec = spec.progressSpring())
        }
    }

    override fun onObservedReadsChanged() {
        val previousController = controller
        observeController()
        if (previousController !== controller) {
            // 参数变化只影响新挂载内容；当前节点落在终态，取消旧协调器的等待与动画。
            animationJob?.cancel()
            animationJob = null
            skipEntrance = true
        }
        invalidateMeasurement()
    }

    private fun observeController() {
        observeReads {
            controller = currentValueOf(LocalAppEntrance)
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val spec = controller?.spec
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (skipEntrance || spec == null || !spec.animate) {
                placeable.place(0, 0)
                return@layout
            }
            placeable.placeWithLayer(0, 0) {
                val frame = resolveEntranceFrame(progress.value, spec)
                alpha = frame.alpha
                scaleX = frame.scale
                scaleY = frame.scale
                translationY = frame.translationYDp * density
            }
        }
    }
}
