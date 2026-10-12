package com.android.purebilibili.feature.live
import com.android.purebilibili.core.util.AppDisplayContext
import com.android.purebilibili.core.util.shouldUsePhonePlayerOrientation
import kotlin.math.roundToInt

internal const val LIVE_INPUT_HEIGHT_DP = 70
internal const val LIVE_CHAT_INPUT_GAP_DP = 55

enum class LiveRoomLayoutMode {
    PortraitPanel,
    PortraitVerticalOverlay,
    LandscapeSplit,
    LandscapeOverlay
}

/** White media-overlay chat colors are only valid when chat is drawn over the video. */
internal fun shouldUseLiveChatMediaOverlay(layoutMode: LiveRoomLayoutMode): Boolean =
    layoutMode == LiveRoomLayoutMode.LandscapeOverlay ||
        layoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay

data class LivePortraitOverlayMetrics(
    val panelHeightFraction: Float,
    val minPanelHeightDp: Int,
    val minPlayerClearanceDp: Int,
    val playerControlsGapDp: Int,
    val topChromeReserveDp: Int,
    val playerControlsReserveDp: Int
)

data class LiveOverlayContentInsets(
    val topDp: Int,
    val bottomDp: Int
)

data class LiveLandscapeChatOverlayMetrics(
    val widthFraction: Float,
    val heightFraction: Float,
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val minHeightDp: Int,
    val edgePaddingDp: Int,
    val topControlReserveDp: Int,
    val bottomControlReserveDp: Int
)

enum class LiveRequestedOrientationMode {
    Unspecified,
    SensorLandscape,
    SensorPortrait,
    Portrait,
}

/**
 * 直播方向请求看显示角色和当前显示器最大窗口，不看当前窗口宽度。
 * 手机横屏后宽度会跨过 600dp；若按当前窗口当成平板并放开方向，传感器会把竖握手机扳回竖屏，形成狂切。
 */
fun resolveLiveRequestedOrientationMode(
    displayContext: AppDisplayContext,
    isFullscreen: Boolean,
    isPortraitLive: Boolean = false,
): LiveRequestedOrientationMode {
    return when {
        displayContext.usesInWindowFullscreen -> LiveRequestedOrientationMode.Unspecified
        !shouldUsePhonePlayerOrientation(displayContext) -> LiveRequestedOrientationMode.Unspecified
        isFullscreen && isPortraitLive -> LiveRequestedOrientationMode.SensorPortrait
        isFullscreen -> LiveRequestedOrientationMode.SensorLandscape
        else -> LiveRequestedOrientationMode.Portrait
    }
}

fun resolveLiveRequestedOrientationMode(
    isTabletDevice: Boolean,
    isFullscreen: Boolean,
    isFoldableCoverWindow: Boolean = false,
    usesInWindowFullscreen: Boolean = false,
    isPortraitLive: Boolean = false,
): LiveRequestedOrientationMode {
    return when {
        usesInWindowFullscreen -> LiveRequestedOrientationMode.Unspecified
        isTabletDevice && !isFoldableCoverWindow -> LiveRequestedOrientationMode.Unspecified
        isFullscreen && isPortraitLive -> LiveRequestedOrientationMode.SensorPortrait
        isFullscreen -> LiveRequestedOrientationMode.SensorLandscape
        else -> LiveRequestedOrientationMode.Portrait
    }
}

fun resolveLiveRoomLayoutMode(
    isLandscape: Boolean,
    isTablet: Boolean,
    isFullscreen: Boolean,
    isPortraitLive: Boolean,
    hasObstructingHinge: Boolean = false,
): LiveRoomLayoutMode {
    if (hasObstructingHinge) return if (isFullscreen) LiveRoomLayoutMode.LandscapeOverlay else LiveRoomLayoutMode.LandscapeSplit
    return when {
        isFullscreen && isPortraitLive && !isLandscape -> LiveRoomLayoutMode.PortraitVerticalOverlay
        isFullscreen -> LiveRoomLayoutMode.LandscapeOverlay
        isLandscape -> LiveRoomLayoutMode.LandscapeSplit
        isPortraitLive -> LiveRoomLayoutMode.PortraitVerticalOverlay
        else -> LiveRoomLayoutMode.PortraitPanel
    }
}

fun shouldShowLiveChatToggle(
    layoutMode: LiveRoomLayoutMode
): Boolean {
    // LandscapeSplit 的聊天列改为跟随用户开关，需提供切换按钮。
    return layoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay ||
        layoutMode == LiveRoomLayoutMode.LandscapeOverlay ||
        layoutMode == LiveRoomLayoutMode.LandscapeSplit
}

fun defaultLiveInteractionPanelVisible(): Boolean = true

fun shouldShowLiveSplitChatPanel(
    layoutMode: LiveRoomLayoutMode,
    isInteractionPanelVisible: Boolean
): Boolean {
    // 普通横屏默认展示完整聊天列，用户可从更多菜单收起。
    return layoutMode == LiveRoomLayoutMode.LandscapeSplit && isInteractionPanelVisible
}

/** PiliPlus desktop: video ~56–70% width, remaining chat column capped at 400dp. */
internal const val LIVE_SPLIT_CHAT_MAX_WIDTH_DP = 400
internal const val LIVE_SPLIT_CHAT_MIN_WIDTH_DP = 280
internal const val LIVE_SPLIT_VIDEO_MIN_FRACTION = 0.56f
internal const val LIVE_SPLIT_VIDEO_MAX_FRACTION = 0.70f
internal const val LIVE_SPLIT_VIDEO_PREFERRED_FRACTION = 0.62f

internal fun resolveLiveSplitChatPanelWidthDp(
    screenWidthDp: Int,
    contentPaddingDp: Int = 24,
    screenHeightDp: Int? = null,
): Int {
    val available = (screenWidthDp - contentPaddingDp).coerceAtLeast(1)
    val videoFraction = screenHeightDp?.let { (it.toFloat() / screenWidthDp.coerceAtLeast(1) * 1.08f).coerceIn(LIVE_SPLIT_VIDEO_MIN_FRACTION, LIVE_SPLIT_VIDEO_MAX_FRACTION) } ?: LIVE_SPLIT_VIDEO_PREFERRED_FRACTION
    val preferredVideo = (available * videoFraction).roundToInt()
        .coerceIn(
            (available * LIVE_SPLIT_VIDEO_MIN_FRACTION).roundToInt(),
            (available * LIVE_SPLIT_VIDEO_MAX_FRACTION).roundToInt(),
        )
    val remaining = (available - preferredVideo).coerceAtLeast(0)
    return remaining.coerceAtMost(LIVE_SPLIT_CHAT_MAX_WIDTH_DP)
        .coerceAtLeast(minOf(LIVE_SPLIT_CHAT_MIN_WIDTH_DP, remaining))
}

fun shouldShowLiveLandscapeChatOverlay(
    layoutMode: LiveRoomLayoutMode,
    isInteractionPanelVisible: Boolean
): Boolean {
    return false // Fullscreen hides ordinary chat, matching PiliPlus.
}

fun shouldReserveLivePortraitInteractionPanel(
    layoutMode: LiveRoomLayoutMode,
    isInteractionPanelVisible: Boolean
): Boolean {
    return layoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay && isInteractionPanelVisible
}

fun shouldApplyLiveTopControlSystemInsets(
    layoutMode: LiveRoomLayoutMode,
    isFullscreen: Boolean
): Boolean {
    return isFullscreen ||
        layoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay ||
        layoutMode == LiveRoomLayoutMode.LandscapeOverlay
}

fun shouldApplyLiveBottomControlSystemInsets(
    layoutMode: LiveRoomLayoutMode,
    isFullscreen: Boolean,
    hasReservedBottomOverlay: Boolean
): Boolean {
    if (hasReservedBottomOverlay) return false
    return isFullscreen ||
        layoutMode == LiveRoomLayoutMode.PortraitVerticalOverlay ||
        layoutMode == LiveRoomLayoutMode.LandscapeOverlay
}

fun shouldShowLivePlayerControlsTopBar(
    layoutMode: LiveRoomLayoutMode,
    isFullscreen: Boolean
): Boolean {
    if (isFullscreen) return true
    return layoutMode == LiveRoomLayoutMode.LandscapeOverlay
}

fun resolveLivePortraitOverlayMetrics(
    screenHeightDp: Int
): LivePortraitOverlayMetrics {
    val compactHeight = screenHeightDp < 720
    return LivePortraitOverlayMetrics(
        panelHeightFraction = 0.32f,
        minPanelHeightDp = if (compactHeight) 144 else 180,
        minPlayerClearanceDp = if (compactHeight) 260 else 320,
        playerControlsGapDp = 10,
        topChromeReserveDp = if (compactHeight) 86 else 96,
        playerControlsReserveDp = if (compactHeight) 68 else 74
    )
}

fun resolveLivePortraitOverlayPanelHeightDp(
    screenHeightDp: Int,
    metrics: LivePortraitOverlayMetrics
): Int {
    val maxPanelHeight = (screenHeightDp - metrics.minPlayerClearanceDp)
        .coerceAtLeast(0)
    val lowerBound = metrics.minPanelHeightDp.coerceAtMost(maxPanelHeight)
    val preferredHeight = (screenHeightDp * metrics.panelHeightFraction).roundToInt()
    return preferredHeight.coerceIn(lowerBound, maxPanelHeight)
}

fun resolveLiveOverlayContentInsets(
    layoutMode: LiveRoomLayoutMode,
    portraitPanelHeightDp: Int,
    portraitMetrics: LivePortraitOverlayMetrics,
    isInteractionPanelVisible: Boolean
): LiveOverlayContentInsets {
    if (layoutMode != LiveRoomLayoutMode.PortraitVerticalOverlay) {
        return LiveOverlayContentInsets(topDp = 0, bottomDp = 0)
    }
    val interactionPanelReserveDp = if (isInteractionPanelVisible) {
        portraitPanelHeightDp
    } else {
        0
    }
    return LiveOverlayContentInsets(
        topDp = portraitMetrics.topChromeReserveDp,
        bottomDp = interactionPanelReserveDp +
            portraitMetrics.playerControlsGapDp +
            portraitMetrics.playerControlsReserveDp
    )
}

fun resolveLiveLandscapeChatOverlayMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int
): LiveLandscapeChatOverlayMetrics {
    val compactHeight = screenHeightDp < 420
    val compactWidth = screenWidthDp < 700
    return LiveLandscapeChatOverlayMetrics(
        widthFraction = if (compactWidth) 0.42f else 0.34f,
        heightFraction = if (compactHeight) 0.46f else 0.54f,
        minWidthDp = if (compactWidth) 220 else 260,
        maxWidthDp = 360,
        minHeightDp = if (compactHeight) 132 else 180,
        edgePaddingDp = 16,
        topControlReserveDp = if (compactHeight) 76 else 86,
        bottomControlReserveDp = if (compactHeight) 92 else 98
    )
}

fun resolveLiveLandscapeChatOverlayWidthDp(
    screenWidthDp: Int,
    metrics: LiveLandscapeChatOverlayMetrics
): Int {
    val maxAvailableWidth = (screenWidthDp - metrics.edgePaddingDp * 2)
        .coerceAtLeast(0)
    val upperBound = minOf(metrics.maxWidthDp, maxAvailableWidth)
    val lowerBound = metrics.minWidthDp.coerceAtMost(upperBound)
    val preferredWidth = (maxAvailableWidth * metrics.widthFraction).roundToInt()
    return preferredWidth.coerceIn(lowerBound, upperBound)
}

fun resolveLiveLandscapeChatOverlayHeightDp(
    screenHeightDp: Int,
    metrics: LiveLandscapeChatOverlayMetrics
): Int {
    val maxAvailableHeight = (
        screenHeightDp -
            metrics.topControlReserveDp -
            metrics.bottomControlReserveDp
        ).coerceAtLeast(0)
    val lowerBound = metrics.minHeightDp.coerceAtMost(maxAvailableHeight)
    val preferredHeight = (screenHeightDp * metrics.heightFraction).roundToInt()
    return preferredHeight.coerceIn(lowerBound, maxAvailableHeight)
}

fun formatLiveDuration(
    liveStartTimeSeconds: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    if (liveStartTimeSeconds <= 0L || nowMillis <= liveStartTimeSeconds * 1000L) {
        return ""
    }
    val totalMinutes = ((nowMillis - liveStartTimeSeconds * 1000L) / 60_000L).coerceAtLeast(0L)
    if (totalMinutes <= 0L) return "刚刚开播"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return buildString {
        append("开播")
        if (hours > 0L) append(hours).append("小时")
        if (minutes > 0L || hours == 0L) append(minutes).append("分钟")
    }
}

fun formatLiveViewerCount(count: Int): String {
    return when {
        count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000f)
        count >= 10_000 -> "%.1f万".format(count / 10_000f)
        count > 0 -> count.toString()
        else -> "-"
    }
}
