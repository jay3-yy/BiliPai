@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.ui.section

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent
import com.android.purebilibili.feature.video.ui.components.GesturePercentMotionDefaults
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.resolveDanmakuSettingsScope
import com.android.purebilibili.feature.video.playback.session.PlaybackSeekSessionState
import com.android.purebilibili.feature.video.playback.session.shouldUsePlaybackSeekSessionPosition
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val INITIAL_PLAYER_CONTROLS_VISIBLE = false
internal const val INITIAL_PLAYER_CHROME_AUTO_HIDE_HANDLED = true

internal fun resolvePlayerInteractionIdentity(
    routeBvid: String,
    playbackBvid: String?,
    playbackCid: Long?
): String {
    val effectiveBvid = playbackBvid?.takeIf { it.isNotBlank() } ?: routeBvid
    return "${effectiveBvid}_${playbackCid?.coerceAtLeast(0L) ?: 0L}"
}

enum class VideoGestureMode { None, Brightness, Volume, Seek, SwipeToFullscreen }

private const val PLAYER_DRAG_GESTURE_BOTTOM_EXCLUSION_BUFFER_DP = 12
private const val PLAYBACK_STALL_LOG_THRESHOLD_MS = 700L
private const val VIDEO_PLAYER_COVER_FADE_ENTER_DURATION_MILLIS = 200
// 封面退出时长不再固定：由 surface 淡入时长派生，保证两者同一条时间轴（见
// resolveVideoPlayerCoverMotionSpec）。历史上这里是 300ms，比 surface 的 140ms 慢，
// 中间的「已不透明 surface + 半透明封面」窗口就是进场时视频位置「叠一层黑」的来源。
private const val VIDEO_PLAYER_COVER_REVEAL_HOLD_DELAY_MILLIS = 0
private const val VIDEO_PLAYER_SURFACE_REVEAL_DURATION_MILLIS = 140
// The card already owns spatial motion; revealing playback must not add another zoom.
private const val VIDEO_PLAYER_SURFACE_REVEAL_INITIAL_SCALE = 1f
// 揭开动画结束后再延迟一小段才允许移除封面垫底，吸收 animateFloatAsState
// 晚一帧启动的相位差，确保移除瞬间视频 surface 已完全不透明。
private const val VIDEO_PLAYER_COVER_REVEAL_SETTLE_BUFFER_MILLIS = 48
private const val LONG_PRESS_SPEED_UNLOCK_HOLD_MS = 1_000L

internal const val LONG_PRESS_SPEED_LOCK_ZONE_HEIGHT_DP = 96
internal const val FOREGROUND_SURFACE_RECOVERY_DELAY_MS = 80L
internal const val FOREGROUND_SURFACE_RECOVERY_TIMEOUT_MS = 1200L

/**
 * Shared card-return 期间，播放器内层封面必须与外层 shared shell 使用同一来源卡圆角。
 *
 * 否则外层 overlay 已按来源卡裁切、内层封面仍按播放器圆角裁切，卸层落位时会发生
 * 一帧的双重形变，看起来像封面轻微抖动。
 */
internal fun resolveVideoPlayerCoverCornerDp(
    sourceCornerDp: Int,
    playerCornerDp: Int,
    preserveSourceCardCornerDuringSharedReturn: Boolean,
): Int {
    return if (preserveSourceCardCornerDuringSharedReturn) {
        sourceCornerDp.coerceAtLeast(0)
    } else {
        playerCornerDp.coerceAtLeast(0)
    }
}

internal data class LongPressSpeedLockSensitivityPolicy(
    val lockZoneHeightDp: Int,
    val minDragDistanceDp: Int
)

internal data class LongPressSpeedLockZoneVisualPolicy(
    val zoneFillAlpha: Float,
    val borderAlpha: Float,
    val edgeGradientAlpha: Float,
    val centerMarkerAlpha: Float,
    val edgeGradientHeightDp: Int,
    val centerMarkerHeightDp: Int,
    val centerMarkerWidthFraction: Float,
    val bottomVisualOffsetDp: Int
)

internal data class LongPressSpeedStartDecision(
    val originalPlaybackParameters: PlaybackParameters,
    val targetPlaybackParameters: PlaybackParameters,
    val clearExistingLock: Boolean
)

internal fun resolveLongPressSpeedLockZoneVisualPolicy(): LongPressSpeedLockZoneVisualPolicy {
    return LongPressSpeedLockZoneVisualPolicy(
        zoneFillAlpha = 0f,
        borderAlpha = 0f,
        edgeGradientAlpha = 0.24f,
        centerMarkerAlpha = 0.68f,
        edgeGradientHeightDp = 16,
        centerMarkerHeightDp = 4,
        centerMarkerWidthFraction = 0.22f,
        bottomVisualOffsetDp = 0
    )
}

internal fun resolveSubtitleBottomOffsetPx(
    isFullscreen: Boolean,
    controlsVisible: Boolean,
    positionLocked: Boolean = false,
    navigationInsetPx: Int,
    bottomControlsHeightPx: Int,
    density: Float
): Int {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    fun dp(value: Int): Int = (value * safeDensity).roundToInt()
    if (!isFullscreen) return dp(32)
    val safeInset = navigationInsetPx.coerceAtLeast(0)
    return if (controlsVisible && !positionLocked) {
        maxOf(safeInset + bottomControlsHeightPx.coerceAtLeast(0) + dp(8), dp(56))
    } else {
        maxOf(safeInset + dp(16), dp(24))
    }
}

internal fun resolveLongPressSpeedLockSensitivityPolicy(
    isFullscreen: Boolean
): LongPressSpeedLockSensitivityPolicy {
    return if (isFullscreen) {
        LongPressSpeedLockSensitivityPolicy(
            lockZoneHeightDp = LONG_PRESS_SPEED_LOCK_ZONE_HEIGHT_DP,
            minDragDistanceDp = 24
        )
    } else {
        LongPressSpeedLockSensitivityPolicy(
            lockZoneHeightDp = 36,
            minDragDistanceDp = 72
        )
    }
}

internal fun resolveGestureSeekableDurationMs(
    playbackDurationMs: Long,
    fallbackDurationMs: Long
): Long {
    return if (playbackDurationMs > 0L) {
        playbackDurationMs
    } else {
        fallbackDurationMs.coerceAtLeast(0L)
    }
}

internal fun shouldKeepVideoPlaybackAwake(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int
): Boolean {
    if (!playWhenReady) return false
    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) return false
    return isPlaying || playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY
}

internal fun resolveVideoPlayerBottomGestureExclusionHeightDp(
    controlBarBottomPaddingDp: Int,
    progressSpacingDp: Int,
    progressContainerHeightDp: Int,
    controlRowHeightDp: Int,
    extraBufferDp: Int = PLAYER_DRAG_GESTURE_BOTTOM_EXCLUSION_BUFFER_DP
): Int {
    return (
        controlBarBottomPaddingDp +
            progressSpacingDp +
            progressContainerHeightDp +
            controlRowHeightDp +
            extraBufferDp
        ).coerceAtLeast(0)
}

internal fun shouldIgnoreVideoPlayerDragStart(
    offsetY: Float,
    containerHeightPx: Float,
    topGestureExclusionPx: Float,
    bottomGestureExclusionPx: Float
): Boolean {
    if (containerHeightPx <= 0f) return false
    val clampedTopExclusionPx = topGestureExclusionPx.coerceIn(0f, containerHeightPx)
    val clampedBottomExclusionPx = bottomGestureExclusionPx.coerceIn(0f, containerHeightPx)
    return offsetY < clampedTopExclusionPx ||
        offsetY >= (containerHeightPx - clampedBottomExclusionPx)
}

internal fun shouldIgnoreVideoPlayerHorizontalEdgeDragStart(
    offsetX: Float,
    containerWidthPx: Float,
    isFullscreen: Boolean,
    edgeGestureExclusionPx: Float,
): Boolean {
    if (!isFullscreen || containerWidthPx <= 0f) return false
    val exclusion = edgeGestureExclusionPx.coerceIn(0f, containerWidthPx / 2f)
    return offsetX < exclusion || offsetX >= containerWidthPx - exclusion
}

internal data class VideoPlayerGestureVerticalExclusions(
    val topPx: Float,
    val bottomPx: Float
)

internal fun resolveVideoPlayerGestureVerticalExclusions(
    containerHeightPx: Float,
    isFullscreen: Boolean,
    controlsVisible: Boolean,
    requestedBottomControlsExclusionPx: Float,
    inlineTopExclusionPx: Float,
    inlineBottomExclusionPx: Float,
    fullscreenEdgeExclusionPx: Float
): VideoPlayerGestureVerticalExclusions {
    if (containerHeightPx <= 0f) {
        return VideoPlayerGestureVerticalExclusions(0f, 0f)
    }
    val requestedTop = if (isFullscreen) fullscreenEdgeExclusionPx else inlineTopExclusionPx
    val requestedBottom = when {
        isFullscreen && controlsVisible ->
            maxOf(fullscreenEdgeExclusionPx, requestedBottomControlsExclusionPx)
        isFullscreen -> fullscreenEdgeExclusionPx
        else -> inlineBottomExclusionPx
    }
    val maximumCombinedExclusion = containerHeightPx * 0.5f
    val combined = (requestedTop + requestedBottom).coerceAtLeast(0f)
    if (combined <= maximumCombinedExclusion || combined <= 0f) {
        return VideoPlayerGestureVerticalExclusions(requestedTop, requestedBottom)
    }
    val scale = maximumCombinedExclusion / combined
    return VideoPlayerGestureVerticalExclusions(
        topPx = requestedTop * scale,
        bottomPx = requestedBottom * scale
    )
}

internal fun resolveEffectivePlaybackSpeed(
    requestedSpeed: Float,
    currentAudioQuality: Int
): Float {
    return requestedSpeed.coerceAtLeast(0.1f)
}

internal fun resolveSpeedSafePlaybackParameters(
    requestedSpeed: Float,
    currentAudioQuality: Int
): PlaybackParameters {
    return PlaybackParameters(
        resolveEffectivePlaybackSpeed(
            requestedSpeed = requestedSpeed,
            currentAudioQuality = currentAudioQuality
        ),
        1.0f
    )
}

internal fun resolveEffectiveLongPressSpeed(
    requestedSpeed: Float,
    currentAudioQuality: Int
): Float {
    return resolveEffectivePlaybackSpeed(
        requestedSpeed = requestedSpeed,
        currentAudioQuality = currentAudioQuality
    )
}

internal fun resolveLongPressPlaybackParameters(
    requestedSpeed: Float,
    currentAudioQuality: Int
): PlaybackParameters {
    return resolveSpeedSafePlaybackParameters(
        requestedSpeed = requestedSpeed,
        currentAudioQuality = currentAudioQuality
    )
}

internal fun resolveLongPressSpeedStartDecision(
    currentPlaybackParameters: PlaybackParameters,
    previousOriginalPlaybackParameters: PlaybackParameters,
    longPressSpeedLocked: Boolean,
    requestedSpeed: Float,
    currentAudioQuality: Int
): LongPressSpeedStartDecision {
    return LongPressSpeedStartDecision(
        originalPlaybackParameters = if (longPressSpeedLocked) {
            previousOriginalPlaybackParameters
        } else {
            currentPlaybackParameters
        },
        targetPlaybackParameters = resolveLongPressPlaybackParameters(
            requestedSpeed = requestedSpeed,
            currentAudioQuality = currentAudioQuality
        ),
        clearExistingLock = false
    )
}

internal fun shouldShowHiResLongPressCompatHint(
    requestedSpeed: Float,
    effectiveSpeed: Float,
    hasShownHint: Boolean
): Boolean {
    if (hasShownHint) return false
    return requestedSpeed - effectiveSpeed > 0.001f
}

internal fun shouldShowLongPressSpeedLockHint(
    hintRequested: Boolean,
    isLongPressing: Boolean,
    isInPipMode: Boolean,
): Boolean {
    return hintRequested && !isLongPressing && !isInPipMode
}

internal fun shouldShowLongPressSpeedFeedback(
    isLongPressing: Boolean,
    isPlaybackSurfaceActive: Boolean,
    hintDismissed: Boolean,
    hintHidden: Boolean,
): Boolean = isLongPressing && isPlaybackSurfaceActive && !hintDismissed && !hintHidden

/**
 * 长按倍速浮层上的关闭（×）按钮。
 *
 * 默认关闭：第二指点 × 会打断长按手势（多点触控/抬手），容易把加速弄没。
 * 该开关不进设置页 UI，仅作隐藏偏好（adb / 备份 / 调试）。
 */
internal fun shouldShowLongPressSpeedHintCloseButton(
    closeButtonEnabled: Boolean,
): Boolean = closeButtonEnabled

internal fun shouldEnableLongPressSpeedGesture(
    isScreenLocked: Boolean,
    scale: Float,
    isMultiTouchActive: Boolean
): Boolean {
    return !isScreenLocked && !isMultiTouchActive && scale <= 1.01f
}

internal fun shouldEnableViewportTransformGesture(
    isScreenLocked: Boolean,
    isFullscreen: Boolean,
    isPortraitFullscreen: Boolean,
    isVerticalVideo: Boolean,
): Boolean {
    return !isScreenLocked && (isFullscreen || isPortraitFullscreen || isVerticalVideo)
}

fun resolveSystemStreamVolumeFromGesture(
    startVolumeStep: Int,
    maxVolumeStep: Int,
    totalDragDistanceY: Float,
    screenHeightPx: Float,
    gestureSensitivity: Float
): Int {
    if (maxVolumeStep <= 0 || screenHeightPx <= 0f) return 0
    val deltaStep = (-totalDragDistanceY / screenHeightPx * maxVolumeStep * gestureSensitivity)
        .roundToInt()
    return (startVolumeStep + deltaStep).coerceIn(0, maxVolumeStep)
}

internal fun shouldTriggerPinchExitFullscreen(
    isFullscreen: Boolean,
    isScreenLocked: Boolean,
    twoFingerSpeedAxisLocked: Boolean,
    currentViewportScale: Float,
    cumulativeZoom: Float,
    minExitZoom: Float = 0.82f
): Boolean {
    if (!isFullscreen || isScreenLocked) return false
    if (twoFingerSpeedAxisLocked) return false
    if (currentViewportScale > 1.01f) return false
    return cumulativeZoom < minExitZoom.coerceIn(0.1f, 1.0f)
}

internal fun shouldLockLongPressSpeedInTargetZone(
    longPressSpeedLockEnabled: Boolean = true,
    isLongPressing: Boolean,
    alreadyLocked: Boolean,
    currentPointerY: Float,
    containerHeightPx: Float,
    lockZoneHeightPx: Float,
    accumulatedDragYPx: Float = 0f,
    minDragDistancePx: Float = 0f
): Boolean {
    if (!longPressSpeedLockEnabled) return false
    if (!isLongPressing || alreadyLocked) return false
    if (containerHeightPx <= 0f || lockZoneHeightPx <= 0f) return false
    if (abs(accumulatedDragYPx) < minDragDistancePx.coerceAtLeast(0f)) return false
    val clampedZoneHeightPx = lockZoneHeightPx.coerceAtMost(containerHeightPx * 0.25f)
    return currentPointerY <= clampedZoneHeightPx ||
        currentPointerY >= containerHeightPx - clampedZoneHeightPx
}

internal fun shouldConsumeExclusiveLongPressSpeedDrag(
    isLongPressing: Boolean,
    longPressSpeedLocked: Boolean
): Boolean {
    return isLongPressing && !longPressSpeedLocked
}

internal fun shouldBypassPlaybackSeekSessionProgressOverride(
    isLongPressing: Boolean,
    longPressSpeedLocked: Boolean
): Boolean {
    return isLongPressing || longPressSpeedLocked
}

internal fun resolveProgressDisplayOverridePositionMs(
    seekSession: PlaybackSeekSessionState,
    pendingPlaybackTransitionPositionMs: Long?,
    isLongPressing: Boolean,
    longPressSpeedLocked: Boolean
): Long? {
    if (shouldBypassPlaybackSeekSessionProgressOverride(isLongPressing, longPressSpeedLocked)) {
        return pendingPlaybackTransitionPositionMs
    }
    return if (shouldUsePlaybackSeekSessionPosition(seekSession)) {
        seekSession.sliderPositionMs
    } else {
        pendingPlaybackTransitionPositionMs
    }
}

internal fun resolveGestureSeekStartPositionMs(
    seekSession: PlaybackSeekSessionState,
    playbackPositionMs: Long
): Long {
    return if (seekSession.isSliderMoving) {
        seekSession.sliderPositionMs
    } else {
        playbackPositionMs.coerceAtLeast(0L)
    }
}

internal fun shouldUnlockLockedLongPressSpeedFromRightDownDrag(
    longPressSpeedLocked: Boolean,
    isLongPressing: Boolean,
    startX: Float,
    startY: Float,
    currentY: Float,
    containerWidthPx: Float,
    holdDurationMs: Long,
    minDownDragPx: Float,
    minHoldDurationMs: Long = LONG_PRESS_SPEED_UNLOCK_HOLD_MS
): Boolean {
    if (!longPressSpeedLocked || !isLongPressing) return false
    if (containerWidthPx <= 0f || startX < containerWidthPx * 0.5f) return false
    if (holdDurationMs < minHoldDurationMs.coerceAtLeast(0L)) return false
    return currentY - startY >= minDownDragPx.coerceAtLeast(0f)
}

internal fun shouldReapplyLockedLongPressSpeed(
    longPressSpeedLocked: Boolean,
    isLongPressing: Boolean,
    observedPlaybackSpeed: Float,
    lockedLongPressSpeed: Float
): Boolean {
    return longPressSpeedLocked &&
        !isLongPressing &&
        abs(observedPlaybackSpeed - lockedLongPressSpeed) > 0.001f
}

internal fun shouldClearLockedLongPressSpeedForExplicitSpeedChange(
    longPressSpeedLocked: Boolean,
    isLongPressing: Boolean
): Boolean {
    return longPressSpeedLocked && !isLongPressing
}

internal fun shouldRestorePlaybackParametersAfterLongPressRelease(
    wasLongPressing: Boolean,
    longPressSpeedLocked: Boolean,
    gestureEnded: Boolean
): Boolean {
    return gestureEnded && wasLongPressing && !longPressSpeedLocked
}

internal fun resolveVerticalGestureMode(
    isFullscreen: Boolean,
    isSwipeUp: Boolean,
    startX: Float,
    leftZoneEnd: Float,
    rightZoneStart: Float,
    portraitSwipeToFullscreenEnabled: Boolean,
    centerSwipeToFullscreenEnabled: Boolean,
    slideVolumeBrightnessEnabled: Boolean = true
): VideoGestureMode {
    if (!slideVolumeBrightnessEnabled && startX < leftZoneEnd) {
        return VideoGestureMode.None
    }
    if (!slideVolumeBrightnessEnabled && startX > rightZoneStart) {
        return VideoGestureMode.None
    }
    return when {
        startX < leftZoneEnd -> VideoGestureMode.Brightness
        startX > rightZoneStart -> VideoGestureMode.Volume
        else -> if (
            centerSwipeToFullscreenEnabled ||
            (!isFullscreen && portraitSwipeToFullscreenEnabled && isSwipeUp)
        ) {
            VideoGestureMode.SwipeToFullscreen
        } else {
            VideoGestureMode.None
        }
    }
}

/** Inline ambient capture stops while portrait fullscreen owns playback. */
internal fun shouldCaptureInlineStatusBarAmbientFrame(
    contentTopInsetPx: Float,
    isFullscreen: Boolean,
    isPortraitFullscreen: Boolean,
    isInPipMode: Boolean,
    hostLifecycleStarted: Boolean,
    statusBarHazeEnabled: Boolean,
): Boolean {
    return contentTopInsetPx > 0f &&
        !isFullscreen &&
        !isPortraitFullscreen &&
        !isInPipMode &&
        hostLifecycleStarted &&
        statusBarHazeEnabled
}

/** Inline player gestures yield touch to PortraitVideoPager during portrait fullscreen. */
internal fun shouldEnableInlinePlayerGestures(
    isPortraitFullscreen: Boolean,
): Boolean {
    return !isPortraitFullscreen
}

internal fun shouldShowDanmakuLayers(
    isInPipMode: Boolean,
    danmakuEnabled: Boolean,
    isPortraitFullscreen: Boolean,
    pipNoDanmakuEnabled: Boolean,
    hostLifecycleStarted: Boolean
): Boolean {
    if (!hostLifecycleStarted) return false
    if (!danmakuEnabled || isPortraitFullscreen) return false
    if (isInPipMode && pipNoDanmakuEnabled) return false
    return true
}

internal fun shouldPollVideoPlayerProgress(
    controlsVisible: Boolean,
    gestureVisible: Boolean,
    isSliderMoving: Boolean,
    hasPendingSeek: Boolean
): Boolean {
    return controlsVisible || gestureVisible || isSliderMoving || hasPendingSeek
}

/**
 * Fullscreen is not by itself an orientation. During the transition into or out of the
 * portrait-fullscreen experience both flags can briefly be true, but those frames must keep
 * reading and writing the portrait danmaku profile.
 */
internal fun resolveVideoPlayerDanmakuSettingsScope(
    isFullscreen: Boolean,
    isPortraitFullscreen: Boolean,
): DanmakuSettingsScope {
    return resolveDanmakuSettingsScope(
        isLandscape = isFullscreen && !isPortraitFullscreen
    )
}

/**
 * Portrait-only surface mode for detail player danmaku.
 * Landscape fullscreen always stays on the video viewport so horizontal playback is unchanged.
 */
internal fun shouldUseScreenTopDanmakuSurface(
    portraitDisplayAreaMode: com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode,
    isLandscapeFullscreen: Boolean
): Boolean {
    if (isLandscapeFullscreen) return false
    return portraitDisplayAreaMode ==
        com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode.SCREEN_TOP
}

internal fun resolveDanmakuLayerTopOffsetPx(
    isFullscreen: Boolean,
    statusBarHeightPx: Int,
    useScreenTopSurface: Boolean = false
): Int {
    // Screen-top mode can sit under status bar when chrome is hidden; keep a small inset.
    if (useScreenTopSurface && isFullscreen) {
        return statusBarHeightPx.coerceAtLeast(0)
    }
    return 0
}

internal fun resolveHorizontalSeekDeltaMs(
    isFullscreen: Boolean,
    fullscreenSwipeSeekEnabled: Boolean,
    totalDragDistanceX: Float,
    containerWidthPx: Float,
    fullscreenSwipeSeekSeconds: Int?,
    inlineSwipeSeekSeconds: Int,
    gestureSensitivity: Float
): Long? {
    if (isFullscreen && fullscreenSwipeSeekEnabled) {
        val seekSeconds = fullscreenSwipeSeekSeconds ?: return null
        if (seekSeconds == 0) return (totalDragDistanceX * 200f * gestureSensitivity).toLong()
        return resolveConfiguredSeekDeltaMs(
            totalDragDistanceX = totalDragDistanceX,
            containerWidthPx = containerWidthPx,
            seekSeconds = seekSeconds.coerceAtLeast(1),
            gestureSensitivity = gestureSensitivity
        )
    }
    if (!isFullscreen) {
        if (inlineSwipeSeekSeconds == 0) return (totalDragDistanceX * 200f * gestureSensitivity).toLong()
        return resolveConfiguredSeekDeltaMs(
            totalDragDistanceX = totalDragDistanceX,
            containerWidthPx = containerWidthPx,
            seekSeconds = inlineSwipeSeekSeconds.coerceIn(1, 120),
            gestureSensitivity = gestureSensitivity
        )
    }
    return (totalDragDistanceX * 200f * gestureSensitivity).toLong()
}

internal const val VIDEO_PLAYER_HORIZONTAL_SEEK_DOMINANCE_RATIO = 1.2f

internal fun shouldEngageHorizontalPlayerSeek(
    totalDragDistanceX: Float,
    totalDragDistanceY: Float,
): Boolean {
    val horizontalDistance = abs(totalDragDistanceX)
    val verticalDistance = abs(totalDragDistanceY)
    return horizontalDistance >= 1f &&
        horizontalDistance >= verticalDistance * VIDEO_PLAYER_HORIZONTAL_SEEK_DOMINANCE_RATIO
}

private fun resolveConfiguredSeekDeltaMs(
    totalDragDistanceX: Float,
    containerWidthPx: Float,
    seekSeconds: Int,
    gestureSensitivity: Float
): Long {
    val effectiveDragRangePx = (containerWidthPx * 0.5f).coerceAtLeast(1f)
    val maxDeltaMs = seekSeconds * 1000L
    val rawDeltaMs = (
        totalDragDistanceX / effectiveDragRangePx * maxDeltaMs * gestureSensitivity
    ).toLong()
    return rawDeltaMs.coerceIn(-maxDeltaMs, maxDeltaMs)
}

internal fun resolveRelativeSeekTargetPosition(
    currentPositionMs: Long,
    deltaMs: Long,
    durationMs: Long
): Long {
    val basePositionMs = currentPositionMs.coerceAtLeast(0L)
    val targetPositionMs = (basePositionMs + deltaMs).coerceAtLeast(0L)
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    return if (safeDurationMs > 0L) {
        targetPositionMs.coerceAtMost(safeDurationMs)
    } else {
        targetPositionMs
    }
}

/**
 * Seek 拖动的「松手取消」逃生口：手指拖入屏幕顶部左/右两个角落区域时取消本次 seek。
 * 角落取顶部 1/8 高度 × 左右各 1/8 宽度，与进度条拖动的常用轨迹（屏幕中部）不重叠。
 */
internal const val VIDEO_PLAYER_SEEK_CANCEL_EDGE_FRACTION = 0.125f

internal fun isInSeekCancelEscapeZone(
    positionX: Float,
    positionY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
): Boolean {
    if (containerWidthPx <= 0f || containerHeightPx <= 0f) return false
    val edgeX = containerWidthPx * VIDEO_PLAYER_SEEK_CANCEL_EDGE_FRACTION
    val topY = containerHeightPx * VIDEO_PLAYER_SEEK_CANCEL_EDGE_FRACTION
    val inCornerColumn = positionX <= edgeX || positionX >= containerWidthPx - edgeX
    return positionY <= topY && inCornerColumn
}

internal fun shouldCommitGestureSeek(
    currentPositionMs: Long,
    targetPositionMs: Long,
    minDeltaMs: Long = 800L
): Boolean {
    return abs(targetPositionMs - currentPositionMs) >= minDeltaMs
}

/**
 * Stepped haptics while scrubbing by horizontal seek:
 * fire once every [stepMs] of target position change (default 1s ticks).
 */
internal fun shouldTriggerSeekStepHaptic(
    previousTargetMs: Long,
    currentTargetMs: Long,
    stepMs: Long = 1_000L
): Boolean {
    if (stepMs <= 0L) return false
    if (previousTargetMs == currentTargetMs) return false
    return previousTargetMs / stepMs != currentTargetMs / stepMs
}

internal fun resolveOrientationSwitchHintText(isFullscreen: Boolean): String {
    return if (isFullscreen) "已切换到横屏" else "已切换到竖屏"
}

internal fun shouldTriggerFullscreenBySwipe(
    isFullscreen: Boolean,
    reverseGesture: Boolean,
    totalDragDistanceY: Float,
    thresholdPx: Float
): Boolean {
    if (thresholdPx <= 0f) return false
    val isSwipeUp = totalDragDistanceY < -thresholdPx
    val isSwipeDown = totalDragDistanceY > thresholdPx
    return if (!isFullscreen) {
        if (reverseGesture) isSwipeDown else isSwipeUp
    } else {
        if (reverseGesture) isSwipeUp else isSwipeDown
    }
}

/**
 * Vertical inline videos own a dedicated portrait-fullscreen pager. Routing their completed
 * upward gesture through the orientation fullscreen toggle can leave the activity in portrait
 * without ever activating that pager, so dispatch directly to the portrait callback.
 */
internal fun shouldEnterPortraitFullscreenFromSwipe(
    isFullscreen: Boolean,
    isVerticalVideo: Boolean,
): Boolean = !isFullscreen && isVerticalVideo

internal fun shouldAllowPlaybackStateAutoFullscreen(
    hasValidWindow: Boolean,
): Boolean {
    return hasValidWindow
}

internal fun shouldToggleAutoFullscreenForCurrentPlaybackSnapshot(
    autoEnterFullscreenEnabled: Boolean,
    autoExitFullscreenEnabled: Boolean,
    allowPlaybackStateAutoFullscreen: Boolean,
    playbackState: Int,
    playWhenReady: Boolean,
    hasAutoEnteredFullscreen: Boolean,
    isFullscreen: Boolean,
    willContinueToNextItem: Boolean = false,
    autoExitFullscreenMode: com.android.purebilibili.core.store.AutoExitFullscreenMode =
        if (autoExitFullscreenEnabled) {
            com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS
        } else {
            com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF
        },
): Boolean {
    // 这里的快照会在全屏/内嵌播放器切换时重新采样。若播放器仍处于
    // READY + playWhenReady，退出全屏后会把一次「重新组合」误认成一次
    // 「开始播放」，从而马上再次进入全屏。自动全屏只能由 Player 的实际
    // 状态事件处理，不能从当前快照补发。
    return false
}

internal fun shouldToggleAutoFullscreenForPlaybackEvent(
    autoEnterFullscreenEnabled: Boolean,
    autoExitFullscreenEnabled: Boolean,
    allowPlaybackStateAutoFullscreen: Boolean,
    playbackState: Int,
    playWhenReady: Boolean,
    hasAutoEnteredFullscreen: Boolean,
    isFullscreen: Boolean,
    previousPlayWhenReady: Boolean = playWhenReady,
    willContinueToNextItem: Boolean = false,
    autoExitFullscreenMode: com.android.purebilibili.core.store.AutoExitFullscreenMode =
        if (autoExitFullscreenEnabled) {
            com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS
        } else {
            com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF
        },
): Boolean {
    if (!allowPlaybackStateAutoFullscreen) return false

    val shouldEnterFullscreen =
        autoEnterFullscreenEnabled &&
            playbackState == Player.STATE_READY &&
            playWhenReady &&
            !hasAutoEnteredFullscreen &&
            !isFullscreen &&
            (!previousPlayWhenReady || playbackState == Player.STATE_READY)
    if (shouldEnterFullscreen) return true

    return shouldAutoExitFullscreenOnPlaybackEnded(
        mode = autoExitFullscreenMode,
        isFullscreen = isFullscreen,
        playbackState = playbackState,
        willContinueToNextItem = willContinueToNextItem,
    )
}

/**
 * 播放结束是否应退出全屏。
 * - OFF：不退出
 * - CURRENT_PART：当前分P/视频结束即退
 * - ALL_PARTS：仍有下一段（分P/合集/队列）可连播时保持全屏
 */
internal fun shouldAutoExitFullscreenOnPlaybackEnded(
    mode: com.android.purebilibili.core.store.AutoExitFullscreenMode,
    isFullscreen: Boolean,
    playbackState: Int,
    willContinueToNextItem: Boolean,
): Boolean {
    if (!isFullscreen || playbackState != Player.STATE_ENDED) return false
    return when (mode) {
        com.android.purebilibili.core.store.AutoExitFullscreenMode.OFF -> false
        com.android.purebilibili.core.store.AutoExitFullscreenMode.CURRENT_PART -> true
        com.android.purebilibili.core.store.AutoExitFullscreenMode.ALL_PARTS -> !willContinueToNextItem
    }
}

/**
 * 当前条目结束后是否还会自动切到下一段（分P / 合集 / 播放列表）。
 */
internal fun resolveWillContinuePlaybackAfterCurrentItem(
    pageCount: Int,
    currentPageIndex: Int,
    hasUgcSeasonNext: Boolean,
    hasPlaylistNext: Boolean,
    completionAdvancesToNext: Boolean,
): Boolean {
    if (!completionAdvancesToNext) return false
    val hasNextPage = pageCount > 1 &&
        currentPageIndex >= 0 &&
        currentPageIndex < pageCount - 1
    return hasNextPage || hasUgcSeasonNext || hasPlaylistNext
}

internal fun resolveGestureIndicatorLabel(mode: VideoGestureMode): String {
    return when (mode) {
        VideoGestureMode.Brightness -> "亮度"
        VideoGestureMode.Volume -> "音量"
        else -> ""
    }
}

/**
 * @deprecated Prefer [com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle].
 * Kept as a thin bridge for older call sites during the overlay redesign.
 */
enum class GestureLevelIconStyle {
    SharedMaterial,
    Md3,
    Ios,
    Miuix
}

internal fun resolveGestureLevelIconStyle(
    presentation: AppTopTabPresentation,
): GestureLevelIconStyle {
    return when (
        com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelOverlayStyle(
            presentation = presentation,
        )
    ) {
        com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle.Md3 ->
            GestureLevelIconStyle.Md3
        com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle.Ios ->
            GestureLevelIconStyle.Ios
        com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle.Miuix ->
            GestureLevelIconStyle.Miuix
    }
}

private fun GestureLevelIconStyle.toOverlayStyle():
    com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle {
    return when (this) {
        GestureLevelIconStyle.SharedMaterial,
        GestureLevelIconStyle.Md3 ->
            com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle.Md3
        GestureLevelIconStyle.Ios ->
            com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle.Ios
        GestureLevelIconStyle.Miuix ->
            com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle.Miuix
    }
}

internal fun resolveGestureDisplayIcon(
    mode: VideoGestureMode,
    percent: Float,
    fallbackIcon: ImageVector?,
    iconStyle: GestureLevelIconStyle = GestureLevelIconStyle.Md3
): ImageVector {
    val kind = com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelKind(mode)
        ?: return fallbackIcon
            ?: com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelIcon(
                style = iconStyle.toOverlayStyle(),
                kind = com.android.purebilibili.feature.video.ui.gesture.GestureLevelKind.Brightness,
                percent = 1f
            )
    return com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelIcon(
        style = iconStyle.toOverlayStyle(),
        kind = kind,
        percent = percent
    )
}

internal fun resolveVolumeGestureIcon(
    percent: Float,
    iconStyle: GestureLevelIconStyle
): ImageVector {
    return com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelIcon(
        style = iconStyle.toOverlayStyle(),
        kind = com.android.purebilibili.feature.video.ui.gesture.GestureLevelKind.Volume,
        percent = percent
    )
}

internal fun resolveBrightnessGestureIcon(
    percent: Float,
    iconStyle: GestureLevelIconStyle
): ImageVector {
    return com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelIcon(
        style = iconStyle.toOverlayStyle(),
        kind = com.android.purebilibili.feature.video.ui.gesture.GestureLevelKind.Brightness,
        percent = percent
    )
}

internal data class GestureLevelOverlayVisualPolicy(
    val accentColor: Color,
    val containerAlpha: Float,
    val borderAlpha: Float,
    val glowAlpha: Float
)

internal data class VideoGestureMotionSpec(
    val digitInitialBlurRadiusDp: Float,
    val digitInitialAlpha: Float,
    val digitBlurHoldDurationMillis: Int,
    val digitBlurResetDurationMillis: Int,
    val digitAlphaResetDurationMillis: Int,
    val digitEnterFadeDurationMillis: Int,
    val digitExitFadeDurationMillis: Int,
    val digitScaleDurationMillis: Int,
    val digitSlideSpringDampingRatio: Float,
    val digitSlideSpringStiffness: Float,
    val levelOverlayEnterFadeDurationMillis: Int,
    val levelOverlayEnterTransformDurationMillis: Int,
    val levelOverlayExitDurationMillis: Int,
    val levelProgressDurationMillis: Int,
    val levelIconScaleDurationMillis: Int,
    val levelValueScaleDurationMillis: Int,
    val levelIconEnterFadeDurationMillis: Int,
    val levelIconExitFadeDurationMillis: Int,
    val levelIconContentScaleDurationMillis: Int,
    val orientationHintEnterFadeDurationMillis: Int,
    val orientationHintEnterTransformDurationMillis: Int,
    val orientationHintExitDurationMillis: Int,
    val longPressHintDurationMillis: Int,
    val longPressArrowCycleDurationMillis: Int,
    val longPressArrowPhaseStepDurationMillis: Int
)

internal fun resolveVideoGestureMotionSpec(): VideoGestureMotionSpec {
    return VideoGestureMotionSpec(
        digitInitialBlurRadiusDp = GesturePercentMotionDefaults.InitialBlurRadiusDp,
        digitInitialAlpha = GesturePercentMotionDefaults.InitialAlpha,
        digitBlurHoldDurationMillis = GesturePercentMotionDefaults.BlurHoldDurationMillis,
        digitBlurResetDurationMillis = GesturePercentMotionDefaults.BlurResetDurationMillis,
        digitAlphaResetDurationMillis = GesturePercentMotionDefaults.AlphaResetDurationMillis,
        digitEnterFadeDurationMillis = GesturePercentMotionDefaults.EnterFadeDurationMillis,
        digitExitFadeDurationMillis = GesturePercentMotionDefaults.ExitFadeDurationMillis,
        digitScaleDurationMillis = 0,
        digitSlideSpringDampingRatio = GesturePercentMotionDefaults.SlideSpringDampingRatio,
        digitSlideSpringStiffness = GesturePercentMotionDefaults.SlideSpringStiffness,
        levelOverlayEnterFadeDurationMillis = 160,
        levelOverlayEnterTransformDurationMillis = 220,
        levelOverlayExitDurationMillis = 200,
        levelProgressDurationMillis = 130,
        levelIconScaleDurationMillis = 180,
        levelValueScaleDurationMillis = 140,
        levelIconEnterFadeDurationMillis = 120,
        levelIconExitFadeDurationMillis = 110,
        levelIconContentScaleDurationMillis = 180,
        orientationHintEnterFadeDurationMillis = 150,
        orientationHintEnterTransformDurationMillis = 230,
        orientationHintExitDurationMillis = 200,
        longPressHintDurationMillis = 200,
        longPressArrowCycleDurationMillis = 900,
        longPressArrowPhaseStepDurationMillis = 300
    )
}

internal fun resolveGestureRenderProgress(percent: Float): Float {
    return percent.coerceIn(0f, 1f)
}

internal fun resolveGestureLevelOverlayVisualPolicy(
    mode: VideoGestureMode,
    percent: Float
): GestureLevelOverlayVisualPolicy {
    val progress = resolveGestureRenderProgress(percent)
    return when (mode) {
        VideoGestureMode.Brightness -> GestureLevelOverlayVisualPolicy(
            accentColor = Color(0xFFFFD54F),
            // Dark scrim must stay opaque enough for white text on bright frames.
            containerAlpha = 0.70f + progress * 0.06f,
            borderAlpha = 0.48f + progress * 0.22f,
            glowAlpha = 0.30f + progress * 0.40f
        )

        VideoGestureMode.Volume -> GestureLevelOverlayVisualPolicy(
            accentColor = Color(0xFF80DEEA),
            containerAlpha = 0.70f + progress * 0.06f,
            borderAlpha = 0.46f + progress * 0.22f,
            glowAlpha = 0.28f + progress * 0.38f
        )

        else -> GestureLevelOverlayVisualPolicy(
            accentColor = Color.White,
            containerAlpha = 0.72f,
            borderAlpha = 0.50f,
            glowAlpha = 0.32f
        )
    }
}

internal fun resolveGesturePercentDigits(percent: Int): List<Char?> {
    val normalized = percent.coerceIn(0, 100)
    val hundreds = (normalized / 100)
    val tens = (normalized / 10) % 10
    val ones = normalized % 10
    return listOf(
        hundreds.takeIf { it > 0 }?.let { ('0'.code + it).toChar() },
        if (hundreds > 0 || tens > 0) ('0'.code + tens).toChar() else null,
        ('0'.code + ones).toChar()
    )
}

internal fun resolveGesturePercentDigitChangeMask(
    previousPercent: Int,
    currentPercent: Int
): List<Boolean> {
    val previousDigits = resolveGesturePercentDigits(previousPercent)
    val currentDigits = resolveGesturePercentDigits(currentPercent)
    return currentDigits.indices.map { index ->
        previousDigits.getOrNull(index) != currentDigits.getOrNull(index)
    }
}

/**
 * User switch + master card transition → may request TextureView for live morph.
 * HDR still wins inside [shouldUseTextureSurfaceForFlip] so playback never SDR-washes.
 */
internal fun resolveNavigationLiveSurfaceTextureEnabled(
    cardTransitionEnabled: Boolean,
    liveSurfaceCardTransitionEnabled: Boolean,
): Boolean {
    return cardTransitionEnabled && liveSurfaceCardTransitionEnabled
}

/**
 * Live player sharedElement is only useful when TextureView can own the frame.
 * Under HDR we keep SurfaceView and fall back to cover (or freeze-frame) morph —
 * never attach live sharedElement on a SurfaceView that Compose cannot transform.
 */
internal fun resolveAllowLivePlayerSharedElementForMorph(
    cardTransitionEnabled: Boolean,
    liveSurfaceCardTransitionEnabled: Boolean,
    requiresHdrSurfaceOutput: Boolean = false,
): Boolean {
    return cardTransitionEnabled &&
        liveSurfaceCardTransitionEnabled &&
        !requiresHdrSurfaceOutput
}

/**
 * Whether the PlayerView must use TextureView (instead of SurfaceView).
 *
 * TextureView is required for flip transforms and shared-element morph capture.
 * SurfaceView is required for HDR/Dolby Vision so color metadata reaches the display
 * (TextureView GPU composition always tone-maps to SDR). When [requiresHdrSurfaceOutput]
 * is true, navigation morph may fall back to cover-only transitions rather than
 * forcing TextureView and silently killing HDR.
 */
internal fun shouldUseTextureSurfaceForFlip(
    isFlippedHorizontal: Boolean,
    isFlippedVertical: Boolean,
    liveBackPreview: Boolean = false,
    navigationTransformEnabled: Boolean = false,
    requiresHdrSurfaceOutput: Boolean = false,
    preferCompatibleTextureSurface: Boolean = com.android.purebilibili.core.util.prefersTextureVideoSurface()
): Boolean {
    // Flip is the only hard TextureView requirement under HDR; matrix transform
    // cannot run on SurfaceView. Navigation morph yields to HDR fidelity.
    if (requiresHdrSurfaceOutput) {
        return isFlippedHorizontal || isFlippedVertical
    }
    return preferCompatibleTextureSurface ||
        isFlippedHorizontal ||
        isFlippedVertical ||
        liveBackPreview ||
        navigationTransformEnabled
}

/**
 * HDR10 (125) / Dolby Vision (126) streams, or Media3 formats with PQ/HLG transfer,
 * must output via SurfaceView so the display can enter HDR mode.
 *
 * @param colorTransfer Media3 [androidx.media3.common.C] color transfer constant, or 0 if unknown.
 */
internal fun requiresHdrSurfaceOutput(
    currentQualityId: Int,
    colorTransfer: Int = 0
): Boolean {
    if (currentQualityId == 125 || currentQualityId == 126) {
        return true
    }
    // Keep numeric constants local so this policy stays free of Media3 imports.
    // C.COLOR_TRANSFER_ST2084 = 6 (PQ/HDR10), C.COLOR_TRANSFER_HLG = 7.
    return colorTransfer == 6 || colorTransfer == 7
}

internal fun shouldEnableLivePlayerSharedElement(
    transitionEnabled: Boolean,
    allowLivePlayerSharedElement: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean,
    forceCoverDuringReturnAnimation: Boolean = false,
    requiresHdrSurfaceOutput: Boolean = false,
): Boolean {
    if (forceCoverDuringReturnAnimation) return false
    // HDR: SurfaceView cannot participate in Compose sharedElement morph.
    if (requiresHdrSurfaceOutput) return false
    return transitionEnabled &&
        allowLivePlayerSharedElement &&
        hasSharedTransitionScope &&
        hasAnimatedVisibilityScope
}

internal fun resolveSubtitleLanguageLabel(
    languageCode: String?,
    fallbackLabel: String
): String {
    val normalized = languageCode?.lowercase().orEmpty()
    return when {
        normalized.contains("zh") -> "中文"
        normalized.contains("en") -> "英文"
        languageCode.isNullOrBlank() -> fallbackLabel
        else -> languageCode
    }
}

internal fun shouldForceCoverDuringReturnAnimation(
    forceCoverOnly: Boolean
): Boolean {
    return forceCoverOnly
}

internal fun shouldShowCoverImage(
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
    hasStartedSmoothReveal: Boolean,
    isSurfaceRevealSettling: Boolean = false,
    surfaceCanCrossfade: Boolean = true,
): Boolean {
    return shouldHoldEntryCoverUnderlay(
        isFirstFrameRendered = isFirstFrameRendered,
        forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
        shouldKeepCoverForManualStart = shouldKeepCoverForManualStart,
        hasStartedSmoothReveal = hasStartedSmoothReveal,
        isSurfaceRevealSettling = isSurfaceRevealSettling,
        surfaceCanCrossfade = surfaceCanCrossfade,
    )
}

/**
 * 竖屏视频进入播放页时不显示封面，改为黑屏起播；但返回转场仍必须允许封面承接，携带封面返回列表。
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldLoadVideoPlayerCoverImage(
    isVerticalVideo: Boolean,
    shouldKeepCoverForManualStart: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
): Boolean {
    if (isVerticalVideo) return forceCoverDuringReturnAnimation
    return forceCoverDuringReturnAnimation || !isVerticalVideo
}

/**
 * 即播路径的封面是透明 TextureView 下的底图，不能持续压在视频帧上；
 * CoverFirst 与返回则继续由封面承担最上层交互/共享 morph。
 */
internal fun resolveVideoPlayerCoverLayerZIndex(
    playbackIntent: VideoSharedTransitionPlaybackIntent,
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
): Float {
    return if (
        forceCoverDuringReturnAnimation ||
        shouldKeepCoverForManualStart ||
        playbackIntent == VideoSharedTransitionPlaybackIntent.CoverFirst
    ) {
        100f
    } else {
        -1f
    }
}

/**
 * 视频输出面是否支持 alpha 叠化。
 *
 * `SurfaceView` 属于独立硬件层，`graphicsLayer.alpha` 对它无效：一旦它开始出帧就是
 * 不透明整块。若此时让封面同步淡出，中间帧会是「不透明黑/帧 + 半透明封面」——即用户
 * 看到的「叠一层黑」，且这个窗口长度 = 封面淡出时长（300ms），非常明显。
 * `TextureView` 参与 Compose 合成，可以真正淡入，此时才允许封面同步淡出叠化。
 *
 * 统一策略、不按设备能力分档：这里依据的只是**当前实际使用的 surface 类型**这一客观事实，
 * 与机型性能无关。HDR 必须 SurfaceView，因此 HDR 走硬切；SDR + 支持 TextureView 的设备
 * 走叠化，两边都是各自最优，且不引入任何性能守卫。
 */
internal fun canSurfaceParticipateInAlphaCrossfade(
    usesTextureSurface: Boolean,
): Boolean = usesTextureSurface

/**
 * 即播进场 / CoverFirst / 返回：封面作为不透明垫底，直到首帧揭示或手动起播。
 * 垫底期间禁止淡入淡出与 Coil crossfade，避免 Hero morph 透出黑底。
 *
 * [isSurfaceRevealSettling] = 揭开已开始但播放器 surface 尚未淡入完成。此窗口内封面必须
 * 继续保持不透明：视频是在封面**之上**淡入的，若封面同步淡出，两层半透明叠加会透出
 * 下方黑底，产生「先变暗再亮起」的亮度凹陷。正确时序是封面全程垫住，等视频完全不透明
 * 后再无声移除（此时移除不可见）。
 *
 * [surfaceCanCrossfade] = 输出面支持 alpha 叠化（TextureView）。为 false（SurfaceView /
 * HDR）时不存在「视频在封面上方淡入」这一步——surface 一出现就是不透明帧——因此封面
 * 必须**保持不透明直到首帧真正落定**，否则会露出黑底窗口。
 */
internal fun shouldHoldEntryCoverUnderlay(
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
    hasStartedSmoothReveal: Boolean,
    isSurfaceRevealSettling: Boolean = false,
    surfaceCanCrossfade: Boolean = true,
): Boolean {
    if (forceCoverDuringReturnAnimation || shouldKeepCoverForManualStart) return true
    if (!isFirstFrameRendered) return true
    // 不支持 alpha 的输出面没有叠化窗口可等，首帧到就必须继续垫住整块。
    if (!surfaceCanCrossfade) return true
    return isSurfaceRevealSettling || !hasStartedSmoothReveal
}

/**
 * 揭开动画完全落定（视频 surface 不透明）后才移除封面垫底的等待时长。
 * [surfaceRevealDurationMillis] 之上叠加少量缓冲，吸收动画晚一帧启动的相位差。
 */
internal fun resolveVideoPlayerCoverRevealSettleDelayMillis(
    surfaceRevealDurationMillis: Int,
): Long {
    val duration = surfaceRevealDurationMillis.coerceAtLeast(0)
    return (duration + VIDEO_PLAYER_COVER_REVEAL_SETTLE_BUFFER_MILLIS).toLong()
}

/** 揭开叠化起点（progress=0）封面的降饱和程度；1.0 为完全不动。 */
private const val VIDEO_PLAYER_COVER_REVEAL_MIN_SATURATION = 0.90f

/**
 * 封面在揭开叠化窗口内的饱和度。
 *
 * 只降饱和、不动亮度：两层相似画面叠化时色彩会轻微叠加强化，把垫底封面
 * 略微去色可以让混合窗口读作「同一画面」，视频接管后色彩自然「活过来」；
 * 若动亮度会重新引入叠加期的亮度凹陷。
 *
 * [progress] 0 = 揭开刚提交，1 = 已落定（返回 null 即不加 colorFilter）。
 */
internal fun resolveVideoPlayerCoverRevealSaturation(progress: Float): Float {
    return VIDEO_PLAYER_COVER_REVEAL_MIN_SATURATION +
        (1f - VIDEO_PLAYER_COVER_REVEAL_MIN_SATURATION) * progress.coerceIn(0f, 1f)
}

internal fun resolveVideoPlayerCoverRevealColorFilter(progress: Float): ColorFilter? {
    if (progress >= 0.999f) return null
    val matrix = ColorMatrix().apply {
        setToSaturation(resolveVideoPlayerCoverRevealSaturation(progress))
    }
    return ColorFilter.colorMatrix(matrix)
}

internal data class VideoPlayerCoverBootstrapState(
    val isFirstFrameRendered: Boolean,
    val hasStartedSmoothReveal: Boolean
)

/**
 * 进场封面 bootstrap。
 *
 * - 可复用已渲染首帧时：跳过等待 FIRST_FRAME 事件，但 **不** 直接把 smooth reveal 置 true。
 *   否则「详情已出画 → 回首页 → 再进详情」会瞬间揭开画面，封面→画面过渡丢失。
 * - 两条路径统一：先垫封面，再由 [shouldCommitSmoothCoverReveal] 在 hold 后揭开。
 */
internal fun resolveVideoPlayerCoverBootstrapState(
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
    hasPersistedRenderedFirstFrame: Boolean,
    preserveCurrentFrameOnFullscreenChange: Boolean = false,
): VideoPlayerCoverBootstrapState {
    val shouldReuseRenderedFrame = !forceCoverDuringReturnAnimation &&
        !shouldKeepCoverForManualStart &&
        hasPersistedRenderedFirstFrame
    val shouldKeepCurrentFrameVisible = !forceCoverDuringReturnAnimation &&
        !shouldKeepCoverForManualStart &&
        preserveCurrentFrameOnFullscreenChange
    return VideoPlayerCoverBootstrapState(
        isFirstFrameRendered = shouldReuseRenderedFrame || shouldKeepCurrentFrameVisible,
        hasStartedSmoothReveal = shouldKeepCurrentFrameVisible,
    )
}

internal fun shouldStartSmoothCoverReveal(
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean
): Boolean {
    return isFirstFrameRendered &&
        !forceCoverDuringReturnAnimation &&
        !shouldKeepCoverForManualStart
}

/**
 * 是否应把 [hasStartedSmoothReveal] 清回 false。
 * 仅在「强制封面 / 手动起播垫底」时回退；首帧标志短暂抖动不得清掉已排程的揭开。
 */
internal fun shouldResetSmoothCoverReveal(
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
): Boolean {
    return forceCoverDuringReturnAnimation || shouldKeepCoverForManualStart
}

/**
 * hold delay 结束后是否提交揭开。
 * 与 [shouldStartSmoothCoverReveal] 相同门闩，单独命名便于单测「提交」语义。
 */
internal fun shouldCommitSmoothCoverReveal(
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
): Boolean = shouldStartSmoothCoverReveal(
    isFirstFrameRendered = isFirstFrameRendered,
    forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
    shouldKeepCoverForManualStart = shouldKeepCoverForManualStart,
)

/**
 * 是否处于「CoverFirst 手动起播」垫封面。
 *
 * - **自动播放开启时永远 false**：进场封面只靠首帧 / smooth reveal，不得用 manual-start 逻辑
 *   把 PlayerView 藏掉；否则合集换片 halt 或重进详情时 `playWhenReady` 短暂为 false 会永久卡住封面。
 * - 关闭自动播放且用户尚未点播：整段 CoverFirst。
 * - 用户已点播：仅在尚未真正起播（!playWhenReady 且进度仍在片头）时继续垫封面。
 */
internal fun shouldKeepCoverForManualStart(
    playWhenReady: Boolean,
    currentPositionMs: Long,
    autoPlayEnabled: Boolean = true,
    hasManualStartPlaybackIntent: Boolean = false
): Boolean {
    // 自动连播：禁止 manual-start 垫封面与 INVISIBLE surface，否则首帧事件永不来。
    if (autoPlayEnabled) return false
    if (!hasManualStartPlaybackIntent) return true
    if (playWhenReady) return false
    return currentPositionMs <= 0L
}

internal fun shouldShowManualStartPlayButton(
    shouldKeepCoverForManualStart: Boolean
): Boolean {
    return shouldKeepCoverForManualStart
}

internal fun shouldEnableManualStartCoverOverlay(
    shouldKeepCoverForManualStart: Boolean
): Boolean {
    return shouldKeepCoverForManualStart
}

internal enum class VideoPlayerCoverContentScaleMode {
    Crop,
    Fit
}

internal data class VideoPlayerEntryPresentationSpec(
    val coverUsesSharedBounds: Boolean,
    val fillCoverViewport: Boolean,
    val showManualStartPlayButton: Boolean,
    val enableManualStartCoverOverlay: Boolean,
    val coverContentScaleMode: VideoPlayerCoverContentScaleMode
)

internal fun resolveVideoPlayerEntryPresentationSpec(
    shouldKeepCoverForManualStart: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    isVerticalVideo: Boolean,
    targetMode: com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode
): VideoPlayerEntryPresentationSpec {
    val targetFillsViewport =
        targetMode == com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode.LandscapeFullscreen ||
            targetMode == com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode.PortraitFullscreen
    val fillCoverViewport = !forceCoverDuringReturnAnimation &&
        (targetFillsViewport || shouldKeepCoverForManualStart || isVerticalVideo)
    return VideoPlayerEntryPresentationSpec(
        coverUsesSharedBounds = if (isVerticalVideo) {
            forceCoverDuringReturnAnimation
        } else {
            forceCoverDuringReturnAnimation || shouldKeepCoverForManualStart
        },
        fillCoverViewport = fillCoverViewport,
        showManualStartPlayButton = shouldKeepCoverForManualStart,
        enableManualStartCoverOverlay = shouldKeepCoverForManualStart,
        // Inline landscape-video covers fill the shared 16:9 placeholder. Fullscreen
        // and vertical-video covers retain their existing fit behavior.
        coverContentScaleMode = if ((!isVerticalVideo && !forceCoverDuringReturnAnimation && targetFillsViewport) ||
            (fillCoverViewport && isVerticalVideo)
        ) {
            VideoPlayerCoverContentScaleMode.Fit
        } else {
            VideoPlayerCoverContentScaleMode.Crop
        }
    )
}

internal fun shouldFillPlayerViewportForManualStartCover(
    shouldKeepCoverForManualStart: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    isVerticalVideo: Boolean = false
): Boolean {
    if (forceCoverDuringReturnAnimation) return false
    return isVerticalVideo
}

internal enum class ManualStartPlayButtonAnchor {
    Center,
    CenterEnd,
    BottomEnd
}

internal data class ManualStartPlayButtonLayoutSpec(
    val anchor: ManualStartPlayButtonAnchor,
    val endPaddingDp: Int,
    val iconWidthDp: Int,
    val iconHeightDp: Int,
    val showCoverScrim: Boolean,
    val showTopDecorations: Boolean
)

internal fun resolveManualStartPlayButtonLayoutSpec(): ManualStartPlayButtonLayoutSpec {
    return ManualStartPlayButtonLayoutSpec(
        anchor = ManualStartPlayButtonAnchor.BottomEnd,
        endPaddingDp = 12,
        iconWidthDp = 72,
        iconHeightDp = 60,
        showCoverScrim = false,
        showTopDecorations = false
    )
}

internal fun shouldDisableCoverFadeAnimation(
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return forceCoverDuringReturnAnimation
}

internal data class VideoPlayerCoverMotionSpec(
    val shouldAnimateFade: Boolean,
    val enterFadeDurationMillis: Int,
    val exitFadeDurationMillis: Int
)

internal data class VideoPlayerRevealMotionSpec(
    val coverRevealHoldDelayMillis: Int,
    val surfaceRevealDurationMillis: Int,
    val surfaceRevealInitialScale: Float
)

internal data class VideoPlayerSurfaceRevealSpec(
    val alpha: Float,
    val scale: Float
)

/**
 * 封面退场动效。
 *
 * [exitFadeDurationMillis] 必须与 [surfaceRevealDurationMillis] **同源同时长**：
 * 视频 surface 是在封面**之上**从 0 淡入的，PlayerView 首帧前自身带不透明黑底。
 * 若封面淡出（300ms）比 surface 淡入（140ms）慢，中间的窗口会同时存在
 * 「已不透明的黑底 + 半透明封面」——这正是进场时视频位置"叠一层黑"的来源。
 *
 * 两者锁死到同一条时间轴后，封面与视频画面是同一对互补曲线：任何一帧
 * `coverAlpha + surfaceAlpha` 都接近 1，黑底没有露出的窗口。
 */
internal fun resolveVideoPlayerCoverMotionSpec(
    forceCoverDuringReturnAnimation: Boolean,
    holdEntryCoverUnderlay: Boolean = false,
    surfaceRevealDurationMillis: Int = VIDEO_PLAYER_SURFACE_REVEAL_DURATION_MILLIS,
): VideoPlayerCoverMotionSpec {
    return VideoPlayerCoverMotionSpec(
        shouldAnimateFade = !forceCoverDuringReturnAnimation && !holdEntryCoverUnderlay,
        enterFadeDurationMillis = VIDEO_PLAYER_COVER_FADE_ENTER_DURATION_MILLIS,
        // 与 surface 淡入同时长，避免中间出现「黑底 + 半透明封面」叠加。
        exitFadeDurationMillis = surfaceRevealDurationMillis.coerceAtLeast(0),
    )
}

internal fun resolveVideoPlayerRevealMotionSpec(
    reducedMotion: Boolean = false,
): VideoPlayerRevealMotionSpec {
    return VideoPlayerRevealMotionSpec(
        coverRevealHoldDelayMillis = VIDEO_PLAYER_COVER_REVEAL_HOLD_DELAY_MILLIS,
        surfaceRevealDurationMillis = if (reducedMotion) 80 else VIDEO_PLAYER_SURFACE_REVEAL_DURATION_MILLIS,
        surfaceRevealInitialScale = VIDEO_PLAYER_SURFACE_REVEAL_INITIAL_SCALE
    )
}

internal fun resolveVideoPlayerSurfaceRevealSpec(
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean,
    hasStartedSmoothReveal: Boolean,
    surfaceRevealInitialScale: Float = VIDEO_PLAYER_SURFACE_REVEAL_INITIAL_SCALE
): VideoPlayerSurfaceRevealSpec {
    if (forceCoverDuringReturnAnimation) {
        return VideoPlayerSurfaceRevealSpec(alpha = 0f, scale = 1f)
    }
    if (shouldKeepCoverForManualStart) {
        return VideoPlayerSurfaceRevealSpec(alpha = 0f, scale = 1f)
    }
    if (!hasStartedSmoothReveal) {
        return VideoPlayerSurfaceRevealSpec(
            alpha = 0f,
            scale = surfaceRevealInitialScale
        )
    }
    return VideoPlayerSurfaceRevealSpec(alpha = 1f, scale = 1f)
}

internal fun shouldHidePlayerSurfaceDuringForcedReturn(
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return forceCoverDuringReturnAnimation
}

internal fun shouldKeepInlinePlayerContentOnReset(
    isPortraitFullscreen: Boolean,
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return !isPortraitFullscreen && !forceCoverDuringReturnAnimation
}

/**
 * 是否展示内联 PlayerView。
 *
 * 注意：**不要**因 CoverFirst 垫封面而 INVISIBLE。封面叠层 + surface alpha 已能挡住画面；
 * 把 View 设为 INVISIBLE 会导致 Surface 不产出 [Player.EVENT_RENDERED_FIRST_FRAME]，
 * 揭开状态机永远等不到首帧（合集换片 / 重进详情「整页一直封面、只有声音」）。
 */
internal fun shouldShowInlinePlayerView(
    isPortraitFullscreen: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    shouldKeepCoverForManualStart: Boolean = false
): Boolean {
    // shouldKeepCoverForManualStart 仅影响封面叠层 / surface alpha，不再隐藏 PlayerView。
    return !isPortraitFullscreen && !forceCoverDuringReturnAnimation
}

internal fun shouldEnableCoverImageCrossfade(
    forceCoverDuringReturnAnimation: Boolean,
    holdEntryCoverUnderlay: Boolean = false,
): Boolean {
    return !forceCoverDuringReturnAnimation && !holdEntryCoverUnderlay
}

internal fun resolvePreferredVideoCoverUrl(
    entryCoverUrl: String,
    detailCoverUrl: String,
    preferDetailCoverUrl: Boolean = false
): String {
    val normalizedDetailCoverUrl = detailCoverUrl.trim()
    if (preferDetailCoverUrl && normalizedDetailCoverUrl.isNotEmpty()) {
        return normalizedDetailCoverUrl
    }

    val normalizedEntryCoverUrl = entryCoverUrl.trim()
    if (normalizedEntryCoverUrl.isNotEmpty()) return normalizedEntryCoverUrl

    if (normalizedDetailCoverUrl.isNotEmpty()) return normalizedDetailCoverUrl

    return ""
}

internal fun shouldEnableForcedReturnCoverSharedBounds(
    forceCoverDuringReturnAnimation: Boolean,
    transitionEnabled: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean,
    sourceRoute: String?
): Boolean {
    return shouldEnableCoverOverlaySharedBounds(
        useCoverOverlaySharedBounds = forceCoverDuringReturnAnimation,
        transitionEnabled = transitionEnabled,
        hasSharedTransitionScope = hasSharedTransitionScope,
        hasAnimatedVisibilityScope = hasAnimatedVisibilityScope,
        sourceRoute = sourceRoute
    )
}

internal fun shouldEnableCoverOverlaySharedBounds(
    useCoverOverlaySharedBounds: Boolean,
    transitionEnabled: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean,
    sourceRoute: String?
): Boolean {
    val sourceRouteBase = sourceRoute?.substringBefore("?")
    // 返回阶段或手动封面阶段由封面承接同一个 cover key，
    // 避免播放器画面和封面各自跑一段动画。
    val allowBySourceRoute = sourceRouteBase == null ||
        (
            com.android.purebilibili.navigation.isVideoCardReturnTargetRoute(sourceRouteBase) &&
                !com.android.purebilibili.core.ui.transition.shouldSkipVideoCardSharedBoundsMorph(
                    sourceRouteBase
                )
        )
    return useCoverOverlaySharedBounds &&
        transitionEnabled &&
        hasSharedTransitionScope &&
        hasAnimatedVisibilityScope &&
        allowBySourceRoute
}

internal fun resolveForcedReturnCoverSharedElementSourceRoute(sourceRoute: String?): String? {
    val sourceRouteBase = sourceRoute?.substringBefore("?")
    return if (com.android.purebilibili.navigation.isVideoCardReturnTargetRoute(sourceRouteBase)) {
        sourceRouteBase?.takeIf { it.isNotBlank() }
    } else {
        null
    }
}

internal fun shouldUseReturnLandingMotionForForcedReturnCover(
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return forceCoverDuringReturnAnimation
}

internal fun shouldPromoteFirstFrameByPlaybackFallback(
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    playbackState: Int,
    playWhenReady: Boolean,
    currentPositionMs: Long,
    videoWidth: Int,
    videoHeight: Int
): Boolean {
    if (isFirstFrameRendered || forceCoverDuringReturnAnimation) return false
    val hasVideoTrack = videoWidth > 0 && videoHeight > 0
    // READY + 有画面尺寸即可提升，避免重进时迟迟等不到 RENDERED_FIRST_FRAME 卡封面。
    return hasVideoTrack &&
        playWhenReady &&
        playbackState == Player.STATE_READY &&
        currentPositionMs >= 0L
}

internal fun shouldAutoHidePlayerChromeOnPlaybackStart(
    showControls: Boolean,
    hasAutoHiddenForCurrentVideo: Boolean,
    isPlaying: Boolean,
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean,
    isSeekScrubbing: Boolean
): Boolean {
    return showControls &&
        !hasAutoHiddenForCurrentVideo &&
        isPlaying &&
        isFirstFrameRendered &&
        !forceCoverDuringReturnAnimation &&
        !isSeekScrubbing
}

internal enum class MediaSwitchSurfaceRebindAction {
    SKIP,
    WAIT_FOR_OUTPUT,
    REBIND
}

internal fun resolveMediaSwitchSurfaceRebindAction(
    hasSuccessPlaybackIdentity: Boolean,
    shouldBindInlinePlayerView: Boolean,
    isInPipMode: Boolean,
    hasPlayerView: Boolean,
    mediaItemCount: Int
): MediaSwitchSurfaceRebindAction {
    if (!hasSuccessPlaybackIdentity || !shouldBindInlinePlayerView || isInPipMode) {
        return MediaSwitchSurfaceRebindAction.SKIP
    }
    if (!hasPlayerView || mediaItemCount <= 0) {
        return MediaSwitchSurfaceRebindAction.WAIT_FOR_OUTPUT
    }
    return MediaSwitchSurfaceRebindAction.REBIND
}

internal fun shouldRetryMediaSwitchSurfaceRebind(
    hasRenderedFirstFrame: Boolean,
    shouldBindInlinePlayerView: Boolean,
    isInPipMode: Boolean,
    hasPlayerView: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return !hasRenderedFirstFrame &&
        shouldBindInlinePlayerView &&
        !isInPipMode &&
        hasPlayerView &&
        playWhenReady &&
        playbackState == Player.STATE_READY
}

internal fun shouldRebindPlayerSurfaceOnForeground(
    hasPlayerView: Boolean,
    isInPipMode: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    needsSurfaceRecovery: Boolean = true
): Boolean {
    if (!hasPlayerView || isInPipMode) return false
    if (needsSurfaceRecovery) return true
    return videoWidth <= 0 || videoHeight <= 0
}

internal fun shouldStartForegroundSurfaceRecovery(
    hasPlayerView: Boolean,
    shouldBindInlinePlayerView: Boolean,
    isInPipMode: Boolean,
    needsSurfaceRecovery: Boolean = true,
    videoWidth: Int = 0,
    videoHeight: Int = 0
): Boolean {
    if (!hasPlayerView || !shouldBindInlinePlayerView || isInPipMode) return false
    if (needsSurfaceRecovery) return true
    return videoWidth <= 0 || videoHeight <= 0
}

internal fun shouldKickPlaybackAfterSurfaceRecovery(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    hasPlaybackResumeIntent: Boolean = true
): Boolean {
    return hasPlaybackResumeIntent &&
        playWhenReady &&
        !isPlaying &&
        (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
}

internal fun shouldLogForegroundSurfaceRecoveryTimeout(
    hasRenderedFirstFrameSinceRecovery: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    if (hasRenderedFirstFrameSinceRecovery || !playWhenReady) return false
    return playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
}

internal fun shouldLogPlaybackStall(
    bufferingDurationMs: Long,
    playWhenReady: Boolean,
    currentPositionMs: Long
): Boolean {
    return bufferingDurationMs >= PLAYBACK_STALL_LOG_THRESHOLD_MS &&
        playWhenReady &&
        currentPositionMs > 0L
}

internal fun shouldBindInlinePlayerViewToPlayer(
    isPortraitFullscreen: Boolean,
    hostLifecycleStarted: Boolean,
    isInPipMode: Boolean,
    liveBackPreview: Boolean = false
): Boolean {
    return !isPortraitFullscreen &&
        (hostLifecycleStarted || isInPipMode || liveBackPreview)
}

internal fun shouldRecoverInlinePlayerAfterPredictiveBackCancel(
    recoveryGeneration: Int,
    hasPlayerView: Boolean,
    shouldBindInlinePlayerView: Boolean,
    isInPipMode: Boolean
): Boolean {
    return recoveryGeneration > 0 &&
        hasPlayerView &&
        shouldBindInlinePlayerView &&
        !isInPipMode
}

internal fun shouldLoadDanmakuForForegroundHost(
    hostLifecycleStarted: Boolean,
    shouldLoadImmediately: Boolean
): Boolean {
    return hostLifecycleStarted && shouldLoadImmediately
}

internal fun rebindPlayerSurfaceIfNeeded(
    playerView: PlayerView,
    player: Player
) {
    when (val videoSurface = playerView.videoSurfaceView) {
        is TextureView -> {
            player.clearVideoTextureView(videoSurface)
        }
        is SurfaceView -> {
            player.clearVideoSurfaceView(videoSurface)
        }
    }
    if (playerView.player === player) {
        playerView.player = null
    }
    playerView.player = player
    when (val videoSurface = playerView.videoSurfaceView) {
        is TextureView -> {
            player.setVideoTextureView(videoSurface)
        }
        is SurfaceView -> {
            player.setVideoSurfaceView(videoSurface)
        }
    }
}
