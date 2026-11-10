package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import com.android.purebilibili.core.ui.adaptive.MotionTier

/**
 * 全 App 统一入场动效的「真相源」。
 *
 * 与旧的 [StaggeredEntranceMotionPolicy](见 core/ui/animation) 不同,这里用 spring 物理
 * 驱动单一 progress(0→1),由 progress 非线性派生 alpha/位移/缩放 —— 更优雅连贯、符合直觉。
 * 解析全部为纯函数,便于单测;Composable 侧(reduce-motion 探测、DataStore 开关)在
 * [EffectiveEntranceMotion] 中组合。
 */
data class EntranceMotionSpec(
    /** false = 直接定格在终态,不播动画(开关关闭或系统 reduce-motion)。 */
    val animate: Boolean,
    val dampingRatio: Float,
    val stiffness: Float,
    /** 相邻 item 的错峰步进。 */
    val staggerStepMs: Int,
    /** 错峰延迟上限,避免长列表尾项等待过久。 */
    val maxStaggerMs: Int,
    /** 起始上移距离(dp,正值表示从下方浮入)。 */
    val offsetDp: Float,
    val initialScale: Float,
    /**
     * alpha 提前到达 1 的 progress 比例:让淡入快于位移收敛,
     * 视觉上「先看清、再落位」,更干净。
     */
    val alphaLeadFraction: Float
) {
    fun progressSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness,
        visibilityThreshold = ENTRANCE_PROGRESS_VISIBILITY_THRESHOLD
    )
}

internal const val ENTRANCE_PROGRESS_VISIBILITY_THRESHOLD = 0.001f

private val INSTANT_ENTRANCE_SPEC = EntranceMotionSpec(
    animate = false,
    dampingRatio = 1f,
    stiffness = 1000f,
    staggerStepMs = 0,
    maxStaggerMs = 0,
    offsetDp = 0f,
    initialScale = 1f,
    alphaLeadFraction = 1f
)

/**
 * 按设备动效档解析入场参数(假定已启用)。门控(开关/reduce-motion)在
 * [resolveEffectiveEntranceMotionSpec] 处理。
 */
fun resolveEntranceMotionSpec(tier: MotionTier): EntranceMotionSpec {
    return when (tier) {
        // 小屏/降级:几乎临界阻尼、极小位移,只保留一丝层次。
        MotionTier.Reduced -> EntranceMotionSpec(
            animate = true,
            dampingRatio = 0.96f,
            stiffness = 520f,
            staggerStepMs = 12,
            maxStaggerMs = 72,
            offsetDp = 8f,
            initialScale = 0.99f,
            alphaLeadFraction = 0.7f
        )
        // 大多数设备:接近临界阻尼的柔和落位。
        MotionTier.Normal -> EntranceMotionSpec(
            animate = true,
            dampingRatio = 0.9f,
            stiffness = 380f,
            staggerStepMs = 22,
            maxStaggerMs = 160,
            offsetDp = 16f,
            initialScale = 0.97f,
            alphaLeadFraction = 0.55f
        )
        // 大屏:更明显的动势 + 极轻微过冲,体现层级。
        MotionTier.Enhanced -> EntranceMotionSpec(
            animate = true,
            dampingRatio = 0.82f,
            stiffness = 320f,
            staggerStepMs = 26,
            maxStaggerMs = 220,
            offsetDp = 22f,
            initialScale = 0.94f,
            alphaLeadFraction = 0.5f
        )
    }
}

/**
 * 合并设备档与门控,得到最终入场参数。
 *
 * @param deviceTier 设备动效档(屏宽决定)。
 * @param appEntranceEnabled App 内「界面入场动画」开关。
 * @param systemReduceMotion 系统是否开启减弱动效(ANIMATOR_DURATION_SCALE==0 等)。
 */
fun resolveEffectiveEntranceMotionSpec(
    deviceTier: MotionTier,
    appEntranceEnabled: Boolean,
    systemReduceMotion: Boolean
): EntranceMotionSpec {
    if (!appEntranceEnabled || systemReduceMotion) return INSTANT_ENTRANCE_SPEC
    return resolveEntranceMotionSpec(deviceTier)
}

/** 第 index 项的错峰起始延迟(ms)。 */
fun resolveEntranceStaggerDelayMs(index: Int, spec: EntranceMotionSpec): Int {
    if (!spec.animate || index <= 0 || spec.staggerStepMs <= 0) return 0
    return (index * spec.staggerStepMs).coerceAtMost(spec.maxStaggerMs)
}

data class EntranceFrame(
    val alpha: Float,
    val translationYDp: Float,
    val scale: Float
)

/** 透明度限定在 0..1；几何保留弹簧过冲，避免提前截断落位。 */
fun resolveEntranceFrame(progress: Float, spec: EntranceMotionSpec): EntranceFrame {
    val p = progress
    val alphaLead = spec.alphaLeadFraction.coerceIn(0.05f, 1f)
    val alpha = (p / alphaLead).coerceIn(0f, 1f)
    return EntranceFrame(
        alpha = alpha,
        translationYDp = spec.offsetDp * (1f - p),
        scale = spec.initialScale + (1f - spec.initialScale) * p
    )
}
