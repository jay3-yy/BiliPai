package com.android.purebilibili.feature.video.ui.pager

import android.content.Context
import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.player.SeekTickHapticPolicy
import com.android.purebilibili.core.store.PlayerInteractionSettings
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.ui.gesture.detectPlayerTapGestures
import com.android.purebilibili.feature.video.ui.overlay.FullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.overlay.resolveFullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.section.resolveHorizontalSeekDeltaMs
import com.android.purebilibili.feature.video.ui.section.resolveLongPressPlaybackParameters
import com.android.purebilibili.feature.video.ui.section.resolveRelativeSeekTargetPosition
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction

internal data class PortraitPageGestureCallbacks(
    val getScale: () -> Float,
    val setScale: (Float) -> Unit,
    val getPanX: () -> Float,
    val setPanX: (Float) -> Unit,
    val getPanY: () -> Float,
    val setPanY: (Float) -> Unit,
    val resetViewportTransform: () -> Unit,
    val isLongPressing: () -> Boolean,
    val onCancelLongPress: () -> Unit,
    val onStartLongPress: (origin: PlaybackParameters, applied: PlaybackParameters) -> Unit,
    val onToggleOverlay: (Boolean) -> Unit,
    val onExitCleanScreen: () -> Unit,
    val onSeekCommitted: (Long) -> Unit,
    val onSeekFeedback: (deltaSeconds: Int) -> Unit,
    val isSeekGestureActive: () -> Boolean,
    val setSeekGestureActive: (Boolean) -> Unit,
    val getSeekStartPosition: () -> Long,
    val setSeekStartPosition: (Long) -> Unit,
    val getSeekTargetPosition: () -> Long,
    val setSeekTargetPosition: (Long) -> Unit,
    val triggerSeekHaptic: (HapticType) -> Unit,
    val triggerLockHaptic: (HapticType) -> Unit,
)

/**
 * 竖屏单页手势集合：双指缩放/单指放大平移、单击/双击/长按倍速、以及横滑调进度。
 * 所有易变状态通过 [State] 读取，保持 pointerInput 协程稳定不因控栏显隐重建。
 */
internal fun Modifier.portraitPageGestures(
    context: Context,
    bvid: String,
    exoPlayer: ExoPlayer,
    danmakuManager: DanmakuManager,
    danmakuTapCoordinates: State<LayoutCoordinates?>,
    isCurrentPage: Boolean,
    playerGesturesEnabled: Boolean,
    seekGestureHapticPolicy: SeekTickHapticPolicy,
    overlayVisibleState: State<Boolean>,
    cleanScreenState: State<Boolean>,
    danmakuEnabledState: State<Boolean>,
    doubleTapSeekEnabledState: State<Boolean>,
    seekForwardSecondsState: State<Int>,
    seekBackwardSecondsState: State<Int>,
    longPressSpeedState: State<Float>,
    currentAudioQualityState: State<Int>,
    progressDurationState: State<Long>,
    playerInteractionSettingsState: State<PlayerInteractionSettings>,
    callbacks: PortraitPageGestureCallbacks,
): Modifier = this
    .pointerInput(isCurrentPage, bvid, playerGesturesEnabled) {
        if (!isCurrentPage || !playerGesturesEnabled) return@pointerInput

        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var observedMultiTouch = false
            var singleFingerPanActive = false
            var singleFingerAccumulated = Offset.Zero
            val touchSlop = viewConfiguration.touchSlop

            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount == 0) break
                if (pressedCount < 2) {
                    if (observedMultiTouch) break
                    val currentScale = callbacks.getScale()
                    if (currentScale > 1.01f) {
                        val change = event.changes.firstOrNull { it.pressed } ?: continue
                        val delta = change.position - change.previousPosition
                        if (delta != Offset.Zero) {
                            singleFingerAccumulated += delta
                            if (!singleFingerPanActive && singleFingerAccumulated.getDistance() >= touchSlop) {
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
                observedMultiTouch = true

                if (callbacks.isLongPressing()) {
                    callbacks.onCancelLongPress()
                }

                val pan = event.calculatePan()
                val zoom = event.calculateZoom()

                if (zoom != 1f || pan != Offset.Zero) {
                    val updatedScale = (callbacks.getScale() * zoom).coerceIn(1f, 5f)
                    callbacks.setScale(updatedScale)

                    if (updatedScale > 1f) {
                        val (nextX, nextY) = resolvePortraitZoomedPan(
                            currentPanX = callbacks.getPanX(),
                            currentPanY = callbacks.getPanY(),
                            deltaX = pan.x * updatedScale,
                            deltaY = pan.y * updatedScale,
                            scale = updatedScale,
                            containerWidth = size.width.toFloat(),
                            containerHeight = size.height.toFloat(),
                        )
                        callbacks.setPanX(nextX)
                        callbacks.setPanY(nextY)
                        callbacks.onToggleOverlay(false)
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
        }
    }
    .pointerInput(isCurrentPage, playerGesturesEnabled, danmakuManager) {
        if (!isCurrentPage || !playerGesturesEnabled) return@pointerInput
        detectPlayerTapGestures(
            doubleTapSlopPx = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat(),
            captureSingleTap = { offset ->
                if (playerGesturesEnabled && isCurrentPage && danmakuEnabledState.value && !cleanScreenState.value &&
                    shouldHandlePortraitTapGesture(scale = callbacks.getScale())
                ) danmakuManager.captureTap(danmakuTapCoordinates.value, offset) else null
            },
            onPressEnd = {
                if (callbacks.isLongPressing()) {
                    callbacks.onCancelLongPress()
                }
            },
            onTap = {
                if (!playerGesturesEnabled || !isCurrentPage) return@detectPlayerTapGestures
                if (cleanScreenState.value) {
                    callbacks.onExitCleanScreen()
                } else if (shouldHandlePortraitTapGesture(scale = callbacks.getScale())) {
                    callbacks.onToggleOverlay(
                        resolvePortraitOverlayVisibilityAfterTap(overlayVisibleState.value)
                    )
                }
            },
            onDoubleTap = { offset ->
                if (!playerGesturesEnabled || !isCurrentPage) {
                    return@detectPlayerTapGestures
                }
                if (!shouldHandlePortraitTapGesture(scale = callbacks.getScale())) {
                    callbacks.triggerLockHaptic(HapticType.LIGHT)
                    callbacks.resetViewportTransform()
                    return@detectPlayerTapGestures
                }
                val screenWidth = size.width.toFloat()
                val relativeX = if (screenWidth > 0f) offset.x / screenWidth else 0.5f
                when (
                    resolveFullscreenDoubleTapAction(
                        relativeX = relativeX,
                        doubleTapSeekEnabled = doubleTapSeekEnabledState.value,
                        playWhenReady = exoPlayer.playWhenReady,
                        isPlaying = exoPlayer.isPlaying,
                        playbackState = exoPlayer.playbackState,
                    )
                ) {
                    FullscreenDoubleTapAction.SeekBackward -> {
                        callbacks.triggerSeekHaptic(HapticType.LIGHT)
                        val backwardSeconds = seekBackwardSecondsState.value
                        val newPos = resolveRelativeSeekTargetPosition(
                            currentPositionMs = exoPlayer.currentPosition,
                            deltaMs = -(backwardSeconds * 1000L),
                            durationMs = exoPlayer.duration,
                        )
                        seekPlayerFromUserAction(exoPlayer, newPos)
                        danmakuManager.seekTo(newPos)
                        callbacks.onSeekCommitted(newPos)
                        callbacks.onSeekFeedback(-backwardSeconds)
                    }
                    FullscreenDoubleTapAction.SeekForward -> {
                        callbacks.triggerSeekHaptic(HapticType.LIGHT)
                        val forwardSeconds = seekForwardSecondsState.value
                        val newPos = resolveRelativeSeekTargetPosition(
                            currentPositionMs = exoPlayer.currentPosition,
                            deltaMs = forwardSeconds * 1000L,
                            durationMs = exoPlayer.duration,
                        )
                        seekPlayerFromUserAction(exoPlayer, newPos)
                        danmakuManager.seekTo(newPos)
                        callbacks.onSeekCommitted(newPos)
                        callbacks.onSeekFeedback(forwardSeconds)
                    }
                    FullscreenDoubleTapAction.TogglePlayPause -> {
                        callbacks.triggerLockHaptic(HapticType.LIGHT)
                        togglePlayerPlaybackFromUserAction(exoPlayer)
                    }
                }
            },
            onLongPress = {
                if (!playerGesturesEnabled || !shouldHandlePortraitLongPressGesture(scale = callbacks.getScale())) {
                    return@detectPlayerTapGestures
                }
                if (!isCurrentPage) return@detectPlayerTapGestures
                val origin = exoPlayer.playbackParameters
                val applied = resolveLongPressPlaybackParameters(
                    requestedSpeed = longPressSpeedState.value,
                    currentAudioQuality = currentAudioQualityState.value,
                )
                callbacks.onStartLongPress(origin, applied)
            },
        )
    }
    .pointerInput(bvid, exoPlayer, isCurrentPage, playerGesturesEnabled) {
        if (!isCurrentPage || !playerGesturesEnabled) return@pointerInput
        var totalDragDistanceX = 0f
        try {
            detectHorizontalDragGestures(
                onDragStart = {
                    callbacks.setSeekGestureActive(false)
                    totalDragDistanceX = 0f
                    val duration = progressDurationState.value
                    if (
                        isCurrentPage &&
                        playerGesturesEnabled &&
                        duration > 0L &&
                        size.width > 0 &&
                        shouldHandlePortraitSeekGesture(scale = callbacks.getScale())
                    ) {
                        val startPos = exoPlayer.currentPosition.coerceIn(0L, duration)
                        callbacks.setSeekStartPosition(startPos)
                        callbacks.setSeekTargetPosition(startPos)
                        callbacks.setSeekGestureActive(true)
                        if (seekGestureHapticPolicy.start(startPos, duration, SystemClock.uptimeMillis())) {
                            callbacks.triggerSeekHaptic(HapticType.LIGHT)
                        }
                    }
                },
                onDragEnd = {
                    try {
                        if (isCurrentPage && callbacks.isSeekGestureActive()) {
                            val targetPos = callbacks.getSeekTargetPosition()
                            seekPlayerFromUserAction(exoPlayer, targetPos)
                            danmakuManager.seekTo(targetPos)
                        }
                    } finally {
                        callbacks.setSeekGestureActive(false)
                        totalDragDistanceX = 0f
                        seekGestureHapticPolicy.finish()
                    }
                },
                onDragCancel = {
                    callbacks.setSeekGestureActive(false)
                    totalDragDistanceX = 0f
                    seekGestureHapticPolicy.finish()
                },
                onHorizontalDrag = { _, dragAmount ->
                    val duration = progressDurationState.value
                    val interactionSettings = playerInteractionSettingsState.value
                    if (isCurrentPage && callbacks.isSeekGestureActive() && duration > 0L) {
                        totalDragDistanceX += dragAmount
                        val seekDelta = resolveHorizontalSeekDeltaMs(
                            totalDragDistanceX = totalDragDistanceX,
                            containerWidthPx = size.width.toFloat(),
                            gestureSensitivity = interactionSettings.gestureSensitivity,
                            durationMs = duration,
                            fixedSeekEnabled = interactionSettings.fullscreenSwipeSeekEnabled,
                            fixedSeekSeconds = interactionSettings.swipeSeekSeconds,
                        )
                        if (seekDelta != null) {
                            val targetPos = (callbacks.getSeekStartPosition() + seekDelta).coerceIn(0L, duration)
                            callbacks.setSeekTargetPosition(targetPos)
                        }
                        if (seekGestureHapticPolicy.update(callbacks.getSeekTargetPosition(), SystemClock.uptimeMillis())) {
                            callbacks.triggerSeekHaptic(HapticType.GESTURE_STEP)
                        }
                    }
                },
            )
        } finally {
            callbacks.setSeekGestureActive(false)
            seekGestureHapticPolicy.finish()
        }
    }
