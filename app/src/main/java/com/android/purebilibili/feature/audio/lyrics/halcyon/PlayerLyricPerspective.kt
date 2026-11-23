package com.android.purebilibili.feature.audio.lyrics.halcyon

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/**
 * Ported Halcyon `PlayerLyricPerspective`.
 */
internal fun Modifier.playerLyricPerspective(
    enabled: Boolean,
    angle: Int,
    lyricTextAlign: Int = PLAYER_LYRIC_ALIGN_START,
    cameraDistanceMultiplier: Float = 18f,
): Modifier = composed {
    if (!enabled || angle <= 0) return@composed this

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val originX = when (lyricTextAlign) {
        PLAYER_LYRIC_ALIGN_RIGHT -> 1f
        PLAYER_LYRIC_ALIGN_CENTER -> 0.5f
        else -> 0f
    }
    val resolvedAngle = when (lyricTextAlign) {
        PLAYER_LYRIC_ALIGN_RIGHT -> angle.toFloat()
        else -> -angle.toFloat()
    }
    val ultraWideLandscape = isUltraWideLandscapePlayerLayout(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
    val isLargeLandscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
        (configuration.smallestScreenWidthDp >= 600 || ultraWideLandscape)
    val effectiveAngle = if (isLargeLandscape) {
        resolvedAngle.coerceIn(-14f, 14f)
    } else {
        resolvedAngle
    }
    val scaleXValue = when {
        ultraWideLandscape -> 0.97f
        isLargeLandscape -> 0.94f
        else -> 1f
    }
    val translationRatio = if (ultraWideLandscape) 0.010f else 0.018f
    val translationXValue = when {
        !isLargeLandscape -> 0f
        lyricTextAlign == PLAYER_LYRIC_ALIGN_RIGHT -> {
            -configuration.screenWidthDp * density.density * translationRatio
        }
        lyricTextAlign == PLAYER_LYRIC_ALIGN_CENTER -> 0f
        else -> configuration.screenWidthDp * density.density * translationRatio
    }

    graphicsLayer {
        rotationY = effectiveAngle
        transformOrigin = TransformOrigin(originX, 0.5f)
        cameraDistance = density.density * cameraDistanceMultiplier
        scaleX = scaleXValue
        translationX = translationXValue
    }
}

internal const val PLAYER_LYRIC_ALIGN_START = 0
internal const val PLAYER_LYRIC_ALIGN_LEFT = PLAYER_LYRIC_ALIGN_START
internal const val PLAYER_LYRIC_ALIGN_CENTER = 1
internal const val PLAYER_LYRIC_ALIGN_RIGHT = 2

internal fun isUltraWideLandscapePlayerLayout(
    screenWidthDp: Int,
    screenHeightDp: Int,
): Boolean {
    if (screenHeightDp <= 0 || screenWidthDp <= 0) return false
    if (screenWidthDp <= screenHeightDp) return false
    return screenWidthDp.toFloat() / screenHeightDp.toFloat() >= 2.1f
}

/** 歌词样式默认 Y 轴旋转角。 */
internal const val HALCYON_DEFAULT_PERSPECTIVE_ANGLE = 25

internal data class ArcLyricViewportProfile(
    val maxRotationZDeg: Float,
    val maxRotationXDeg: Float,
    val baseRotationYDeg: Float,
    val arcCurveIndentDp: Float,
    val curveDirectionSign: Float = -1f,
    val minScale: Float,
    val primaryTextSizeSp: Float,
    val secondaryTextSizeSp: Float,
    val lineSpacingDp: Int,
    val focusOffsetRatio: Float,
    val horizontalPaddingStartDp: Int,
    val horizontalPaddingEndDp: Int,
    val topContentPaddingDp: Int,
    val bottomContentPaddingDp: Int,
    val applySystemBarInsets: Boolean,
)

internal fun resolveArcLyricViewportProfile(
    layout: com.android.purebilibili.feature.audio.screen.MusicPlayerLayout,
    widthDp: Int,
    heightDp: Int,
    fullScreenLyrics: Boolean,
    tiltScale: Float = 1.0f,
    curveDirection: com.android.purebilibili.core.store.ArcLyricsCurveDirection =
        com.android.purebilibili.core.store.ArcLyricsCurveDirection.LEFT,
): ArcLyricViewportProfile {
    val clampedScale = tiltScale.coerceIn(0.5f, 1.5f)
    val base = when (layout) {
        com.android.purebilibili.feature.audio.screen.MusicPlayerLayout.EXPANDED_SPLIT -> {
            val isLargeTablet = widthDp >= 840
            ArcLyricViewportProfile(
                maxRotationZDeg = if (isLargeTablet) 11f else 9.5f,
                maxRotationXDeg = if (isLargeTablet) 16f else 14f,
                baseRotationYDeg = if (isLargeTablet) -8f else -6f,
                arcCurveIndentDp = if (isLargeTablet) 14f else 10f,
                minScale = 0.85f,
                primaryTextSizeSp = if (isLargeTablet) 30f else 27f,
                secondaryTextSizeSp = if (isLargeTablet) 16f else 15f,
                lineSpacingDp = if (isLargeTablet) 22 else 18,
                focusOffsetRatio = 0.46f,
                horizontalPaddingStartDp = 16,
                horizontalPaddingEndDp = if (isLargeTablet) 32 else 24,
                topContentPaddingDp = 48,
                bottomContentPaddingDp = 72,
                applySystemBarInsets = false,
            )
        }

        com.android.purebilibili.feature.audio.screen.MusicPlayerLayout.TABLETOP,
        com.android.purebilibili.feature.audio.screen.MusicPlayerLayout.COMPACT_LANDSCAPE -> {
            val isShortViewport = heightDp < 440
            ArcLyricViewportProfile(
                maxRotationZDeg = if (isShortViewport) 7f else 8.5f,
                maxRotationXDeg = if (isShortViewport) 11f else 13f,
                baseRotationYDeg = -5.5f,
                arcCurveIndentDp = if (isShortViewport) 6f else 9f,
                minScale = 0.86f,
                primaryTextSizeSp = if (isShortViewport) 22f else 24f,
                secondaryTextSizeSp = 13f,
                lineSpacingDp = if (isShortViewport) 14 else 16,
                focusOffsetRatio = 0.44f,
                horizontalPaddingStartDp = 16,
                horizontalPaddingEndDp = 22,
                topContentPaddingDp = 24,
                bottomContentPaddingDp = 32,
                applySystemBarInsets = false,
            )
        }

        else -> {
            // Mobile portrait / narrow multi-window: gentler tilt & inward arc indent so no glyphs clip.
            val isNarrow = widthDp < 380
            ArcLyricViewportProfile(
                maxRotationZDeg = if (isNarrow) 7f else 8.5f,
                maxRotationXDeg = if (isNarrow) 10f else 12f,
                baseRotationYDeg = -4f,
                arcCurveIndentDp = if (isNarrow) 5f else 8f,
                minScale = 0.86f,
                primaryTextSizeSp = if (isNarrow) 24f else 26f,
                secondaryTextSizeSp = 14f,
                lineSpacingDp = 19,
                focusOffsetRatio = if (fullScreenLyrics) 0.43f else 0.40f,
                horizontalPaddingStartDp = if (isNarrow) 20 else 24,
                horizontalPaddingEndDp = if (isNarrow) 22 else 28,
                topContentPaddingDp = 64,
                bottomContentPaddingDp = if (fullScreenLyrics) 196 else 72,
                applySystemBarInsets = true,
            )
        }
    }
    val resolvedIndentDp = (base.arcCurveIndentDp * clampedScale).coerceAtMost(20f)
    val leftEdgeGuardDp = if (curveDirection == com.android.purebilibili.core.store.ArcLyricsCurveDirection.LEFT) {
        kotlin.math.ceil(resolvedIndentDp * 1.5625f).toInt() + 12
    } else {
        base.horizontalPaddingStartDp
    }
    return base.copy(
        maxRotationZDeg = (base.maxRotationZDeg * clampedScale).coerceAtMost(15f),
        maxRotationXDeg = (base.maxRotationXDeg * clampedScale).coerceAtMost(22f),
        baseRotationYDeg = (base.baseRotationYDeg * clampedScale).coerceAtLeast(-11f),
        arcCurveIndentDp = resolvedIndentDp,
        curveDirectionSign = curveDirection.directionSign,
        horizontalPaddingStartDp = maxOf(base.horizontalPaddingStartDp, leftEdgeGuardDp),
    )
}

internal data class ArcLyricTransform(
    val rotationZ: Float,
    val rotationX: Float,
    val rotationY: Float,
    val scale: Float,
    val translationXDp: Float,
    val alpha: Float,
)

internal fun resolveArcLyricTransform(
    normalizedOffset: Float,
    reduceMotion: Boolean = false,
    maxRotationZDeg: Float = 11f,
    maxRotationXDeg: Float = 18f,
    baseRotationYDeg: Float = -8f,
    arcCurveIndentDp: Float = 14f,
    curveDirectionSign: Float = -1f,
    minScale: Float = 0.84f,
): ArcLyricTransform {
    val d = normalizedOffset.coerceIn(-1.25f, 1.25f)
    val absD = abs(d)
    // Keep the active focus band at full alpha so word-by-word karaoke highlight retains full contrast;
    // AppleMusicLyricLine already dims inactive rows, so apply only a gentle outer-edge falloff.
    val edgeDistance = (absD - 0.25f).coerceAtLeast(0f)
    val alpha = (1f - edgeDistance * 0.52f).coerceIn(0.48f, 1f)
    if (reduceMotion) {
        return ArcLyricTransform(
            rotationZ = 0f,
            rotationX = 0f,
            rotationY = 0f,
            scale = 1f,
            translationXDp = 0f,
            alpha = alpha,
        )
    }
    return ArcLyricTransform(
        rotationZ = d * maxRotationZDeg,
        rotationX = -d * maxRotationXDeg,
        rotationY = baseRotationYDeg,
        scale = (1f - absD * (1f - minScale)).coerceIn(minScale, 1f),
        translationXDp = curveDirectionSign * (d * d) * arcCurveIndentDp,
        alpha = alpha,
    )
}

/**
 * Applies a left-anchored 3D arc-wheel perspective transform to each lyric row during the
 * graphics-layer phase so scrolling stays free of frame-rate composition invalidations.
 */
internal fun Modifier.arcLyricRowTransform(
    listState: LazyListState,
    itemKey: Any,
    focusOffsetRatio: Float,
    reduceMotion: Boolean,
    viewportProfile: ArcLyricViewportProfile? = null,
): Modifier = graphicsLayer {
    val layoutInfo = listState.layoutInfo
    val viewportHeight = layoutInfo.viewportSize.height.toFloat()
    if (viewportHeight <= 0f) return@graphicsLayer
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
        ?: return@graphicsLayer
    val focusY = viewportHeight * focusOffsetRatio.coerceIn(0.1f, 0.9f)
    val itemCenterY = itemInfo.offset + itemInfo.size * 0.5f
    val normalizedOffset = (itemCenterY - focusY) / (viewportHeight * 0.48f)
    val transform = if (viewportProfile != null) {
        resolveArcLyricTransform(
            normalizedOffset = normalizedOffset,
            reduceMotion = reduceMotion,
            maxRotationZDeg = viewportProfile.maxRotationZDeg,
            maxRotationXDeg = viewportProfile.maxRotationXDeg,
            baseRotationYDeg = viewportProfile.baseRotationYDeg,
            arcCurveIndentDp = viewportProfile.arcCurveIndentDp,
            curveDirectionSign = viewportProfile.curveDirectionSign,
            minScale = viewportProfile.minScale,
        )
    } else {
        resolveArcLyricTransform(
            normalizedOffset = normalizedOffset,
            reduceMotion = reduceMotion,
        )
    }
    transformOrigin = TransformOrigin(0f, 0.5f)
    cameraDistance = 16f * density
    rotationZ = transform.rotationZ
    rotationX = transform.rotationX
    rotationY = transform.rotationY
    scaleX = transform.scale
    scaleY = transform.scale
    translationX = transform.translationXDp * density
    alpha = transform.alpha
}
