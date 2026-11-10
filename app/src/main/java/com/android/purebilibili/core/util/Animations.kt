package com.android.purebilibili.core.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.motion.resolveEntranceMotionSpec
import kotlinx.coroutines.delay

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
 * - 使用 [Animatable] + 在 graphicsLayer 内读 value，避免 animate*AsState 的每帧重组
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
): Modifier = if (!animationEnabled) this else composed {
    val motionPolicy = remember(motionTier, coordinateWithSharedTransition) {
        resolveEnterMotionPolicy(
            motionTier = motionTier,
            coordinateWithSharedTransition = coordinateWithSharedTransition
        )
    }
    val progress = remember(key) { Animatable(0f) }

    LaunchedEffect(key, motionPolicy) {
        // 挂载时若已是终态（例如配置热切换后的重组），不再倒播
        if (progress.value >= 0.999f) return@LaunchedEffect
        progress.snapTo(0f)
        val delayMs = (index * motionPolicy.staggerStepMs).coerceAtMost(motionPolicy.maxStaggerMs)
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = motionPolicy.dampingRatio,
                stiffness = motionPolicy.stiffness
            )
        )
    }

    this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val translateFactor = motionPolicy.translationFactor
        if (translateFactor != 0f) {
            translationY = (initialOffsetY * translateFactor) * (1f - p)
        }
        val startScale = motionPolicy.initialScale
        if (startScale != 1f) {
            val scale = startScale + (1f - startScale) * p
            scaleX = scale
            scaleY = scale
        }
    }
}
