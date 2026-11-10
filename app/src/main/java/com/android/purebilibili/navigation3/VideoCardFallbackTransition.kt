package com.android.purebilibili.navigation3

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

// 仅关闭一镜到底后的默认视频侧滑使用短时钟和轻遮罩；各材质共用，
// 不改变其他页面、显式选择的转场及卡片形变。手势提交/取消沿用原有弹簧。
private val OrdinaryVideoTransition: NavTransition = object : NavTransition by NavTransitions.MiuixDefault {
    override val motion = NavMotion(
        programmatic = NavSettleSpec.Tween(260, AppMotionEasing.Continuity),
        commit = NavTransitions.MiuixDefault.motion.commit,
        cancel = NavTransitions.MiuixDefault.motion.cancel,
    )

    override fun scrimFraction(scope: NavTransitionScope): Float =
        NavTransitions.MiuixDefault.scrimFraction(scope) * 0.4f
}

internal fun resolveVideoCardFallbackTransition(
    cardTransitionEnabled: Boolean,
    animationStyle: BiliPaiPredictiveBackAnimationStyle,
    globalTransition: NavTransition,
    cardEnabledFallback: NavTransition,
): NavTransition = when {
    cardTransitionEnabled -> cardEnabledFallback
    animationStyle == BiliPaiPredictiveBackAnimationStyle.MIUIX -> OrdinaryVideoTransition
    else -> globalTransition
}
