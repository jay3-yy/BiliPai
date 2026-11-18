package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import kotlin.math.roundToLong

/**
 * 数值变化时带滚动 + 脉冲缩放的计数文本：
 * 首次从零匀速展示，后续数值从当前值滚动到目标，同时整体放大再回落（1 -> 1.15 -> 1），
 * 用于高赞弹幕计数、点赞数等需要"跳一下"反馈的场景。
 * 数字以等宽特性渲染，滚动与进位期间文本宽度保持稳定。
 */
@Composable
fun AnimatedCountText(
    count: Long,
    modifier: Modifier = Modifier,
    prefix: String = "",
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    formatter: (Long) -> String = { it.toString() },
    animateFromZero: Boolean = false,
    blurOnChange: Boolean = false,
    blurPadding: Dp = 4.dp,
) {
    val motionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val target = count.coerceAtLeast(0L)
    var displayedCount by remember { mutableLongStateOf(if (animateFromZero) 0L else target) }
    var initialRevealComplete by remember { mutableStateOf(!animateFromZero) }
    LaunchedEffect(target, motionEnabled) {
        val start = displayedCount
        if (motionEnabled && start != target) {
            val spec = if (initialRevealComplete) tween<Float>(180, easing = AppMotionEasing.EmphasizedEnter)
            else tween(900, easing = LinearEasing)
            animate(initialValue = 0f, targetValue = 1f, animationSpec = spec) { progress, _ ->
                displayedCount = (start.toDouble() + (target - start).toDouble() * progress)
                    .roundToLong().coerceIn(minOf(start, target), maxOf(start, target))
            }
        }
        displayedCount = target
        // Only a completed reveal switches to the short update animation.
        if (target > 0L || !motionEnabled) initialRevealComplete = true
    }
    val pulseScale = remember { Animatable(1f) }
    val blurProgress = remember { Animatable(0f) }
    val density = LocalDensity.current
    // 缓存轻微模糊的几个档位，数字动画期间不逐帧创建 RenderEffect。
    val blurEffects = remember(blurOnChange, motionEnabled, density.density) {
        if (blurOnChange && motionEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List(6) { index ->
                val radius = with(density) { ((index + 1) * 0.25f).dp.toPx() }
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL).asComposeRenderEffect()
            }
        } else emptyList()
    }
    var previousCount by remember { mutableLongStateOf(count) }
    LaunchedEffect(count, blurOnChange, motionEnabled) {
        if (!blurOnChange) blurProgress.snapTo(0f)
        if (!motionEnabled) {
            previousCount = count
            pulseScale.snapTo(1f)
            blurProgress.snapTo(0f)
            return@LaunchedEffect
        }
        if (count != previousCount) {
            previousCount = count
            if (!initialRevealComplete) return@LaunchedEffect
            coroutineScope {
                launch {
                    // Retarget from the current value when counts change during a rebound.
                    pulseScale.animateTo(1.15f, tween(80, easing = AppMotionEasing.M1Standard))
                    pulseScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
                if (blurOnChange) launch {
                    blurProgress.animateTo(1f, tween(80, easing = AppMotionEasing.M1Standard))
                    blurProgress.animateTo(0f, tween(180, easing = AppMotionEasing.EmphasizedEnter))
                }
            }
        }
    }
    Text(
        text = prefix + formatter(if (motionEnabled) displayedCount else target),
        style = style.withTabularNumbers(),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.graphicsLayer {
            scaleX = if (motionEnabled) pulseScale.value else 1f
            scaleY = scaleX
            transformOrigin = TransformOrigin(0f, 0.5f)
            renderEffect = if (blurEffects.isNotEmpty() && blurProgress.value > 0.02f) {
                blurEffects[((blurProgress.value * blurEffects.size).toInt() - 1).coerceIn(blurEffects.indices)]
            } else null
        }.padding(if (blurOnChange) blurPadding else 0.dp),
    )
}
