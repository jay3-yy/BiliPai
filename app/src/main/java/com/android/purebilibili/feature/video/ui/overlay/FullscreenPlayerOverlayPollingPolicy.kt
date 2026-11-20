package com.android.purebilibili.feature.video.ui.overlay

internal fun shouldAutoHideFullscreenControls(
    showControls: Boolean,
    gestureMode: FullscreenGestureMode,
    isPlaying: Boolean
): Boolean {
    return showControls &&
        gestureMode == FullscreenGestureMode.None &&
        isPlaying
}
