package com.android.purebilibili.feature.dynamic.components

internal data class ImageDecodeSize(
    val widthPx: Int,
    val heightPx: Int
)

internal enum class ImageDecodeTarget {
    FULLSCREEN_PREVIEW,
    COMMENT_THUMBNAIL,
    DYNAMIC_GRID_THUMBNAIL,
    /** 原图模式的整图底图；完整细节由视口区域解码提供。 */
    ORIGINAL_QUALITY
}

private const val FULLSCREEN_PREVIEW_MAX_EDGE_PX = 4096
private const val COMMENT_THUMBNAIL_MAX_EDGE_PX = 1024
private const val DYNAMIC_SINGLE_MAX_EDGE_PX = 1280
private const val DYNAMIC_MULTI_MAX_EDGE_PX = 768
private const val ORIGINAL_QUALITY_MAX_EDGE_PX = 4608

internal fun resolveImageDecodeSize(target: ImageDecodeTarget): ImageDecodeSize {
    val maxEdgePx = when (target) {
        ImageDecodeTarget.FULLSCREEN_PREVIEW -> FULLSCREEN_PREVIEW_MAX_EDGE_PX
        ImageDecodeTarget.COMMENT_THUMBNAIL -> COMMENT_THUMBNAIL_MAX_EDGE_PX
        ImageDecodeTarget.DYNAMIC_GRID_THUMBNAIL -> DYNAMIC_SINGLE_MAX_EDGE_PX
        ImageDecodeTarget.ORIGINAL_QUALITY -> ORIGINAL_QUALITY_MAX_EDGE_PX
    }
    return ImageDecodeSize(widthPx = maxEdgePx, heightPx = maxEdgePx)
}

/**
 * 动态图文九宫格按实际格子尺寸请求解码，同时受单图/多图最大边上限约束：
 * - 小格子按实际像素请求，避免强行拉高 CDN 宽度桶（如 360px 格子从 480w 跳到 960w）；
 * - 大屏或超大图仍以单图 1280px / 多图 768px 封顶，点击全屏预览继续走 `FULLSCREEN_PREVIEW` / `ORIGINAL_QUALITY`。
 */
internal fun resolveDynamicGridDecodeSize(
    displayCount: Int,
    targetWidthPx: Int = 0,
    targetHeightPx: Int = 0,
): ImageDecodeSize {
    val maxEdgePx = if (displayCount <= 1) {
        DYNAMIC_SINGLE_MAX_EDGE_PX
    } else {
        DYNAMIC_MULTI_MAX_EDGE_PX
    }
    if (targetWidthPx <= 0) {
        return ImageDecodeSize(widthPx = maxEdgePx, heightPx = maxEdgePx)
    }
    val rawHeightPx = if (targetHeightPx > 0) targetHeightPx else targetWidthPx
    val longestEdge = maxOf(targetWidthPx, rawHeightPx)
    if (longestEdge <= maxEdgePx) {
        return ImageDecodeSize(widthPx = targetWidthPx, heightPx = rawHeightPx)
    }
    val scale = maxEdgePx.toFloat() / longestEdge.toFloat()
    return ImageDecodeSize(
        widthPx = (targetWidthPx * scale).toInt().coerceIn(1, maxEdgePx),
        heightPx = (rawHeightPx * scale).toInt().coerceIn(1, maxEdgePx),
    )
}

internal fun estimateArgb8888ByteCount(size: ImageDecodeSize): Long {
    return size.widthPx.toLong() * size.heightPx.toLong() * 4L
}
