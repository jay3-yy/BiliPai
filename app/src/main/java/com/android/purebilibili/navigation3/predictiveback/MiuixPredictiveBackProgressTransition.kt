// SPDX-License-Identifier: GPL-3.0-only
package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastRoundToInt
import top.yukonga.miuix.kmp.nav.runtime.boundedNavGestureProgress
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition

internal const val MIUIX_PREDICTIVE_BACK_DEFAULT_MAX_PROGRESS_PERCENT = 100
internal const val MIUIX_PREDICTIVE_BACK_SETTLE_DURATION_MILLIS = 300

private val MiuixPredictiveBackEasing = CubicBezierEasing(0.12f, 0.38f, 0.2f, 1f)

/** Limits only the held gesture preview; commit and programmatic back still reach the full exit. */
internal fun resolveMiuixPredictiveBackVisualProgress(
    rawProgress: Float,
    gestureProgress: Float?,
    settlePhase: NavSettlePhase?,
    maxPreviewFraction: Float,
): Float {
    val raw = rawProgress.coerceIn(0f, 1f)
    val previewLimit = maxPreviewFraction.coerceIn(0f, 1f)
    val release = gestureProgress?.let(::boundedNavGestureProgress) ?: return raw
    return when (settlePhase) {
        null,
        NavSettlePhase.Cancel,
        -> raw * previewLimit

        NavSettlePhase.Commit -> {
            val postRelease = ((raw - release) / (1f - release)).coerceIn(0f, 1f)
            // 先延续跟手的位移比例，再平滑补足预览上限留下的距离。
            // 补足项首尾斜率均为零，避免松手瞬间把导航释放速度额外放大。
            val completion = postRelease * postRelease * (3f - 2f * postRelease)
            raw * previewLimit + (1f - previewLimit) * completion
        }

        NavSettlePhase.Programmatic -> raw
    }.coerceIn(0f, 1f)
}

internal fun miuixPredictiveBackProgressTransition(maxPreviewFraction: Float): NavTransition {
    val settleMotion = NavMotion(
        commit = NavSettleSpec.Tween(
            durationMillis = MIUIX_PREDICTIVE_BACK_SETTLE_DURATION_MILLIS,
            easing = MiuixPredictiveBackEasing,
            matchReleaseVelocity = true,
        ),
        cancel = NavSettleSpec.Spring(stiffness = 1500f),
        programmatic = NavSettleSpec.Tween(
            durationMillis = MIUIX_PREDICTIVE_BACK_SETTLE_DURATION_MILLIS,
            easing = MiuixPredictiveBackEasing,
        ),
    )
    val pop = object : NavTransition {
        override val motion: NavMotion = settleMotion

        override fun scrimFraction(scope: NavTransitionScope): Float = if (
            scope.relativeDepth <= 0f
        ) {
            1f - resolveVisualProgress(
                scope = scope,
                rawProgress = (-scope.relativeDepth).coerceIn(0f, 1f),
                maxPreviewFraction = maxPreviewFraction,
            )
        } else {
            resolveMiuixPredictiveBackCoveredDepth(scope, maxPreviewFraction)
        }

        override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = graphicsLayer {
            val widthPx = scope.layoutSize.width.toFloat()
            val isRtl = scope.layoutDirection == LayoutDirection.Rtl
            if (scope.relativeDepth <= 0f) {
                val progress = resolveVisualProgress(
                    scope = scope,
                    rawProgress = (-scope.relativeDepth).coerceIn(0f, 1f),
                    maxPreviewFraction = maxPreviewFraction,
                )
                val direction = if (isRtl) -1f else 1f
                translationX = (direction * progress * widthPx)
                    .fastRoundToInt()
                    .toFloat()
            } else {
                val coveredDepth =
                    resolveMiuixPredictiveBackCoveredDepth(scope, maxPreviewFraction)
                translationX = (if (isRtl) 1f else -1f) * coveredDepth * widthPx * 0.25f
                alpha = 1f - 0.1f * coveredDepth
            }
        }
    }
    return navDirectionalTransition(
        push = NavTransitions.MiuixDefault,
        pop = NavTransitions.MiuixDefault,
        predictivePop = pop,
    )
}

internal fun resolveMiuixPredictiveBackCoveredDepth(
    scope: NavTransitionScope,
    maxPreviewFraction: Float,
): Float {
    val rawRevealProgress = 1f - scope.relativeDepth.coerceIn(0f, 1f)
    return 1f - resolveVisualProgress(scope, rawRevealProgress, maxPreviewFraction)
}

private fun resolveVisualProgress(
    scope: NavTransitionScope,
    rawProgress: Float,
    maxPreviewFraction: Float,
): Float = resolveMiuixPredictiveBackVisualProgress(
    rawProgress = rawProgress,
    gestureProgress = scope.gesture?.progress,
    settlePhase = scope.settle?.phase,
    maxPreviewFraction = maxPreviewFraction,
)
