package com.android.purebilibili.feature.video.ui.section

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.Player
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.ui.gesture.LockedTwoFingerSpeedAxis
import com.android.purebilibili.feature.video.ui.gesture.TwoFingerSpeedGestureMode
import com.android.purebilibili.feature.video.ui.gesture.resolveLockedTwoFingerSpeedAxis
import com.android.purebilibili.feature.video.ui.gesture.resolveTwoFingerGesturePlaybackSpeed
import com.android.purebilibili.feature.video.ui.pager.resolvePortraitZoomedPan
import kotlin.math.abs
import kotlin.math.hypot

internal data class VideoPlayerMultiTouchCallbacks(
    val getScale: () -> Float,
    val setScale: (Float) -> Unit,
    val getPanX: () -> Float,
    val setPanX: (Float) -> Unit,
    val getPanY: () -> Float,
    val setPanY: (Float) -> Unit,
    val setMultiTouchActive: (Boolean) -> Unit,
    val isLongPressing: () -> Boolean,
    val onFinishLongPress: () -> Unit,
    val isVerticalVideo: () -> Boolean,
    val getCurrentAudioQuality: () -> Int,
    val onHideControlsAndGesture: () -> Unit,
    val onApplyPlaybackSpeed: (Float) -> Unit,
    val onShowTwoFingerSpeedFeedback: (Float) -> Unit,
    val isTwoFingerChapterSkipEnabled: () -> Boolean,
    val getViewPoints: () -> List<ViewPoint>,
    val onChapterSeek: (positionMs: Long, feedbackText: String) -> Unit,
    val isPinchZoomSnapAndBadgeEnabled: () -> Boolean,
    val onZoomSnappedToFill: () -> Unit,
    val onCancelZoomSettle: () -> Unit = {},
    val onAnimateZoomRelease: ((targetScale: Float, targetPanX: Float, targetPanY: Float) -> Unit)? = null,
)

private const val PLAYER_ZOOM_MIN_OVERSHOOT = 0.86f
private const val PLAYER_ZOOM_MAX_OVERSHOOT = 5.35f
private const val PLAYER_ZOOM_RUBBER_BAND_RESISTANCE = 0.35f

private fun applyPlayerPinchRubberBandScale(currentScale: Float, zoomFactor: Float): Float {
    val rawNext = currentScale * zoomFactor
    return when {
        rawNext < 1f -> {
            val damped = if (currentScale < 1f) {
                currentScale + (rawNext - currentScale) * PLAYER_ZOOM_RUBBER_BAND_RESISTANCE
            } else {
                1f - (1f - rawNext) * PLAYER_ZOOM_RUBBER_BAND_RESISTANCE
            }
            damped.coerceIn(PLAYER_ZOOM_MIN_OVERSHOOT, 1f)
        }
        rawNext > 5f -> {
            val damped = if (currentScale > 5f) {
                currentScale + (rawNext - currentScale) * PLAYER_ZOOM_RUBBER_BAND_RESISTANCE
            } else {
                5f + (rawNext - 5f) * PLAYER_ZOOM_RUBBER_BAND_RESISTANCE
            }
            damped.coerceIn(5f, PLAYER_ZOOM_MAX_OVERSHOOT)
        }
        else -> rawNext
    }
}

/**
 * 横屏/详情内嵌播放器的多点触控与缩放手势：
 * - 放大态单指拖动平移画幅
 * - 双指缩放/平移 + 松手贴合吸附
 * - 全屏双指滑调倍速
 * - 双指双击切换章节
 */
internal fun Modifier.videoPlayerMultiTouchGestures(
    currentPlaybackIdentity: Any?,
    player: Player,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    isScreenLocked: Boolean,
    twoFingerSpeedMode: TwoFingerSpeedGestureMode,
    playbackSpeedOptions: List<Float>,
    isPortraitFullscreen: Boolean,
    callbacks: VideoPlayerMultiTouchCallbacks,
): Modifier = this.pointerInput(
    currentPlaybackIdentity,
    player,
    isFullscreen,
    isInPipMode,
    isScreenLocked,
    twoFingerSpeedMode,
    playbackSpeedOptions,
    isPortraitFullscreen,
) {
    if (!shouldEnableInlinePlayerGestures(isPortraitFullscreen)) {
        return@pointerInput
    }
    try {
        var lastTwoFingerTapAtMs = 0L
        var lastTwoFingerTapForward: Boolean? = null
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            callbacks.onCancelZoomSettle()
            var totalPanX = 0f
            var totalPanY = 0f
            var lockedAxis: LockedTwoFingerSpeedAxis? = null
            var gestureStartSpeed = player.playbackParameters.speed
            val directionThresholdPx = viewConfiguration.touchSlop * 1.5f
            var observedMultiTouch = false
            var viewportTransformObserved = false
            var singleFingerPanActive = false
            var singleFingerAccumulated = Offset.Zero
            val singleFingerTouchSlop = viewConfiguration.touchSlop
            var twoFingerStartedAtMs = 0L
            var twoFingerCentroidX = -1f

            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount == 0) {
                    callbacks.setMultiTouchActive(false)
                    break
                }
                if (pressedCount < 2) {
                    if (observedMultiTouch) {
                        callbacks.setMultiTouchActive(false)
                        break
                    }
                    val currentScale = callbacks.getScale()
                    if (currentScale > 1.01f && !isScreenLocked && !isInPipMode) {
                        val change = event.changes.firstOrNull { it.pressed } ?: continue
                        val delta = change.position - change.previousPosition
                        if (delta != Offset.Zero) {
                            singleFingerAccumulated += delta
                            if (!singleFingerPanActive && singleFingerAccumulated.getDistance() >= singleFingerTouchSlop) {
                                singleFingerPanActive = true
                            }
                            if (singleFingerPanActive) {
                                val (nextX, nextY) = resolvePortraitZoomedPan(
                                    currentPanX = callbacks.getPanX(),
                                    currentPanY = callbacks.getPanY(),
                                    deltaX = delta.x,
                                    deltaY = delta.y,
                                    scale = currentScale,
                                    containerWidth = size.width.toFloat(),
                                    containerHeight = size.height.toFloat(),
                                )
                                callbacks.setPanX(nextX)
                                callbacks.setPanY(nextY)
                                change.consume()
                            }
                        }
                    }
                    continue
                }
                if (!observedMultiTouch && pressedCount == 2) {
                    twoFingerStartedAtMs = SystemClock.uptimeMillis()
                    val active = event.changes.filter { it.pressed }
                    twoFingerCentroidX = active.map { it.position.x }.average().toFloat()
                }
                observedMultiTouch = true
                callbacks.setMultiTouchActive(true)

                if (callbacks.isLongPressing()) {
                    callbacks.onFinishLongPress()
                }

                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                if (abs(zoom - 1f) > 0.005f) {
                    viewportTransformObserved = true
                }
                totalPanX += pan.x
                totalPanY += pan.y

                val speedModeAllowed = isFullscreen &&
                    !isInPipMode &&
                    !isScreenLocked &&
                    !viewportTransformObserved &&
                    twoFingerSpeedMode != TwoFingerSpeedGestureMode.Off

                if (speedModeAllowed && lockedAxis == null) {
                    lockedAxis = resolveLockedTwoFingerSpeedAxis(
                        mode = twoFingerSpeedMode,
                        totalDragX = totalPanX,
                        totalDragY = totalPanY,
                        thresholdPx = directionThresholdPx
                    )
                    if (lockedAxis != null) {
                        gestureStartSpeed = player.playbackParameters.speed
                        callbacks.onHideControlsAndGesture()
                    }
                }

                if (speedModeAllowed && lockedAxis != null) {
                    val resolvedSpeed = resolveTwoFingerGesturePlaybackSpeed(
                        startSpeed = gestureStartSpeed,
                        mode = twoFingerSpeedMode,
                        totalDragX = totalPanX,
                        totalDragY = totalPanY,
                        containerWidthPx = size.width.toFloat(),
                        containerHeightPx = size.height.toFloat(),
                        supportedSpeeds = playbackSpeedOptions
                    )
                    val effectiveSpeed = resolveEffectivePlaybackSpeed(
                        requestedSpeed = resolvedSpeed,
                        currentAudioQuality = callbacks.getCurrentAudioQuality()
                    )
                    if (abs(player.playbackParameters.speed - effectiveSpeed) > 0.001f) {
                        callbacks.onApplyPlaybackSpeed(resolvedSpeed)
                    }
                    callbacks.onShowTwoFingerSpeedFeedback(effectiveSpeed)
                    event.changes.forEach { change ->
                        if (change.position != change.previousPosition) {
                            change.consume()
                        }
                    }
                    continue
                }

                if (
                    shouldEnableViewportTransformGesture(
                        isScreenLocked = isScreenLocked,
                        isFullscreen = isFullscreen,
                        isPortraitFullscreen = isPortraitFullscreen,
                        isVerticalVideo = callbacks.isVerticalVideo(),
                    ) && (zoom != 1f || pan != Offset.Zero)
                ) {
                    val oldScale = callbacks.getScale()
                    val updatedScale = applyPlayerPinchRubberBandScale(oldScale, zoom)
                    callbacks.setScale(updatedScale)

                    if (updatedScale > 1f) {
                        val currentPanX = callbacks.getPanX()
                        val currentPanY = callbacks.getPanY()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val centroidDx: Float
                        val centroidDy: Float
                        if (centroid.isSpecified && oldScale > 0f && updatedScale != oldScale) {
                            val zoomRatio = updatedScale / oldScale
                            centroidDx = (1f - zoomRatio) * (centroid.x - size.width / 2f - currentPanX)
                            centroidDy = (1f - zoomRatio) * (centroid.y - size.height / 2f - currentPanY)
                        } else {
                            centroidDx = 0f
                            centroidDy = 0f
                        }
                        val (nextX, nextY) = resolvePortraitZoomedPan(
                            currentPanX = currentPanX,
                            currentPanY = currentPanY,
                            deltaX = pan.x + centroidDx,
                            deltaY = pan.y + centroidDy,
                            scale = updatedScale,
                            containerWidth = size.width.toFloat(),
                            containerHeight = size.height.toFloat(),
                        )
                        callbacks.setPanX(nextX)
                        callbacks.setPanY(nextY)
                        callbacks.onHideControlsAndGesture()
                    } else {
                        callbacks.setPanX(0f)
                        callbacks.setPanY(0f)
                    }

                    event.changes.forEach { change ->
                        if (change.position != change.previousPosition) {
                            change.consume()
                        }
                    }
                }
            }
            if (
                observedMultiTouch &&
                !viewportTransformObserved &&
                lockedAxis == null &&
                callbacks.isTwoFingerChapterSkipEnabled() &&
                !isScreenLocked &&
                !isInPipMode &&
                twoFingerStartedAtMs > 0L &&
                twoFingerCentroidX >= 0f &&
                size.width > 0
            ) {
                val nowMs = SystemClock.uptimeMillis()
                val tapDurationMs = nowMs - twoFingerStartedAtMs
                val movedDist = hypot(totalPanX.toDouble(), totalPanY.toDouble())
                if (tapDurationMs <= 260L && movedDist < directionThresholdPx) {
                    val relativeX = twoFingerCentroidX / size.width.toFloat()
                    val sideForward = when {
                        relativeX > 0.55f -> true
                        relativeX < 0.45f -> false
                        else -> null
                    }
                    if (sideForward != null) {
                        if (lastTwoFingerTapForward == sideForward && nowMs - lastTwoFingerTapAtMs <= 420L) {
                            lastTwoFingerTapAtMs = 0L
                            lastTwoFingerTapForward = null
                            val target = resolveAdjacentChapterTarget(
                                currentPositionMs = player.currentPosition,
                                viewPoints = callbacks.getViewPoints(),
                                forward = sideForward,
                            )
                            if (target != null) {
                                callbacks.onChapterSeek(
                                    target.positionMs,
                                    "${if (target.isNext) "下一章" else "上一章"} · ${target.title.take(12)}",
                                )
                            }
                        } else {
                            lastTwoFingerTapAtMs = nowMs
                            lastTwoFingerTapForward = sideForward
                        }
                    }
                }
            }
            if (viewportTransformObserved) {
                val videoSize = player.videoSize
                val fillScale = resolveFillZoomScale(
                    containerWidth = size.width,
                    containerHeight = size.height,
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                )
                val releaseResult = resolvePinchZoomReleaseScale(
                    currentScale = callbacks.getScale(),
                    fillScale = fillScale,
                    snapEnabled = callbacks.isPinchZoomSnapAndBadgeEnabled(),
                )
                val targetScale = releaseResult.scale.coerceIn(1f, 5f)
                val (targetPanX, targetPanY) = if (releaseResult.resetPan) {
                    0f to 0f
                } else {
                    resolvePortraitZoomedPan(
                        currentPanX = callbacks.getPanX(),
                        currentPanY = callbacks.getPanY(),
                        deltaX = 0f,
                        deltaY = 0f,
                        scale = targetScale,
                        containerWidth = size.width.toFloat(),
                        containerHeight = size.height.toFloat(),
                    )
                }
                val animateRelease = callbacks.onAnimateZoomRelease
                if (animateRelease != null && (
                        abs(callbacks.getScale() - targetScale) > 0.001f ||
                            abs(callbacks.getPanX() - targetPanX) > 0.5f ||
                            abs(callbacks.getPanY() - targetPanY) > 0.5f
                    )
                ) {
                    animateRelease(targetScale, targetPanX, targetPanY)
                } else {
                    callbacks.setScale(targetScale)
                    callbacks.setPanX(targetPanX)
                    callbacks.setPanY(targetPanY)
                }
                if (releaseResult.snappedToFill) {
                    callbacks.onZoomSnappedToFill()
                }
            }
        }
    } finally {
        callbacks.setMultiTouchActive(false)
    }
}
