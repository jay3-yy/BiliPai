package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.motion.iosMorphTween

internal fun <T> bottomBarDockWidthMotionSpec(): TweenSpec<T> = iosMorphTween(260)

internal fun <T> bottomBarChromeHeightMotionSpec(): TweenSpec<T> = iosMorphTween(220)

internal fun <T> bottomBarSearchGapMotionSpec(): TweenSpec<T> = iosMorphTween(240)

internal fun <T> bottomBarContentVisibilityMotionSpec(): TweenSpec<T> = tween(
    durationMillis = 180,
    easing = FastOutSlowInEasing,
)

internal fun <T> bottomBarClickPulseMotionSpec(): TweenSpec<T> = tween(
    durationMillis = 240,
    easing = LinearEasing,
)

internal fun <T> bottomBarTapReleaseMotionSpec(): TweenSpec<T> = tween(
    durationMillis = 240,
    easing = FastOutSlowInEasing,
)

internal fun <T> bottomBarSettleReboundMotionSpec(): TweenSpec<T> = tween(
    durationMillis = 260,
    easing = LinearEasing,
)

internal fun <T> bottomBarSearchHoldMotionSpec(): SpringSpec<T> = spring(
    dampingRatio = 0.62f,
    stiffness = 560f,
)
