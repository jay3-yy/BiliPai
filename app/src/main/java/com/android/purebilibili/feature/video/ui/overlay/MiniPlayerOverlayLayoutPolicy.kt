package com.android.purebilibili.feature.video.ui.overlay

data class MiniPlayerOverlayLayoutPolicy(
    val miniPlayerWidthDp: Int,
    val miniPlayerHeightDp: Int,
    val outerPaddingDp: Int,
    val headerHeightDp: Int,
    val cardCornerRadiusDp: Int,
    val cardShadowDp: Int,
    val cardElevationDp: Int,
    val dragTopInsetDp: Int,
    val dragBottomInsetDp: Int,
    val titleFontSp: Int,
    val titleStartPaddingDp: Int,
    val titleEndPaddingDp: Int,
    val headerButtonSizeDp: Int,
    val headerButtonIconSizeDp: Int,
    val headerButtonIconPaddingDp: Int,
    val headerButtonRowEndPaddingDp: Int,
    val headerButtonSpacingDp: Int,
    val controlsGradientHeightDp: Int,
    val centerPlayIconPaddingDp: Int,
    val centerPlayIconSizeDp: Int,
    val seekHintBottomPaddingDp: Int,
    val seekHintCornerRadiusDp: Int,
    val seekHintFontSp: Int,
    val seekHintHorizontalPaddingDp: Int,
    val seekHintVerticalPaddingDp: Int,
    val dragHintBottomPaddingDp: Int,
    val dragHintFontSp: Int,
    val progressBarHeightDp: Int,
    val stashedWidthDp: Int,
    val stashedHeightDp: Int,
    val stashedSideCornerExtraDp: Int,
    val stashedIconSizeDp: Int,
    val stashedShadowDp: Int
)

fun resolveMiniPlayerOverlayLayoutPolicy(
    widthDp: Int
): MiniPlayerOverlayLayoutPolicy {
    if (widthDp >= 1600) {
        return MiniPlayerOverlayLayoutPolicy(
            miniPlayerWidthDp = 320,
            miniPlayerHeightDp = 180,
            outerPaddingDp = 16,
            headerHeightDp = 32,
            cardCornerRadiusDp = 18,
            cardShadowDp = 18,
            cardElevationDp = 14,
            dragTopInsetDp = 60,
            dragBottomInsetDp = 120,
            titleFontSp = 12,
            titleStartPaddingDp = 10,
            titleEndPaddingDp = 128,
            headerButtonSizeDp = 28,
            headerButtonIconSizeDp = 16,
            headerButtonIconPaddingDp = 6,
            headerButtonRowEndPaddingDp = 6,
            headerButtonSpacingDp = 4,
            controlsGradientHeightDp = 44,
            centerPlayIconPaddingDp = 12,
            centerPlayIconSizeDp = 30,
            seekHintBottomPaddingDp = 18,
            seekHintCornerRadiusDp = 12,
            seekHintFontSp = 13,
            seekHintHorizontalPaddingDp = 14,
            seekHintVerticalPaddingDp = 7,
            dragHintBottomPaddingDp = 8,
            dragHintFontSp = 10,
            progressBarHeightDp = 2,
            stashedWidthDp = 44,
            stashedHeightDp = 52,
            stashedSideCornerExtraDp = 12,
            stashedIconSizeDp = 22,
            stashedShadowDp = 10
        )
    }

    if (widthDp >= 840) {
        return MiniPlayerOverlayLayoutPolicy(
            miniPlayerWidthDp = 300,
            miniPlayerHeightDp = 168,
            outerPaddingDp = 14,
            headerHeightDp = 30,
            cardCornerRadiusDp = 17,
            cardShadowDp = 17,
            cardElevationDp = 13,
            dragTopInsetDp = 56,
            dragBottomInsetDp = 110,
            titleFontSp = 12,
            titleStartPaddingDp = 9,
            titleEndPaddingDp = 120,
            headerButtonSizeDp = 26,
            headerButtonIconSizeDp = 15,
            headerButtonIconPaddingDp = 5,
            headerButtonRowEndPaddingDp = 5,
            headerButtonSpacingDp = 3,
            controlsGradientHeightDp = 42,
            centerPlayIconPaddingDp = 11,
            centerPlayIconSizeDp = 29,
            seekHintBottomPaddingDp = 17,
            seekHintCornerRadiusDp = 10,
            seekHintFontSp = 12,
            seekHintHorizontalPaddingDp = 13,
            seekHintVerticalPaddingDp = 6,
            dragHintBottomPaddingDp = 7,
            dragHintFontSp = 10,
            progressBarHeightDp = 2,
            stashedWidthDp = 42,
            stashedHeightDp = 50,
            stashedSideCornerExtraDp = 10,
            stashedIconSizeDp = 21,
            stashedShadowDp = 9
        )
    }

    if (widthDp >= 600) {
        return MiniPlayerOverlayLayoutPolicy(
            miniPlayerWidthDp = 280,
            miniPlayerHeightDp = 157,
            outerPaddingDp = 12,
            headerHeightDp = 28,
            cardCornerRadiusDp = 16,
            cardShadowDp = 16,
            cardElevationDp = 12,
            dragTopInsetDp = 50,
            dragBottomInsetDp = 100,
            titleFontSp = 11,
            titleStartPaddingDp = 8,
            titleEndPaddingDp = 112,
            headerButtonSizeDp = 24,
            headerButtonIconSizeDp = 14,
            headerButtonIconPaddingDp = 5,
            headerButtonRowEndPaddingDp = 4,
            headerButtonSpacingDp = 2,
            controlsGradientHeightDp = 40,
            centerPlayIconPaddingDp = 10,
            centerPlayIconSizeDp = 28,
            seekHintBottomPaddingDp = 16,
            seekHintCornerRadiusDp = 8,
            seekHintFontSp = 12,
            seekHintHorizontalPaddingDp = 12,
            seekHintVerticalPaddingDp = 6,
            dragHintBottomPaddingDp = 6,
            dragHintFontSp = 9,
            progressBarHeightDp = 2,
            stashedWidthDp = 40,
            stashedHeightDp = 48,
            stashedSideCornerExtraDp = 8,
            stashedIconSizeDp = 20,
            stashedShadowDp = 8
        )
    }

    return MiniPlayerOverlayLayoutPolicy(
        miniPlayerWidthDp = 220,
        miniPlayerHeightDp = 130,
        outerPaddingDp = 12,
        headerHeightDp = 28,
        cardCornerRadiusDp = 16,
        cardShadowDp = 16,
        cardElevationDp = 12,
        dragTopInsetDp = 50,
        dragBottomInsetDp = 100,
        titleFontSp = 11,
        titleStartPaddingDp = 8,
        titleEndPaddingDp = 112,
        headerButtonSizeDp = 24,
        headerButtonIconSizeDp = 14,
        headerButtonIconPaddingDp = 5,
        headerButtonRowEndPaddingDp = 4,
        headerButtonSpacingDp = 2,
        controlsGradientHeightDp = 40,
        centerPlayIconPaddingDp = 10,
        centerPlayIconSizeDp = 28,
        seekHintBottomPaddingDp = 16,
        seekHintCornerRadiusDp = 8,
        seekHintFontSp = 12,
        seekHintHorizontalPaddingDp = 12,
        seekHintVerticalPaddingDp = 6,
        dragHintBottomPaddingDp = 6,
        dragHintFontSp = 9,
        progressBarHeightDp = 2,
        stashedWidthDp = 40,
        stashedHeightDp = 48,
        stashedSideCornerExtraDp = 8,
        stashedIconSizeDp = 20,
        stashedShadowDp = 8
    )
}

data class AdaptiveMiniPlayerDimensions(
    val widthDp: Float,
    val heightDp: Float,
    val aspectRatio: Float,
)

/**
 * Resolves adaptive mini player dimensions based on the actual video aspect ratio.
 * - Horizontal videos (aspect >= 1.0, e.g. 16:9, 4:3, 21:9): anchored by baseWidthDp, height = width / aspect.
 * - Vertical videos (aspect < 1.0, e.g. 9:16 story/shorts): anchored by height, width = height * aspect.
 * This guarantees zero black bars for vertical videos while maintaining a clean, compact floating card size.
 */
fun resolveAdaptiveMiniPlayerDimensions(
    videoAspectRatio: Float,
    currentWidthDp: Float,
    defaultHeightDp: Float,
    maxWidthDp: Float = Float.POSITIVE_INFINITY,
    maxHeightDp: Float = Float.POSITIVE_INFINITY,
): AdaptiveMiniPlayerDimensions {
    val safeAspect = if (videoAspectRatio.isFinite() && videoAspectRatio > 0f) {
        videoAspectRatio.coerceIn(0.45f, 2.39f)
    } else {
        16f / 9f
    }

    val dimensions = if (safeAspect >= 1.0f) {
        val width = currentWidthDp.coerceAtLeast(120f)
        val height = width / safeAspect
        AdaptiveMiniPlayerDimensions(
            widthDp = width,
            heightDp = height,
            aspectRatio = safeAspect,
        )
    } else {
        val scale = (currentWidthDp / 220f).coerceAtLeast(0.5f)
        val baseHeight = (defaultHeightDp * 1.55f).coerceIn(180f, 320f)
        val height = (baseHeight * scale).coerceIn(160f, 450f)
        val width = height * safeAspect
        AdaptiveMiniPlayerDimensions(
            widthDp = width,
            heightDp = height,
            aspectRatio = safeAspect,
        )
    }
    val fitScale = minOf(
        1f,
        maxWidthDp.coerceAtLeast(1f) / dimensions.widthDp,
        maxHeightDp.coerceAtLeast(1f) / dimensions.heightDp,
    )
    return dimensions.copy(widthDp = dimensions.widthDp * fitScale, heightDp = dimensions.heightDp * fitScale)
}

internal fun MiniPlayerOverlayLayoutPolicy.dimensionsForWindow(
    videoAspectRatio: Float,
    widthDp: Float,
    screenWidthDp: Int,
    screenHeightDp: Int,
): AdaptiveMiniPlayerDimensions = resolveAdaptiveMiniPlayerDimensions(
    videoAspectRatio = videoAspectRatio,
    currentWidthDp = widthDp,
    defaultHeightDp = miniPlayerHeightDp.toFloat(),
    maxWidthDp = (screenWidthDp - outerPaddingDp * 2).coerceAtLeast(1).toFloat(),
    maxHeightDp = (screenHeightDp - outerPaddingDp * 2 - dragTopInsetDp - dragBottomInsetDp)
        .coerceAtLeast(1).toFloat(),
)
