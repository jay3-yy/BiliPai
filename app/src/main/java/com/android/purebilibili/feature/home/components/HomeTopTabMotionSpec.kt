package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.TweenSpec
import com.android.purebilibili.core.ui.motion.iosMorphTween

internal fun <T> iosTopTabCapsuleMotionSpec(): TweenSpec<T> = iosMorphTween(260)
