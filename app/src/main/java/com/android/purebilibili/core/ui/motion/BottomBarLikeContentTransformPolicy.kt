package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.navigation.resolveBottomPagerNavigationDurationMillis

internal fun resolveBottomBarLikeTransitionMillis(
    animationEnabled: Boolean,
    reduceMotion: Boolean,
    pageDistance: Int = 1
): Int {
    if (!animationEnabled || reduceMotion) return 0
    return resolveBottomPagerNavigationDurationMillis(pageDistance = pageDistance)
}

internal fun resolveBottomBarLikeHorizontalContentTransform(
    durationMillis: Int,
    forward: Boolean
): ContentTransform {
    if (durationMillis <= 0) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val spec = tween<IntOffset>(durationMillis = durationMillis, easing = EaseInOut)
    return if (forward) {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = spec
        ) togetherWith slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth },
            animationSpec = spec
        )
    } else {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth },
            animationSpec = spec
        ) togetherWith slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = spec
        )
    }
}
