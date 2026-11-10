package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback

enum class GesturePercentTransitionDirection {
    None,
    Increase,
    Decrease
}

fun resolveGesturePercentTransitionDirection(
    previousPercent: Int,
    currentPercent: Int
): GesturePercentTransitionDirection {
    val previous = previousPercent.coerceIn(0, 100)
    val current = currentPercent.coerceIn(0, 100)
    return when {
        current > previous -> GesturePercentTransitionDirection.Increase
        current < previous -> GesturePercentTransitionDirection.Decrease
        else -> GesturePercentTransitionDirection.None
    }
}

fun shouldTriggerGesturePercentHaptic(
    previousPercent: Int,
    currentPercent: Int,
    stepPercent: Int = 5
): Boolean {
    if (stepPercent <= 0) return false
    val previous = previousPercent.coerceIn(0, 100)
    val current = currentPercent.coerceIn(0, 100)
    if (previous == current) return false
    if (current == 0 || current == 100) return true
    return if (current > previous) {
        previous / stepPercent != current / stepPercent
    } else {
        (previous - 1).coerceAtLeast(0) / stepPercent !=
            (current - 1).coerceAtLeast(0) / stepPercent
    }
}

internal object GesturePercentMotionDefaults {
    const val EnterFadeDurationMillis = 100
    const val ExitFadeDurationMillis = 70
    const val SlideSpringDampingRatio = 0.88f
    const val SlideSpringStiffness = 780f

    fun <T> slideSpring(): SpringSpec<T> = spring(
        dampingRatio = SlideSpringDampingRatio,
        stiffness = SlideSpringStiffness
    )
}

@Composable
fun AnimatedGesturePercentText(
    percent: Int,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    label: String = "gesture-percent-scroll",
    enableHaptic: Boolean = true
) {
    val normalizedPercent = percent.coerceIn(0, 100)
    val motionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    var previousPercent by remember { mutableIntStateOf(normalizedPercent) }
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_GESTURES)

    LaunchedEffect(normalizedPercent, enableHaptic, motionEnabled) {
        if (previousPercent == normalizedPercent) return@LaunchedEffect
        if (
            enableHaptic &&
            shouldTriggerGesturePercentHaptic(previousPercent, normalizedPercent)
        ) {
            haptic(HapticType.SELECTION)
        }
        previousPercent = normalizedPercent
    }

    AnimatedContent(
        targetState = normalizedPercent,
        transitionSpec = {
            if (!motionEnabled) {
                (androidx.compose.animation.EnterTransition.None togetherWith
                    androidx.compose.animation.ExitTransition.None).using(null)
            } else {
                val direction = resolveGesturePercentTransitionDirection(initialState, targetState)
                val enterOffset: (Int) -> Int = { height ->
                    when (direction) {
                        GesturePercentTransitionDirection.Increase -> height / 2
                        GesturePercentTransitionDirection.Decrease -> -height / 2
                        GesturePercentTransitionDirection.None -> 0
                    }
                }
                val exitOffset: (Int) -> Int = { height ->
                    when (direction) {
                        GesturePercentTransitionDirection.Increase -> -height / 2
                        GesturePercentTransitionDirection.Decrease -> height / 2
                        GesturePercentTransitionDirection.None -> 0
                    }
                }
                (slideInVertically(
                    animationSpec = GesturePercentMotionDefaults.slideSpring(),
                    initialOffsetY = enterOffset
                ) + fadeIn(
                    animationSpec = emphasizedEnterTween(GesturePercentMotionDefaults.EnterFadeDurationMillis)
                ) togetherWith
                    slideOutVertically(
                        animationSpec = GesturePercentMotionDefaults.slideSpring(),
                        targetOffsetY = exitOffset
                    ) + fadeOut(
                        animationSpec = emphasizedExitTween(GesturePercentMotionDefaults.ExitFadeDurationMillis)
                    )) using
                    SizeTransform(clip = false)
            }
        },
        label = label
    ) { targetPercent ->
        AppText(
            text = "$targetPercent%",
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            modifier = modifier
        )
    }
}
