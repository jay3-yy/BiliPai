// File: feature/video/ui/gesture/PlayerGestureHandler.kt
package com.android.purebilibili.feature.video.ui.gesture

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
// 🌈 Material Icons Extended - 亮度图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import kotlin.math.abs
// gesture level overlay helpers are in the same package

/**
 * Player Gesture Handler
 * 
 * Unified gesture handling for video player:
 * - Brightness adjustment (left side drag)
 * - Volume adjustment (right side drag)
 * - Seek (middle drag)
 * - Tap to show/hide controls
 * - Double tap to play/pause
 * 
 * Requirement Reference: AC6.1 - Unified PlayerGestureHandler
 */

/**
 * Gesture mode enum
 */
enum class GestureMode {
    None,
    Brightness,
    Volume,
    Seek
}

/**
 * Player gesture state
 */
@Stable
class PlayerGestureState(
    private val context: Context,
    private val audioManager: AudioManager,
    private val maxVolume: Int
) {
    var gestureMode by mutableStateOf(GestureMode.None)
    var gestureValue by mutableFloatStateOf(0f)
    var dragDelta by mutableFloatStateOf(0f)
    var seekPreviewPosition by mutableLongStateOf(0L)
    var currentBrightness by mutableFloatStateOf(
        try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) { 0.5f }
    )
    
    /**
     * Start drag gesture
     */
    fun onDragStart(
        offsetX: Float,
        screenWidth: Float,
        currentPosition: Long
    ) {
        dragDelta = 0f
        
        gestureMode = when {
            offsetX < screenWidth * 0.3f -> {
                gestureValue = currentBrightness
                GestureMode.Brightness
            }
            offsetX > screenWidth * 0.7f -> {
                gestureValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                GestureMode.Volume
            }
            else -> {
                seekPreviewPosition = currentPosition
                GestureMode.Seek
            }
        }
    }
    
    /**
     * Handle drag
     */
    fun onDrag(
        dragAmountX: Float,
        dragAmountY: Float,
        screenWidth: Float,
        screenHeight: Float,
        currentPosition: Long,
        duration: Long
    ): Float {
        when (gestureMode) {
            GestureMode.Brightness -> {
                gestureValue = (gestureValue - dragAmountY / screenHeight).coerceIn(0f, 1f)
                currentBrightness = gestureValue
                (context as? Activity)?.window?.let { window ->
                    val params = window.attributes
                    params.screenBrightness = gestureValue
                    window.attributes = params
                }
                return gestureValue
            }
            GestureMode.Volume -> {
                gestureValue = (gestureValue - dragAmountY / screenHeight).coerceIn(0f, 1f)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (gestureValue * maxVolume).toInt(),
                    0
                )
                return gestureValue
            }
            GestureMode.Seek -> {
                dragDelta += dragAmountX
                val seekDelta = (dragDelta / screenWidth * duration).toLong()
                seekPreviewPosition = (currentPosition + seekDelta).coerceIn(0L, duration)
                return (seekPreviewPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            else -> return 0f
        }
    }
    
    /**
     * End drag gesture
     */
    fun onDragEnd(onSeek: (Long) -> Unit) {
        if (gestureMode == GestureMode.Seek && abs(dragDelta) > 20f) {
            onSeek(seekPreviewPosition)
        }
        gestureMode = GestureMode.None
    }
    
    /**
     * Cancel drag gesture
     */
    fun onDragCancel() {
        gestureMode = GestureMode.None
    }
    
    /**
     * Get display value for gesture indicator
     */
    fun getDisplayValue(): Float {
        return when (gestureMode) {
            GestureMode.Brightness -> currentBrightness
            GestureMode.Volume -> gestureValue
            GestureMode.Seek -> (seekPreviewPosition.toFloat() / 1000f) // Will be formatted
            else -> 0f
        }
    }
}

/**
 * Remember player gesture state
 */
@Composable
fun rememberPlayerGestureState(): PlayerGestureState {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    return remember(context, audioManager, maxVolume) {
        PlayerGestureState(context, audioManager, maxVolume)
    }
}

/**
 * Gesture Indicator
 * 
 * Displays current gesture mode and value
 * 🖼️ [修复] 添加 videoshotData 支持，在 Seek 模式下显示视频缩略图
 */
@Composable
fun GestureIndicator(
    mode: GestureMode,
    value: Float,
    seekTime: Long?,
    duration: Long,
    modifier: Modifier = Modifier,
    // 🖼️ [新增] 视频预览数据
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    currentPositionMs: Long = 0L
) {
    if (mode == GestureMode.None) return
    
    when (mode) {
        GestureMode.Seek -> {
            // 🖼️ Seek 模式：显示带缩略图的预览气泡（居中显示）
            val targetPositionMs = seekTime ?: 0L
            
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                if (videoshotData != null && videoshotData.isValid) {
                    // 🖼️ 有缩略图：显示完整预览
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble(
                        videoshotData = videoshotData,
                        targetPositionMs = targetPositionMs,
                        currentPositionMs = currentPositionMs,
                        durationMs = duration,
                        offsetX = 0f,
                        containerWidth = 0f,
                        placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Centered
                    )
                } else {
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple(
                        targetPositionMs = targetPositionMs,
                        currentPositionMs = currentPositionMs,
                        offsetX = 0f,
                        containerWidth = 0f,
                        placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Centered
                    )
                }
            }
        }
        GestureMode.Brightness, GestureMode.Volume -> {
            val playerChromeProfile = rememberAppPlayerChromeProfile()
            val overlayStyle = rememberGestureLevelOverlayStyle(playerChromeProfile.tabPresentation)
            val mappedMode = if (mode == GestureMode.Brightness) {
                VideoGestureMode.Brightness
            } else {
                VideoGestureMode.Volume
            }
            val kind = resolveGestureLevelKind(mappedMode) ?: return
            val alignment = resolveGestureLevelOverlaySpec(
                style = overlayStyle,
                kind = kind,
                percent = value,
                brightnessColor = androidx.compose.ui.res.colorResource(com.android.purebilibili.R.color.player_brightness_accent),
            ).alignment
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = alignment
            ) {
                GestureLevelOverlayContent(
                    mode = mappedMode,
                    percent = value,
                    style = overlayStyle,
                    modifier = if (overlayStyle == GestureLevelOverlayStyle.Miuix) {
                        Modifier.padding(horizontal = 22.dp)
                    } else {
                        Modifier
                    }
                )
            }
        }
        else -> {}
    }
}

/**
 * A stable Compose hit target above passive Android rendering views. It does not consume
 * events: the parent owns tap/drag/long-press recognition. Interactive overlays are placed
 * above this surface, so their buttons and BAS links keep priority over player gestures.
 */
@Composable
internal fun PlayerGestureInputSurface(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent()
            }
        },
    )
}

/**
 * Only short presses can enter the double-tap stream. Long presses remain owned by
 * the optional speed callback or the separate drag detector; release always restores speed.
 * Distant taps are independent, and the second can still start a new double tap.
 */
suspend fun PointerInputScope.detectPlayerTapGestures(
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    doubleTapSlopPx: Float,
    captureSingleTap: (Offset) -> com.android.purebilibili.danmaku.engine.DanmakuClickCapture? = { null },
    onLongPress: ((Offset) -> Unit)? = null,
    onPressEnd: () -> Unit = {},
    detectDoubleTap: Boolean = true,
) {
    val doubleTapSlopSquared = doubleTapSlopPx * doubleTapSlopPx
    val danmakuSlopSquared = (2.dp.toPx()).let { it * it }
    awaitEachGesture {
        var down = awaitFirstDown()
        var captured: com.android.purebilibili.danmaku.engine.DanmakuClickCapture? = null
        var confirmed = false
        try {
            while (true) {
                down.consume()
                captured = captureSingleTap(down.position)
                confirmed = false
                val strictHit = captured != null
                var pressCancelled = false
                val up = withTimeoutOrNull(if (strictHit) 300L else viewConfiguration.longPressTimeoutMillis) {
                    waitForPlayerTapRelease(
                        down.position,
                        if (strictHit) danmakuSlopSquared else viewConfiguration.touchSlop * viewConfiguration.touchSlop,
                    ).also { pressCancelled = it == null }
                }
                if (up == null) {
                    captured?.cancel()
                    captured = null
                    // A cancelled drag must not turn into a speed gesture.
                    if (!pressCancelled) {
                        if (onLongPress != null) {
                            onLongPress(down.position)
                            // The speed gesture owns this press until every finger is released.
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        } else {
                            waitForUpOrCancellation()
                        }
                    }
                    return@awaitEachGesture
                }
                up.consume()
                if (!detectDoubleTap) {
                    captured?.invoke() ?: onTap(up.position)
                    confirmed = true
                    return@awaitEachGesture
                }
                val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    val minUptime = up.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
                    var candidate = awaitFirstDown()
                    while (candidate.uptimeMillis < minUptime) candidate = awaitFirstDown()
                    candidate
                }
                if (secondDown == null) {
                    captured?.invoke() ?: onTap(up.position)
                    confirmed = true
                    return@awaitEachGesture
                }
                if ((secondDown.position - down.position).getDistanceSquared() > doubleTapSlopSquared) {
                    captured?.invoke() ?: onTap(up.position)
                    confirmed = true
                    down = secondDown
                    continue
                }
                // A second tap belongs to playback; release the frozen item immediately.
                captured?.cancel()
                captured = null
                secondDown.consume()
                val secondUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation()
                }
                if (secondUp != null) {
                    secondUp.consume()
                    onDoubleTap(secondUp.position)
                }
                return@awaitEachGesture
            }
        } finally {
            if (!confirmed) captured?.cancel()
            onPressEnd()
        }
    }
}

/** Movement is checked on every event, including a finger that returns to its origin. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForPlayerTapRelease(
    origin: Offset,
    maxDistanceSquared: Float?,
): androidx.compose.ui.input.pointer.PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.size != 1 || event.changes.any {
                it.isConsumed || it.position.x < 0 || it.position.y < 0 ||
                    it.position.x > size.width || it.position.y > size.height ||
                    (maxDistanceSquared != null && (it.position - origin).getDistanceSquared() > maxDistanceSquared)
            }) return null
        val change = event.changes.first()
        if (!change.pressed) return change
        if (awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                .changes.any { it.isConsumed }) return null
    }
}

/**
 * Create drag gesture detector for player
 */
suspend fun PointerInputScope.detectPlayerDragGestures(
    gestureState: PlayerGestureState,
    currentPosition: Long,
    duration: Long,
    onShowControls: () -> Unit,
    onSeek: (Long) -> Unit,
    onProgressUpdate: (Float) -> Unit
) {
    val screenWidth = size.width.toFloat()
    val screenHeight = size.height.toFloat()
    
    detectDragGestures(
        onDragStart = { offset ->
            onShowControls()
            gestureState.onDragStart(offset.x, screenWidth, currentPosition)
        },
        onDragEnd = {
            gestureState.onDragEnd(onSeek)
        },
        onDragCancel = {
            gestureState.onDragCancel()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            val progress = gestureState.onDrag(
                dragAmount.x,
                dragAmount.y,
                screenWidth,
                screenHeight,
                currentPosition,
                duration
            )
            if (gestureState.gestureMode == GestureMode.Seek) {
                onProgressUpdate(progress)
            }
        }
    )
}
