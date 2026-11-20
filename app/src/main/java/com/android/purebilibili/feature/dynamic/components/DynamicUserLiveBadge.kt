package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.feature.dynamic.shouldShowDynamicUserLiveBadge
import kotlin.math.PI
import kotlin.math.sin

private val StaticLiveBarHeights = floatArrayOf(0.24f, 0.9f, 0.54f)

@Composable
internal fun DynamicUserAvatarWithLiveBadge(
    isLive: Boolean,
    modifier: Modifier = Modifier,
    avatar: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        Box(content = avatar)
        if (shouldShowDynamicUserLiveBadge(isLive)) DynamicUserLiveBadge()
    }
}

@Composable
private fun DynamicUserLiveBadge(modifier: Modifier = Modifier) {
    val contentColor = AppContentColors.onAccent()
    Row(
        modifier = modifier
            .heightIn(min = 16.dp)
            .background(AppContentColors.accent(), CircleShape)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DynamicLivePulseIndicator(color = contentColor)
        AppText(
            text = "直播中",
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
        )
    }
}

@Composable
private fun DynamicLivePulseIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val progress = if (!reduceMotion && animationGate.active) {
        val transition = rememberInfiniteTransition(label = "dynamicLivePulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_200, easing = LinearEasing),
            ),
            label = "dynamicLivePulseProgress",
        )
    } else {
        null
    }

    Canvas(modifier = modifier.size(width = 10.dp, height = 9.dp).then(animationGate.modifier)) {
        drawDynamicLivePulseBars(
            progress = progress?.value,
            color = color,
        )
    }
}

private fun DrawScope.drawDynamicLivePulseBars(
    progress: Float?,
    color: Color,
) {
    val barWidth = size.width * 0.18f
    val gap = size.width * 0.14f
    val minHeight = size.height * 0.3f
    val availableHeight = size.height - minHeight

    repeat(3) { index ->
        val heightFraction = progress?.let { value ->
            val phase = value * 2f * PI.toFloat() + index * 2.1f
            0.5f + 0.5f * sin(phase)
        } ?: StaticLiveBarHeights[index]
        val barHeight = minHeight + availableHeight * heightFraction
        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = size.width * 0.09f + index * (barWidth + gap),
                y = size.height - barHeight,
            ),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
}
