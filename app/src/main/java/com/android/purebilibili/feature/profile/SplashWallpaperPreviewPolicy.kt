package com.android.purebilibili.feature.profile

internal fun resolveSplashWallpaperPreviewAspectRatio(
    isTabletPreview: Boolean,
    isTabletWindow: Boolean,
    windowWidthPx: Int,
    windowHeightPx: Int,
): Float {
    if (isTabletPreview == isTabletWindow && windowWidthPx > 0 && windowHeightPx > 0) {
        return windowWidthPx.toFloat() / windowHeightPx
    }
    // 另一设备与窗口尚未测量时仅展示示意比例，不改变已保存的对齐位置。
    return if (isTabletPreview) 16f / 10f else 9f / 18f
}
