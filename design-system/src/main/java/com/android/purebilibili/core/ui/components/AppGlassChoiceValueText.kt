package com.android.purebilibili.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** 仅玻璃单选行尾使用：值变化时同步过渡文字与占位宽度。 */
@Composable
internal fun AppGlassChoiceValueText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (!com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current ||
        com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    ) {
        AppText(
            text = text,
            style = style,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
            modifier = modifier,
        )
        return
    }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.CenterEnd,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 30)) togetherWith fadeOut(tween(120)))
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(210) }))
        },
        label = "glassChoiceValue",
    ) { value ->
        val blur = transition.animateFloat(transitionSpec = { tween(180) }, label = "glassChoiceValueBlur") { state ->
            if (state == EnterExitState.Visible) 0f else 4f
        }
        AppText(
            text = value,
            style = style,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            tapToCopyEnabled = false,
            modifier = Modifier.graphicsLayer {
                val radius = blur.value * density
                renderEffect = if (radius > 0.1f) BlurEffect(radius, radius) else null
            },
        )
    }
}
