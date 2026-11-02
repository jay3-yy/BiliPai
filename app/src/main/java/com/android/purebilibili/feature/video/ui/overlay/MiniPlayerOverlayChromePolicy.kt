package com.android.purebilibili.feature.video.ui.overlay

internal data class MiniPlayerOverlayChrome(
    val showHeaderChrome: Boolean,
    val showCenterControls: Boolean,
    val showProgressBar: Boolean,
    val progressBarAlpha: Float
)

internal fun resolveMiniPlayerOverlayChrome(
    showControls: Boolean,
    isDraggingPosition: Boolean,
    isResizing: Boolean
): MiniPlayerOverlayChrome {
    // 移动和缩放时只留窗口操作，避免手势结束后误触播放或跳播。
    val transportVisible = showControls && !isDraggingPosition && !isResizing
    return MiniPlayerOverlayChrome(
        showHeaderChrome = showControls || isDraggingPosition || isResizing,
        showCenterControls = transportVisible,
        showProgressBar = true,
        progressBarAlpha = if (transportVisible) 1f else 0.46f
    )
}

internal data class MiniPlayerControlLayout(
    val showCloseButton: Boolean,
    val showExpandButton: Boolean,
    val showBottomTransport: Boolean,
    val showHeaderTransport: Boolean,
    val showSeekButtons: Boolean
)

/** 每个按钮保留 48dp 点击区域；短窗口合并为一行，窄窗口把次要操作收进菜单。 */
internal fun resolveMiniPlayerControlLayout(
    widthDp: Float,
    heightDp: Float,
    seekEnabled: Boolean
): MiniPlayerControlLayout {
    val bottomTransport = heightDp >= 104f
    return MiniPlayerControlLayout(
        showCloseButton = widthDp >= 96f,
        showExpandButton = widthDp >= (if (bottomTransport) 144f else 192f),
        showBottomTransport = bottomTransport,
        showHeaderTransport = !bottomTransport && widthDp >= 144f,
        showSeekButtons = bottomTransport && widthDp >= 184f && seekEnabled
    )
}
