package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Read during layout so rotation and cutout changes never use a stale inset snapshot. */
internal class SymmetricPlayerHorizontalInsets(private val cutout: WindowInsets) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
        maxOf(cutout.getLeft(density, layoutDirection), cutout.getRight(density, layoutDirection))

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
        getLeft(density, layoutDirection)

    override fun getTop(density: Density): Int = 0
    override fun getBottom(density: Density): Int = 0
}

@Composable
internal fun playerControlHorizontalInsets(isFullscreen: Boolean, isPortrait: Boolean): WindowInsets {
    val cutout = WindowInsets.displayCutout
    return if (isFullscreen && !isPortrait) {
        remember(cutout) { SymmetricPlayerHorizontalInsets(cutout) }
    } else {
        cutout.only(WindowInsetsSides.Horizontal)
    }
}

internal fun shouldReserveHiddenPlayerStatusBar(isFullscreen: Boolean, isPortrait: Boolean): Boolean =
    isFullscreen && isPortrait

/** 控件避让独立于视频画布；竖屏全屏隐藏系统栏后按挖孔实际高度避让，不再多留整条状态栏高度。 */
@Composable
internal fun Modifier.playerControlTopSafeArea(
    isFullscreen: Boolean,
    isPortrait: Boolean,
    statusBarVisible: Boolean,
): Modifier = when {
    shouldReserveHiddenPlayerStatusBar(isFullscreen, isPortrait) -> windowInsetsPadding(
        WindowInsets.displayCutout.only(WindowInsetsSides.Top),
    )
    statusBarVisible -> windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
    else -> this
}
