package com.android.purebilibili.feature.live

internal data class LivePortraitPresentation(
    val usePortraitControls: Boolean,
    val clearScreen: Boolean,
    val showChrome: Boolean,
    val showChatPreview: Boolean,
    val showMediaOverlays: Boolean,
)

internal fun resolveLivePortraitPresentation(
    layoutMode: LiveRoomLayoutMode,
    clearScreen: Boolean,
    chatVisible: Boolean,
    controlsVisible: Boolean = true,
    isFullscreen: Boolean = false,
): LivePortraitPresentation {
    val portrait = layoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay
    val cleared = portrait && clearScreen
    return LivePortraitPresentation(
        usePortraitControls = portrait,
        clearScreen = cleared,
        showChrome = portrait && !cleared && !isFullscreen,
        // The full-screen player owns the media overlay area. Keep the chat preview
        // out of it so chat bubbles cannot stack on top of scrolling danmaku.
        showChatPreview = portrait && !cleared && !isFullscreen && chatVisible,
        showMediaOverlays = !cleared,
    )
}

internal fun resolveLivePortraitChatPreviewCount(heightDp: Int, fontScale: Float): Int =
    if (heightDp < 720 || fontScale > 1.2f) 3 else 4

data class LivePlayerGesturePolicy(
    val doubleTapPlayback: Boolean,
    val centerDragFullscreen: Boolean,
)

fun resolveLivePlayerGesturePolicy(layoutMode: LiveRoomLayoutMode): LivePlayerGesturePolicy {
    return LivePlayerGesturePolicy(
        doubleTapPlayback = true,
        centerDragFullscreen = true,
    )
}
