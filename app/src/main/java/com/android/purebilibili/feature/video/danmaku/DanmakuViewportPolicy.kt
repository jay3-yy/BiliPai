package com.android.purebilibili.feature.video.danmaku

/**
 * Geometry shared by every video-danmaku layer.
 *
 * Density is the system display density, not the app's Compose UI-scale override.
 * The measured box controls row capacity, not the portrait/fullscreen font baseline.
 * When [fillDisplayArea] is true (e.g. the dedicated portrait letterbox band above a landscape
 * video), the band already represents the reserved danmaku area and should not be shrunk again
 * by the general display-area ratio.
 */
data class DanmakuViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fillDisplayArea: Boolean = false,
) {
    init {
        require(widthPx > 0 && heightPx > 0)
        require(density.isFinite() && density > 0f)
    }
}

fun resolveDanmakuViewport(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    fillDisplayArea: Boolean = false,
): DanmakuViewport? {
    if (widthPx <= 0 || heightPx <= 0 || !density.isFinite() || density <= 0f) return null
    return DanmakuViewport(
        widthPx = widthPx,
        heightPx = heightPx,
        density = density,
        fillDisplayArea = fillDisplayArea,
    )
}

internal data class PortraitDanmakuUpperRegion(
    val topPx: Int,
    val heightPx: Int,
)

/**
 * Only ordinary comments may use letterboxing; authored coordinates remain video-relative.
 * Account for FIT inside the player host, its glass offset, and user zoom/pan before reserving
 * the upper band. A cropped/stretched or full-height picture must not invent free space.
 */
internal fun resolvePortraitDanmakuUpperRegion(
    containerWidthPx: Int,
    containerHeightPx: Int,
    videoViewportWidthPx: Int,
    videoViewportHeightPx: Int,
    videoWidth: Int,
    videoHeight: Int,
    fitVideoInsideViewport: Boolean,
    viewportOffsetYPx: Float,
    safeTopPx: Int,
    minimumHeightPx: Float,
    videoScale: Float = 1f,
    videoTranslationYPx: Float = 0f,
): PortraitDanmakuUpperRegion? {
    if (containerWidthPx <= 0 || containerHeightPx <= containerWidthPx ||
        videoViewportWidthPx <= 0 || videoViewportHeightPx <= 0 ||
        videoWidth <= videoHeight || videoHeight <= 0 ||
        !videoScale.isFinite() || videoScale <= 0f ||
        !viewportOffsetYPx.isFinite() || !videoTranslationYPx.isFinite() ||
        !minimumHeightPx.isFinite() || minimumHeightPx <= 0f
    ) return null

    val pictureHeight = if (fitVideoInsideViewport) {
        minOf(
            videoViewportHeightPx.toFloat(),
            videoViewportWidthPx * (videoHeight.toFloat() / videoWidth),
        )
    } else {
        videoViewportHeightPx.toFloat()
    }
    val pictureTop = (containerHeightPx - videoViewportHeightPx) / 2f +
        viewportOffsetYPx + (videoViewportHeightPx - pictureHeight * videoScale) / 2f +
        videoTranslationYPx
    val top = safeTopPx.coerceAtLeast(0)
    val bottom = kotlin.math.floor(pictureTop).toInt().coerceIn(0, containerHeightPx)
    val height = bottom - top
    if (height < minimumHeightPx) return null
    return PortraitDanmakuUpperRegion(topPx = top, heightPx = height)
}
