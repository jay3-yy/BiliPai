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
 * 动态图文九宫格按单图/多图布局使用静态解码上限，保持磁盘/内存缓存键与 CDN 宽度桶稳定；
 * 点击进入全屏预览仍走 `FULLSCREEN_PREVIEW` / `ORIGINAL_QUALITY`，不影响看图清晰度。
 */
internal fun resolveDynamicGridDecodeSize(displayCount: Int): ImageDecodeSize {
    val maxEdgePx = if (displayCount <= 1) {
        DYNAMIC_SINGLE_MAX_EDGE_PX
    } else {
        DYNAMIC_MULTI_MAX_EDGE_PX
    }
    return ImageDecodeSize(widthPx = maxEdgePx, heightPx = maxEdgePx)
}

internal fun estimateArgb8888ByteCount(size: ImageDecodeSize): Long {
    return size.widthPx.toLong() * size.heightPx.toLong() * 4L
}
