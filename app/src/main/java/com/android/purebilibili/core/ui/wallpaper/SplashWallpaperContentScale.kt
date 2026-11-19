package com.android.purebilibili.core.ui.wallpaper

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import com.android.purebilibili.core.store.sanitizeSplashWallpaperScale

/**
 * 1x 保持全屏裁切；继续缩小时在完整图片比例处停止裁切，避免只缩放裁切后的画面。
 */
internal fun splashWallpaperContentScale(scale: Float): ContentScale =
    SplashWallpaperContentScale(sanitizeSplashWallpaperScale(scale))

private class SplashWallpaperContentScale(
    private val zoom: Float,
) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        if (srcSize.width <= 0f || srcSize.height <= 0f ||
            dstSize.width <= 0f || dstSize.height <= 0f
        ) {
            return ScaleFactor(1f, 1f)
        }
        val cropScale = maxOf(
            dstSize.width / srcSize.width,
            dstSize.height / srcSize.height,
        )
        val fitScale = minOf(
            dstSize.width / srcSize.width,
            dstSize.height / srcSize.height,
        )
        val fitThreshold = (fitScale / cropScale).coerceIn(0f, 1f)
        val resolved = if (zoom >= fitThreshold) {
            cropScale * zoom
        } else {
            fitScale * (zoom / fitThreshold)
        }
        return ScaleFactor(resolved, resolved)
    }
}
