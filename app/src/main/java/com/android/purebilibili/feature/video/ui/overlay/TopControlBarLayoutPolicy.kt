package com.android.purebilibili.feature.video.ui.overlay

/** Android HeaderControl gates from PiliPlus; portrait means window orientation, not video aspect. */
internal data class TopControlBarLayoutPolicy(
    val showHome: Boolean,
    val showTitle: Boolean,
    val showStatus: Boolean,
    val showAudio: Boolean,
    val showCast: Boolean,
    val showDanmakuActions: Boolean,
    val showEngagement: Boolean,
    val showDislike: Boolean,
) {
    val buttonWidthDp: Int get() = 42
    val buttonHeightDp: Int get() = 34
    val verticalPaddingDp: Int get() = 12
    val titleFontSp: Int get() = 16
    val statusFontSp: Int get() = 13
    val onlineCountFontSp: Int get() = 11
}

internal fun resolveTopControlBarLayoutPolicy(
    isPortrait: Boolean,
    isFullscreen: Boolean,
    horizontalScreen: Boolean = true,
    isFileSource: Boolean = false,
    isUgc: Boolean = true,
    showFullscreenActionItems: Boolean = true,
): TopControlBarLayoutPolicy = TopControlBarLayoutPolicy(
    showHome = !isFullscreen || !isPortrait,
    showTitle = isFullscreen || (!horizontalScreen && !isPortrait),
    showStatus = !isPortrait && (isFullscreen || !horizontalScreen),
    showAudio = !isFileSource && !isFullscreen && isUgc,
    showCast = !isFileSource && !isFullscreen,
    showDanmakuActions = !isPortrait || isFullscreen,
    showEngagement = !isFileSource && isFullscreen && showFullscreenActionItems,
    showDislike = isUgc,
)

internal fun shouldPollTopControlBarClock(
    showCurrentTime: Boolean,
    hostLifecycleStarted: Boolean,
): Boolean = showCurrentTime && hostLifecycleStarted

internal fun shouldPollTopControlBarBattery(
    showBatteryLevel: Boolean,
    hostLifecycleStarted: Boolean,
): Boolean = showBatteryLevel && hostLifecycleStarted
