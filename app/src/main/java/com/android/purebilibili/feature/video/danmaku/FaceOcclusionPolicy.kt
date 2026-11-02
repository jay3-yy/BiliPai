package com.android.purebilibili.feature.video.danmaku

import androidx.media3.ui.AspectRatioFrameLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class FaceOcclusionMaskRect(
    val leftRatio: Float,
    val topRatio: Float,
    val rightRatio: Float,
    val bottomRatio: Float
) {
    val widthRatio: Float
        get() = (rightRatio - leftRatio).coerceAtLeast(0f)
    val heightRatio: Float
        get() = (bottomRatio - topRatio).coerceAtLeast(0f)
    val areaRatio: Float
        get() = widthRatio * heightRatio

    fun normalized(): FaceOcclusionMaskRect {
        val left = leftRatio.coerceIn(0f, 1f)
        val top = topRatio.coerceIn(0f, 1f)
        val right = rightRatio.coerceIn(left, 1f)
        val bottom = bottomRatio.coerceIn(top, 1f)
        return FaceOcclusionMaskRect(left, top, right, bottom)
    }

    fun expanded(expansion: Float): FaceOcclusionMaskRect {
        val pad = expansion.coerceIn(0f, 0.2f)
        return FaceOcclusionMaskRect(
            leftRatio = leftRatio - pad,
            topRatio = topRatio - pad,
            rightRatio = rightRatio + pad,
            bottomRatio = bottomRatio + pad
        ).normalized()
    }
}

internal data class NormalizedPoint(
    val xRatio: Float,
    val yRatio: Float
) {
    fun normalized(): NormalizedPoint {
        return NormalizedPoint(
            xRatio = xRatio.coerceIn(0f, 1f),
            yRatio = yRatio.coerceIn(0f, 1f)
        )
    }
}

internal data class FaceOcclusionVisualMask(
    val fallbackRect: FaceOcclusionMaskRect,
    val polygonPoints: List<NormalizedPoint>
)

internal data class FaceOcclusionMaskStabilizerConfig(
    val positionLerpFactor: Float = 0.35f,
    val minIouForTracking: Float = 0.2f,
    val holdMissingFrames: Int = 2,
    val maxMaskCount: Int = 6
)

internal class FaceOcclusionMaskStabilizer(
    private val config: FaceOcclusionMaskStabilizerConfig = FaceOcclusionMaskStabilizerConfig()
) {
    private var trackedMasks: List<FaceOcclusionVisualMask> = emptyList()
    private var missingFrames: Int = 0

    fun reset() {
        trackedMasks = emptyList()
        missingFrames = 0
    }

    fun step(detectedMasks: List<FaceOcclusionVisualMask>): List<FaceOcclusionVisualMask> {
        val maxCount = config.maxMaskCount.coerceIn(1, 12)
        val normalizedDetected = sortMasks(
            detectedMasks.map { it.normalizedVisualMask() }.take(maxCount)
        )

        if (normalizedDetected.isEmpty()) {
            missingFrames += 1
            if (missingFrames <= config.holdMissingFrames.coerceAtLeast(0)) {
                return trackedMasks
            }
            trackedMasks = emptyList()
            return emptyList()
        }

        missingFrames = 0
        if (trackedMasks.isEmpty()) {
            trackedMasks = normalizedDetected
            return trackedMasks
        }

        val unmatchedPrevious = trackedMasks.toMutableList()
        val next = ArrayList<FaceOcclusionVisualMask>(normalizedDetected.size)
        normalizedDetected.forEach { current ->
            val matchIndex = findBestMaskMatchIndex(
                previousMasks = unmatchedPrevious,
                currentMask = current,
                minIou = config.minIouForTracking.coerceIn(0f, 1f)
            )
            if (matchIndex < 0) {
                next += current
                return@forEach
            }

            val previous = unmatchedPrevious.removeAt(matchIndex)
            next += smoothVisualMask(
                previous = previous,
                current = current,
                factor = config.positionLerpFactor.coerceIn(0f, 1f)
            )
        }

        trackedMasks = sortMasks(next).take(maxCount)
        return trackedMasks
    }
}

internal data class FaceViewportRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float
        get() = (right - left).coerceAtLeast(0f)
    val height: Float
        get() = (bottom - top).coerceAtLeast(0f)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun resolveVideoContentRect(
    containerWidth: Int,
    containerHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    resizeMode: Int
): FaceViewportRect {
    val cw = containerWidth.coerceAtLeast(1).toFloat()
    val ch = containerHeight.coerceAtLeast(1).toFloat()
    if (videoWidth <= 0 || videoHeight <= 0) {
        return FaceViewportRect(0f, 0f, cw, ch)
    }

    val videoAspect = (videoWidth.toFloat() / videoHeight.toFloat()).coerceAtLeast(0.01f)
    val containerAspect = cw / ch

    val (contentWidth, contentHeight) = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> cw to ch
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> {
            val h = cw / videoAspect
            cw to h
        }
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> {
            val w = ch * videoAspect
            w to ch
        }
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
            if (videoAspect > containerAspect) {
                val w = ch * videoAspect
                w to ch
            } else {
                val h = cw / videoAspect
                cw to h
            }
        }
        else -> {
            if (videoAspect > containerAspect) {
                val h = cw / videoAspect
                cw to h
            } else {
                val w = ch * videoAspect
                w to ch
            }
        }
    }

    val left = (cw - contentWidth) / 2f
    val top = (ch - contentHeight) / 2f
    return FaceViewportRect(
        left = left,
        top = top,
        right = left + contentWidth,
        bottom = top + contentHeight
    )
}

internal fun resolveMaskEdgeExpansionRatio(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    featherPx: Float = 8f,
    minRatio: Float = 0.004f,
    maxRatio: Float = 0.03f
): Float {
    val width = viewportWidthPx.coerceAtLeast(1f)
    val height = viewportHeightPx.coerceAtLeast(1f)
    val shortSide = min(width, height)
    val feather = featherPx.coerceAtLeast(0f)
    if (feather <= 0f) {
        return minRatio.coerceIn(0f, maxRatio.coerceAtLeast(0f))
    }
    return (feather / shortSide).coerceIn(
        minRatio.coerceIn(0f, 1f),
        maxRatio.coerceIn(minRatio.coerceIn(0f, 1f), 0.2f)
    )
}

internal fun resolveFaceOcclusionMasks(
    rawRects: List<FaceOcclusionMaskRect>,
    expansionRatio: Float = 0.04f,
    minSizeRatio: Float = 0.035f,
    mergeGapRatio: Float = 0.02f,
    maxMaskCount: Int = 4
): List<FaceOcclusionMaskRect> {
    if (rawRects.isEmpty()) return emptyList()

    val minSize = minSizeRatio.coerceIn(0.01f, 0.2f)
    val expansion = expansionRatio.coerceIn(0f, 0.2f)
    val mergeGap = mergeGapRatio.coerceIn(0f, 0.1f)
    val maxCount = maxMaskCount.coerceIn(1, 8)

    val normalized = rawRects
        .map { it.normalized().expanded(expansion) }
        .filter { it.widthRatio >= minSize && it.heightRatio >= minSize }
        .sortedByDescending { it.areaRatio }

    if (normalized.isEmpty()) return emptyList()

    val merged = ArrayList<FaceOcclusionMaskRect>(normalized.size)
    normalized.forEach { rect ->
        var current = rect
        var index = 0
        while (index < merged.size) {
            val existing = merged[index]
            if (!isRectIntersectOrClose(existing, current, mergeGap)) {
                index++
                continue
            }
            current = mergeRects(existing, current)
            merged.removeAt(index)
            index = 0
        }
        merged += current
    }

    return merged
        .sortedByDescending { it.areaRatio }
        .take(maxCount)
}

private fun isRectIntersectOrClose(
    a: FaceOcclusionMaskRect,
    b: FaceOcclusionMaskRect,
    gap: Float
): Boolean {
    val horizontalSeparated = a.rightRatio + gap < b.leftRatio || b.rightRatio + gap < a.leftRatio
    val verticalSeparated = a.bottomRatio + gap < b.topRatio || b.bottomRatio + gap < a.topRatio
    return !horizontalSeparated && !verticalSeparated
}

private fun mergeRects(
    a: FaceOcclusionMaskRect,
    b: FaceOcclusionMaskRect
): FaceOcclusionMaskRect {
    return FaceOcclusionMaskRect(
        leftRatio = minOf(a.leftRatio, b.leftRatio),
        topRatio = minOf(a.topRatio, b.topRatio),
        rightRatio = maxOf(a.rightRatio, b.rightRatio),
        bottomRatio = maxOf(a.bottomRatio, b.bottomRatio)
    ).normalized()
}

internal fun expandNormalizedPolygon(
    points: List<NormalizedPoint>,
    expansionRatio: Float
): List<NormalizedPoint> {
    if (points.isEmpty()) return emptyList()
    val normalized = points.map { it.normalized() }
    if (normalized.size < 3) return normalized

    val expansion = expansionRatio.coerceIn(0f, 0.2f)
    if (expansion <= 0f) return normalized

    val cx = normalized.sumOf { it.xRatio.toDouble() }.toFloat() / normalized.size
    val cy = normalized.sumOf { it.yRatio.toDouble() }.toFloat() / normalized.size
    val scale = 1f + expansion * 1.8f

    return normalized.map { point ->
        val dx = point.xRatio - cx
        val dy = point.yRatio - cy
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 0.0001f) {
            return@map point
        }
        NormalizedPoint(
            xRatio = cx + dx * scale,
            yRatio = cy + dy * scale
        ).normalized()
    }
}

internal fun buildVisualMask(
    rect: FaceOcclusionMaskRect,
    polygon: List<NormalizedPoint>,
    polygonMinPoints: Int = 5,
    polygonExpansionRatio: Float = 0.035f
): FaceOcclusionVisualMask {
    val normalizedRect = rect.normalized()
    val normalizedPolygon = polygon.map { it.normalized() }
    val visualPolygon = if (normalizedPolygon.size >= polygonMinPoints) {
        expandNormalizedPolygon(normalizedPolygon, polygonExpansionRatio)
    } else {
        emptyList()
    }

    return FaceOcclusionVisualMask(
        fallbackRect = normalizedRect.expanded(0.015f),
        polygonPoints = visualPolygon
    )
}

private fun FaceOcclusionVisualMask.normalizedVisualMask(): FaceOcclusionVisualMask {
    return FaceOcclusionVisualMask(
        fallbackRect = fallbackRect.normalized(),
        polygonPoints = polygonPoints.map { it.normalized() }
    )
}

private fun findBestMaskMatchIndex(
    previousMasks: List<FaceOcclusionVisualMask>,
    currentMask: FaceOcclusionVisualMask,
    minIou: Float
): Int {
    var bestIndex = -1
    var bestIou = minIou
    previousMasks.forEachIndexed { index, previous ->
        val iou = rectIou(previous.fallbackRect, currentMask.fallbackRect)
        if (iou >= bestIou) {
            bestIou = iou
            bestIndex = index
        }
    }
    return bestIndex
}

private fun rectIou(a: FaceOcclusionMaskRect, b: FaceOcclusionMaskRect): Float {
    val interLeft = max(a.leftRatio, b.leftRatio)
    val interTop = max(a.topRatio, b.topRatio)
    val interRight = min(a.rightRatio, b.rightRatio)
    val interBottom = min(a.bottomRatio, b.bottomRatio)
    val interWidth = (interRight - interLeft).coerceAtLeast(0f)
    val interHeight = (interBottom - interTop).coerceAtLeast(0f)
    val intersection = interWidth * interHeight
    if (intersection <= 0f) return 0f

    val union = a.areaRatio + b.areaRatio - intersection
    if (union <= 0f) return 0f
    return (intersection / union).coerceIn(0f, 1f)
}

private fun smoothVisualMask(
    previous: FaceOcclusionVisualMask,
    current: FaceOcclusionVisualMask,
    factor: Float
): FaceOcclusionVisualMask {
    val f = factor.coerceIn(0f, 1f)
    if (f <= 0f) return previous
    if (f >= 1f) return current

    val rect = FaceOcclusionMaskRect(
        leftRatio = lerp(previous.fallbackRect.leftRatio, current.fallbackRect.leftRatio, f),
        topRatio = lerp(previous.fallbackRect.topRatio, current.fallbackRect.topRatio, f),
        rightRatio = lerp(previous.fallbackRect.rightRatio, current.fallbackRect.rightRatio, f),
        bottomRatio = lerp(previous.fallbackRect.bottomRatio, current.fallbackRect.bottomRatio, f)
    ).normalized()

    val polygon = when {
        current.polygonPoints.isEmpty() -> previous.polygonPoints
        previous.polygonPoints.size == current.polygonPoints.size && current.polygonPoints.size >= 5 -> {
            current.polygonPoints.mapIndexed { index, point ->
                val prev = previous.polygonPoints[index]
                NormalizedPoint(
                    xRatio = lerp(prev.xRatio, point.xRatio, f),
                    yRatio = lerp(prev.yRatio, point.yRatio, f)
                ).normalized()
            }
        }
        else -> current.polygonPoints
    }

    return FaceOcclusionVisualMask(
        fallbackRect = rect,
        polygonPoints = polygon
    )
}

private fun sortMasks(masks: List<FaceOcclusionVisualMask>): List<FaceOcclusionVisualMask> {
    return masks.sortedWith(
        compareBy<FaceOcclusionVisualMask> { it.fallbackRect.topRatio }
            .thenBy { it.fallbackRect.leftRatio }
    )
}

private fun lerp(start: Float, end: Float, factor: Float): Float {
    return start + (end - start) * factor
}
