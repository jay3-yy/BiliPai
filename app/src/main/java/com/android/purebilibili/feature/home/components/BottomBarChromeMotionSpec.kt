package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.iosMorphTween

internal fun <T> bottomBarDockWidthMotionSpec(): TweenSpec<T> = iosMorphTween(260)

internal fun <T> bottomBarChromeHeightMotionSpec(): TweenSpec<T> = iosMorphTween(220)

internal fun <T> bottomBarSearchGapMotionSpec(): TweenSpec<T> = iosMorphTween(240)

internal fun <T> bottomBarSearchMorphSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.82f,
    stiffness = 360f,
)

internal fun <T> bottomBarContentVisibilityMotionSpec(): TweenSpec<T> = tween(
    durationMillis = 180,
    easing = AppMotionEasing.M1Standard,
)

internal fun bottomBarClickPulseMotionSpec(): SpringSpec<Float> = spring(
    dampingRatio = 0.55f,
    stiffness = 700f,
    visibilityThreshold = 0.0001f,
)

internal fun <T> bottomBarTapReleaseMotionSpec(): TweenSpec<T> = tween(
    durationMillis = 240,
    easing = AppMotionEasing.M1Standard,
)

internal fun <T> bottomBarSearchHoldMotionSpec(): SpringSpec<T> = spring(
    dampingRatio = 0.62f,
    stiffness = 560f,
)
