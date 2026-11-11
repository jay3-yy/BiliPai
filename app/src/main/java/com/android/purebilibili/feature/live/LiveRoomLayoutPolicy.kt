package com.android.purebilibili.feature.live
import android.content.pm.ActivityInfo
import com.android.purebilibili.core.util.AppDisplayContext
import com.android.purebilibili.core.util.shouldUsePhonePlayerOrientation
import kotlin.math.roundToInt
import com.android.purebilibili.core.util.FormatUtils

internal const val LIVE_INPUT_HEIGHT_DP = 70
internal const val LIVE_CHAT_INPUT_GAP_DP = 55

enum class LiveRoomLayoutMode {
    PortraitPanel,
    PortraitVerticalOverlay,
    LandscapeSplit,
    LandscapeOverlay
}

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
        // 手机系统小窗仍提交方向请求，由窗口管理器决定窗口形状；不因多窗口状态丢失全屏方向。
        displayContext.usesInWindowFullscreen -> LiveRequestedOrientationMode.Unspecified
        !shouldUsePhonePlayerOrientation(displayContext) -> LiveRequestedOrientationMode.Unspecified
        isFullscreen && isPortraitLive -> LiveRequestedOrientationMode.SensorPortrait
        isFullscreen -> LiveRequestedOrientationMode.SensorLandscape
        else -> LiveRequestedOrientationMode.Portrait
    }
}

internal fun LiveRequestedOrientationMode.toActivityOrientation(): Int = when (this) {
    LiveRequestedOrientationMode.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    LiveRequestedOrientationMode.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    LiveRequestedOrientationMode.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    LiveRequestedOrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

/** 方向驱动的全屏先等待窗口到达目标方向，避免在旋转前先铺满竖屏；窗口内全屏直接呈现。 */
internal fun shouldPresentLiveFullscreen(
    fullscreenRequested: Boolean,
    isLandscape: Boolean,
    orientationMode: LiveRequestedOrientationMode,
): Boolean = fullscreenRequested && when (orientationMode) {
    LiveRequestedOrientationMode.SensorLandscape -> isLandscape
    LiveRequestedOrientationMode.SensorPortrait,
    LiveRequestedOrientationMode.Portrait -> !isLandscape
    LiveRequestedOrientationMode.Unspecified -> true
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
    // Hinge panes provide the interaction area even when the window is portrait or compact.
    if (hasObstructingHinge) {
        return if (isFullscreen) LiveRoomLayoutMode.LandscapeOverlay else LiveRoomLayoutMode.LandscapeSplit
    }
    return when {
        isFullscreen && isPortraitLive && !isLandscape -> LiveRoomLayoutMode.PortraitVerticalOverlay
        isFullscreen -> LiveRoomLayoutMode.LandscapeOverlay
        isLandscape -> LiveRoomLayoutMode.LandscapeSplit
        isPortraitLive -> LiveRoomLayoutMode.PortraitVerticalOverlay
        else -> LiveRoomLayoutMode.PortraitPanel
    }
}

/** 横向窗口保留右侧互动区，视频占比随窗口宽高变化，互动区最多 400dp。 */
internal const val LIVE_SPLIT_CHAT_MAX_WIDTH_DP = 400
internal const val LIVE_SPLIT_VIDEO_MIN_FRACTION = 0.56f
internal const val LIVE_SPLIT_VIDEO_MAX_FRACTION = 0.70f

internal fun resolveLiveSplitChatPanelWidthDp(
    screenWidthDp: Int,
    contentPaddingDp: Int = 0,
    screenHeightDp: Int = 600,
): Int {
    val available = (screenWidthDp - contentPaddingDp).coerceAtLeast(1)
    val videoFraction = (screenHeightDp.toFloat() / available * 1.08f)
        .coerceIn(LIVE_SPLIT_VIDEO_MIN_FRACTION, LIVE_SPLIT_VIDEO_MAX_FRACTION)
    return (available * (1f - videoFraction)).roundToInt().coerceAtMost(LIVE_SPLIT_CHAT_MAX_WIDTH_DP)
}

fun shouldApplyLiveTopControlSystemInsets(
    layoutMode: LiveRoomLayoutMode,
    isFullscreen: Boolean
): Boolean {
    return isFullscreen
}

fun shouldApplyLiveBottomControlSystemInsets(
    layoutMode: LiveRoomLayoutMode,
    isFullscreen: Boolean,
    hasReservedBottomOverlay: Boolean
): Boolean {
    return isFullscreen
}

fun shouldShowLivePlayerControlsTopBar(
    layoutMode: LiveRoomLayoutMode,
    isFullscreen: Boolean
): Boolean {
    return true
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
    // 0 表示人数未知/未开播，与真实零值区分，保留 "-" 占位。
    if (count <= 0) return "-"
    return FormatUtils.formatStat(count.toLong())
}

/** 未就绪的尺寸不覆盖详情方向；像素宽高比参与实际画面方向判断。 */
internal fun resolveLiveVideoIsPortrait(width: Int, height: Int, pixelRatio: Float = 1f): Boolean? {
    if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0f) return null
    return width * pixelRatio < height
}
