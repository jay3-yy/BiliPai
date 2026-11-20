package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import kotlin.math.PI
import kotlin.math.sin

/** 三根底部对齐的圆头音柱；动画值只在绘制阶段读取。 */
@Composable
internal fun PlaybackBarsIndicator(
    isPlaying: Boolean,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // 预加载页面仍在组合中；只有当前页面且音柱进入视口时才运行循环。
    val animationGate = if (isPlaying) rememberDecorativeAnimationGate() else null
    val animate = animationGate?.active == true
    val phase = if (animate) {
        rememberInfiniteTransition(label = "playbackBars").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "playbackBarsPhase",
        )
    } else null
    Canvas(modifier.size(width = 14.dp, height = 16.dp).then(animationGate?.modifier ?: Modifier).semantics {
        this.contentDescription = contentDescription
    }) {
        val progress = phase?.value ?: 0f
        val barWidth = size.width * 0.18f
        val gap = size.width * 0.14f
        val minHeight = size.height * 0.28f
        val idle = floatArrayOf(0.42f, 0.72f, 0.52f)
        repeat(3) { index ->
            val fraction = if (animate) {
                0.5f + 0.5f * sin(progress * 2f * PI.toFloat() + index * 2.1f)
            } else idle[index]
            val barHeight = minHeight + (size.height - minHeight) * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.09f + index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
