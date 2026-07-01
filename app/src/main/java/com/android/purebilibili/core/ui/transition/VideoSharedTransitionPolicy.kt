package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

internal enum class VideoSharedTransitionProfile {
    COVER_ONLY,
    COVER_AND_METADATA
}

internal enum class VideoSharedTransitionPlaybackIntent {
    ImmediatePlayback,
    CoverFirst
}

internal fun resolveVideoSharedTransitionPlaybackIntent(
    clickToPlayEnabled: Boolean,
    forceImmediatePlayback: Boolean = false
): VideoSharedTransitionPlaybackIntent {
    return if (!forceImmediatePlayback && !clickToPlayEnabled) {
        VideoSharedTransitionPlaybackIntent.CoverFirst
    } else {
        VideoSharedTransitionPlaybackIntent.ImmediatePlayback
    }
}

internal fun shouldFadePlayerSurfaceOnDetailReturn(
    isLeaving: Boolean,
    playbackIntent: VideoSharedTransitionPlaybackIntent
): Boolean {
    return isLeaving && playbackIntent == VideoSharedTransitionPlaybackIntent.ImmediatePlayback
}

internal fun shouldUseDetailReturnCoverCrossfade(
    isLeaving: Boolean,
    playbackIntent: VideoSharedTransitionPlaybackIntent
): Boolean {
    return isLeaving && playbackIntent == VideoSharedTransitionPlaybackIntent.ImmediatePlayback
}

internal enum class VideoSharedTransitionTargetMode {
    InlineCover,
    InlinePlayer,
    LandscapeFullscreen,
    PortraitFullscreen
}

internal const val VIDEO_SHARED_COVER_ASPECT_RATIO = 16f / 10f
private const val HOME_SOURCE_ROUTE = "home"
internal const val VIDEO_SHARED_TRANSITION_FAST_DURATION_MILLIS = 360
internal const val VIDEO_SHARED_TRANSITION_STANDARD_DURATION_MILLIS = 460
internal const val VIDEO_SHARED_TRANSITION_SLOW_DURATION_MILLIS = 560
internal const val VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS = 280
internal const val VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS = 900
internal const val VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS =
    VIDEO_SHARED_TRANSITION_STANDARD_DURATION_MILLIS
private const val HOME_DETAIL_REVEAL_DELAY_MILLIS = 40
private const val HOME_DETAIL_REVEAL_MIN_DURATION_MILLIS = 220
private const val HOME_DETAIL_REVEAL_MAX_DURATION_MILLIS = 360
private const val HOME_DETAIL_REVEAL_SLIDE_OFFSET_DP = 14
private const val HOME_DETAIL_REVEAL_INITIAL_SCALE = 0.985f
private const val VIDEO_METADATA_SHARED_BOUNDS_RATIO = 0.72f
private const val VIDEO_METADATA_SHARED_BOUNDS_MIN_MILLIS = 200
private const val VIDEO_METADATA_SHARED_BOUNDS_MAX_MILLIS = 360
private const val HOME_SHARED_TRANSITION_CARD_CORNER_DP = 16
private const val HOME_SHARED_TRANSITION_PLAYER_CORNER_DP = 12
private const val DEFAULT_VIDEO_CARD_CORNER_DP = 12
private const val DEFAULT_VIDEO_PLAYER_CORNER_DP = 12
private const val DYNAMIC_VIDEO_CARD_CORNER_DP = 10
private const val WATCH_LATER_VIDEO_CARD_CORNER_DP = 8
private val VIDEO_CARD_IOS_LIKE_EASE_OUT = CubicBezierEasing(0.18f, 0.76f, 0.22f, 1f)

enum class VideoSharedTransitionSpeed(val value: Int, val label: String) {
    FAST(0, "快速"),
    STANDARD(1, "标准"),
    SLOW(2, "慢速"),
    CUSTOM(3, "自定义");

    companion object {
        fun fromValue(value: Int): VideoSharedTransitionSpeed =
            entries.find { it.value == value } ?: STANDARD
    }
}

internal data class VideoSharedTransitionSpeedSettings(
    val speed: VideoSharedTransitionSpeed = VideoSharedTransitionSpeed.STANDARD,
    val customDurationMillis: Int = VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS
)

internal data class VideoSharedTransitionOwnership(
    val useCoverSharedBounds: Boolean,
    val useMetadataSharedBounds: Boolean
)

internal data class VideoSharedTransitionMotionSpec(
    val enabled: Boolean,
    val durationMillis: Int,
    val fullscreenDurationMillis: Int,
    val contentDelayMillis: Int,
    val contentDurationMillis: Int,
    val contentSlideOffsetDp: Int,
    val contentInitialScale: Float,
    val easing: Easing
)

internal data class VideoSharedCornerSpec(
    val enabled: Boolean,
    val startCornerDp: Int,
    val endCornerDp: Int
)

internal data class VideoSharedTransitionVisualSpec(
    val targetMode: VideoSharedTransitionTargetMode,
    val sourceCornerDp: Int,
    val targetCornerDp: Int,
    val fillTargetViewport: Boolean,
    val useCoverSharedBounds: Boolean,
    val suppressCoverFade: Boolean
)

internal fun resolveVideoSharedTransitionProfile(): VideoSharedTransitionProfile {
    return VideoSharedTransitionProfile.COVER_AND_METADATA
}

internal fun resolveVideoCardSharedTransitionEasing(): Easing {
    return VIDEO_CARD_IOS_LIKE_EASE_OUT
}

internal fun normalizeVideoSharedTransitionCustomDurationMillis(durationMillis: Int): Int {
    return durationMillis.coerceIn(
        VIDEO_SHARED_TRANSITION_CUSTOM_MIN_MILLIS,
        VIDEO_SHARED_TRANSITION_CUSTOM_MAX_MILLIS
    )
}

internal fun resolveVideoSharedTransitionDurationMillis(
    speedSettings: VideoSharedTransitionSpeedSettings
): Int {
    return when (speedSettings.speed) {
        VideoSharedTransitionSpeed.FAST -> VIDEO_SHARED_TRANSITION_FAST_DURATION_MILLIS
        VideoSharedTransitionSpeed.STANDARD -> VIDEO_SHARED_TRANSITION_STANDARD_DURATION_MILLIS
        VideoSharedTransitionSpeed.SLOW -> VIDEO_SHARED_TRANSITION_SLOW_DURATION_MILLIS
        VideoSharedTransitionSpeed.CUSTOM ->
            normalizeVideoSharedTransitionCustomDurationMillis(speedSettings.customDurationMillis)
    }
}

internal fun resolveVideoSharedTransitionFullscreenDurationMillis(durationMillis: Int): Int {
    return durationMillis + 80
}

internal fun resolveVideoSharedTransitionContentDurationMillis(durationMillis: Int): Int {
    return (durationMillis * 0.6f).roundToInt().coerceIn(
        HOME_DETAIL_REVEAL_MIN_DURATION_MILLIS,
        HOME_DETAIL_REVEAL_MAX_DURATION_MILLIS
    )
}

internal fun resolveVideoSharedTransitionSourceCornerDp(
    sourceRoute: String?,
    fallbackCornerDp: Int = DEFAULT_VIDEO_CARD_CORNER_DP
): Int {
    return when (sourceRoute?.substringBefore("?")) {
        "dynamic",
        "dynamic_detail" -> DYNAMIC_VIDEO_CARD_CORNER_DP
        "watch_later" -> WATCH_LATER_VIDEO_CARD_CORNER_DP
        else -> fallbackCornerDp
    }.coerceAtLeast(0)
}

internal fun resolveVideoSharedTransitionVisualSpec(
    sourceRoute: String?,
    sourceCornerDp: Int = resolveVideoSharedTransitionSourceCornerDp(sourceRoute),
    playbackIntent: VideoSharedTransitionPlaybackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
    fullscreen: Boolean = false,
    autoPortrait: Boolean = false,
    initialVertical: Boolean = false,
    isVerticalVideo: Boolean = false,
    isReturning: Boolean = false,
    playerCornerDp: Int = DEFAULT_VIDEO_PLAYER_CORNER_DP
): VideoSharedTransitionVisualSpec {
    val normalizedSourceRoute = sourceRoute?.substringBefore("?")?.takeIf { it.isNotBlank() }
    val safeSourceCornerDp = sourceCornerDp.coerceAtLeast(0)
    val shouldPreferPortraitTarget = initialVertical || (autoPortrait && isVerticalVideo)
    val targetMode = when {
        isReturning -> VideoSharedTransitionTargetMode.InlineCover
        shouldPreferPortraitTarget -> VideoSharedTransitionTargetMode.PortraitFullscreen
        playbackIntent == VideoSharedTransitionPlaybackIntent.CoverFirst ->
            VideoSharedTransitionTargetMode.InlineCover
        fullscreen -> VideoSharedTransitionTargetMode.LandscapeFullscreen
        else -> VideoSharedTransitionTargetMode.InlinePlayer
    }
    val targetCornerDp = when {
        isReturning -> safeSourceCornerDp
        targetMode == VideoSharedTransitionTargetMode.LandscapeFullscreen -> 0
        targetMode == VideoSharedTransitionTargetMode.PortraitFullscreen -> 0
        else -> playerCornerDp.coerceAtLeast(0)
    }

    return VideoSharedTransitionVisualSpec(
        targetMode = targetMode,
        sourceCornerDp = safeSourceCornerDp,
        targetCornerDp = targetCornerDp,
        fillTargetViewport = targetMode == VideoSharedTransitionTargetMode.LandscapeFullscreen ||
            targetMode == VideoSharedTransitionTargetMode.PortraitFullscreen,
        useCoverSharedBounds = normalizedSourceRoute != null,
        suppressCoverFade = isReturning
    )
}

private fun resolveVideoSharedTransitionProfile(sourceRoute: String?): VideoSharedTransitionProfile {
    return if (sourceRoute?.substringBefore("?") == HOME_SOURCE_ROUTE) {
        VideoSharedTransitionProfile.COVER_ONLY
    } else {
        VideoSharedTransitionProfile.COVER_AND_METADATA
    }
}

internal fun shouldEnableVideoCoverSharedTransition(
    transitionEnabled: Boolean,
    hasSharedTransitionScope: Boolean,
    hasAnimatedVisibilityScope: Boolean
): Boolean {
    return transitionEnabled &&
        hasSharedTransitionScope &&
        hasAnimatedVisibilityScope
}

internal fun shouldEnableVideoMetadataSharedTransition(
    coverSharedEnabled: Boolean,
    isQuickReturnLimited: Boolean,
    useCardContainerSharedBounds: Boolean = false,
    profile: VideoSharedTransitionProfile = resolveVideoSharedTransitionProfile()
): Boolean {
    if (!coverSharedEnabled) return false
    // 卡片容器已经承载整体放大/回收时，标题、UP、统计等不要再各自抢独立 sharedBounds。
    if (useCardContainerSharedBounds) return false
    // COVER_ONLY 来源只让封面参与共享元素，避免标题、UP 名等文本在落位时被 bounds 缩放。
    if (profile == VideoSharedTransitionProfile.COVER_ONLY) return false
    return true
}

internal fun resolveVideoSharedTransitionOwnership(
    sourceRoute: String?,
    coverSharedEnabled: Boolean,
    isQuickReturnLimited: Boolean
): VideoSharedTransitionOwnership {
    if (!coverSharedEnabled) {
        return VideoSharedTransitionOwnership(
            useCoverSharedBounds = false,
            useMetadataSharedBounds = false
        )
    }

    return VideoSharedTransitionOwnership(
        useCoverSharedBounds = true,
        useMetadataSharedBounds = shouldEnableVideoMetadataSharedTransition(
            coverSharedEnabled = true,
            isQuickReturnLimited = isQuickReturnLimited,
            profile = resolveVideoSharedTransitionProfile(sourceRoute)
        )
    )
}

internal fun resolveVideoCardSharedTransitionMotionSpec(
    sourceRoute: String?,
    transitionEnabled: Boolean,
    speedSettings: VideoSharedTransitionSpeedSettings = VideoSharedTransitionSpeedSettings()
): VideoSharedTransitionMotionSpec {
    val enabled = transitionEnabled &&
        !sourceRoute?.substringBefore("?").isNullOrBlank()
    if (!enabled) {
        return VideoSharedTransitionMotionSpec(
            enabled = false,
            durationMillis = 0,
            fullscreenDurationMillis = 0,
            contentDelayMillis = 0,
            contentDurationMillis = 0,
            contentSlideOffsetDp = 0,
            contentInitialScale = 1f,
            easing = VIDEO_CARD_IOS_LIKE_EASE_OUT
        )
    }
    val durationMillis = resolveVideoSharedTransitionDurationMillis(speedSettings)

    return VideoSharedTransitionMotionSpec(
        enabled = true,
        durationMillis = durationMillis,
        fullscreenDurationMillis = resolveVideoSharedTransitionFullscreenDurationMillis(durationMillis),
        contentDelayMillis = HOME_DETAIL_REVEAL_DELAY_MILLIS,
        contentDurationMillis = resolveVideoSharedTransitionContentDurationMillis(durationMillis),
        contentSlideOffsetDp = HOME_DETAIL_REVEAL_SLIDE_OFFSET_DP,
        contentInitialScale = HOME_DETAIL_REVEAL_INITIAL_SCALE,
        easing = VIDEO_CARD_IOS_LIKE_EASE_OUT
    )
}

internal fun resolveVideoMetadataSharedTransitionMotionSpec(
    transitionEnabled: Boolean,
    speedSettings: VideoSharedTransitionSpeedSettings = VideoSharedTransitionSpeedSettings()
): VideoSharedTransitionMotionSpec {
    val durationMillis = if (transitionEnabled) {
        resolveVideoSharedTransitionDurationMillis(speedSettings)
    } else {
        0
    }
    return VideoSharedTransitionMotionSpec(
        enabled = transitionEnabled,
        durationMillis = durationMillis,
        fullscreenDurationMillis = if (transitionEnabled) {
            resolveVideoSharedTransitionFullscreenDurationMillis(durationMillis)
        } else {
            0
        },
        contentDelayMillis = 0,
        contentDurationMillis = durationMillis,
        contentSlideOffsetDp = 0,
        contentInitialScale = 1f,
        easing = VIDEO_CARD_IOS_LIKE_EASE_OUT
    )
}

internal fun <T> videoSharedElementBoundsTransformSpec(
    motion: VideoSharedTransitionMotionSpec
): FiniteAnimationSpec<T> {
    return tween(
        durationMillis = motion.durationMillis,
        easing = motion.easing
    )
}

internal fun <T> videoMetadataSharedElementBoundsTransformSpec(
    motion: VideoSharedTransitionMotionSpec
): FiniteAnimationSpec<T> {
    return tween(
        durationMillis = resolveVideoMetadataSharedBoundsDurationMillis(motion),
        easing = motion.easing
    )
}

internal fun resolveVideoMetadataSharedBoundsDurationMillis(
    motion: VideoSharedTransitionMotionSpec
): Int {
    if (!motion.enabled) return 0
    return (motion.durationMillis * VIDEO_METADATA_SHARED_BOUNDS_RATIO)
        .roundToInt()
        .coerceIn(
            VIDEO_METADATA_SHARED_BOUNDS_MIN_MILLIS,
            VIDEO_METADATA_SHARED_BOUNDS_MAX_MILLIS
        )
}

internal fun resolveHomeVideoSharedTransitionCornerSpec(
    sourceRoute: String?,
    transitionEnabled: Boolean
): VideoSharedCornerSpec {
    val enabled = transitionEnabled &&
        !sourceRoute?.substringBefore("?").isNullOrBlank()
    return if (enabled) {
        VideoSharedCornerSpec(
            enabled = true,
            startCornerDp = HOME_SHARED_TRANSITION_CARD_CORNER_DP,
            endCornerDp = HOME_SHARED_TRANSITION_PLAYER_CORNER_DP
        )
    } else {
        VideoSharedCornerSpec(
            enabled = false,
            startCornerDp = 0,
            endCornerDp = 0
        )
    }
}
