package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Only fullscreen landscape chrome mirrors the camera/back-gesture clearance. */
@Composable
internal fun playerControlHorizontalInsets(
    isLandscape: Boolean,
    isFullscreen: Boolean,
    displayCutoutInsets: WindowInsets = WindowInsets.displayCutout,
    systemGestureInsets: WindowInsets = WindowInsets.systemGestures,
): WindowInsets = remember(isLandscape, isFullscreen, displayCutoutInsets, systemGestureInsets) {
    if (isLandscape && !isFullscreen) {
        WindowInsets(0, 0, 0, 0)
    } else if (!isLandscape) {
        displayCutoutInsets.only(WindowInsetsSides.Horizontal)
    } else {
        val unsafeEdges = displayCutoutInsets.union(systemGestureInsets)
        // Read at layout time, like platform insets, so rotation/gesture-mode updates stay live.
        object : WindowInsets {
            private fun horizontal(density: Density, layoutDirection: LayoutDirection): Int = maxOf(
                unsafeEdges.getLeft(density, layoutDirection),
                unsafeEdges.getRight(density, layoutDirection),
            )
            override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
                horizontal(density, layoutDirection)
            override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
                horizontal(density, layoutDirection)
            override fun getTop(density: Density): Int = 0
            override fun getBottom(density: Density): Int = 0
        }
    }
}
