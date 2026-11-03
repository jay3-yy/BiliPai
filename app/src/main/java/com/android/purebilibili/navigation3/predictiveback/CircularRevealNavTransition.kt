// SPDX-License-Identifier: GPL-3.0-only
package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import kotlin.math.hypot
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

/** 以当前导航容器中心为圆心，最大半径覆盖四角，兼容窄分屏和横向页面。 */
internal fun circularRevealRadius(width: Float, height: Float, progress: Float): Float =
    hypot(width.coerceAtLeast(0f), height.coerceAtLeast(0f)) * 0.5f * progress.coerceIn(0f, 1f)

internal val CircularRevealNavTransition: NavTransition = object : NavTransition {
    // 自身提供圆形裁剪，不再叠加宿主的方向性圆角裁剪。
    override val handlesOwnClipping: Boolean = true
    override val motion = NavMotion(
        programmatic = NavSettleSpec.Tween(380, AppMotionEasing.Md3Standard),
        commit = NavSettleSpec.Tween(240, AppMotionEasing.Md3Standard, matchReleaseVelocity = true),
        cancel = NavSettleSpec.Spring(stiffness = 1500f),
    )

    override fun scrimFraction(scope: NavTransitionScope): Float = 0f

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = drawWithCache {
        val path = Path()
        val maxRadius = circularRevealRadius(size.width, size.height, 1f)
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.5f
        onDrawWithContent {
            // 后页保持完整；路径复用，进度只在绘制阶段读取。
            val depth = scope.relativeDepth
            val progress = if (depth <= 0f) topProgress(depth) else 1f
            if (progress >= 1f) {
                drawContent()
            } else if (progress > 0f) {
                val radius = maxRadius * progress
                path.rewind()
                path.addOval(Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius))
                clipPath(path) { this@onDrawWithContent.drawContent() }
            }
        }
    }
}
