// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 BiliPai contributors
package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.transition.navigationBlurBlend
import com.android.purebilibili.core.ui.transition.resolveNavigationBlurBlendFrame
import com.android.purebilibili.core.ui.adaptive.MotionTier
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

internal fun biliPaiMiuixNavTransition(
    animation: BiliPaiPredictiveBackAnimationStyle,
    exitDirection: BiliPaiPredictiveBackExitDirection,
    isLightBackground: Boolean,
    miuixTransitionBlurEnabled: Boolean = true,
    miuixPredictiveBackMaxProgressPercent: Int =
        MIUIX_PREDICTIVE_BACK_DEFAULT_MAX_PROGRESS_PERCENT,
    miuixPredictiveBackProgressEnabled: Boolean = true,
    blurMotionTierProvider: () -> MotionTier = { MotionTier.Normal },
): NavTransition {
    val progressControlEnabled = shouldUseMiuixPredictiveBackProgress(
        animation = animation,
        enabled = miuixPredictiveBackProgressEnabled,
    )
    val maxPreviewFraction =
        miuixPredictiveBackMaxProgressPercent.coerceIn(0, 100) / 100f
    val baseTransition = when (animation) {
        BiliPaiPredictiveBackAnimationStyle.NONE -> return NoPredictiveBackTransition
        BiliPaiPredictiveBackAnimationStyle.MIUIX -> if (progressControlEnabled) {
            miuixPredictiveBackProgressTransition(maxPreviewFraction)
        } else {
            NavTransitions.MiuixDefault
        }
        BiliPaiPredictiveBackAnimationStyle.AOSP -> AospNavTransition
        BiliPaiPredictiveBackAnimationStyle.SCALE -> scaleNavTransition(exitDirection)
        BiliPaiPredictiveBackAnimationStyle.CLASSIC -> ClassicNavTransition
        BiliPaiPredictiveBackAnimationStyle.CIRCULAR_REVEAL -> return CircularRevealNavTransition
        BiliPaiPredictiveBackAnimationStyle.IOS -> return IosPageNavTransition
        BiliPaiPredictiveBackAnimationStyle.NO_TRANSITION -> return InstantPageNavTransition
        BiliPaiPredictiveBackAnimationStyle.SIZE -> return SizePageNavTransition
        BiliPaiPredictiveBackAnimationStyle.SHARED_AXIS -> return SharedAxisPageNavTransition
        BiliPaiPredictiveBackAnimationStyle.FADE,
        BiliPaiPredictiveBackAnimationStyle.FADE_IN,
        BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT,
        BiliPaiPredictiveBackAnimationStyle.LEFT_TO_RIGHT,
        BiliPaiPredictiveBackAnimationStyle.UP_TO_DOWN,
        BiliPaiPredictiveBackAnimationStyle.DOWN_TO_UP,
        BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT_FADE,
        BiliPaiPredictiveBackAnimationStyle.LEFT_TO_RIGHT_FADE,
        BiliPaiPredictiveBackAnimationStyle.ZOOM -> return pagePresetNavTransition(animation)
    }
    return realtimeCoveredBlurTransition(
        baseTransition = baseTransition,
        isLightBackground = isLightBackground,
        blurEnabled = miuixTransitionBlurEnabled,
        progressControlEnabled = progressControlEnabled,
        maxPreviewFraction = maxPreviewFraction,
        motionTierProvider = blurMotionTierProvider,
    )
}

internal fun shouldUseMiuixPredictiveBackProgress(
    animation: BiliPaiPredictiveBackAnimationStyle,
    enabled: Boolean,
): Boolean = enabled && animation == BiliPaiPredictiveBackAnimationStyle.MIUIX

private fun realtimeCoveredBlurTransition(
    baseTransition: NavTransition,
    isLightBackground: Boolean,
    blurEnabled: Boolean,
    progressControlEnabled: Boolean,
    maxPreviewFraction: Float,
    motionTierProvider: () -> MotionTier,
): NavTransition {
    // 仅附加被覆盖页模糊；各样式继续拥有自己的时钟、遮罩及裁剪契约。
    return object : NavTransition by baseTransition {
        override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier {
            val transformed = with(baseTransition) {
                this@transformEntry.transformEntry(scope)
            }
            // 模糊只作用于被覆盖页；开关关闭或进入/退出的顶层页不挂额外效果图层。
            if (!blurEnabled || scope.role != NavRole.Covered) return transformed
            return transformed.navigationBlurBlend {
                val progress = if (scope.gesture != null || scope.settle != null) {
                    val coveredDepth = if (progressControlEnabled && scope.relativeDepth > 0f) {
                        resolveMiuixPredictiveBackCoveredDepth(scope, maxPreviewFraction)
                    } else scope.relativeDepth
                    resolveMiuixNavCoveredBlurProgress(coveredDepth)
                } else 0f
                resolveNavigationBlurBlendFrame(progress, motionTierProvider(), isLightBackground)
            }
        }
    }
}

/** Depth 0 is fully revealed; depth 1 is fully covered by the current page. */
internal fun resolveMiuixNavCoveredBlurProgress(relativeDepth: Float): Float {
    val coveredDepth = relativeDepth.coerceIn(0f, 1f)
    return coveredDepth * coveredDepth
}
