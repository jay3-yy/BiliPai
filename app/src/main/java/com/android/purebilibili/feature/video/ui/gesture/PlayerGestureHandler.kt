// File: feature/video/ui/gesture/PlayerGestureHandler.kt
package com.android.purebilibili.feature.video.ui.gesture

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
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
 * Create tap gesture detector
 */
suspend fun PointerInputScope.detectPlayerTapGestures(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit
) {
    detectTapGestures(
        onTap = { onTap() },
        onDoubleTap = { onDoubleTap() }
    )
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
