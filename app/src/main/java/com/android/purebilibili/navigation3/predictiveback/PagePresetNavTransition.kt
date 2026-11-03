// SPDX-License-Identifier: GPL-3.0-only
package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

internal data class PageTransitionPreset(
    val originX: Float = 0f,
    val originY: Float = 0f,
    val fades: Boolean = false,
    val minimumScale: Float = 1f,
    val coveredPageMovement: Boolean = false,
    val offsetDp: Float = 0f,
)

internal fun resolvePageTransitionPreset(style: BiliPaiPredictiveBackAnimationStyle): PageTransitionPreset =
    when (style) {
        BiliPaiPredictiveBackAnimationStyle.FADE -> PageTransitionPreset(fades = true, offsetDp = 50f)
        BiliPaiPredictiveBackAnimationStyle.FADE_IN -> PageTransitionPreset(fades = true)
        BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT -> PageTransitionPreset(originX = 1f)
        BiliPaiPredictiveBackAnimationStyle.LEFT_TO_RIGHT -> PageTransitionPreset(originX = -1f)
        BiliPaiPredictiveBackAnimationStyle.UP_TO_DOWN -> PageTransitionPreset(originY = -1f)
        BiliPaiPredictiveBackAnimationStyle.DOWN_TO_UP -> PageTransitionPreset(originY = 1f)
        BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT_FADE ->
            PageTransitionPreset(originX = 1f, fades = true, coveredPageMovement = true)
        BiliPaiPredictiveBackAnimationStyle.LEFT_TO_RIGHT_FADE ->
            PageTransitionPreset(originX = -1f, fades = true, coveredPageMovement = true)
        BiliPaiPredictiveBackAnimationStyle.ZOOM -> PageTransitionPreset(fades = true, minimumScale = 0.92f)
        else -> error("Not a page transition preset: $style")
    }

internal val PagePresetMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(300, AppMotionEasing.Md3Standard),
    commit = NavSettleSpec.Tween(220, AppMotionEasing.Md3Standard, matchReleaseVelocity = true),
    cancel = NavSettleSpec.Spring(stiffness = 1500f),
)

/** 普通页面共用；卡片、设置容器和分栏通过 entry 自身的转场覆盖此策略。 */
internal fun pagePresetNavTransition(style: BiliPaiPredictiveBackAnimationStyle): NavTransition {
    val preset = resolvePageTransitionPreset(style)
    return navGraphicsTransition(
        opaqueDepth = 1f,
        motion = PagePresetMotion,
        scrim = { 0f },
    ) { scope ->
        // 仅由导航的有限进度驱动，在图层读取；进入、返回、取消沿同一路径往返。
        val depth = scope.relativeDepth
        val width = scope.layoutSize.width.toFloat()
        val height = scope.layoutSize.height.toFloat()
        if (depth <= 0f) {
            val progress = topProgress(depth)
            val remaining = 1f - progress
            scaleX = preset.minimumScale + (1f - preset.minimumScale) * progress
            scaleY = scaleX
            translationX = snapTranslationToPixelEdge(preset.originX * remaining * width, scaleX, width)
            translationY = snapTranslationToPixelEdge(
                (preset.originY * height + with(scope.density) { preset.offsetDp.dp.toPx() }) * remaining,
                scaleY, height,
            )
            alpha = if (preset.fades) progress else 1f
        } else if (preset.coveredPageMovement) {
            translationX = -preset.originX * coverProgress(depth) * width
        }
    }
}
