package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
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

/**
 * 数值变化时带滚动 + 脉冲缩放的计数文本：
 * 首次强调从零展示，后续数值从当前值滚动到目标，同时整体放大再回落（1 -> 1.15 -> 1），
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
    val target = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    // 首次强调只播一次；后续回填从当前值接续，不用弹簧越过真实计数。
    val animatedCount = remember { Animatable(if (animateFromZero) 0 else target, Int.VectorConverter) }
    var firstCountUpdate by remember { mutableStateOf(true) }
    LaunchedEffect(target, motionEnabled) {
        val initialReveal = firstCountUpdate && animateFromZero
        firstCountUpdate = false
        if (!motionEnabled) {
            animatedCount.snapTo(target)
        } else {
            animatedCount.animateTo(
                target,
                tween(if (initialReveal) 450 else 180, easing = AppMotionEasing.EmphasizedEnter),
            )
        }
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
    var previousCount by remember { mutableLongStateOf(if (animateFromZero && blurOnChange) Long.MIN_VALUE else count) }
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
        text = prefix + formatter(
            if (!motionEnabled || count > Int.MAX_VALUE.toLong()) count.coerceAtLeast(0L)
            else animatedCount.value.coerceAtLeast(0).toLong()
        ),
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
