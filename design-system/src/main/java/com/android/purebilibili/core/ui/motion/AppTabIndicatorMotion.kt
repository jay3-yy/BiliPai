package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Tab indicator elastic edge interpolation built on the shared StandardEase token. */
object AppTabIndicatorMotion {
    const val DurationMillis = 300
    val ease: Easing = AppMotionEasing.StandardEase
    val decelerate = Easing { fraction ->
        kotlin.math.sin(ease.transform(fraction) * Math.PI.toFloat() / 2f)
    }
    val accelerate = Easing { fraction ->
        1f - kotlin.math.cos(ease.transform(fraction) * Math.PI.toFloat() / 2f)
    }
}
