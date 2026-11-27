package com.android.purebilibili.feature.dynamic.components

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build

/**
 * 仅在 Android 14+、当前页检测到真实 Ultra HDR Gainmap、用户未手动关闭增强且未处于退场过渡时
 * 点亮预览弹窗的 COLOR_MODE_HDR，切到普通 SDR 图或关闭弹窗立即恢复默认，避免额外合成功耗。
 */
internal fun resolveUltraHdrWindowColorMode(
    hasGainmap: Boolean,
    boostEnabled: Boolean,
    isDismissing: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Int {
    return if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasGainmap && boostEnabled && !isDismissing) {
        ActivityInfo.COLOR_MODE_HDR
    } else {
        ActivityInfo.COLOR_MODE_DEFAULT
    }
}

internal fun bitmapHasUltraHdrGainmap(bitmap: Bitmap?): Boolean {
    if (bitmap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    return bitmap.hasGainmap()
}
