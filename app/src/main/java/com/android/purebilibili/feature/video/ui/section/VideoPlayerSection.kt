// 文件路径: feature/video/VideoPlayerSection.kt
package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionDanmakuContainer
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionMaskStabilizer
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionModuleState
import com.android.purebilibili.feature.video.danmaku.FaceOcclusionVisualMask
import com.android.purebilibili.feature.video.danmaku.checkFaceOcclusionModuleState
import com.android.purebilibili.feature.video.danmaku.createFaceOcclusionDetector
import com.android.purebilibili.feature.video.danmaku.detectFaceOcclusionRegions
import com.android.purebilibili.feature.video.danmaku.installFaceOcclusionModule
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlay
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlCallbacks
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlUiState
import com.android.purebilibili.feature.video.ui.overlay.resolveBottomControlBarLayoutPolicy
import com.android.purebilibili.feature.video.ui.overlay.resolveVideoProgressBarLayoutPolicy
import com.android.purebilibili.feature.video.ui.components.SponsorSkipButton
import com.android.purebilibili.feature.video.ui.components.TwoFingerSpeedFeedbackOverlay
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout
import com.android.purebilibili.feature.video.ui.components.toFullscreenAspectRatio
import com.android.purebilibili.feature.video.ui.components.toVideoAspectRatio
import com.android.purebilibili.feature.video.ui.gesture.LockedTwoFingerSpeedAxis
import com.android.purebilibili.feature.video.ui.gesture.TwoFingerSpeedGestureMode
import com.android.purebilibili.feature.video.ui.gesture.resolveLockedTwoFingerSpeedAxis
import com.android.purebilibili.feature.video.ui.gesture.resolveTwoFingerGesturePlaybackSpeed
import com.android.purebilibili.feature.video.ui.gesture.resolveTwoFingerSpeedGestureMode
import com.android.purebilibili.feature.video.playback.policy.resolveDisplayedQualityId
import com.android.purebilibili.data.model.response.ViewPoint

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Path
import androidx.activity.compose.BackHandler
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
// 🌈 Material Icons Extended - 亮度图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackJankStateValue
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.resolveDefaultSubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleControlAvailability
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayModeByAutoPreference
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTextAt
import com.android.purebilibili.feature.video.subtitle.shouldRenderPrimarySubtitle
import com.android.purebilibili.feature.video.subtitle.shouldRenderSecondarySubtitle
import com.android.purebilibili.feature.video.usecase.playPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.shouldResumePlaybackAfterUserSeek
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import com.android.purebilibili.feature.video.util.captureAndSaveVideoScreenshot
import com.android.purebilibili.feature.video.playback.session.PlaybackSeekSessionState
import com.android.purebilibili.feature.video.playback.session.cancelPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.finishPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.shouldUsePlaybackSeekSessionPosition
import com.android.purebilibili.feature.video.playback.session.startPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.syncPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.updatePlaybackSeekInteraction
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

enum class VideoGestureMode { None, Brightness, Volume, Seek, SwipeToFullscreen }

private const val HI_RES_AUDIO_QUALITY_ID = 30251
private const val HI_RES_LONG_PRESS_SPEED_LIMIT = 1.5f
private const val LONG_PRESS_SPEED_LOCK_THRESHOLD_DP = 72
private const val FOREGROUND_SURFACE_RECOVERY_DELAY_MS = 80L
private const val FOREGROUND_SURFACE_RECOVERY_TIMEOUT_MS = 1200L
private const val PLAYBACK_STALL_LOG_THRESHOLD_MS = 700L
private const val PLAYER_DRAG_GESTURE_BOTTOM_EXCLUSION_BUFFER_DP = 12

internal data class LongPressSpeedStartDecision(
    val originalPlaybackParameters: PlaybackParameters,
    val targetPlaybackParameters: PlaybackParameters,
    val clearExistingLock: Boolean
)

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

internal fun resolveVideoPlayerBottomGestureExclusionHeightDp(
    controlBarBottomPaddingDp: Int,
    progressSpacingDp: Int,
    progressTouchHeightDp: Int,
    controlRowHeightDp: Int,
    extraBufferDp: Int = PLAYER_DRAG_GESTURE_BOTTOM_EXCLUSION_BUFFER_DP
): Int {
    return (
        controlBarBottomPaddingDp +
            progressSpacingDp +
            progressTouchHeightDp +
            controlRowHeightDp +
            extraBufferDp
        ).coerceAtLeast(0)
}

internal fun shouldIgnoreVideoPlayerDragStart(
    offsetY: Float,
    containerHeightPx: Float,
    edgeSafeZonePx: Float,
    bottomGestureExclusionPx: Float
): Boolean {
    if (containerHeightPx <= 0f) return false
    val clampedBottomExclusionPx = bottomGestureExclusionPx.coerceIn(0f, containerHeightPx)
    val inEdgeSafeZone = offsetY < edgeSafeZonePx || offsetY > (containerHeightPx - edgeSafeZonePx)
    val inBottomGestureExclusionZone = clampedBottomExclusionPx > 0f &&
        offsetY >= (containerHeightPx - clampedBottomExclusionPx)
    return inEdgeSafeZone || inBottomGestureExclusionZone
}

internal fun resolveEffectiveLongPressSpeed(
    requestedSpeed: Float,
    currentAudioQuality: Int,
    hiResSpeedLimit: Float = HI_RES_LONG_PRESS_SPEED_LIMIT
): Float {
    val normalized = requestedSpeed.coerceAtLeast(0.1f)
    return if (currentAudioQuality == HI_RES_AUDIO_QUALITY_ID) {
        normalized.coerceAtMost(hiResSpeedLimit)
    } else {
        normalized
    }
}

internal fun resolveLongPressPlaybackParameters(
    requestedSpeed: Float,
    currentAudioQuality: Int
): PlaybackParameters {
    return PlaybackParameters(
        resolveEffectiveLongPressSpeed(
            requestedSpeed = requestedSpeed,
            currentAudioQuality = currentAudioQuality
        ),
        1.0f
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
        clearExistingLock = longPressSpeedLocked
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

internal fun shouldEnableLongPressSpeedGesture(
    isScreenLocked: Boolean,
    scale: Float,
    isMultiTouchActive: Boolean
): Boolean {
    return !isScreenLocked && !isMultiTouchActive && scale <= 1.01f
}

internal fun shouldLockLongPressSpeedBySwipe(
    isLongPressing: Boolean,
    alreadyLocked: Boolean,
    totalDragDistanceY: Float,
    thresholdPx: Float
): Boolean {
    return isLongPressing &&
        !alreadyLocked &&
        thresholdPx > 0f &&
        totalDragDistanceY <= -thresholdPx
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
    if (!isFullscreen && portraitSwipeToFullscreenEnabled && isSwipeUp) {
        return VideoGestureMode.SwipeToFullscreen
    }
    if (!slideVolumeBrightnessEnabled && startX < leftZoneEnd) {
        return VideoGestureMode.None
    }
    if (!slideVolumeBrightnessEnabled && startX > rightZoneStart) {
        return VideoGestureMode.None
    }
    return when {
        startX < leftZoneEnd -> VideoGestureMode.Brightness
        startX > rightZoneStart -> VideoGestureMode.Volume
        else -> if (centerSwipeToFullscreenEnabled) {
            VideoGestureMode.SwipeToFullscreen
        } else {
            VideoGestureMode.None
        }
    }
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

internal fun resolveDanmakuLayerTopOffsetPx(
    isFullscreen: Boolean,
    statusBarHeightPx: Int
): Int {
    return 0
}

internal fun resolveHorizontalSeekDeltaMs(
    isFullscreen: Boolean,
    fullscreenSwipeSeekEnabled: Boolean,
    totalDragDistanceX: Float,
    containerWidthPx: Float,
    fullscreenSwipeSeekSeconds: Int?,
    gestureSensitivity: Float
): Long? {
    if (isFullscreen && fullscreenSwipeSeekEnabled) {
        val seekSeconds = fullscreenSwipeSeekSeconds ?: return null
        val stepWidthPx = (containerWidthPx / 8f).coerceAtLeast(1f)
        val stepCount = (totalDragDistanceX / stepWidthPx).toInt()
        val steppedDelta = stepCount * seekSeconds * 1000L
        if (steppedDelta != 0L) return steppedDelta
    }
    return (totalDragDistanceX * 200f * gestureSensitivity).toLong()
}

internal fun shouldCommitGestureSeek(
    currentPositionMs: Long,
    targetPositionMs: Long,
    minDeltaMs: Long = 300L
): Boolean {
    return abs(targetPositionMs - currentPositionMs) >= minDeltaMs
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

internal fun shouldAllowPlaybackStateAutoFullscreen(
    smallestScreenWidthDp: Int
): Boolean {
    return smallestScreenWidthDp < 600
}

internal fun resolveGestureIndicatorLabel(mode: VideoGestureMode): String {
    return when (mode) {
        VideoGestureMode.Brightness -> "亮度"
        VideoGestureMode.Volume -> "音量"
        else -> ""
    }
}

internal fun resolveGestureDisplayIcon(
    mode: VideoGestureMode,
    percent: Float,
    fallbackIcon: ImageVector?
): ImageVector {
    val normalizedPercent = percent.coerceIn(0f, 1f)
    return when (mode) {
        VideoGestureMode.Brightness -> when {
            normalizedPercent < 0.34f -> CupertinoIcons.Outlined.SunMax
            else -> CupertinoIcons.Default.SunMax
        }
        VideoGestureMode.Volume -> when {
            normalizedPercent < 0.01f -> CupertinoIcons.Default.SpeakerSlash
            normalizedPercent < 0.5f -> CupertinoIcons.Default.Speaker
            else -> CupertinoIcons.Default.SpeakerWave2
        }
        else -> fallbackIcon ?: CupertinoIcons.Filled.SunMax
    }
}

internal data class GestureLevelOverlayVisualPolicy(
    val accentColor: Color,
    val containerAlpha: Float,
    val borderAlpha: Float,
    val glowAlpha: Float
)

internal data class VideoGestureMotionSpec(
    val digitBlurResetDurationMillis: Int,
    val digitAlphaResetDurationMillis: Int,
    val digitEnterFadeDurationMillis: Int,
    val digitExitFadeDurationMillis: Int,
    val digitScaleDurationMillis: Int,
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
        digitBlurResetDurationMillis = 220,
        digitAlphaResetDurationMillis = 220,
        digitEnterFadeDurationMillis = 130,
        digitExitFadeDurationMillis = 120,
        digitScaleDurationMillis = 200,
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
            containerAlpha = 0.20f + progress * 0.08f,
            borderAlpha = 0.52f + progress * 0.22f,
            glowAlpha = 0.30f + progress * 0.40f
        )

        VideoGestureMode.Volume -> GestureLevelOverlayVisualPolicy(
            accentColor = Color(0xFF80DEEA),
            containerAlpha = 0.19f + progress * 0.08f,
            borderAlpha = 0.50f + progress * 0.20f,
            glowAlpha = 0.28f + progress * 0.38f
        )

        else -> GestureLevelOverlayVisualPolicy(
            accentColor = Color.White,
            containerAlpha = 0.22f,
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

internal fun shouldUseTextureSurfaceForFlip(
    isFlippedHorizontal: Boolean,
    isFlippedVertical: Boolean
): Boolean {
    return isFlippedHorizontal || isFlippedVertical
}

internal fun shouldEnableLivePlayerSharedElement(
    transitionEnabled: Boolean,
    allowLivePlayerSharedElement: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean
): Boolean {
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
    hasStartedSmoothReveal: Boolean
): Boolean {
    return forceCoverDuringReturnAnimation ||
        shouldKeepCoverForManualStart ||
        !isFirstFrameRendered ||
        !hasStartedSmoothReveal
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

internal fun shouldKeepCoverForManualStart(
    playWhenReady: Boolean,
    currentPositionMs: Long
): Boolean {
    return !playWhenReady && currentPositionMs <= 0L
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

internal fun shouldFillPlayerViewportForManualStartCover(
    shouldKeepCoverForManualStart: Boolean,
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return shouldKeepCoverForManualStart && !forceCoverDuringReturnAnimation
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
        endPaddingDp = 24,
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

private const val VIDEO_PLAYER_COVER_FADE_ENTER_DURATION_MILLIS = 200
private const val VIDEO_PLAYER_COVER_FADE_EXIT_DURATION_MILLIS = 300
private const val VIDEO_PLAYER_COVER_REVEAL_HOLD_DELAY_MILLIS = 96
private const val VIDEO_PLAYER_SURFACE_REVEAL_DURATION_MILLIS = 220
private const val VIDEO_PLAYER_SURFACE_REVEAL_INITIAL_SCALE = 0.985f

internal fun resolveVideoPlayerCoverMotionSpec(
    forceCoverDuringReturnAnimation: Boolean
): VideoPlayerCoverMotionSpec {
    return VideoPlayerCoverMotionSpec(
        shouldAnimateFade = !forceCoverDuringReturnAnimation,
        enterFadeDurationMillis = VIDEO_PLAYER_COVER_FADE_ENTER_DURATION_MILLIS,
        exitFadeDurationMillis = VIDEO_PLAYER_COVER_FADE_EXIT_DURATION_MILLIS
    )
}

internal fun resolveVideoPlayerRevealMotionSpec(): VideoPlayerRevealMotionSpec {
    return VideoPlayerRevealMotionSpec(
        coverRevealHoldDelayMillis = VIDEO_PLAYER_COVER_REVEAL_HOLD_DELAY_MILLIS,
        surfaceRevealDurationMillis = VIDEO_PLAYER_SURFACE_REVEAL_DURATION_MILLIS,
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

internal fun shouldShowInlinePlayerView(
    isPortraitFullscreen: Boolean,
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return !isPortraitFullscreen && !forceCoverDuringReturnAnimation
}

internal fun shouldEnableCoverImageCrossfade(
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return !forceCoverDuringReturnAnimation
}

internal fun resolvePreferredVideoCoverUrl(
    entryCoverUrl: String,
    detailCoverUrl: String
): String {
    val normalizedEntryCoverUrl = entryCoverUrl.trim()
    if (normalizedEntryCoverUrl.isNotEmpty()) return normalizedEntryCoverUrl

    val normalizedDetailCoverUrl = detailCoverUrl.trim()
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
    val sourceRouteBase = sourceRoute?.substringBefore("?")
    // 允许所有卡片列表页面（首页、历史、收藏、搜索等）使用返回封面共享过渡
    val allowBySourceRoute = sourceRouteBase == null ||
        com.android.purebilibili.navigation.isVideoCardReturnTargetRoute(sourceRouteBase)
    return forceCoverDuringReturnAnimation &&
        transitionEnabled &&
        hasSharedTransitionScope &&
        hasAnimatedVisibilityScope &&
        allowBySourceRoute
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
    return hasVideoTrack &&
        playWhenReady &&
        playbackState == Player.STATE_READY &&
        currentPositionMs > 300L
}

internal fun shouldAutoHidePlayerChromeOnPlaybackStart(
    showControls: Boolean,
    hasAutoHiddenForCurrentVideo: Boolean,
    isPlaying: Boolean,
    isFirstFrameRendered: Boolean,
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return showControls &&
        !hasAutoHiddenForCurrentVideo &&
        isPlaying &&
        isFirstFrameRendered &&
        !forceCoverDuringReturnAnimation
}

internal fun shouldRebindPlayerSurfaceOnForeground(
    hasPlayerView: Boolean,
    isInPipMode: Boolean,
    videoWidth: Int,
    videoHeight: Int
): Boolean {
    return hasPlayerView && !isInPipMode
}

internal fun shouldStartForegroundSurfaceRecovery(
    hasPlayerView: Boolean,
    shouldBindInlinePlayerView: Boolean,
    isInPipMode: Boolean
): Boolean {
    return hasPlayerView && shouldBindInlinePlayerView && !isInPipMode
}

internal fun shouldKickPlaybackAfterSurfaceRecovery(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int
): Boolean {
    return playWhenReady &&
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
    forceCoverDuringReturnAnimation: Boolean
): Boolean {
    return !isPortraitFullscreen &&
        !forceCoverDuringReturnAnimation &&
        (hostLifecycleStarted || isInPipMode)
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

@Composable
private fun GesturePercentDigit(
    digit: Char?,
    shouldAnimate: Boolean,
    textStyle: TextStyle,
    textShadow: Shadow,
    slotWidth: androidx.compose.ui.unit.Dp,
    motionSpec: VideoGestureMotionSpec
) {
    val blurAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(digit, shouldAnimate) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (!shouldAnimate || digit == null) {
            blurAnim.snapTo(0f)
            alphaAnim.snapTo(1f)
            return@LaunchedEffect
        }
        blurAnim.snapTo(8f)
        alphaAnim.snapTo(0.55f)
        launch {
            blurAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = motionSpec.digitBlurResetDurationMillis)
            )
        }
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = motionSpec.digitAlphaResetDurationMillis)
        )
    }

    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            (fadeIn(animationSpec = tween(motionSpec.digitEnterFadeDurationMillis)) +
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(motionSpec.digitScaleDurationMillis)
                ))
                .togetherWith(
                    fadeOut(animationSpec = tween(motionSpec.digitExitFadeDurationMillis)) +
                        scaleOut(
                            targetScale = 1.1f,
                            animationSpec = tween(motionSpec.digitScaleDurationMillis)
                        )
                )
        },
        label = "gesture-percent-digit"
    ) { target ->
        if (target == null) {
            Spacer(modifier = Modifier.width(slotWidth))
        } else {
            Text(
                text = target.toString(),
                color = Color.White,
                style = textStyle.copy(shadow = textShadow),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(slotWidth)
                    .graphicsLayer { alpha = alphaAnim.value }
                    .blur(
                        radius = blurAnim.value.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
        }
    }
}

@Composable
private fun GesturePercentValue(
    percent: Int,
    previousPercent: Int,
    textStyle: TextStyle,
    textShadow: Shadow,
    modifier: Modifier = Modifier,
    motionSpec: VideoGestureMotionSpec
) {
    val digits = remember(percent) { resolveGesturePercentDigits(percent) }
    val changeMask = remember(previousPercent, percent) {
        resolveGesturePercentDigitChangeMask(previousPercent, percent)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        digits.forEachIndexed { index, digit ->
            key(index) {
                GesturePercentDigit(
                    digit = digit,
                    shouldAnimate = changeMask.getOrElse(index) { false },
                    textStyle = textStyle,
                    textShadow = textShadow,
                    slotWidth = 16.dp,
                    motionSpec = motionSpec
                )
            }
        }
        Text(
            text = "%",
            color = Color.White,
            style = textStyle.copy(shadow = textShadow),
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSection(
    playerState: VideoPlayerState,
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    transitionEnabled: Boolean = true,
    onToggleFullscreen: () -> Unit,
    onQualityChange: (Int, Long) -> Unit,
    onBack: () -> Unit,
    onHomeClick: (() -> Unit)? = null,
    onDanmakuInputClick: () -> Unit = {},
    // 🔗 [新增] 分享功能
    bvid: String = "",
    coverUrl: String = "",
    //  实验性功能：双击点赞
    onDoubleTapLike: () -> Unit = {},
    //  空降助手
    sponsorSegment: com.android.purebilibili.data.model.response.SponsorSegment? = null,
    showSponsorSkipButton: Boolean = false,
    onSponsorSkip: () -> Unit = {},
    onSponsorDismiss: () -> Unit = {},
    //  [新增] 重载视频回调
    onReloadVideo: () -> Unit = {},
    //  [新增] CDN 线路切换
    currentCdnIndex: Int = 0,
    cdnCount: Int = 1,
    onSwitchCdn: () -> Unit = {},
    onSwitchCdnTo: (Int) -> Unit = {},
    
    //  [新增] 音频模式
    isAudioOnly: Boolean = false,
    onAudioOnlyToggle: () -> Unit = {},
    
    //  [新增] 定时关闭
    sleepTimerMinutes: Int? = null,
    onSleepTimerChange: (Int?) -> Unit = {},
    
    // 🖼️ [新增] 视频预览图数据
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    
    // 📖 [新增] 视频章节数据
    viewPoints: List<ViewPoint> = emptyList(),
    sponsorMarkers: List<com.android.purebilibili.data.model.response.SponsorProgressMarker> = emptyList(),
    onUserSeek: (Long) -> Unit = {},
    
    // 📱 [新增] 竖屏全屏模式
    isVerticalVideo: Boolean = false,
    onPortraitFullscreen: () -> Unit = {},
    isPortraitFullscreen: Boolean = false,
    viewportWidthDpOverride: Int? = null,
    // 📲 [新增] 小窗模式
    // 📲 [新增] 小窗模式
    onPipClick: () -> Unit = {},
    // [New] Codec & Audio Params
    currentCodec: String = "hev1", 
    onCodecChange: (String) -> Unit = {},
    currentSecondCodec: String = "avc1",
    onSecondCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    onAudioQualityChange: (Int) -> Unit = {},
    // [New] Audio Language
    onAudioLangChange: (String) -> Unit = {},
    // 👀 [新增] 在线观看人数
    onlineCount: String = "",
    // [New Actions]
    onSaveCover: () -> Unit = {},
    onDownloadAudio: () -> Unit = {},
    // 🔁 [新增] 播放模式
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode = com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
    onPlayModeClick: () -> Unit = {},

    // [新增] 侧边栏抽屉数据与交互
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = {_,_ -> },
    relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo> = emptyList(),
    ugcSeason: com.android.purebilibili.data.model.response.UgcSeason? = null,
    isFollowed: Boolean = false,
    isLiked: Boolean = false,
    isCoined: Boolean = false,
    isFavorited: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onCoin: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onTriple: () -> Unit = {},  // [新增] 一键三连回调
    onPageSelect: (Int) -> Unit = {},
    forceCoverOnly: Boolean = false,
    allowLivePlayerSharedElement: Boolean = true,
    suppressSubtitleOverlay: Boolean = false,
    subtitleDisplayModePreferenceOverride: SubtitleDisplayMode? = null,
    onSubtitleDisplayModePreferenceOverrideChange: (SubtitleDisplayMode) -> Unit = {},
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val configuration = LocalConfiguration.current
    val uiLayoutWidthDp = remember(configuration.screenWidthDp, viewportWidthDpOverride) {
        (viewportWidthDpOverride ?: configuration.screenWidthDp).coerceAtLeast(1)
    }
    val uiLayoutPolicy = remember(uiLayoutWidthDp) {
        resolveVideoPlayerUiLayoutPolicy(
            widthDp = uiLayoutWidthDp
        )
    }
    val bottomControlBarLayoutPolicy = remember(uiLayoutWidthDp) {
        resolveBottomControlBarLayoutPolicy(widthDp = uiLayoutWidthDp)
    }
    val videoProgressBarLayoutPolicy = remember(uiLayoutWidthDp) {
        resolveVideoProgressBarLayoutPolicy(widthDp = uiLayoutWidthDp)
    }
    val bottomGestureExclusionHeightDp = remember(uiLayoutWidthDp) {
        resolveVideoPlayerBottomGestureExclusionHeightDp(
            controlBarBottomPaddingDp = bottomControlBarLayoutPolicy.bottomPaddingDp,
            progressSpacingDp = bottomControlBarLayoutPolicy.progressSpacingDp,
            progressTouchHeightDp = videoProgressBarLayoutPolicy.touchContainerHeightDp,
            controlRowHeightDp = bottomControlBarLayoutPolicy.playButtonSizeDp
        )
    }
    val gestureSeekFallbackDurationMs = remember(uiState) {
        (uiState as? PlayerUiState.Success)?.videoDurationMs ?: 0L
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val settingsScope = rememberCoroutineScope()

    // --- 新增：读取设置中的"详细统计信息"开关 ---
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    // 使用 rememberUpdatedState 确保重组时获取最新值（虽然在单一 Activity 生命周期内可能需要重启生效，但简单场景够用）
    val showStats by remember { mutableStateOf(prefs.getBoolean("show_stats", false)) }

    val playerInteractionSettings by com.android.purebilibili.core.store.SettingsManager
        .getPlayerInteractionSettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings(
                hiResLongPressCompatHintShown = com.android.purebilibili.core.store.SettingsManager
                    .getHiResLongPressCompatHintShownSync(context)
            ),
            lifecycle = lifecycleOwner.lifecycle
        )

    val gestureSensitivity = playerInteractionSettings.gestureSensitivity

    // 📱 [优化] realResolution 现在从 playerState.videoSize 计算（见下方）
    val doubleTapLikeEnabled = playerInteractionSettings.doubleTapLikeEnabled
    val doubleTapSeekEnabled = playerInteractionSettings.doubleTapSeekEnabled
    val portraitSwipeToFullscreenEnabled = playerInteractionSettings.portraitSwipeToFullscreenEnabled
    val centerSwipeToFullscreenEnabled = playerInteractionSettings.centerSwipeToFullscreenEnabled
    val slideVolumeBrightnessEnabled = playerInteractionSettings.slideVolumeBrightnessEnabled
    val setSystemBrightnessEnabled = playerInteractionSettings.setSystemBrightnessEnabled
    val pipNoDanmakuEnabled = playerInteractionSettings.pipNoDanmakuEnabled
    val seekForwardSeconds = playerInteractionSettings.seekForwardSeconds
    val seekBackwardSeconds = playerInteractionSettings.seekBackwardSeconds
    val fullscreenSwipeSeekSeconds = playerInteractionSettings.fullscreenSwipeSeekSeconds
    val fullscreenSwipeSeekEnabled = playerInteractionSettings.fullscreenSwipeSeekEnabled
    val fullscreenGestureReverse = playerInteractionSettings.fullscreenGestureReverse
    val autoEnterFullscreenEnabled = playerInteractionSettings.autoEnterFullscreenEnabled
    val autoExitFullscreenEnabled = playerInteractionSettings.autoExitFullscreenEnabled
    val allowPlaybackStateAutoFullscreen = remember(configuration.smallestScreenWidthDp) {
        shouldAllowPlaybackStateAutoFullscreen(
            smallestScreenWidthDp = configuration.smallestScreenWidthDp
        )
    }
    val fixedFullscreenAspectRatio = playerInteractionSettings.fixedFullscreenAspectRatio
    val subtitleAutoPreference = playerInteractionSettings.subtitleAutoPreference
    
    //  [新增] 双击跳转视觉反馈状态
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackVisible by remember { mutableStateOf(false) }
    
    //  [新增] 长按倍速设置和状态
    val longPressSpeed = playerInteractionSettings.longPressSpeed
    val twoFingerVerticalSpeedEnabled = playerInteractionSettings.twoFingerVerticalSpeedEnabled
    val twoFingerHorizontalSpeedEnabled = playerInteractionSettings.twoFingerHorizontalSpeedEnabled
    val twoFingerSpeedMode = remember(
        twoFingerVerticalSpeedEnabled,
        twoFingerHorizontalSpeedEnabled
    ) {
        resolveTwoFingerSpeedGestureMode(
            verticalEnabled = twoFingerVerticalSpeedEnabled,
            horizontalEnabled = twoFingerHorizontalSpeedEnabled
        )
    }
    val hiResCompatHintShownPersisted = playerInteractionSettings.hiResLongPressCompatHintShown
    var isLongPressing by remember { mutableStateOf(false) }
    var originalPlaybackParameters by remember(bvid) { mutableStateOf(PlaybackParameters.DEFAULT) }
    var effectiveLongPressSpeed by remember { mutableFloatStateOf(longPressSpeed) }
    var longPressSpeedFeedbackVisible by remember { mutableStateOf(false) }
    var longPressSpeedLocked by remember(bvid) { mutableStateOf(false) }
    var lockedLongPressSpeed by remember(bvid) { mutableFloatStateOf(1.0f) }
    var isMultiTouchActive by remember { mutableStateOf(false) }
    var twoFingerSpeedFeedbackVisible by remember { mutableStateOf(false) }
    var twoFingerSpeedFeedbackRevision by remember { mutableIntStateOf(0) }
    var twoFingerFeedbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var hasShownHiResCompatHintLocally by remember {
        mutableStateOf(
            com.android.purebilibili.core.store.SettingsManager
                .getHiResLongPressCompatHintShownSync(context)
        )
    }
    val hasShownHiResCompatHint = hiResCompatHintShownPersisted || hasShownHiResCompatHintLocally
    var hasAutoEnteredFullscreen by remember(bvid) { mutableStateOf(false) }

    LaunchedEffect(hiResCompatHintShownPersisted) {
        if (hiResCompatHintShownPersisted) {
            hasShownHiResCompatHintLocally = true
        }
    }

    LaunchedEffect(twoFingerSpeedFeedbackRevision) {
        if (twoFingerSpeedFeedbackRevision > 0) {
            delay(900)
            twoFingerSpeedFeedbackVisible = false
        }
    }
    
    //  [新增] 缓冲状态监听
    var isBuffering by remember { mutableStateOf(false) }
    var bufferingStartedAtMs by remember { mutableLongStateOf(0L) }
    var foregroundRecoveryGeneration by remember { mutableIntStateOf(0) }
    var foregroundRecoveryStartedAtMs by remember { mutableLongStateOf(0L) }
    var foregroundRecoveryStartPositionMs by remember { mutableLongStateOf(0L) }
    var hasRenderedFirstFrameSinceForegroundRecovery by remember { mutableStateOf(true) }
    var observedPlaybackSpeed by remember(playerState.player) {
        mutableFloatStateOf(playerState.player.playbackParameters.speed)
    }
    DisposableEffect(playerState.player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                val now = android.os.SystemClock.elapsedRealtime()
                if (playbackState == Player.STATE_BUFFERING) {
                    if (bufferingStartedAtMs == 0L) {
                        bufferingStartedAtMs = now
                        Logger.d("VideoPlayerSection") {
                            "🎬 Playback buffering started: pos=${playerState.player.currentPosition}, " +
                                "buffered=${playerState.player.bufferedPosition}, playWhenReady=${playerState.player.playWhenReady}"
                        }
                    }
                } else if (bufferingStartedAtMs != 0L) {
                    val bufferingDurationMs = (now - bufferingStartedAtMs).coerceAtLeast(0L)
                    if (shouldLogPlaybackStall(
                            bufferingDurationMs = bufferingDurationMs,
                            playWhenReady = playerState.player.playWhenReady,
                            currentPositionMs = playerState.player.currentPosition
                        )
                    ) {
                        Logger.w(
                            "VideoPlayerSection",
                            "⚠️ Playback stall recovered after ${bufferingDurationMs}ms: " +
                                "state=$playbackState, pos=${playerState.player.currentPosition}, " +
                                "buffered=${playerState.player.bufferedPosition}, speed=${playerState.player.playbackParameters.speed}"
                        )
                    }
                    bufferingStartedAtMs = 0L
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                observedPlaybackSpeed = playbackParameters.speed
            }
        }
        playerState.player.addListener(listener)
        // 初始化状态
        isBuffering = playerState.player.playbackState == Player.STATE_BUFFERING
        observedPlaybackSpeed = playerState.player.playbackParameters.speed
        onDispose {
            playerState.player.removeListener(listener)
        }
    }

    LaunchedEffect(observedPlaybackSpeed, longPressSpeedLocked, lockedLongPressSpeed, isLongPressing) {
        if (
            longPressSpeedLocked &&
            !isLongPressing &&
            abs(observedPlaybackSpeed - lockedLongPressSpeed) > 0.001f
        ) {
            longPressSpeedLocked = false
        }
    }

    val latestIsFullscreen by rememberUpdatedState(isFullscreen)
    val latestOnToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    DisposableEffect(
        playerState.player,
        autoEnterFullscreenEnabled,
        autoExitFullscreenEnabled,
        allowPlaybackStateAutoFullscreen,
        bvid
    ) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (
                    allowPlaybackStateAutoFullscreen &&
                    playbackState == Player.STATE_READY &&
                    autoEnterFullscreenEnabled &&
                    !hasAutoEnteredFullscreen &&
                    playerState.player.playWhenReady &&
                    !latestIsFullscreen
                ) {
                    hasAutoEnteredFullscreen = true
                    latestOnToggleFullscreen()
                }
                if (
                    allowPlaybackStateAutoFullscreen &&
                    playbackState == Player.STATE_ENDED &&
                    autoExitFullscreenEnabled &&
                    latestIsFullscreen
                ) {
                    latestOnToggleFullscreen()
                }
            }
        }
        playerState.player.addListener(listener)
        onDispose {
            playerState.player.removeListener(listener)
        }
    }

    // 📱 [优化] 复用 VideoPlayerState 中的视频尺寸状态，避免重复监听
    val videoSizeState by playerState.videoSize.collectAsStateWithLifecycle()
    val debugInfo by playerState.debugInfo.collectAsStateWithLifecycle()
    val diagnosticEvents by playerState.diagnosticEvents.collectAsStateWithLifecycle()
    val pendingUserAction by playerState.pendingUserAction.collectAsStateWithLifecycle()
    val playerDiagnosticLoggingEnabled by SettingsManager
        .getPlayerDiagnosticLoggingEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)

    // 控制器显示状态
    var showControls by remember { mutableStateOf(true) }
    var hasAutoHiddenControlsForCurrentVideo by remember(bvid) { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var faceVisualMasks by remember { mutableStateOf(emptyList<FaceOcclusionVisualMask>()) }
    val faceMaskStabilizer = remember { FaceOcclusionMaskStabilizer() }
    var smartOcclusionModuleState by remember { mutableStateOf(FaceOcclusionModuleState.Checking) }
    var smartOcclusionDownloadProgress by remember { mutableStateOf<Int?>(null) }
    
    // 🔒 [新增] 屏幕锁定状态（全屏时防误触）
    var isScreenLocked by remember { mutableStateOf(false) }

    var gestureMode by remember { mutableStateOf<VideoGestureMode>(VideoGestureMode.None) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    val gesturePercentDisplay by remember {
        derivedStateOf { (gesturePercent * 100f).roundToInt().coerceIn(0, 100) }
    }
    var previousGesturePercentDisplay by remember { mutableIntStateOf(gesturePercentDisplay) }
    var orientationHintVisible by remember { mutableStateOf(false) }
    var orientationHintText by remember { mutableStateOf(resolveOrientationSwitchHintText(isFullscreen)) }
    var hasObservedOrientationChange by remember { mutableStateOf(false) }
    val gestureMotionSpec = remember { resolveVideoGestureMotionSpec() }
    val forceCoverDuringReturnAnimation = shouldForceCoverDuringReturnAnimation(
        forceCoverOnly = forceCoverOnly
    )
    val shouldBindInlinePlayerView = remember(
        isPortraitFullscreen,
        hostLifecycleStarted,
        isInPipMode,
        forceCoverDuringReturnAnimation
    ) {
        shouldBindInlinePlayerViewToPlayer(
            isPortraitFullscreen = isPortraitFullscreen,
            hostLifecycleStarted = hostLifecycleStarted,
            isInPipMode = isInPipMode,
            forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
        )
    }

    // 进度手势相关状态
    var seekTargetTime by remember { mutableLongStateOf(0L) }
    var startPosition by remember { mutableLongStateOf(0L) }
    val currentSeekSessionCid = (uiState as? PlayerUiState.Success)?.info?.cid ?: 0L
    var sharedSeekSession by remember(bvid, currentSeekSessionCid) {
        mutableStateOf(
            syncPlaybackSeekSession(
                state = PlaybackSeekSessionState(),
                playbackPositionMs = playerState.player.currentPosition.coerceAtLeast(0L)
            )
        )
    }
    var isGestureVisible by remember { mutableStateOf(false) }
    TrackJankStateFlag(
        stateName = "video_player:gesture_visible",
        isActive = isGestureVisible
    )
    TrackJankStateValue(
        stateName = "video_player:gesture_mode",
        stateValue = gestureMode.takeUnless { it == VideoGestureMode.None }?.name
    )
    
    //  视频比例状态
    var currentAspectRatio by remember { mutableStateOf(fixedFullscreenAspectRatio.toVideoAspectRatio()) }
    
    //  [新增] 视频翻转状态
    var isFlippedHorizontal by remember { mutableStateOf(false) }
    var isFlippedVertical by remember { mutableStateOf(false) }

    // 记录手势开始时的初始值
    var startVolume by remember { mutableIntStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0f) }

    // 记录累计拖动距离
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
    // 记录手势起点 X（用于锁定分区，避免拖动过程横向漂移导致误判）
    var dragStartX by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(playerState.player, bvid, currentSeekSessionCid) {
        while (isActive) {
            sharedSeekSession = syncPlaybackSeekSession(
                state = sharedSeekSession,
                playbackPositionMs = playerState.player.currentPosition.coerceAtLeast(0L)
            )
            delay(200)
        }
    }

    fun getActivity(): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> context.baseContext as? Activity
        else -> null
    }

    //  [新增] 缩放和平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(fixedFullscreenAspectRatio) {
        currentAspectRatio = fixedFullscreenAspectRatio.toVideoAspectRatio()
    }

    DisposableEffect(Unit) {
        onDispose { playerViewRef = null }
    }

    LaunchedEffect(gesturePercentDisplay) {
        if (gesturePercentDisplay != previousGesturePercentDisplay) {
            previousGesturePercentDisplay = gesturePercentDisplay
        }
    }

    // [新增] 共享元素过渡支持
    val sharedTransitionScope = com.android.purebilibili.core.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    //  共享弹幕管理器（用于所有 seek 路径的一致同步）
    val danmakuManager = rememberDanmakuManager()
    val overlayDrawerHazeState = com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState()

    var rootModifier = Modifier
        .fillMaxSize()
        .clipToBounds()
        .background(Color.Black)
        .hazeSource(overlayDrawerHazeState)

    // 应用共享元素
    val livePlayerSharedElementEnabled = shouldEnableLivePlayerSharedElement(
            transitionEnabled = transitionEnabled,
            allowLivePlayerSharedElement = allowLivePlayerSharedElement,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null
        )
    if (bvid.isNotEmpty() && livePlayerSharedElementEnabled) {
         with(requireNotNull(sharedTransitionScope)) {
             rootModifier = rootModifier.sharedElement(
                 sharedContentState = rememberSharedContentState(key = "video-$bvid"),
                 animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                 boundsTransform = { _, _ ->
                     com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                 }
             )
         }
    }

    Box(
        modifier = rootModifier
            //  [新增] 处理双指缩放/平移，并在全屏时支持双指调倍速
            .pointerInput(isFullscreen, isInPipMode, isScreenLocked, twoFingerSpeedMode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalPanX = 0f
                    var totalPanY = 0f
                    var lockedAxis: LockedTwoFingerSpeedAxis? = null
                    var gestureStartSpeed = playerState.player.playbackParameters.speed
                    val directionThresholdPx = viewConfiguration.touchSlop * 1.5f
                    var observedMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) {
                            isMultiTouchActive = false
                            break
                        }
                        if (pressedCount < 2) {
                            if (observedMultiTouch) {
                                isMultiTouchActive = false
                                break
                            }
                            continue
                        }
                        observedMultiTouch = true
                        isMultiTouchActive = true

                        if (isLongPressing) {
                            if (
                                shouldRestorePlaybackParametersAfterLongPressRelease(
                                    wasLongPressing = isLongPressing,
                                    longPressSpeedLocked = longPressSpeedLocked,
                                    gestureEnded = true
                                )
                            ) {
                                playerState.player.playbackParameters = originalPlaybackParameters
                            }
                            isLongPressing = false
                            longPressSpeedFeedbackVisible = false
                            totalDragDistanceY = 0f
                        }

                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        totalPanX += pan.x
                        totalPanY += pan.y

                        val speedModeAllowed = isFullscreen &&
                            !isInPipMode &&
                            !isScreenLocked &&
                            twoFingerSpeedMode != TwoFingerSpeedGestureMode.Off

                        if (speedModeAllowed && lockedAxis == null) {
                            lockedAxis = resolveLockedTwoFingerSpeedAxis(
                                mode = twoFingerSpeedMode,
                                totalDragX = totalPanX,
                                totalDragY = totalPanY,
                                thresholdPx = directionThresholdPx
                            )
                            if (lockedAxis != null) {
                                gestureStartSpeed = playerState.player.playbackParameters.speed
                                showControls = false
                                isGestureVisible = false
                            }
                        }

                        if (speedModeAllowed && lockedAxis != null) {
                            val resolvedSpeed = resolveTwoFingerGesturePlaybackSpeed(
                                startSpeed = gestureStartSpeed,
                                mode = twoFingerSpeedMode,
                                totalDragX = totalPanX,
                                totalDragY = totalPanY,
                                containerWidthPx = size.width.toFloat(),
                                containerHeightPx = size.height.toFloat()
                            )
                            if (abs(playerState.player.playbackParameters.speed - resolvedSpeed) > 0.001f) {
                                playerState.player.setPlaybackSpeed(resolvedSpeed)
                            }
                            twoFingerFeedbackSpeed = resolvedSpeed
                            twoFingerSpeedFeedbackVisible = true
                            twoFingerSpeedFeedbackRevision++
                            event.changes.forEach { change ->
                                if (change.position != change.previousPosition) {
                                    change.consume()
                                }
                            }
                            continue
                        }

                        if (zoom != 1f || pan != Offset.Zero) {
                            scale = (scale * zoom).coerceIn(1f, 5f)

                            if (scale > 1f) {
                                val maxPanX = (size.width * scale - size.width) / 2
                                val maxPanY = (size.height * scale - size.height) / 2
                                panX = (panX + pan.x * scale).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + pan.y * scale).coerceIn(-maxPanY, maxPanY)
                                isGestureVisible = false
                                showControls = false
                            } else {
                                panX = 0f
                                panY = 0f
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
            //  先处理拖拽手势 (音量/亮度/进度)
            .pointerInput(
                isInPipMode,
                isScreenLocked,
                showControls,
                portraitSwipeToFullscreenEnabled,
                centerSwipeToFullscreenEnabled,
                fullscreenSwipeSeekSeconds,
                bottomGestureExclusionHeightDp,
                gestureSeekFallbackDurationMs
            ) {
                if (!isInPipMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // [新增] 如果处于缩放状态，禁用常规拖拽手势，优先处理平移
                            if (scale > 1.01f) {  // 留一点浮点数buffer
                                return@detectDragGestures
                            }
                            
                            // 🔒 锁定时禁用拖拽手势
                            if (isScreenLocked) {
                                return@detectDragGestures
                            }                
                            //  [新增] 边缘防误触检测
                            //  如果在屏幕顶部或底部区域开始滑动，则视为系统手势（如下拉通知栏），不触发播放器手势
                            val safeZonePx = with(localDensity) { 48.dp.toPx() }
                            val bottomGestureExclusionPx = if (showControls) {
                                with(localDensity) { bottomGestureExclusionHeightDp.dp.toPx() }
                            } else {
                                0f
                            }
                            val shouldIgnoreDragStart = shouldIgnoreVideoPlayerDragStart(
                                offsetY = offset.y,
                                containerHeightPx = size.height.toFloat(),
                                edgeSafeZonePx = safeZonePx,
                                bottomGestureExclusionPx = bottomGestureExclusionPx
                            )

                            if (shouldIgnoreDragStart) {
                                isGestureVisible = false
                                gestureMode = VideoGestureMode.None
                                dragStartX = -1f
                                // 不需要 return，直接不执行下面的初始化逻辑即可
                            } else {
                                isGestureVisible = true
                                gestureMode = VideoGestureMode.None
                                dragStartX = offset.x
                                totalDragDistanceY = 0f
                                totalDragDistanceX = 0f

                                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                startPosition = sharedSeekSession.sliderPositionMs.coerceAtLeast(0L)
                                seekTargetTime = startPosition

                                val attributes = getActivity()?.window?.attributes
                                val currentWindowBrightness = attributes?.screenBrightness ?: -1f

                                if (currentWindowBrightness < 0) {
                                    try {
                                        val sysBrightness = Settings.System.getInt(
                                            context.contentResolver,
                                            Settings.System.SCREEN_BRIGHTNESS
                                        )
                                        startBrightness = sysBrightness / 255f
                                    } catch (e: Exception) {
                                        startBrightness = 0.5f
                                    }
                                } else {
                                    startBrightness = currentWindowBrightness
                                }
                            }
                        },
                        onDragEnd = {
                            if (isLongPressing) {
                                if (
                                    shouldRestorePlaybackParametersAfterLongPressRelease(
                                        wasLongPressing = isLongPressing,
                                        longPressSpeedLocked = longPressSpeedLocked,
                                        gestureEnded = true
                                    )
                                ) {
                                    playerState.player.playbackParameters = originalPlaybackParameters
                                }
                                isLongPressing = false
                                longPressSpeedFeedbackVisible = false
                                totalDragDistanceY = 0f
                                totalDragDistanceX = 0f
                                isGestureVisible = false
                                gestureMode = VideoGestureMode.None
                                dragStartX = -1f
                                return@detectDragGestures
                            }
                            if (gestureMode == VideoGestureMode.Seek) {
                                val currentPosition = playerState.player.currentPosition
                                if (shouldCommitGestureSeek(
                                        currentPositionMs = currentPosition,
                                        targetPositionMs = sharedSeekSession.sliderPositionMs
                                    )
                                ) {
                                    val commitResult = finishPlaybackSeekInteraction(
                                        updatePlaybackSeekInteraction(
                                            state = sharedSeekSession,
                                            positionMs = sharedSeekSession.sliderPositionMs
                                        )
                                    )
                                    sharedSeekSession = commitResult.state
                                    seekPlayerFromUserAction(
                                        player = playerState.player,
                                        positionMs = commitResult.committedPositionMs,
                                        shouldResumePlaybackOverride = commitResult.shouldResumePlayback
                                    )
                                    danmakuManager.seekTo(commitResult.committedPositionMs)
                                } else {
                                    sharedSeekSession = cancelPlaybackSeekInteraction(sharedSeekSession)
                                }
                            } else if (gestureMode == VideoGestureMode.SwipeToFullscreen) {
                                //  阈值判定：上滑超过一定距离触发全屏
                                val swipeThreshold = 50.dp.toPx()
                                if (
                                    shouldTriggerFullscreenBySwipe(
                                        isFullscreen = isFullscreen,
                                        reverseGesture = fullscreenGestureReverse,
                                        totalDragDistanceY = totalDragDistanceY,
                                        thresholdPx = swipeThreshold
                                    )
                                ) {
                                    onToggleFullscreen()
                                    // 震动反馈 (可选)
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                        if (isFullscreen) {
                                            "👇 Swipe to exit fullscreen triggered"
                                        } else {
                                            "👆 Swipe to fullscreen triggered"
                                        }
                                    }
                                }
                            }
                            isGestureVisible = false
                            gestureMode = VideoGestureMode.None
                            dragStartX = -1f
                        },
                        onDragCancel = {
                            if (isLongPressing) {
                                if (
                                    shouldRestorePlaybackParametersAfterLongPressRelease(
                                        wasLongPressing = isLongPressing,
                                        longPressSpeedLocked = longPressSpeedLocked,
                                        gestureEnded = true
                                    )
                                ) {
                                    playerState.player.playbackParameters = originalPlaybackParameters
                                }
                                isLongPressing = false
                                longPressSpeedFeedbackVisible = false
                                totalDragDistanceX = 0f
                                totalDragDistanceY = 0f
                                isGestureVisible = false
                                gestureMode = VideoGestureMode.None
                                dragStartX = -1f
                                return@detectDragGestures
                            }
                            isGestureVisible = false
                            if (gestureMode == VideoGestureMode.Seek) {
                                sharedSeekSession = cancelPlaybackSeekInteraction(sharedSeekSession)
                            }
                            gestureMode = VideoGestureMode.None
                            dragStartX = -1f
                        },
                        //  [修复点] 使用 dragAmount 而不是 change.positionChange()
                        onDrag = { change, dragAmount ->
                            if (isLongPressing) {
                                if (
                                    !shouldEnableLongPressSpeedGesture(
                                        isScreenLocked = isScreenLocked,
                                        scale = scale,
                                        isMultiTouchActive = isMultiTouchActive
                                    )
                                ) {
                                    change.consume()
                                    return@detectDragGestures
                                }
                                totalDragDistanceY += dragAmount.y
                                val lockThresholdPx = LONG_PRESS_SPEED_LOCK_THRESHOLD_DP.dp.toPx()
                                if (
                                    shouldLockLongPressSpeedBySwipe(
                                        isLongPressing = isLongPressing,
                                        alreadyLocked = longPressSpeedLocked,
                                        totalDragDistanceY = totalDragDistanceY,
                                        thresholdPx = lockThresholdPx
                                    )
                                ) {
                                    longPressSpeedLocked = true
                                    lockedLongPressSpeed = effectiveLongPressSpeed
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    Toast.makeText(
                                        context,
                                        "已锁定 ${effectiveLongPressSpeed}x",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                change.consume()
                                return@detectDragGestures
                            }
                            // 如果手势不可见（即在 safe zone 中启动被忽略），则停止处理
                            if (!isGestureVisible && gestureMode == VideoGestureMode.None) {
                                // do nothing
                            } else {
                            
                            // [修复] 累积拖动距离，用于更准确的方向判断
                            totalDragDistanceX += dragAmount.x
                            totalDragDistanceY += dragAmount.y
                            
                            // [修复] 等待累积一定距离后再确定手势类型，避免初始噪声导致误判
                            val minDragThreshold = 20.dp.toPx()
                            val totalDrag = kotlin.math.hypot(totalDragDistanceX, totalDragDistanceY)

                            if (gestureMode == VideoGestureMode.None && totalDrag >= minDragThreshold) {
                                // [修复] 使用累积距离判断方向，而非单帧增量
                                if (abs(totalDragDistanceX) > abs(totalDragDistanceY)) {
                                    gestureMode = VideoGestureMode.Seek
                                    com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                        "🎯 Gesture: Seek (cumDx=$totalDragDistanceX, cumDy=$totalDragDistanceY)"
                                    }
                                } else {
                                    // 根据起始 X 坐标判断区域 (左1/3=亮度, 右1/3=音量, 中间1/3=功能区)
                                    val width = size.width.toFloat()
                                    // 使用 onDragStart 锁定的起点 X，避免拖动中横向偏移导致误触
                                    val startX = if (dragStartX >= 0f) dragStartX else change.position.x
                                    // 分区边界增加缓冲，避免中间区域在边界附近被误判
                                    val boundaryPadding = uiLayoutPolicy.gestureBoundaryPaddingDp.dp.toPx()
                                    val leftZoneEnd = (width / 3f - boundaryPadding).coerceAtLeast(0f)
                                    val rightZoneStart = (width * 2f / 3f + boundaryPadding).coerceAtMost(width)
                                    val isSwipeUp = totalDragDistanceY < -minDragThreshold

                                    gestureMode = resolveVerticalGestureMode(
                                        isFullscreen = isFullscreen,
                                        isSwipeUp = isSwipeUp,
                                        startX = startX,
                                        leftZoneEnd = leftZoneEnd,
                                        rightZoneStart = rightZoneStart,
                                        portraitSwipeToFullscreenEnabled = portraitSwipeToFullscreenEnabled,
                                        centerSwipeToFullscreenEnabled = centerSwipeToFullscreenEnabled,
                                        slideVolumeBrightnessEnabled = slideVolumeBrightnessEnabled
                                    )

                                    // 横屏中间 1/3 的垂直手势直接忽略，避免误触亮度/音量
                                    if (isFullscreen && gestureMode == VideoGestureMode.None) {
                                        isGestureVisible = false
                                        com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                            "🎯 Gesture ignored in center zone (fullscreen, startX=$startX, width=$width)"
                                        }
                                        return@detectDragGestures
                                    }

                                    com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                        "🎯 Gesture: $gestureMode (startX=$startX, width=$width, isFullscreen=$isFullscreen)"
                                    }
                                }
                            }

                            when (gestureMode) {
                                VideoGestureMode.SwipeToFullscreen -> {
                                    // 累积 Y 轴距离已在上方处理
                                }
                                VideoGestureMode.Seek -> {
                                    if (!sharedSeekSession.isSliderMoving) {
                                        sharedSeekSession = startPlaybackSeekInteraction(
                                            state = sharedSeekSession,
                                            positionMs = startPosition,
                                            shouldResumePlayback = shouldResumePlaybackAfterUserSeek(
                                                playWhenReadyBeforeSeek = playerState.player.playWhenReady,
                                                playbackStateBeforeSeek = playerState.player.playbackState
                                            )
                                        )
                                    }
                                    // 距离已在上方累积，直接计算目标位置
                                    val duration = resolveGestureSeekableDurationMs(
                                        playbackDurationMs = playerState.player.duration,
                                        fallbackDurationMs = gestureSeekFallbackDurationMs
                                    )
                                    val seekDelta = resolveHorizontalSeekDeltaMs(
                                        isFullscreen = isFullscreen,
                                        fullscreenSwipeSeekEnabled = fullscreenSwipeSeekEnabled,
                                        totalDragDistanceX = totalDragDistanceX,
                                        containerWidthPx = size.width.toFloat(),
                                        fullscreenSwipeSeekSeconds = fullscreenSwipeSeekSeconds,
                                        gestureSensitivity = gestureSensitivity
                                    )
                                    if (seekDelta != null) {
                                        seekTargetTime = (startPosition + seekDelta).coerceIn(0L, duration)
                                        sharedSeekSession = updatePlaybackSeekInteraction(
                                            state = sharedSeekSession,
                                            positionMs = seekTargetTime
                                        )
                                    }
                                }
                                VideoGestureMode.Brightness -> {
                                    // 距离已在上方累积，使用负值因为上滑是负 Y
                                    val screenHeight = context.resources.displayMetrics.heightPixels
                                    //  应用灵敏度
                                    val deltaPercent = -totalDragDistanceY / screenHeight * gestureSensitivity
                                    val newBrightness = (startBrightness + deltaPercent).coerceIn(0f, 1f)
                                    
                                    //  优化：仅在变化超过阈值时更新（减少 WindowManager 调用）
                                    if (kotlin.math.abs(newBrightness - gesturePercent) > 0.02f) {
                                        getActivity()?.window?.attributes = getActivity()?.window?.attributes?.apply {
                                            screenBrightness = newBrightness
                                        }
                                        if (setSystemBrightnessEnabled) {
                                            runCatching {
                                                if (Settings.System.canWrite(context)) {
                                                    val value = (newBrightness * 255f).roundToInt().coerceIn(1, 255)
                                                    Settings.System.putInt(
                                                        context.contentResolver,
                                                        Settings.System.SCREEN_BRIGHTNESS,
                                                        value
                                                    )
                                                }
                                            }
                                        }
                                        gesturePercent = newBrightness
                                    }
                                    //  亮度图标：CupertinoIcons SunMax (iOS SF Symbols 风格)
                                    gestureIcon = CupertinoIcons.Default.SunMax
                                }
                                VideoGestureMode.Volume -> {
                                    // 距离已在上方累积，使用负值因为上滑是负 Y
                                    val screenHeight = context.resources.displayMetrics.heightPixels
                                    //  应用灵敏度
                                    val deltaPercent = -totalDragDistanceY / screenHeight * gestureSensitivity
                                    val newVolPercent = ((startVolume.toFloat() / maxVolume) + deltaPercent).coerceIn(0f, 1f)
                                    val targetVol = (newVolPercent * maxVolume).toInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    gesturePercent = newVolPercent
                                    //  动态音量图标：3 级
                                    gestureIcon = when {
                                        gesturePercent < 0.01f -> CupertinoIcons.Default.SpeakerSlash
                                        gesturePercent < 0.5f -> CupertinoIcons.Default.Speaker
                                        else -> CupertinoIcons.Default.SpeakerWave2
                                    }
                                }
                                else -> {}
                            }
                            }
                        }
                    )
                }
            }
            //  点击/双击/长按手势在拖拽之后处理
            .pointerInput(
                seekForwardSeconds,
                seekBackwardSeconds,
                doubleTapSeekEnabled,
                longPressSpeed,
                isScreenLocked
            ) {
                detectTapGestures(
                    onTap = { 
                        // 🔒 锁定时点击只显示解锁按钮
                        if (isScreenLocked) {
                            showControls = !showControls  // 显示/隐藏解锁按钮
                        } else {
                            showControls = !showControls
                        }
                    },
                    onLongPress = {
                        if (
                            !shouldEnableLongPressSpeedGesture(
                                isScreenLocked = isScreenLocked,
                                scale = scale,
                                isMultiTouchActive = isMultiTouchActive
                            )
                        ) {
                            return@detectTapGestures
                        }
                        //  长按开始：保存原速度并应用长按倍速
                        val player = playerState.player
                        val startDecision = resolveLongPressSpeedStartDecision(
                            currentPlaybackParameters = player.playbackParameters,
                            previousOriginalPlaybackParameters = originalPlaybackParameters,
                            longPressSpeedLocked = longPressSpeedLocked,
                            requestedSpeed = longPressSpeed,
                            currentAudioQuality = currentAudioQuality
                        )
                        originalPlaybackParameters = startDecision.originalPlaybackParameters
                        if (startDecision.clearExistingLock) {
                            longPressSpeedLocked = false
                        }
                        effectiveLongPressSpeed = startDecision.targetPlaybackParameters.speed
                        player.playbackParameters = startDecision.targetPlaybackParameters
                        if (
                            shouldShowHiResLongPressCompatHint(
                                requestedSpeed = longPressSpeed,
                                effectiveSpeed = effectiveLongPressSpeed,
                                hasShownHint = hasShownHiResCompatHint
                            )
                        ) {
                            hasShownHiResCompatHintLocally = true
                            Toast.makeText(
                                context,
                                "Hi-Res 音源长按倍速最高 1.5x，以降低失真",
                                Toast.LENGTH_SHORT
                            ).show()
                            settingsScope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setHiResLongPressCompatHintShown(context, true)
                            }
                        }
                        isLongPressing = true
                        totalDragDistanceY = 0f
                        longPressSpeedFeedbackVisible = true
                        com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                            "⏩ LongPress: speed ${effectiveLongPressSpeed}x (requested=${longPressSpeed}x, audio=$currentAudioQuality)"
                        }
                    },
                    onDoubleTap = { offset ->
                        // 🔒 锁定时禁用双击
                        if (isScreenLocked) return@detectTapGestures
                        
                        val screenWidth = size.width
                        val player = playerState.player
                        
                        //  [新增] 读取双击跳转开关
                        // 注意：这里 directly accessing the state value captured in the closure
                        // We need to ensure we have access to the latest value. 
                        // Since `doubleTapSeekEnabled` is a state, we can read it here.
                        
                        // 逻辑：如果开启跳转 -> 以前的逻辑 (两侧跳转，中间暂停)
                        //      如果关闭跳转 -> 全屏双击均为暂停/播放 (解决长屏按不到暂停的问题)
                        
                        if (doubleTapSeekEnabled) {
                            when {
                                // 右侧 1/3：快进
                                offset.x > screenWidth * 2 / 3 -> {
                                    val seekMs = seekForwardSeconds * 1000L
                                    val newPos = (player.currentPosition + seekMs).coerceAtMost(player.duration.coerceAtLeast(0L))
                                    seekPlayerFromUserAction(player, newPos)
                                    danmakuManager.seekTo(newPos)
                                    seekFeedbackText = "+${seekForwardSeconds}s"
                                    seekFeedbackVisible = true
                                    com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                        "⏩ DoubleTap right: +${seekForwardSeconds}s"
                                    }
                                }
                                // 左侧 1/3：后退
                                offset.x < screenWidth / 3 -> {
                                    val seekMs = seekBackwardSeconds * 1000L
                                    val newPos = (player.currentPosition - seekMs).coerceAtLeast(0L)
                                    seekPlayerFromUserAction(player, newPos)
                                    danmakuManager.seekTo(newPos)
                                    seekFeedbackText = "-${seekBackwardSeconds}s"
                                    seekFeedbackVisible = true
                                    com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                        "⏪ DoubleTap left: -${seekBackwardSeconds}s"
                                    }
                                }
                                // 中间：暂停/播放
                                else -> {
                                    togglePlayerPlaybackFromUserAction(player)
                                    com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                        "⏯️ DoubleTap center: toggle play/pause"
                                    }
                                }
                            }
                        } else {
                            // 关闭跳转时，全屏双击暂停/播放
                            togglePlayerPlaybackFromUserAction(player)
                            com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                "⏯️ DoubleTap (Seek Disabled): toggle play/pause"
                            }
                        }
                    },
                    onPress = { offset ->
                        //  等待手指抬起
                        val released = tryAwaitRelease()
                        //  如果之前是长按状态，松开时恢复原速度
                        if (released && isLongPressing) {
                            if (
                                shouldRestorePlaybackParametersAfterLongPressRelease(
                                    wasLongPressing = isLongPressing,
                                    longPressSpeedLocked = longPressSpeedLocked,
                                    gestureEnded = true
                                )
                            ) {
                                playerState.player.playbackParameters = originalPlaybackParameters
                            }
                            isLongPressing = false
                            longPressSpeedFeedbackVisible = false
                            totalDragDistanceY = 0f
                            com.android.purebilibili.core.util.Logger.d("VideoPlayerSection") {
                                if (longPressSpeedLocked) {
                                    "🔒 LongPress locked: speed ${lockedLongPressSpeed}x"
                                } else {
                                    "⏹️ LongPress released: speed ${originalPlaybackParameters.speed}x"
                                }
                            }
                        }
                    }
                )
            }
    ) {
        val scope = rememberCoroutineScope()  //  用于设置弹幕开关
        val activeDanmakuScope = remember(isFullscreen) {
            com.android.purebilibili.core.store.resolveDanmakuSettingsScope(isLandscape = isFullscreen)
        }
        
        val danmakuSettings by com.android.purebilibili.core.store.SettingsManager
            .getDanmakuSettings(context, activeDanmakuScope)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.DanmakuSettings(),
                lifecycle = lifecycleOwner.lifecycle
            )
        val danmakuEnabled = danmakuSettings.enabled
        val danmakuOpacity = danmakuSettings.opacity
        val danmakuFontScale = danmakuSettings.fontScale
        val danmakuSpeed = danmakuSettings.speed
        val danmakuDisplayArea = danmakuSettings.displayArea
        val danmakuMergeDuplicates = danmakuSettings.mergeDuplicates
        val danmakuAllowScroll = danmakuSettings.allowScroll
        val danmakuAllowTop = danmakuSettings.allowTop
        val danmakuAllowBottom = danmakuSettings.allowBottom
        val danmakuAllowColorful = danmakuSettings.allowColorful
        val danmakuAllowSpecial = danmakuSettings.allowSpecial
        val danmakuSmartOcclusion = danmakuSettings.smartOcclusion
        val danmakuFullscreenPanelWidthMode by com.android.purebilibili.core.store.SettingsManager
            .getDanmakuFullscreenPanelWidthMode(context)
            .collectAsStateWithLifecycle(
                initialValue = com.android.purebilibili.core.store.DanmakuPanelWidthMode.THIRD,
                lifecycle = lifecycleOwner.lifecycle
            )
        val danmakuBlockRulesRaw = danmakuSettings.blockRulesRaw
        val danmakuBlockRules = danmakuSettings.blockRules
        val faceDetector = remember { createFaceOcclusionDetector() }
        DisposableEffect(faceDetector) {
            onDispose { faceDetector.close() }
        }

        LaunchedEffect(faceDetector) {
            smartOcclusionModuleState = FaceOcclusionModuleState.Checking
            smartOcclusionDownloadProgress = null
            smartOcclusionModuleState = checkFaceOcclusionModuleState(context, faceDetector)
        }
        val canSyncDanmakuCloud = (uiState as? PlayerUiState.Success)?.isLoggedIn == true
        var pendingDanmakuCloudSync by remember {
            mutableStateOf<com.android.purebilibili.data.repository.DanmakuCloudSyncSettings?>(null)
        }

        fun queueDanmakuCloudSync(
            enabled: Boolean = danmakuEnabled,
            allowScroll: Boolean = danmakuAllowScroll,
            allowTop: Boolean = danmakuAllowTop,
            allowBottom: Boolean = danmakuAllowBottom,
            allowColorful: Boolean = danmakuAllowColorful,
            allowSpecial: Boolean = danmakuAllowSpecial,
            opacity: Float = danmakuOpacity,
            displayAreaRatio: Float = danmakuDisplayArea,
            speed: Float = danmakuSpeed,
            fontScale: Float = danmakuFontScale
        ) {
            pendingDanmakuCloudSync = com.android.purebilibili.data.repository.DanmakuCloudSyncSettings(
                enabled = enabled,
                allowScroll = allowScroll,
                allowTop = allowTop,
                allowBottom = allowBottom,
                allowColorful = allowColorful,
                allowSpecial = allowSpecial,
                opacity = opacity,
                displayAreaRatio = displayAreaRatio,
                speed = speed,
                fontScale = fontScale
            )
        }
        
        //  当视频/开关状态变化时更新弹幕加载策略
        val cid = (uiState as? PlayerUiState.Success)?.info?.cid ?: 0L
        val aid = (uiState as? PlayerUiState.Success)?.info?.aid ?: 0L
        val danmakuDurationHintMs = playerState.player.duration.takeIf { it > 0 } ?: 0L
        val danmakuLoadPolicy = remember(cid, danmakuEnabled) {
            resolveVideoPlayerDanmakuLoadPolicy(
                cid = cid,
                danmakuEnabled = danmakuEnabled,
                durationHintMs = danmakuDurationHintMs
            )
        }
        //  直接加载弹幕，不再等待 duration；仓库层会回退到 metadata/fallback 段数。
        LaunchedEffect(cid, aid, danmakuEnabled, hostLifecycleStarted) {
            danmakuManager.isEnabled = danmakuLoadPolicy.shouldEnable
            if (!shouldLoadDanmakuForForegroundHost(
                    hostLifecycleStarted = hostLifecycleStarted,
                    shouldLoadImmediately = danmakuLoadPolicy.shouldLoadImmediately
                )
            ) {
                return@LaunchedEffect
            }

            android.util.Log.d(
                "VideoPlayerSection",
                "🎯 Loading danmaku for cid=$cid, aid=$aid, durationHint=${danmakuLoadPolicy.durationHintMs}ms"
            )
            danmakuManager.loadDanmaku(cid, aid, danmakuLoadPolicy.durationHintMs)
        }

        //  横竖屏/小窗切换后，重绑 surface 并在需要时主动恢复播放。
        LaunchedEffect(
            isFullscreen,
            isInPipMode,
            playerViewRef,
            shouldBindInlinePlayerView
        ) {
            val player = playerState.player
            if (!shouldBindInlinePlayerView) {
                playerViewRef?.player = null
                return@LaunchedEffect
            }
            val shouldRebindSurface = shouldRebindPlayerSurfaceOnForeground(
                hasPlayerView = playerViewRef != null,
                isInPipMode = isInPipMode,
                videoWidth = player.videoSize.width,
                videoHeight = player.videoSize.height
            )
            if (shouldRebindSurface) {
                playerViewRef?.let { playerView ->
                    rebindPlayerSurfaceIfNeeded(playerView = playerView, player = player)
                    Logger.d("VideoPlayerSection") {
                        "🎬 Foreground surface rebind applied to avoid audio-only resume"
                    }
                }
            }
            if (shouldKickPlaybackAfterSurfaceRecovery(
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                    playbackState = player.playbackState
                )
            ) {
                player.play()
            }
        }

        LaunchedEffect(
            foregroundRecoveryGeneration,
            playerViewRef,
            shouldBindInlinePlayerView,
            isInPipMode
        ) {
            if (foregroundRecoveryGeneration <= 0) return@LaunchedEffect
            if (!shouldStartForegroundSurfaceRecovery(
                    hasPlayerView = playerViewRef != null,
                    shouldBindInlinePlayerView = shouldBindInlinePlayerView,
                    isInPipMode = isInPipMode
                )
            ) {
                return@LaunchedEffect
            }

            delay(FOREGROUND_SURFACE_RECOVERY_DELAY_MS)
            val player = playerState.player
            playerViewRef?.let { playerView ->
                rebindPlayerSurfaceIfNeeded(playerView = playerView, player = player)
                Logger.d("VideoPlayerSection") {
                    "🎬 Foreground recovery retry: surface=${playerView.videoSurfaceView?.javaClass?.simpleName}, " +
                        "pos=${player.currentPosition}, state=${player.playbackState}, playing=${player.isPlaying}"
                }
            }
            if (shouldKickPlaybackAfterSurfaceRecovery(
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                    playbackState = player.playbackState
                )
            ) {
                player.play()
                Logger.d("VideoPlayerSection") {
                    "▶️ Foreground recovery kicked playback to rebuild render chain"
                }
            }

            delay(FOREGROUND_SURFACE_RECOVERY_TIMEOUT_MS)
            if (!shouldLogForegroundSurfaceRecoveryTimeout(
                    hasRenderedFirstFrameSinceRecovery = hasRenderedFirstFrameSinceForegroundRecovery,
                    playWhenReady = player.playWhenReady,
                    playbackState = player.playbackState
                )
            ) {
                return@LaunchedEffect
            }

            val elapsedMs = (android.os.SystemClock.elapsedRealtime() - foregroundRecoveryStartedAtMs)
                .coerceAtLeast(0L)
            val advancedPositionMs = (player.currentPosition - foregroundRecoveryStartPositionMs)
                .coerceAtLeast(0L)
            Logger.w(
                "VideoPlayerSection",
                "⚠️ Foreground recovery still missing first frame after ${elapsedMs}ms: " +
                    "state=${player.playbackState}, playing=${player.isPlaying}, playWhenReady=${player.playWhenReady}, " +
                    "pos=${player.currentPosition}, advanced=${advancedPositionMs}, buffered=${player.bufferedPosition}, " +
                    "surface=${playerViewRef?.videoSurfaceView?.javaClass?.simpleName}, viewAttached=${playerViewRef?.isAttachedToWindow}"
            )

            playerViewRef?.let { playerView ->
                rebindPlayerSurfaceIfNeeded(playerView = playerView, player = player)
            }
            if (shouldKickPlaybackAfterSurfaceRecovery(
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                    playbackState = player.playbackState
                )
            ) {
                player.play()
            }
        }

        LaunchedEffect(isFullscreen) {
            if (!hasObservedOrientationChange) {
                hasObservedOrientationChange = true
                return@LaunchedEffect
            }
            orientationHintText = resolveOrientationSwitchHintText(isFullscreen)
            orientationHintVisible = true
            delay(760)
            orientationHintVisible = false
        }
        
        //  弹幕设置变化时实时应用
        LaunchedEffect(
            danmakuOpacity,
            danmakuFontScale,
            danmakuSpeed,
            danmakuDisplayArea,
            danmakuMergeDuplicates,
            danmakuAllowScroll,
            danmakuAllowTop,
            danmakuAllowBottom,
            danmakuAllowColorful,
            danmakuAllowSpecial,
            danmakuBlockRules,
            danmakuSmartOcclusion
        ) {
            danmakuManager.updateSettings(
                opacity = danmakuOpacity,
                fontScale = danmakuFontScale,
                speed = danmakuSpeed,
                displayArea = danmakuDisplayArea,
                mergeDuplicates = danmakuMergeDuplicates,
                allowScroll = danmakuAllowScroll,
                allowTop = danmakuAllowTop,
                allowBottom = danmakuAllowBottom,
                allowColorful = danmakuAllowColorful,
                allowSpecial = danmakuAllowSpecial,
                blockedRules = danmakuBlockRules,
                // Mask-only mode: keep lane layout fixed, do not move danmaku tracks.
                smartOcclusion = false
            )
        }

        LaunchedEffect(
            playerViewRef,
            faceDetector,
            danmakuEnabled,
            danmakuSmartOcclusion,
            smartOcclusionModuleState,
            isInPipMode,
            isPortraitFullscreen
        ) {
            if (
                !danmakuEnabled ||
                !danmakuSmartOcclusion ||
                smartOcclusionModuleState != FaceOcclusionModuleState.Ready ||
                isInPipMode ||
                isPortraitFullscreen
            ) {
                faceMaskStabilizer.reset()
                faceVisualMasks = emptyList()
                return@LaunchedEffect
            }
            faceMaskStabilizer.reset()

            while (isActive) {
                val view = playerViewRef
                val player = playerState.player
                if (view == null || view.width <= 0 || view.height <= 0 || !player.isPlaying) {
                    kotlinx.coroutines.delay(1200L)
                    continue
                }

                val videoWidth = player.videoSize.width
                val videoHeight = player.videoSize.height
                val sampleWidth = 480
                val sampleHeight = when {
                    videoWidth > 0 && videoHeight > 0 -> (sampleWidth * videoHeight / videoWidth).coerceIn(270, 960)
                    else -> 270
                }

                val detection = withTimeoutOrNull(1_500L) {
                    detectFaceOcclusionRegions(
                        playerView = view,
                        sampleWidth = sampleWidth,
                        sampleHeight = sampleHeight,
                        detector = faceDetector
                    )
                } ?: com.android.purebilibili.feature.video.danmaku.FaceOcclusionDetectionResult(
                    verticalRegions = emptyList(),
                    maskRects = emptyList(),
                    visualMasks = emptyList()
                )
                faceVisualMasks = faceMaskStabilizer.step(detection.visualMasks)
                kotlinx.coroutines.delay(if (detection.visualMasks.isEmpty()) 1300L else 900L)
            }
        }

        // 账号云同步：用户修改弹幕设置后防抖上云，避免滑杆拖动时高频请求
        LaunchedEffect(pendingDanmakuCloudSync, canSyncDanmakuCloud) {
            val settings = pendingDanmakuCloudSync ?: return@LaunchedEffect
            if (!canSyncDanmakuCloud) return@LaunchedEffect

            kotlinx.coroutines.delay(700)
            val result = com.android.purebilibili.data.repository.DanmakuRepository
                .syncDanmakuCloudConfig(settings)
            if (result.isFailure) {
                android.util.Log.w(
                    "VideoPlayerSection",
                    "Danmaku cloud sync failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
        
        //  绑定 Player（不在 onDispose 中释放，单例保持状态）
        DisposableEffect(playerState.player) {
            android.util.Log.d("VideoPlayerSection", " attachPlayer, isFullscreen=$isFullscreen")
            danmakuManager.attachPlayer(playerState.player)
            onDispose {
                // 单例模式不需要释放
            }
        }
        
        //  [修复] 使用 LifecycleOwner 监听真正的 Activity 生命周期
        // DisposableEffect(Unit) 会在横竖屏切换时触发，导致 player 引用被清除
        //  [关键修复] 添加 ON_RESUME 事件，确保从其他视频返回后重新绑定弹幕播放器
        DisposableEffect(lifecycleOwner, playerState.player) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        //  [关键修复] 返回页面时重新绑定弹幕播放器
                        // 解决导航到其他视频后返回，弹幕暂停失效的问题
                        android.util.Log.d("VideoPlayerSection", " ON_RESUME: Re-attaching danmaku player")
                        danmakuManager.attachPlayer(playerState.player)
                        val player = playerState.player
                        if (!shouldBindInlinePlayerViewToPlayer(
                                isPortraitFullscreen = isPortraitFullscreen,
                                hostLifecycleStarted = true,
                                isInPipMode = isInPipMode,
                                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
                            )
                        ) {
                            return@LifecycleEventObserver
                        }
                        foregroundRecoveryGeneration += 1
                        foregroundRecoveryStartedAtMs = android.os.SystemClock.elapsedRealtime()
                        foregroundRecoveryStartPositionMs = player.currentPosition.coerceAtLeast(0L)
                        hasRenderedFirstFrameSinceForegroundRecovery = false
                        Logger.d("VideoPlayerSection") {
                            "🌅 ON_RESUME recovery start: pos=${player.currentPosition}, buffered=${player.bufferedPosition}, " +
                                "state=${player.playbackState}, playing=${player.isPlaying}, playWhenReady=${player.playWhenReady}, " +
                                "surface=${playerViewRef?.videoSurfaceView?.javaClass?.simpleName}"
                        }
                        val shouldRebindSurface = shouldRebindPlayerSurfaceOnForeground(
                            hasPlayerView = playerViewRef != null,
                            isInPipMode = isInPipMode,
                            videoWidth = player.videoSize.width,
                            videoHeight = player.videoSize.height
                        )
                        if (shouldRebindSurface) {
                            playerViewRef?.let { playerView ->
                                rebindPlayerSurfaceIfNeeded(playerView = playerView, player = player)
                                Logger.d("VideoPlayerSection") {
                                    "🎬 ON_RESUME surface rebind applied"
                                }
                            }
                        }
                        if (shouldKickPlaybackAfterSurfaceRecovery(
                                playWhenReady = player.playWhenReady,
                                isPlaying = player.isPlaying,
                                playbackState = player.playbackState
                            )
                        ) {
                            player.play()
                            Logger.d("VideoPlayerSection") {
                                "▶️ ON_RESUME kicked playback after surface recovery"
                            }
                        }
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                        android.util.Log.d("VideoPlayerSection", " ON_DESTROY: Clearing danmaku references")
                        danmakuManager.clearViewReference()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // --- [优化] 视频封面逻辑 ---
        // 使用 isFirstFrameRendered + smooth reveal 确保只有在首帧稳定后才揭开封面，避免黑屏和硬切。
        var isFirstFrameRendered by remember(bvid) { mutableStateOf(false) }
        var hasStartedSmoothReveal by remember(bvid, forceCoverDuringReturnAnimation) {
            mutableStateOf(false)
        }
        val keepCoverForManualStart = shouldKeepCoverForManualStart(
            playWhenReady = playerState.player.playWhenReady,
            currentPositionMs = playerState.player.currentPosition
        )
        val revealMotionSpec = remember {
            resolveVideoPlayerRevealMotionSpec()
        }
        val surfaceRevealSpec = remember(
            forceCoverDuringReturnAnimation,
            keepCoverForManualStart,
            hasStartedSmoothReveal,
            revealMotionSpec.surfaceRevealInitialScale
        ) {
            resolveVideoPlayerSurfaceRevealSpec(
                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
                shouldKeepCoverForManualStart = keepCoverForManualStart,
                hasStartedSmoothReveal = hasStartedSmoothReveal,
                surfaceRevealInitialScale = revealMotionSpec.surfaceRevealInitialScale
            )
        }
        val playerSurfaceAlpha by animateFloatAsState(
            targetValue = surfaceRevealSpec.alpha,
            animationSpec = tween(revealMotionSpec.surfaceRevealDurationMillis)
        )
        val playerSurfaceScale by animateFloatAsState(
            targetValue = surfaceRevealSpec.scale,
            animationSpec = tween(revealMotionSpec.surfaceRevealDurationMillis)
        )

        // 1. PlayerView (底层) - key 触发 graphicsLayer 强制更新
        //  [修复] 添加 isPortraitFullscreen 到 key，确保从全屏返回时重建 PlayerView 并重新绑定 Surface (解决黑屏问题)
        key(isFlippedHorizontal, isFlippedVertical, isPortraitFullscreen) {
            val viewportAspectRatio = if (isFullscreen) currentAspectRatio else VideoAspectRatio.FIT
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val viewportLayout = remember(maxWidth, maxHeight, viewportAspectRatio) {
                    with(density) {
                        resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = viewportAspectRatio
                        )
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val useTextureSurface = shouldUseTextureSurfaceForFlip(
                            isFlippedHorizontal = isFlippedHorizontal,
                            isFlippedVertical = isFlippedVertical
                        )
                        val basePlayerView = if (useTextureSurface) {
                            LayoutInflater.from(ctx)
                                .inflate(com.android.purebilibili.R.layout.view_player_texture, null, false) as PlayerView
                        } else {
                            PlayerView(ctx)
                        }
                        basePlayerView.apply {
                            playerViewRef = this
                            player = if (shouldBindInlinePlayerView) playerState.player else null
                            setKeepContentOnPlayerReset(
                                shouldKeepInlinePlayerContentOnReset(
                                    isPortraitFullscreen = isPortraitFullscreen,
                                    forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
                                )
                            )
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            useController = false
                            keepScreenOn = true
                            resizeMode = viewportAspectRatio.playerResizeMode
                            visibility = if (shouldShowInlinePlayerView(
                                    isPortraitFullscreen = isPortraitFullscreen,
                                    forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
                                )
                            ) {
                                View.VISIBLE
                            } else {
                                View.INVISIBLE
                            }
                        }
                    },
                    update = { playerView ->
                        playerViewRef = playerView
                        playerView.player = if (shouldBindInlinePlayerView) playerState.player else null
                        playerView.setKeepContentOnPlayerReset(
                            shouldKeepInlinePlayerContentOnReset(
                                isPortraitFullscreen = isPortraitFullscreen,
                                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
                            )
                        )
                        playerView.resizeMode = viewportAspectRatio.playerResizeMode
                        playerView.visibility = if (shouldShowInlinePlayerView(
                                isPortraitFullscreen = isPortraitFullscreen,
                                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
                            )
                        ) {
                            View.VISIBLE
                        } else {
                            View.INVISIBLE
                        }
                    },
                    modifier = with(density) {
                        Modifier
                            .size(
                                width = viewportLayout.width.toDp(),
                                height = viewportLayout.height.toDp()
                            )
                            .alpha(playerSurfaceAlpha)
                            .graphicsLayer {
                                val revealAwareScaleX = scale * playerSurfaceScale
                                val revealAwareScaleY = scale * playerSurfaceScale
                                scaleX = if (isFlippedHorizontal) -revealAwareScaleX else revealAwareScaleX
                                scaleY = if (isFlippedVertical) -revealAwareScaleY else revealAwareScaleY
                                translationX = panX
                                translationY = panY
                            }
                    }
                )
            }
        }
        
    DisposableEffect(playerState.player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.d("VideoPlayerCover", "🎬 onRenderedFirstFrame triggered")
                isFirstFrameRendered = true
                if (!hasRenderedFirstFrameSinceForegroundRecovery) {
                    hasRenderedFirstFrameSinceForegroundRecovery = true
                    val costMs = (android.os.SystemClock.elapsedRealtime() - foregroundRecoveryStartedAtMs)
                        .coerceAtLeast(0L)
                    Logger.d("VideoPlayerSection") {
                        "✅ Foreground recovery first frame rendered in ${costMs}ms: " +
                            "pos=${playerState.player.currentPosition}, buffered=${playerState.player.bufferedPosition}"
                    }
                }
            }
            
            // 兼容性：同时也监听 Events
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) {
                    android.util.Log.d("VideoPlayerCover", "🎬 EVENT_RENDERED_FIRST_FRAME triggered")
                    isFirstFrameRendered = true
                    if (!hasRenderedFirstFrameSinceForegroundRecovery) {
                        hasRenderedFirstFrameSinceForegroundRecovery = true
                        val costMs = (android.os.SystemClock.elapsedRealtime() - foregroundRecoveryStartedAtMs)
                            .coerceAtLeast(0L)
                        Logger.d("VideoPlayerSection") {
                            "✅ Foreground recovery first frame event received in ${costMs}ms: " +
                                "pos=${playerState.player.currentPosition}, buffered=${playerState.player.bufferedPosition}"
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 播放结束显示重播状态（通常由上层逻辑处理，这里不复位封面以免闪烁）
                    // isFirstFrameRendered = false 
                }
            }
        }
        
        playerState.player.addListener(listener)
        
        // 初始化检查：如果播放器已经开始播放且有进度，可能错过了事件
        // [Debug] Log initial check
        if (playerState.player.isPlaying && playerState.player.currentPosition > 0) {
             android.util.Log.d("VideoPlayerCover", "⚠️ Initial check: Already playing at ${playerState.player.currentPosition}, hiding cover. (Might be previous video?)")
             isFirstFrameRendered = true
        } else {
             android.util.Log.d("VideoPlayerCover", "✅ Initial check: Not playing or at start. Keeping cover.")
        }

        onDispose {
            playerState.player.removeListener(listener)
        }
    }
    
    // 4. 封面图 (Cover Image) - 始终在第一帧渲染前显示
    // 统一优先使用入口卡片封面，保证从各类列表进入详情时封面与入口一致。
    val detailCoverUrl = (uiState as? PlayerUiState.Success)?.info?.pic.orEmpty()
    val rawCoverUrl = resolvePreferredVideoCoverUrl(
        entryCoverUrl = coverUrl,
        detailCoverUrl = detailCoverUrl
    )
    
    // [Fix] 使用 FormatUtils 统一处理 URL (支持无协议头 URL)
    val currentCoverUrl = FormatUtils.fixImageUrl(rawCoverUrl)
    
    LaunchedEffect(playerState.player, bvid, forceCoverDuringReturnAnimation) {
        if (forceCoverDuringReturnAnimation || isFirstFrameRendered) return@LaunchedEffect
        while (isActive && !isFirstFrameRendered) {
            val player = playerState.player
            if (shouldPromoteFirstFrameByPlaybackFallback(
                    isFirstFrameRendered = isFirstFrameRendered,
                    forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
                    playbackState = player.playbackState,
                    playWhenReady = player.playWhenReady,
                    currentPositionMs = player.currentPosition,
                    videoWidth = player.videoSize.width,
                    videoHeight = player.videoSize.height
                )
            ) {
                android.util.Log.d(
                    "VideoPlayerCover",
                    "🎬 Fallback promoted first-frame state by playback progress"
                )
                isFirstFrameRendered = true
                break
            }
            delay(120L)
        }
    }
    LaunchedEffect(
        bvid,
        isFirstFrameRendered,
        forceCoverDuringReturnAnimation,
        keepCoverForManualStart
    ) {
        if (
            !shouldStartSmoothCoverReveal(
                isFirstFrameRendered = isFirstFrameRendered,
                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
                shouldKeepCoverForManualStart = keepCoverForManualStart
            )
        ) {
            hasStartedSmoothReveal = false
            return@LaunchedEffect
        }
        if (hasStartedSmoothReveal) return@LaunchedEffect
        delay(revealMotionSpec.coverRevealHoldDelayMillis.toLong())
        if (
            shouldStartSmoothCoverReveal(
                isFirstFrameRendered = isFirstFrameRendered,
                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
                shouldKeepCoverForManualStart = keepCoverForManualStart
            )
        ) {
            hasStartedSmoothReveal = true
        }
    }
    val showCover = shouldShowCoverImage(
        isFirstFrameRendered = isFirstFrameRendered,
        forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
        shouldKeepCoverForManualStart = keepCoverForManualStart,
        hasStartedSmoothReveal = hasStartedSmoothReveal
    )
    val showManualStartPlayButton = shouldShowManualStartPlayButton(
        shouldKeepCoverForManualStart = keepCoverForManualStart
    )
    val enableManualStartCoverOverlay = shouldEnableManualStartCoverOverlay(
        shouldKeepCoverForManualStart = keepCoverForManualStart
    )
    val fillPlayerViewportForManualStartCover = shouldFillPlayerViewportForManualStartCover(
        shouldKeepCoverForManualStart = keepCoverForManualStart,
        forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
    )
    val manualStartPlayButtonLayoutSpec = remember {
        resolveManualStartPlayButtonLayoutSpec()
    }

    LaunchedEffect(
        showControls,
        hasAutoHiddenControlsForCurrentVideo,
        isFirstFrameRendered,
        forceCoverDuringReturnAnimation,
        playerState.player.isPlaying
    ) {
        if (
            shouldAutoHidePlayerChromeOnPlaybackStart(
                showControls = showControls,
                hasAutoHiddenForCurrentVideo = hasAutoHiddenControlsForCurrentVideo,
                isPlaying = playerState.player.isPlaying,
                isFirstFrameRendered = isFirstFrameRendered,
                forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation
            )
        ) {
            showControls = false
            hasAutoHiddenControlsForCurrentVideo = true
        }
    }

    val coverMotionSpec = remember(forceCoverDuringReturnAnimation) {
        resolveVideoPlayerCoverMotionSpec(forceCoverDuringReturnAnimation)
    }
    val disableCoverFadeAnimation = !coverMotionSpec.shouldAnimateFade
    val forceReturnCoverSharedBoundsEnabled = shouldEnableForcedReturnCoverSharedBounds(
        forceCoverDuringReturnAnimation = forceCoverDuringReturnAnimation,
        transitionEnabled = transitionEnabled,
        hasSharedTransitionScope = sharedTransitionScope != null,
        hasAnimatedVisibilityScope = animatedVisibilityScope != null,
        sourceRoute = CardPositionManager.lastVideoSourceRoute
    )
    
    // [Debug] Logging
    LaunchedEffect(showCover, currentCoverUrl, isFirstFrameRendered, hasStartedSmoothReveal, uiState) {
        android.util.Log.d(
            "VideoPlayerCover",
            "🔍 Check: bvid=$bvid, showCover=$showCover, isFirstFrame=$isFirstFrameRendered, " +
                "hasStartedSmoothReveal=$hasStartedSmoothReveal, coverUrl=$coverUrl, finalUrl=$currentCoverUrl"
        )
    }

    AnimatedVisibility(
        visible = showCover && currentCoverUrl.isNotEmpty(),
        enter = if (disableCoverFadeAnimation) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(coverMotionSpec.enterFadeDurationMillis))
        },
        exit = if (disableCoverFadeAnimation) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(coverMotionSpec.exitFadeDurationMillis))
        },
        modifier = Modifier.zIndex(100f) // 返回中强制封面时，确保封面压住所有播放器层
    ) {
        val coverCardShape = RoundedCornerShape(12.dp)
        val forcedReturnCoverModifier = if (forceReturnCoverSharedBoundsEnabled) {
            with(requireNotNull(sharedTransitionScope)) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "video_cover_$bvid"),
                    animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                    boundsTransform = { _, _ -> com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec },
                    clipInOverlayDuringTransition = OverlayClip(coverCardShape)
                )
            }
        } else {
            Modifier
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val coverContainerModifier = if (fillPlayerViewportForManualStartCover) {
                Modifier
                    .matchParentSize()
                    .background(Color.Black)
            } else {
                forcedReturnCoverModifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .aspectRatio(VIDEO_SHARED_COVER_ASPECT_RATIO)
                    .clip(coverCardShape)
                    .background(Color.Black)
            }
            Box(
                modifier = coverContainerModifier
                    .clickable(enabled = enableManualStartCoverOverlay) {
                        playPlayerFromUserAction(playerState.player)
                    }
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(currentCoverUrl)
                        // [关键] 尝试使用首页卡片的缓存 Key 作为占位，实现无缝过渡
                        // 假设首页卡片使用的是普通模式 ("n")
                        .placeholderMemoryCacheKey("cover_${bvid}_n")
                        .listener(
                            onStart = { android.util.Log.d("VideoPlayerCover", "🖼️ Image loading started: $currentCoverUrl") },
                            onSuccess = { _, _ -> android.util.Log.d("VideoPlayerCover", "🖼️ Image loaded successfully") },
                            onError = { _, result -> android.util.Log.e("VideoPlayerCover", "❌ Image load failed: ${result.throwable.message}", result.throwable) }
                        )
                        .crossfade(shouldEnableCoverImageCrossfade(forceCoverDuringReturnAnimation))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (showManualStartPlayButton) {
                    if (manualStartPlayButtonLayoutSpec.showCoverScrim) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.18f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(
                                when (manualStartPlayButtonLayoutSpec.anchor) {
                                    ManualStartPlayButtonAnchor.Center -> Alignment.Center
                                    ManualStartPlayButtonAnchor.CenterEnd -> Alignment.CenterEnd
                                    ManualStartPlayButtonAnchor.BottomEnd -> Alignment.BottomEnd
                                }
                            )
                            .padding(
                                end = manualStartPlayButtonLayoutSpec.endPaddingDp.dp,
                                bottom = if (manualStartPlayButtonLayoutSpec.anchor == ManualStartPlayButtonAnchor.BottomEnd) {
                                    24.dp
                                } else {
                                    0.dp
                                }
                            )
                            .size(
                                width = manualStartPlayButtonLayoutSpec.iconWidthDp.dp,
                                height = manualStartPlayButtonLayoutSpec.iconHeightDp.dp
                            )
                            .clickable {
                                playPlayerFromUserAction(playerState.player)
                            },
                    ) {
                        if (manualStartPlayButtonLayoutSpec.showTopDecorations) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(x = (-11).dp, y = 4.dp)
                                    .size(width = 12.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.96f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(x = 11.dp, y = 4.dp)
                                    .size(width = 12.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.96f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(if (manualStartPlayButtonLayoutSpec.showTopDecorations) Alignment.BottomCenter else Alignment.Center)
                                .size(width = 58.dp, height = 46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.96f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play video",
                                tint = Color(0xFF4D5160),
                                modifier = Modifier
                                    .size(28.dp)
                                    .offset(x = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 2. DanmakuView (使用 ByteDance DanmakuRenderEngine - 覆盖在 PlayerView 上方)
    val shouldShowDanmakuLayer = !forceCoverDuringReturnAnimation && shouldShowDanmakuLayers(
        isInPipMode = isInPipMode,
        danmakuEnabled = danmakuEnabled,
        isPortraitFullscreen = isPortraitFullscreen,
        pipNoDanmakuEnabled = pipNoDanmakuEnabled,
        hostLifecycleStarted = hostLifecycleStarted
    )
    android.util.Log.d("VideoPlayerSection", "🔍 DanmakuView check: isInPipMode=$isInPipMode, danmakuEnabled=$danmakuEnabled, pipNoDanmakuEnabled=$pipNoDanmakuEnabled")
        if (shouldShowDanmakuLayer) {
            android.util.Log.d("VideoPlayerSection", " Conditions met, creating DanmakuView...")
            //  计算状态栏高度
            val statusBarHeightPx = remember(context) {
                val resourceId = context.resources.getIdentifier(
                    "status_bar_height", "dimen", "android"
                )
                if (resourceId > 0) {
                    context.resources.getDimensionPixelSize(resourceId)
                } else {
                    (24 * context.resources.displayMetrics.density).toInt()
                }
            }
            
            //  非全屏时的顶部偏移量
            val topOffset = resolveDanmakuLayerTopOffsetPx(
                isFullscreen = isFullscreen,
                statusBarHeightPx = statusBarHeightPx
            )
            
            //  [修复] 移除 key(isFullscreen)，避免横竖屏切换时重建 DanmakuView 导致弹幕消失
            // 使用 remember 保存 DanmakuView 引用，在 update 回调中处理尺寸变化
            val viewportAspectRatio = if (isFullscreen) currentAspectRatio else VideoAspectRatio.FIT
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isFullscreen) {
                            Modifier.padding(top = with(LocalContext.current.resources.displayMetrics) {
                                (topOffset / density).dp
                            })
                        } else Modifier
                    )
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val viewportLayout = remember(maxWidth, maxHeight, viewportAspectRatio) {
                    with(density) {
                        resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = viewportAspectRatio
                        )
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        FaceOcclusionDanmakuContainer(ctx).apply {
                            setMasks(faceVisualMasks)
                            setVideoViewport(
                                videoWidth = playerState.player.videoSize.width,
                                videoHeight = playerState.player.videoSize.height,
                                resizeMode = viewportAspectRatio.playerResizeMode
                            )
                            danmakuManager.attachView(danmakuView())
                            android.util.Log.d("VideoPlayerSection", " DanmakuView (RenderEngine) created, isFullscreen=$isFullscreen")
                        }
                    },
                    update = { container ->
                        container.setMasks(faceVisualMasks)
                        container.setVideoViewport(
                            videoWidth = playerState.player.videoSize.width,
                            videoHeight = playerState.player.videoSize.height,
                            resizeMode = viewportAspectRatio.playerResizeMode
                        )
                        val view = container.danmakuView()
                        //  [关键] 横竖屏切换后视图尺寸变化时，重新 attachView 确保弹幕正确显示
                        android.util.Log.d("VideoPlayerSection", " DanmakuView update: size=${view.width}x${view.height}, isFullscreen=$isFullscreen")
                        // 只有当视图有有效尺寸时才 re-attach
                        if (view.width > 0 && view.height > 0) {
                            val sizeTag = "${view.width}x${view.height}"
                            if (view.tag != sizeTag) {
                                view.tag = sizeTag
                                danmakuManager.attachView(view)
                            }
                        }
                    },
                    modifier = with(density) {
                        Modifier.size(
                            width = viewportLayout.width.toDp(),
                            height = viewportLayout.height.toDp()
                        )
                    }
                )
            }
        }
        
        // 3. 高级弹幕层 (Mode 7) - 覆盖在标准弹幕上方
        val advancedDanmakuList by danmakuManager.advancedDanmakuFlow.collectAsStateWithLifecycle()
        
        if (shouldShowDanmakuLayer && advancedDanmakuList.isNotEmpty()) {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                com.android.purebilibili.feature.video.ui.overlay.AdvancedDanmakuOverlay(
                    danmakuList = advancedDanmakuList,
                    player = playerState.player,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 4. B站字幕叠加层（支持中英双语）
        val subtitleFeatureEnabled = isSubtitleFeatureEnabledForUser()
        val subtitleBelongsToCurrentVideo = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            success.subtitleOwnerBvid == success.info.bvid &&
                success.subtitleOwnerCid == success.info.cid &&
                success.info.cid > 0L
        }
        val subtitlePrimaryAvailable = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            subtitleBelongsToCurrentVideo && success.subtitlePrimaryCues.isNotEmpty()
        }
        val subtitleSecondaryAvailable = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            subtitleBelongsToCurrentVideo && success.subtitleSecondaryCues.isNotEmpty()
        }
        val subtitlePrimaryTrackBound = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            subtitleBelongsToCurrentVideo &&
                (!success.subtitlePrimaryTrackKey.isNullOrBlank() || !success.subtitlePrimaryLanguage.isNullOrBlank())
        }
        val subtitleSecondaryTrackBound = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            subtitleBelongsToCurrentVideo &&
                (!success.subtitleSecondaryTrackKey.isNullOrBlank() || !success.subtitleSecondaryLanguage.isNullOrBlank())
        }
        val subtitlePrimaryLikelyAi = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            subtitleBelongsToCurrentVideo && success.subtitlePrimaryLikelyAi
        }
        val subtitleSecondaryLikelyAi = remember(uiState, subtitleFeatureEnabled) {
            if (!subtitleFeatureEnabled) return@remember false
            val success = uiState as? PlayerUiState.Success ?: return@remember false
            subtitleBelongsToCurrentVideo && success.subtitleSecondaryLikelyAi
        }
        val subtitleControlAvailability = remember(
            subtitleFeatureEnabled,
            subtitlePrimaryTrackBound,
            subtitleSecondaryTrackBound,
            subtitlePrimaryAvailable,
            subtitleSecondaryAvailable
        ) {
            if (!subtitleFeatureEnabled) {
                com.android.purebilibili.feature.video.subtitle.SubtitleControlAvailability(
                    trackAvailable = false,
                    primarySelectable = false,
                    secondarySelectable = false
                )
            } else {
                resolveSubtitleControlAvailability(
                    primaryTrackBound = subtitlePrimaryTrackBound,
                    secondaryTrackBound = subtitleSecondaryTrackBound,
                    primaryCueAvailable = subtitlePrimaryAvailable,
                    secondaryCueAvailable = subtitleSecondaryAvailable
                )
            }
        }
        val subtitleAutoModeMuted = remember(playerState.player, audioManager, bvid) {
            val systemMuted = runCatching {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0
            }.getOrDefault(false)
            systemMuted || playerState.player.volume <= 0f
        }
        val subtitleToggleKey = remember(uiState, bvid, subtitleAutoPreference) {
            val success = uiState as? PlayerUiState.Success
            if (success == null) {
                "no-subtitle"
            } else {
                "${bvid}_${success.info.cid}_${success.subtitlePrimaryLanguage}_${success.subtitleSecondaryLanguage}_${success.subtitlePrimaryLikelyAi}_${success.subtitleSecondaryLikelyAi}_${subtitleAutoPreference.name}"
            }
        }
        var localSubtitleDisplayModePreference by rememberSaveable("${subtitleToggleKey}_mode") {
            mutableStateOf(
                if (subtitleFeatureEnabled) {
                    resolveSubtitleDisplayModeByAutoPreference(
                        preference = subtitleAutoPreference,
                        hasPrimaryTrack = subtitlePrimaryTrackBound,
                        hasSecondaryTrack = subtitleSecondaryTrackBound,
                        primaryTrackLikelyAi = subtitlePrimaryLikelyAi,
                        secondaryTrackLikelyAi = subtitleSecondaryLikelyAi,
                        isMuted = subtitleAutoModeMuted
                    )
                } else {
                    SubtitleDisplayMode.OFF
                }
            )
        }
        var subtitleLargeTextByUser by rememberSaveable("${subtitleToggleKey}_large") {
            mutableStateOf(false)
        }
        val subtitleDisplayModePreference = subtitleDisplayModePreferenceOverride ?: localSubtitleDisplayModePreference
        val applySubtitleDisplayModePreferenceChange: (SubtitleDisplayMode) -> Unit = remember(
            subtitleDisplayModePreferenceOverride,
            onSubtitleDisplayModePreferenceOverrideChange
        ) {
            if (subtitleDisplayModePreferenceOverride != null) {
                onSubtitleDisplayModePreferenceOverrideChange
            } else {
                { mode -> localSubtitleDisplayModePreference = mode }
            }
        }
        val subtitleDisplayMode = remember(
            subtitleFeatureEnabled,
            subtitleBelongsToCurrentVideo,
            subtitleDisplayModePreference,
            subtitlePrimaryAvailable,
            subtitleSecondaryAvailable
        ) {
            if (!subtitleFeatureEnabled || !subtitleBelongsToCurrentVideo) {
                SubtitleDisplayMode.OFF
            } else {
                normalizeSubtitleDisplayMode(
                    preferredMode = subtitleDisplayModePreference,
                    hasPrimaryTrack = subtitlePrimaryAvailable,
                    hasSecondaryTrack = subtitleSecondaryAvailable
                )
            }
        }
        val subtitleOverlayEnabled = subtitleFeatureEnabled && subtitleDisplayMode != SubtitleDisplayMode.OFF
        val subtitlePrimaryLabel = remember(uiState) {
            val success = uiState as? PlayerUiState.Success
            resolveSubtitleLanguageLabel(
                languageCode = success?.takeIf {
                    it.subtitleOwnerBvid == it.info.bvid && it.subtitleOwnerCid == it.info.cid
                }?.subtitlePrimaryLanguage,
                fallbackLabel = "中文"
            )
        }
        val subtitleSecondaryLabel = remember(uiState) {
            val success = uiState as? PlayerUiState.Success
            resolveSubtitleLanguageLabel(
                languageCode = success?.takeIf {
                    it.subtitleOwnerBvid == it.info.bvid && it.subtitleOwnerCid == it.info.cid
                }?.subtitleSecondaryLanguage,
                fallbackLabel = "英文"
            )
        }

        val subtitlePositionMs by produceState(initialValue = 0L, key1 = playerState.player, key2 = uiState) {
            while (isActive) {
                value = playerState.player.currentPosition.coerceAtLeast(0L)
                delay(if (playerState.player.isPlaying) 120L else 260L)
            }
        }
        val subtitlePrimaryText = remember(uiState, subtitleFeatureEnabled, subtitlePositionMs, subtitleDisplayMode) {
            if (!subtitleFeatureEnabled) return@remember null
            val success = uiState as? PlayerUiState.Success ?: return@remember null
            if (success.subtitleOwnerBvid != success.info.bvid || success.subtitleOwnerCid != success.info.cid) {
                return@remember null
            }
            if (!shouldRenderPrimarySubtitle(subtitleDisplayMode)) return@remember null
            resolveSubtitleTextAt(success.subtitlePrimaryCues, subtitlePositionMs)
        }
        val subtitleSecondaryText = remember(uiState, subtitleFeatureEnabled, subtitlePositionMs, subtitleDisplayMode) {
            if (!subtitleFeatureEnabled) return@remember null
            val success = uiState as? PlayerUiState.Success ?: return@remember null
            if (success.subtitleOwnerBvid != success.info.bvid || success.subtitleOwnerCid != success.info.cid) {
                return@remember null
            }
            if (!shouldRenderSecondarySubtitle(subtitleDisplayMode)) return@remember null
            resolveSubtitleTextAt(success.subtitleSecondaryCues, subtitlePositionMs)
        }
        if (!isInPipMode &&
            !isAudioOnly &&
            uiState is PlayerUiState.Success &&
            !suppressSubtitleOverlay &&
            subtitleOverlayEnabled &&
            (subtitlePrimaryText != null || subtitleSecondaryText != null)
        ) {
            val subtitleBottomPadding = when {
                showControls && isFullscreen -> 132.dp
                showControls -> 108.dp
                else -> 42.dp
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.9f)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = subtitleBottomPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val subtitleShadow = Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 6f
                )
                val showPrimaryLine = !subtitlePrimaryText.isNullOrBlank()
                val showSecondaryLine = !subtitleSecondaryText.isNullOrBlank()
                val secondaryAsPrimaryLine = showSecondaryLine && !showPrimaryLine
                if (!subtitleSecondaryText.isNullOrBlank()) {
                    Text(
                        text = subtitleSecondaryText,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = when {
                            secondaryAsPrimaryLine && subtitleLargeTextByUser -> 18.sp
                            secondaryAsPrimaryLine -> 16.sp
                            subtitleLargeTextByUser -> 16.sp
                            else -> 14.sp
                        },
                        fontWeight = if (secondaryAsPrimaryLine) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(shadow = subtitleShadow)
                    )
                }
                if (!subtitlePrimaryText.isNullOrBlank()) {
                    Text(
                        text = subtitlePrimaryText,
                        color = Color.White,
                        fontSize = if (subtitleLargeTextByUser) 18.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(shadow = subtitleShadow)
                    )
                }
            }
        }

        // 🖼️ [修复] 手势指示器：仅在亮度/音量/Seek 模式显示，避免上滑全屏时误显示亮度图标
        val shouldShowGestureIndicator = isGestureVisible &&
            !isInPipMode &&
            (gestureMode == VideoGestureMode.Seek ||
                gestureMode == VideoGestureMode.Brightness ||
                gestureMode == VideoGestureMode.Volume)
        val shouldShowSeekIndicator = shouldShowGestureIndicator && gestureMode == VideoGestureMode.Seek
        val shouldShowLevelIndicator = shouldShowGestureIndicator &&
            (gestureMode == VideoGestureMode.Brightness || gestureMode == VideoGestureMode.Volume)

        if (shouldShowSeekIndicator) {
            // 🖼️ Seek 模式：显示带缩略图的预览气泡
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                if (videoshotData != null && videoshotData.isValid) {
                    // 🖼️ 有缩略图：显示完整预览
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble(
                        videoshotData = videoshotData,
                        targetPositionMs = seekTargetTime,
                        currentPositionMs = startPosition,
                        durationMs = playerState.player.duration,
                        offsetX = 80f,  // 居中偏移（气泡宽度的一半）
                        containerWidth = 160f  // 与气泡宽度匹配
                    )
                } else {
                    // 无缩略图：使用原有样式
                    Box(
                        modifier = Modifier
                            .size(uiLayoutPolicy.gestureOverlaySizeDp.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val durationSeconds = (playerState.player.duration / 1000).coerceAtLeast(1)
                            val targetSeconds = (seekTargetTime / 1000).toInt()

                            Text(
                                text = "${FormatUtils.formatDuration(targetSeconds)} / ${FormatUtils.formatDuration(durationSeconds.toInt())}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            val deltaSeconds = (seekTargetTime - startPosition) / 1000
                            val sign = if (deltaSeconds > 0) "+" else ""
                            if (deltaSeconds != 0L) {
                                Text(
                                    text = "($sign${deltaSeconds}s)",
                                    color = if (deltaSeconds > 0) com.android.purebilibili.core.theme.iOSGreen else com.android.purebilibili.core.theme.iOSRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = shouldShowLevelIndicator,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(gestureMotionSpec.levelOverlayEnterFadeDurationMillis)) +
                scaleIn(
                    initialScale = 0.84f,
                    animationSpec = tween(gestureMotionSpec.levelOverlayEnterTransformDurationMillis)
                ) +
                slideInVertically(
                    initialOffsetY = { it / 8 },
                    animationSpec = tween(gestureMotionSpec.levelOverlayEnterTransformDurationMillis)
                ),
            exit = fadeOut(animationSpec = tween(gestureMotionSpec.levelOverlayExitDurationMillis)) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(gestureMotionSpec.levelOverlayExitDurationMillis)
                ) +
                slideOutVertically(
                    targetOffsetY = { -it / 10 },
                    animationSpec = tween(gestureMotionSpec.levelOverlayExitDurationMillis)
                )
        ) {
            val levelLabel = resolveGestureIndicatorLabel(gestureMode)
            val dynamicGestureIcon = resolveGestureDisplayIcon(
                mode = gestureMode,
                percent = gesturePercent,
                fallbackIcon = gestureIcon
            )
            val visualPolicy = resolveGestureLevelOverlayVisualPolicy(
                mode = gestureMode,
                percent = gesturePercent
            )
            val renderProgress by animateFloatAsState(
                targetValue = resolveGestureRenderProgress(gesturePercent),
                animationSpec = tween(durationMillis = gestureMotionSpec.levelProgressDurationMillis),
                label = "gesture-progress"
            )
            val iconScale by animateFloatAsState(
                targetValue = 0.9f + gesturePercent.coerceIn(0f, 1f) * 0.35f,
                animationSpec = tween(durationMillis = gestureMotionSpec.levelIconScaleDurationMillis),
                label = "gesture-icon-scale"
            )
            val valueScale by animateFloatAsState(
                targetValue = if (gesturePercentDisplay != previousGesturePercentDisplay) 1.06f else 1f,
                animationSpec = tween(durationMillis = gestureMotionSpec.levelValueScaleDurationMillis),
                label = "gesture-value-scale"
            )
            val overlayTextShadow = Shadow(
                color = Color.Black.copy(alpha = 0.62f),
                offset = Offset(0f, 2f),
                blurRadius = 8f
            )
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 132.dp, max = 188.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size((uiLayoutPolicy.gestureIconSizeDp + 20).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    visualPolicy.accentColor.copy(alpha = visualPolicy.glowAlpha),
                                    CircleShape
                                )
                                .blur(
                                    radius = 14.dp,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.White.copy(alpha = 0.10f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.66f), CircleShape)
                        )
                        AnimatedContent(
                            targetState = dynamicGestureIcon,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(gestureMotionSpec.levelIconEnterFadeDurationMillis)) +
                                    scaleIn(
                                        initialScale = 0.78f,
                                        animationSpec = tween(gestureMotionSpec.levelIconContentScaleDurationMillis)
                                    ))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(gestureMotionSpec.levelIconExitFadeDurationMillis)) +
                                            scaleOut(
                                                targetScale = 1.2f,
                                                animationSpec = tween(gestureMotionSpec.levelIconContentScaleDurationMillis)
                                            )
                                    )
                            },
                            label = "gesture-icon-content"
                        ) { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = visualPolicy.accentColor,
                                modifier = Modifier
                                    .size(uiLayoutPolicy.gestureIconSizeDp.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        }
                    }
                    Text(
                        text = levelLabel,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            shadow = overlayTextShadow
                        )
                    )
                    GesturePercentValue(
                        percent = gesturePercentDisplay,
                        previousPercent = previousGesturePercentDisplay,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        textShadow = overlayTextShadow,
                        motionSpec = gestureMotionSpec,
                        modifier = Modifier
                            .widthIn(min = 74.dp)
                            .graphicsLayer {
                                scaleX = valueScale
                                scaleY = valueScale
                            }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(renderProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            visualPolicy.accentColor.copy(alpha = 0.68f),
                                            visualPolicy.accentColor
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = orientationHintVisible && !isInPipMode,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 196.dp),
            enter = fadeIn(animationSpec = tween(gestureMotionSpec.orientationHintEnterFadeDurationMillis)) +
                scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(gestureMotionSpec.orientationHintEnterTransformDurationMillis)
                ) +
                slideInVertically(
                    initialOffsetY = { -it / 5 },
                    animationSpec = tween(gestureMotionSpec.orientationHintEnterTransformDurationMillis)
                ),
            exit = fadeOut(animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)) +
                scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = orientationHintText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
        
        //  [新增] 双击跳转视觉反馈 (±Ns 提示)
        LaunchedEffect(seekFeedbackVisible) {
            if (seekFeedbackVisible) {
                kotlinx.coroutines.delay(800)
                seekFeedbackVisible = false
            }
        }
        
        AnimatedVisibility(
            visible = seekFeedbackVisible && !isInPipMode,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.5f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(uiLayoutPolicy.seekFeedbackSizeDp.dp)
                    .background(Color.Black.copy(0.75f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = seekFeedbackText ?: "",
                    color = if (seekFeedbackText?.startsWith("+") == true) com.android.purebilibili.core.theme.iOSGreen else com.android.purebilibili.core.theme.iOSRed,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        TwoFingerSpeedFeedbackOverlay(
            visible = twoFingerSpeedFeedbackVisible && !isInPipMode,
            speed = twoFingerFeedbackSpeed,
            mode = twoFingerSpeedMode,
            hazeState = overlayDrawerHazeState
        )

        //  [新增] 缩放还原按钮 (仅在放大时显示)
        AnimatedVisibility(
            visible = scale > 1.05f && !isInPipMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = uiLayoutPolicy.restoreButtonBottomOffsetDp.dp), // 避开底部进度条位置
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Button(
                onClick = {
                    scale = 1f
                    panX = 0f
                    panY = 0f
                    // showControls = true // 可选：还原后显示控制栏
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(
                    horizontal = uiLayoutPolicy.restoreButtonHorizontalPaddingDp.dp,
                    vertical = uiLayoutPolicy.restoreButtonVerticalPaddingDp.dp
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "还原画面",
                    modifier = Modifier.size(uiLayoutPolicy.restoreButtonIconSizeDp.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "还原画面",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        
        //  长按倍速提示（透明背景 + 快进箭头动画，整个长按期间持续显示）
        AnimatedVisibility(
            visible = isLongPressing && !isInPipMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            enter = fadeIn(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)) +
                slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)) +
                slideOutVertically(targetOffsetY = { -it })
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "fast_forward")
            val arrowCycleDuration = gestureMotionSpec.longPressArrowCycleDurationMillis
            val arrowPhase = gestureMotionSpec.longPressArrowPhaseStepDurationMillis
            // 三个箭头循环亮度动画，依次偏移相位
            val arrow1Alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = arrowCycleDuration
                        0.3f at 0
                        1.0f at arrowPhase
                        0.3f at arrowPhase * 2
                        0.3f at arrowCycleDuration
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "arrow1"
            )
            val arrow2Alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = arrowCycleDuration
                        0.3f at 0
                        0.3f at arrowPhase
                        1.0f at arrowPhase * 2
                        0.3f at arrowCycleDuration
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "arrow2"
            )
            val arrow3Alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = arrowCycleDuration
                        0.3f at 0
                        0.3f at arrowPhase * 2
                        1.0f at arrowCycleDuration
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "arrow3"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 三个三角形快进箭头
                val arrowAlphas = listOf(arrow1Alpha, arrow2Alpha, arrow3Alpha)
                arrowAlphas.forEach { alpha ->
                    Canvas(
                        modifier = Modifier.size(14.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = alpha)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 倍速文字
                Text(
                    text = if (longPressSpeedLocked) {
                        "已锁定 ${effectiveLongPressSpeed}x"
                    } else {
                        "${effectiveLongPressSpeed}x 上滑锁定"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }

        if (uiState is PlayerUiState.Success && !isInPipMode) {
            val currentPageIndex = uiState.info.pages.indexOfFirst { it.cid == uiState.info.cid }.coerceAtLeast(0)
            val displayedQualityId = resolveDisplayedQualityId(
                currentQuality = uiState.currentQuality,
                requestedQuality = uiState.requestedQuality,
                isQualitySwitching = uiState.isQualitySwitching
            )
            VideoPlayerOverlay(
                player = playerState.player,
                title = uiState.info.title,
                // [修复] 竖屏全屏模式下隐藏底部 Overlay，避免进度状态冲突
                isVisible = showControls && !isPortraitFullscreen,
                onToggleVisible = { showControls = !showControls },
                isFullscreen = isFullscreen,
                currentQualityLabel = uiState.qualityLabels.getOrNull(uiState.qualityIds.indexOf(displayedQualityId)) ?: "自动",
                qualityLabels = uiState.qualityLabels,
                qualityIds = uiState.qualityIds,
                onQualitySelected = { index ->
                    val id = uiState.qualityIds.getOrNull(index) ?: 0
                    onQualityChange(id, playerState.player.currentPosition)
                },
                onBack = onBack,
                onHomeClick = resolveVideoPlayerOverlayHomeClick(
                    onBack = onBack,
                    onHomeClick = onHomeClick
                ),
                onToggleFullscreen = onToggleFullscreen,
                
                // 🔒 [新增] 屏幕锁定
                isScreenLocked = isScreenLocked,
                onLockToggle = { isScreenLocked = !isScreenLocked },
                //  [关键] 传入设置状态和调试信息
                showStats = showStats,
                debugInfo = debugInfo,
                diagnosticEvents = diagnosticEvents,
                pendingUserAction = pendingUserAction,
                playerDiagnosticLoggingEnabled = playerDiagnosticLoggingEnabled,
                //  [新增] 传入清晰度切换状态和会员状态
                isQualitySwitching = uiState.isQualitySwitching,
                isBuffering = isBuffering,  // 缓冲状态
                isLoggedIn = uiState.isLoggedIn,
                isVip = uiState.isVip,
                //  [新增] 弹幕开关和设置
                danmakuEnabled = danmakuEnabled,
                onDanmakuToggle = {
                    val newState = !danmakuEnabled
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuEnabled(
                            context,
                            newState,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(enabled = newState)
                    //  记录弹幕开关事件
                    com.android.purebilibili.core.util.AnalyticsHelper.logDanmakuToggle(newState)
                },
                onDanmakuInputClick = onDanmakuInputClick,
                danmakuOpacity = danmakuOpacity,
                danmakuFontScale = danmakuFontScale,
                danmakuSpeed = danmakuSpeed,
                danmakuDisplayArea = danmakuDisplayArea,
                danmakuMergeDuplicates = danmakuMergeDuplicates,
                danmakuAllowScroll = danmakuAllowScroll,
                danmakuAllowTop = danmakuAllowTop,
                danmakuAllowBottom = danmakuAllowBottom,
                danmakuAllowColorful = danmakuAllowColorful,
                danmakuAllowSpecial = danmakuAllowSpecial,
                danmakuBlockRulesRaw = danmakuBlockRulesRaw,
                danmakuSmartOcclusion = danmakuSmartOcclusion,
                danmakuFullscreenPanelWidthMode = danmakuFullscreenPanelWidthMode,
                onDanmakuOpacityChange = { value ->
                    danmakuManager.opacity = value
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuOpacity(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(opacity = value)
                },
                onDanmakuFontScaleChange = { value ->
                    danmakuManager.fontScale = value
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuFontScale(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(fontScale = value)
                },
                onDanmakuSpeedChange = { value ->
                    danmakuManager.speedFactor = value
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuSpeed(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(speed = value)
                },
                onDanmakuDisplayAreaChange = { value ->
                    danmakuManager.displayArea = value
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuArea(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(displayAreaRatio = value)
                },
                onDanmakuMergeDuplicatesChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuMergeDuplicates(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                },
                onDanmakuAllowScrollChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuAllowScroll(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(allowScroll = value)
                },
                onDanmakuAllowTopChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuAllowTop(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(allowTop = value)
                },
                onDanmakuAllowBottomChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuAllowBottom(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(allowBottom = value)
                },
                onDanmakuAllowColorfulChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuAllowColorful(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(allowColorful = value)
                },
                onDanmakuAllowSpecialChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuAllowSpecial(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                    queueDanmakuCloudSync(allowSpecial = value)
                },
                onDanmakuSmartOcclusionChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuSmartOcclusion(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                },
                onDanmakuFullscreenPanelWidthModeChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuFullscreenPanelWidthMode(context, value)
                    }
                },
                onDanmakuBlockRulesRawChange = { value ->
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setDanmakuBlockRulesRaw(
                            context,
                            value,
                            activeDanmakuScope
                        )
                    }
                },
                smartOcclusionModuleState = smartOcclusionModuleState,
                smartOcclusionDownloadProgress = smartOcclusionDownloadProgress,
                onDanmakuSmartOcclusionDownloadClick = {
                    if (smartOcclusionModuleState != FaceOcclusionModuleState.Downloading) {
                        scope.launch {
                            smartOcclusionModuleState = FaceOcclusionModuleState.Downloading
                            smartOcclusionDownloadProgress = 0
                            smartOcclusionModuleState = installFaceOcclusionModule(
                                context = context,
                                detector = faceDetector,
                                onProgress = { progress ->
                                    smartOcclusionDownloadProgress = progress
                                }
                            )
                            if (smartOcclusionModuleState != FaceOcclusionModuleState.Downloading) {
                                smartOcclusionDownloadProgress = null
                            }
                        }
                    }
                },
                //  视频比例调节

                currentAspectRatio = currentAspectRatio,
                onAspectRatioChange = { ratio ->
                    currentAspectRatio = ratio
                    scope.launch {
                        com.android.purebilibili.core.store.SettingsManager
                            .setFullscreenAspectRatio(context, ratio.toFullscreenAspectRatio())
                    }
                },
                // 🕺 [新增] 分享功能
                bvid = bvid,
                cid = uiState.info.cid,
                videoOwnerName = uiState.info.owner.name,
                videoOwnerFace = uiState.info.owner.face,
                videoDuration = uiState.videoDurationMs,
                videoTitle = uiState.info.title,
                currentAid = uiState.info.aid,
                currentQuality = uiState.currentQuality,
                currentVideoUrl = uiState.playUrl,
                currentAudioUrl = uiState.audioUrl ?: "",
                coverUrl = uiState.info.pic,
                //  [新增] 视频设置面板回调
                onReloadVideo = onReloadVideo,
                isFlippedHorizontal = isFlippedHorizontal,
                isFlippedVertical = isFlippedVertical,
                onFlipHorizontal = { isFlippedHorizontal = !isFlippedHorizontal },
                onFlipVertical = { isFlippedVertical = !isFlippedVertical },
                //  [新增] 画质切换（用于设置面板）
                onQualityChange = { qid, pos ->
                    onQualityChange(qid, playerState.player.currentPosition)
                },
                //  [新增] CDN 线路切换
                currentCdnIndex = currentCdnIndex,
                cdnCount = cdnCount,
                onSwitchCdn = onSwitchCdn,
                onSwitchCdnTo = onSwitchCdnTo,
                
                //  [新增] 音频模式
                isAudioOnly = isAudioOnly,
                onAudioOnlyToggle = onAudioOnlyToggle,
                subtitleControlState = SubtitleControlUiState(
                    trackAvailable = subtitleControlAvailability.trackAvailable,
                    primaryAvailable = subtitleControlAvailability.primarySelectable,
                    secondaryAvailable = subtitleControlAvailability.secondarySelectable,
                    enabled = subtitleFeatureEnabled && subtitleOverlayEnabled,
                    displayMode = if (subtitleFeatureEnabled) subtitleDisplayMode else SubtitleDisplayMode.OFF,
                    primaryLabel = subtitlePrimaryLabel,
                    secondaryLabel = subtitleSecondaryLabel,
                    largeTextEnabled = subtitleLargeTextByUser
                ),
                subtitleControlCallbacks = SubtitleControlCallbacks(
                    onDisplayModeChange = { mode ->
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerSection",
                            "字幕显示模式切换: mode=$mode"
                        )
                        applySubtitleDisplayModePreferenceChange(mode)
                    },
                    onEnabledChange = { enabled ->
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerSection",
                            "字幕总开关切换: enabled=$enabled"
                        )
                        val nextMode = if (enabled) {
                            resolveDefaultSubtitleDisplayMode(
                                hasPrimaryTrack = subtitleControlAvailability.primarySelectable,
                                hasSecondaryTrack = subtitleControlAvailability.secondarySelectable
                            )
                        } else {
                            SubtitleDisplayMode.OFF
                        }
                        applySubtitleDisplayModePreferenceChange(nextMode)
                    },
                    onLargeTextChange = { enabled ->
                        com.android.purebilibili.core.util.Logger.d(
                            "VideoPlayerSection",
                            "字幕大字号切换: enabled=$enabled"
                        )
                        subtitleLargeTextByUser = enabled
                    }
                ),
                
                //  [新增] 定时关闭
                sleepTimerMinutes = sleepTimerMinutes,
                onSleepTimerChange = onSleepTimerChange,
                
                // 🖼️ [新增] 视频预览图数据
                videoshotData = videoshotData,
                
                // 📖 [新增] 视频章节数据
                viewPoints = viewPoints,
                sponsorMarkers = sponsorMarkers,
                
                // 📱 [新增] 竖屏全屏模式
                isVerticalVideo = isVerticalVideo,
                onPortraitFullscreen = onPortraitFullscreen,
                // 📲 [新增] 小窗模式
                // 📲 [新增] 小窗模式
                onPipClick = onPipClick,
                //  [新增] 拖动进度条开始时清除弹幕
                onSeekStart = { danmakuManager.clear() },
                onSeekDragStart = { position ->
                    sharedSeekSession = startPlaybackSeekInteraction(
                        state = sharedSeekSession,
                        positionMs = position,
                        shouldResumePlayback = shouldResumePlaybackAfterUserSeek(
                            playWhenReadyBeforeSeek = playerState.player.playWhenReady,
                            playbackStateBeforeSeek = playerState.player.playbackState
                        )
                    )
                },
                onSeekDragUpdate = { position ->
                    sharedSeekSession = updatePlaybackSeekInteraction(
                        state = sharedSeekSession,
                        positionMs = position
                    )
                },
                onSeekDragCancel = {
                    sharedSeekSession = cancelPlaybackSeekInteraction(sharedSeekSession)
                },
                //  [加固] 显式同步弹幕到新进度，避免某些设备 seek 回调时机差导致短暂不同步
                onSeekTo = { position ->
                    val commitResult = finishPlaybackSeekInteraction(
                        updatePlaybackSeekInteraction(
                            state = sharedSeekSession,
                            positionMs = position
                        )
                    )
                    sharedSeekSession = commitResult.state
                    seekPlayerFromUserAction(
                        player = playerState.player,
                        positionMs = commitResult.committedPositionMs,
                        shouldResumePlaybackOverride = commitResult.shouldResumePlayback
                    )
                    danmakuManager.seekTo(commitResult.committedPositionMs)
                    onUserSeek(commitResult.committedPositionMs)
                },
                previewSeekPositionMs = sharedSeekSession.sliderPositionMs,
                previewSeekActive = shouldUsePlaybackSeekSessionPosition(sharedSeekSession),
                playbackTransitionPositionMs = uiState.pendingPlaybackTransitionPositionMs,
                // [New] Codec & Audio
                currentCodec = currentCodec,
                onCodecChange = onCodecChange,
                currentSecondCodec = currentSecondCodec,
                onSecondCodecChange = onSecondCodecChange,
                currentAudioQuality = currentAudioQuality,
                onAudioQualityChange = onAudioQualityChange,
                // [New] AI Audio
                aiAudioInfo = uiState.aiAudio,
                currentAudioLang = uiState.currentAudioLang,
                onAudioLangChange = onAudioLangChange,
                // 👀 [新增] 在线观看人数
                onlineCount = uiState.onlineCount,
                // [New]
                onSaveCover = onSaveCover,
                onCaptureScreenshot = {
                    val playerView = playerViewRef
                    if (playerView == null) {
                        Toast.makeText(context, "截图失败：播放器未就绪", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            val success = captureAndSaveVideoScreenshot(
                                context = context,
                                playerView = playerView,
                                videoWidth = videoSizeState.first,
                                videoHeight = videoSizeState.second,
                                videoTitle = uiState.info.title,
                            )
                            Toast.makeText(
                                context,
                                if (success) "截图已保存到相册（PNG）" else "截图失败，请稍后重试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDownloadAudio = onDownloadAudio,
                // 🔁 [新增] 播放模式
                currentPlayMode = currentPlayMode,
                onPlayModeClick = onPlayModeClick,
                
                // [新增] 侧边栏抽屉数据与交互
                relatedVideos = relatedVideos,
                ugcSeason = ugcSeason,
                isFollowed = isFollowed,
                isLiked = isLiked,
                isCoined = isCoined,
                isFavorited = isFavorited,
                likeCount = uiState.info.stat.like.toLong(),
                favoriteCount = uiState.info.stat.favorite.toLong(),
                coinCount = uiState.coinCount,
                onToggleFollow = onToggleFollow,
                onToggleLike = onToggleLike,
                onDislike = onDislike,
                onCoin = onCoin,
                onToggleFavorite = onToggleFavorite,
                onDrawerVideoClick = { vid, options ->
                    onRelatedVideoClick(vid, options) 
                },
                pages = uiState.info.pages,
                currentPageIndex = currentPageIndex,
                onPageSelect = onPageSelect,
                drawerHazeState = overlayDrawerHazeState
            )

            SponsorSkipButton(
                segment = sponsorSegment,
                visible = showSponsorSkipButton,
                onSkip = onSponsorSkip,
                onDismiss = onSponsorDismiss,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 60.dp, end = 16.dp)
            )
    }



    // [新增] 返回时的触感反馈
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticScope = rememberCoroutineScope()

    // 拦截系统返回事件 (仅在全屏时拦截以处理退出全屏，否则交给系统处理预测性返回)
    BackHandler(enabled = !isScreenLocked && isFullscreen) {
        onToggleFullscreen()
    }
    }
}
