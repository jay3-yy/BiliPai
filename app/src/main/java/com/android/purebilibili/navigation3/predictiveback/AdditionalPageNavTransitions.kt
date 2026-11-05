// SPDX-License-Identifier: GPL-3.0-only
package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private val instantMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(0, LinearEasing),
    commit = NavSettleSpec.Tween(0, LinearEasing),
    cancel = NavSettleSpec.Tween(0, LinearEasing),
)

internal val InstantPageNavTransition: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f, motion = instantMotion, scrim = { 0f },
) { }

internal val SizePageNavTransition: NavTransition = object : NavTransition {
    override val handlesOwnClipping = true
    override val motion = PagePresetMotion
    override fun scrimFraction(scope: NavTransitionScope): Float = 0f
    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = drawWithContent {
        val progress = if (scope.relativeDepth <= 0f) topProgress(scope.relativeDepth) else 1f
        val inset = size.height * (1f - progress) / 2f
        clipRect(top = inset, bottom = size.height - inset) { this@drawWithContent.drawContent() }
    // 与圆形揭示一致，动态裁剪和页面内容分层，内容自身更新仍正常重绘。
    }.graphicsLayer()
}

internal fun sharedAxisMovement(progress: Float): Float =
    AppMotionEasing.M1Standard.transform(progress.coerceIn(0f, 1f))

internal fun sharedAxisIncomingAlpha(progress: Float): Float =
    AppMotionEasing.M1Standard.transform(((progress - 0.15f) / 0.85f).coerceIn(0f, 1f))

internal val SharedAxisPageNavTransition: NavTransition = navGraphicsTransition(
    motion = PagePresetMotion, scrim = { 0f },
) { scope ->
    val depth = scope.relativeDepth
    val distance = with(scope.density) { 30.dp.toPx() }
    val direction = if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
    if (depth <= 0f) {
        val progress = topProgress(depth)
        translationX = direction * distance * (1f - sharedAxisMovement(progress))
        alpha = sharedAxisIncomingAlpha(progress)
    } else {
        translationX = -direction * distance * sharedAxisMovement(coverProgress(depth))
        // Keep the lower page opaque until the upper page covers it.
        alpha = 1f
    }
}
