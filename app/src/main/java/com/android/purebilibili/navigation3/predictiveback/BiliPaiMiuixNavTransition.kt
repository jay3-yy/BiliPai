// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 BiliPai contributors
package com.android.purebilibili.navigation3.predictiveback

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
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
            val renderEffectCache = MiuixCoveredBlurRenderEffectCache()
            return transformed.graphicsLayer {
                val motionTier = motionTierProvider()
                renderEffect = if (motionTier != MotionTier.Reduced) {
                    val blurFrame = resolvePredictiveBackBlurFrame(
                        progress = if (scope.gesture != null || scope.settle != null) {
                            val coveredDepth = if (
                                progressControlEnabled && scope.relativeDepth > 0f
                            ) {
                                resolveMiuixPredictiveBackCoveredDepth(
                                    scope = scope,
                                    maxPreviewFraction = maxPreviewFraction,
                                )
                            } else {
                                scope.relativeDepth
                            }
                            resolveMiuixNavCoveredBlurProgress(coveredDepth)
                        } else {
                            0f
                        },
                        motionTier = motionTier,
                        isLightBackground = isLightBackground,
                    )
                    renderEffectCache.resolve(blurFrame.blurRadiusPx)
                } else {
                    null
                }
            }
        }
    }
}

/** Depth 0 is fully revealed; depth 1 is fully covered by the current page. */
internal fun resolveMiuixNavCoveredBlurProgress(relativeDepth: Float): Float {
    val coveredDepth = relativeDepth.coerceIn(0f, 1f)
    return coveredDepth * coveredDepth
}

private class MiuixCoveredBlurRenderEffectCache {
    // 半径已由公共策略按 4px 量化，往返手势复用各档效果，避免只缓存上一档反复创建。
    private val effectsByRadius = mutableMapOf<Float, ComposeRenderEffect>()

    fun resolve(radiusPx: Float): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radiusPx <= 0.5f) {
            return null
        }
        val quantizedRadius = kotlin.math.round(radiusPx * 2f) / 2f
        return effectsByRadius.getOrPut(quantizedRadius) {
            AndroidRenderEffect.createBlurEffect(
                quantizedRadius,
                quantizedRadius,
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        }
    }
}
