package com.android.purebilibili.feature.download

import android.content.pm.ActivityInfo
import androidx.media3.common.Player
import com.android.purebilibili.core.store.FullscreenMode
import com.android.purebilibili.core.util.AppDisplayContext
import com.android.purebilibili.core.util.shouldUsePhonePlayerOrientation
import com.android.purebilibili.feature.video.screen.resolvePhoneFullscreenEnterOrientation

internal enum class OfflineRequestedOrientationMode {
    Unspecified,
    SensorLandscape,
    Portrait,
    KeepCurrent,
}

internal fun resolveOfflineRequestedOrientationMode(
    isFullscreen: Boolean,
    usesInWindowFullscreen: Boolean,
    isPhonePlayerOrientation: Boolean = true,
    fullscreenMode: FullscreenMode = FullscreenMode.AUTO,
    isVerticalVideo: Boolean = false,
): OfflineRequestedOrientationMode = when {
    usesInWindowFullscreen || !isPhonePlayerOrientation ->
        OfflineRequestedOrientationMode.Unspecified
    !isFullscreen -> OfflineRequestedOrientationMode.Portrait
    else -> when (resolvePhoneFullscreenEnterOrientation(fullscreenMode, isVerticalVideo)) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> OfflineRequestedOrientationMode.Portrait
        null -> OfflineRequestedOrientationMode.KeepCurrent
        else -> OfflineRequestedOrientationMode.SensorLandscape
    }
}

internal fun resolveOfflineRequestedOrientationMode(
    isFullscreen: Boolean,
    displayContext: AppDisplayContext,
    fullscreenMode: FullscreenMode,
    isVerticalVideo: Boolean,
): OfflineRequestedOrientationMode = resolveOfflineRequestedOrientationMode(
    isFullscreen = isFullscreen,
    usesInWindowFullscreen = displayContext.usesInWindowFullscreen,
    isPhonePlayerOrientation = shouldUsePhonePlayerOrientation(displayContext),
    fullscreenMode = fullscreenMode,
    isVerticalVideo = isVerticalVideo,
)

internal fun shouldResumePlaybackAfterOfflineSeek(
    playbackState: Int,
    wasPlayingBeforeSeek: Boolean,
    targetPositionMs: Long,
    durationMs: Long
): Boolean {
    if (targetPositionMs < 0L) return false
    val cappedDurationMs = durationMs.coerceAtLeast(0L)
    return wasPlayingBeforeSeek || (
        playbackState == Player.STATE_ENDED &&
            (cappedDurationMs <= 0L || targetPositionMs < cappedDurationMs)
        )
}

internal fun resolveOfflinePersistedPlaybackPosition(
    currentPositionMs: Long,
    durationMs: Long
): Long {
    val safeCurrent = currentPositionMs.coerceAtLeast(0L)
    val safeDuration = durationMs.coerceAtLeast(0L)
    if (safeDuration > 0L && safeCurrent >= safeDuration - 1_500L) {
        return 0L
    }
    return safeCurrent
}

internal fun shouldShowOfflineDanmakuControl(
    localSegmentCount: Int,
    isAudioOnly: Boolean
): Boolean = localSegmentCount > 0 && !isAudioOnly

internal fun shouldShowOfflineDanmakuLayer(
    localSegmentCount: Int,
    isAudioOnly: Boolean,
    danmakuEnabled: Boolean
): Boolean {
    return danmakuEnabled && shouldShowOfflineDanmakuControl(
        localSegmentCount = localSegmentCount,
        isAudioOnly = isAudioOnly
    )
}

internal fun resolveOfflineSeekProgressFromTouch(
    touchX: Float,
    containerWidthPx: Float
): Float {
    if (!touchX.isFinite() || !containerWidthPx.isFinite() || containerWidthPx <= 0f) {
        return 0f
    }
    return (touchX / containerWidthPx).coerceIn(0f, 1f)
}

internal fun resolveOfflineSeekPositionFromTouch(
    touchX: Float,
    containerWidthPx: Float,
    durationMs: Long
): Long {
    val safeDuration = durationMs.coerceAtLeast(0L)
    return (resolveOfflineSeekProgressFromTouch(touchX, containerWidthPx) * safeDuration).toLong()
}
