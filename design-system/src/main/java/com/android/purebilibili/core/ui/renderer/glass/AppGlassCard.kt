package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppCardColors
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor

/**
 * 液态玻璃渲染线的 iOS 连续曲率卡片：
 * 使用连续曲率轮廓与克制的镜面高光细边，可点击卡片在动效开启时提供轻柔的按压缩放反馈。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppGlassCard(
    modifier: Modifier,
    shape: AppCardShape?,
    colors: AppCardColors,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedShape = when (shape) {
        is AppCardShape.Semantic -> AppGlassTokens.containerShape(shape.level)
        is AppCardShape.Uniform -> AppGlassTokens.continuousShape(shape.cornerRadius)
        null -> AppGlassTokens.continuousShape(AppGlassTokens.CardCornerRadius)
    }
    val resolvedContentColor = colors.contentColor.takeOrElse { AppContentColors.primaryText() }
    val interactive = onClick != null || onLongClick != null
    val interactionSource = remember(interactive) { if (interactive) MutableInteractionSource() else null }
    val pressed by interactionSource?.collectIsPressedAsState() ?: remember { androidx.compose.runtime.mutableStateOf(false) }
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val pressScale by animateFloatAsState(
        targetValue = if (interactive && pressed && motionEnabled) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "AppGlassCardPressScale",
    )

    val cardModifier = modifier
        .graphicsLayer {
            if (pressScale != 1f) {
                scaleX = pressScale
                scaleY = pressScale
            }
        }
        .clip(resolvedShape)
        .background(colors.containerColor)
        .border(
            width = AppGlassTokens.SpecularBorderWidth,
            color = AppGlassTokens.cardRimColor(),
            shape = resolvedShape,
        )
        .let { base ->
            if (interactive && interactionSource != null) {
                base.combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                )
            } else {
                base
            }
        }

    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides resolvedContentColor,
    ) {
        ProvideAppActionContentColor(resolvedContentColor) {
            Column(
                modifier = cardModifier,
                content = content,
            )
        }
    }
}
