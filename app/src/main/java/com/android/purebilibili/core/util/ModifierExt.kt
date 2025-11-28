package com.android.purebilibili.core.util

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 骨架屏闪光特效 Modifier
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        ),
        label = "shimmer_offset"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFE0E0E0), // 浅灰
                Color(0xFFF5F5F5), // 亮灰 (高光)
                Color(0xFFE0E0E0), // 浅灰
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

/**
 * 一个假的视频卡片组件 (用于 Loading 时占位)
 */
@Composable
fun VideoGridItemSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // 封面占位
        Box(
            modifier = Modifier
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(8.dp))
                .shimmerEffect() // ✨ 加上闪光特效
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 标题占位 (两行)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 作者占位
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
    }
}