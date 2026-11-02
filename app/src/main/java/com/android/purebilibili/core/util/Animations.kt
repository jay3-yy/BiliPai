package com.android.purebilibili.core.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier
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
 * 无缩放、极小位移、更短错峰、更高刚度，避免与 sharedBounds 抢几何，也更快让位给点击进详情。
 */
fun resolveEnterMotionPolicy(
    motionTier: MotionTier,
    coordinateWithSharedTransition: Boolean = false
): EnterMotionPolicy {
    val base = when (motionTier) {
        MotionTier.Reduced -> EnterMotionPolicy(
            staggerStepMs = 10,
            maxStaggerMs = 60,
            initialScale = 0.97f,
            translationFactor = 0.35f,
            dampingRatio = 0.92f,
            stiffness = 720f
        )

        MotionTier.Enhanced -> EnterMotionPolicy(
            staggerStepMs = 24,
            maxStaggerMs = 180,
            initialScale = 0.88f,
            translationFactor = 1.1f,
            dampingRatio = 0.62f,
            stiffness = 320f
        )

        MotionTier.Normal -> EnterMotionPolicy(
            staggerStepMs = 30,
            maxStaggerMs = 200,
            initialScale = 0.9f,
            translationFactor = 1f,
            dampingRatio = 0.7f,
            stiffness = 350f
        )
    }

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
        alpha = p
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

/**
 * Q弹点击效果 (按压缩放)
 */
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.90f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "BouncyScale"
    )

    this
        .graphicsLayer {
            val s = scale.value
            scaleX = s
            scaleY = s
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
