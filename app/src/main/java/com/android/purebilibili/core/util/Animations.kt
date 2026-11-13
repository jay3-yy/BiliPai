package com.android.purebilibili.core.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.motion.resolveEntranceMotionSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class EnterMotionPolicy(
    val staggerStepMs: Int,
    val maxStaggerMs: Int,
    val initialScale: Float,
    val translationFactor: Float,
    val dampingRatio: Float,
    val stiffness: Float
)

/**
 * @param coordinateWithSharedTransition 为 true 时走「过渡协同」档：
 * 无缩放和位移、更短错峰、更高刚度，避免与 sharedBounds 抢几何，也更快让位给点击进详情。
 */
fun resolveEnterMotionPolicy(
    motionTier: MotionTier,
    coordinateWithSharedTransition: Boolean = false
): EnterMotionPolicy {
    val entrance = resolveEntranceMotionSpec(motionTier)
    // 列表挂载与页面入场共用尺度和弹簧；位移仍由调用方的像素距离决定。
    val base = EnterMotionPolicy(
        staggerStepMs = entrance.staggerStepMs,
        maxStaggerMs = entrance.maxStaggerMs,
        initialScale = entrance.initialScale,
        translationFactor = when (motionTier) {
            MotionTier.Reduced -> 0.35f
            MotionTier.Normal -> 1f
            MotionTier.Enhanced -> 1.1f
        },
        dampingRatio = entrance.dampingRatio,
        stiffness = entrance.stiffness,
    )

    if (!coordinateWithSharedTransition) return base

    // 与过渡动画并存：几乎只淡入，几何保持终态，降低对 shared 源卡的干扰。
    return EnterMotionPolicy(
        staggerStepMs = minOf(base.staggerStepMs, 12),
        maxStaggerMs = minOf(base.maxStaggerMs, 72),
        initialScale = 1f,
        translationFactor = 0f,
        dampingRatio = 0.95f,
        stiffness = maxOf(base.stiffness, 560f)
    )
}

/**
 * 列表项进场动画。
 *
 * 流畅度约定：
 * - [animationEnabled] 在 call site 已做滚动/返回/分类门控；此处不再读滚动态
 * - 节点持有 [Animatable]，仅图层读取进度，避免每项创建组合和 Effect 节点
 * - [coordinateWithSharedTransition] 为 true 时仅 alpha 淡入，不改 scale/translation
 *
 * @param index 列表项索引，用于交错延迟
 * @param key 重置动画身份（首页卡片统一传 Unit，避免分类切换整表重播）
 * @param initialOffsetY 独立进场时的起始 Y 偏移（过渡协同档会忽略）
 * @param animationEnabled 是否启用
 * @param motionTier 设备动效档
 * @param coordinateWithSharedTransition 与共享元素过渡并存时弱化几何动效
 */
fun Modifier.animateEnter(
    index: Int = 0,
    key: Any? = Unit,
    initialOffsetY: Float = 60f,
    animationEnabled: Boolean = true,
    motionTier: MotionTier = MotionTier.Normal,
    coordinateWithSharedTransition: Boolean = false
): Modifier = if (!animationEnabled) this else this then EnterAnimationElement(
    index, key, initialOffsetY, resolveEnterMotionPolicy(motionTier, coordinateWithSharedTransition),
)

private data class EnterAnimationElement(
    val index: Int,
    val key: Any?,
    val initialOffsetY: Float,
    val policy: EnterMotionPolicy,
) : ModifierNodeElement<EnterAnimationNode>() {
    override fun create() = EnterAnimationNode(index, key, initialOffsetY, policy)
    override fun update(node: EnterAnimationNode) = node.update(index, key, initialOffsetY, policy)
    override fun InspectorInfo.inspectableProperties() {
        name = "animateEnter"
    }
}

private class EnterAnimationNode(
    private var index: Int,
    private var key: Any?,
    private var initialOffsetY: Float,
    private var policy: EnterMotionPolicy,
) : Modifier.Node(), LayoutModifierNode {
    private var progress = Animatable(0f)
    private var animationJob: Job? = null

    override fun onAttach() = startEntrance()

    override fun onReset() {
        // Lazy 容器复用节点时，新内容仍从自己的入场起点开始。
        animationJob?.cancel()
        progress = Animatable(0f)
        if (isAttached) {
            startEntrance()
            invalidatePlacement()
        }
    }

    fun update(index: Int, key: Any?, initialOffsetY: Float, policy: EnterMotionPolicy) {
        val identityChanged = this.key != key
        val policyChanged = this.policy != policy
        this.index = index
        this.key = key
        this.initialOffsetY = initialOffsetY
        this.policy = policy
        if (identityChanged) {
            animationJob?.cancel()
            progress = Animatable(0f)
        }
        if (isAttached && (identityChanged || policyChanged)) startEntrance()
    }

    private fun startEntrance() {
        animationJob?.cancel()
        // 已完成入场的节点切换动效档时保持可见，不倒播。
        if (progress.value >= 0.999f) return
        val currentProgress = progress
        val currentPolicy = policy
        val delayMs = (index * currentPolicy.staggerStepMs).coerceAtMost(currentPolicy.maxStaggerMs)
        animationJob = coroutineScope.launch {
            currentProgress.snapTo(0f)
            if (delayMs > 0) delay(delayMs.toLong())
            currentProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = currentPolicy.dampingRatio,
                    stiffness = currentPolicy.stiffness,
                ),
            )
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                translationY = initialOffsetY * policy.translationFactor * (1f - p)
                val scale = policy.initialScale + (1f - policy.initialScale) * p
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
