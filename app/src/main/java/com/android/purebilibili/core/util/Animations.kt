package com.android.purebilibili.core.util

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 1. 列表项进场动画 (Q弹上浮 + 交错延迟)
 * @param index: 列表项的索引，用于计算延迟时间
 * @param key: 🔥 关键参数：用于触发重置动画的键值 (通常传视频ID)
 */
fun Modifier.animateEnter(
    index: Int = 0,
    key: Any? = Unit, // 👈 必须加上这个参数，HomeScreen 才能正常编译
    initialOffsetY: Float = 100f
): Modifier = composed {
    // 使用 remember(key) 确保当 key (例如视频ID) 变化时，动画状态会被重置
    val alpha = remember(key) { Animatable(0f) }
    val translationY = remember(key) { Animatable(initialOffsetY) }

    LaunchedEffect(key) {
        // 根据索引计算延迟，实现波浪效果
        val delayMs = (index * 50L).coerceAtMost(500L)
        delay(delayMs)

        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = LinearEasing)
            )
        }
        launch {
            translationY.animateTo(
                targetValue = 0f,
                // Q弹果冻效果
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

/**
 * 2. Q弹点击效果 (按压缩放)
 */
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.90f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BouncyScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}