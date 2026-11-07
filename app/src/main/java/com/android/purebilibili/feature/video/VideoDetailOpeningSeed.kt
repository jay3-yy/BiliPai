package com.android.purebilibili.feature.video

import android.graphics.Bitmap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import kotlin.math.roundToInt

/** 复用整卡像素，但播放器只能读取封面区域；标题与信息面仅属于窗口转场。 */
internal data class VideoDetailOpeningSeed(val bitmap: Bitmap, val coverPixels: IntRect)

internal val LocalVideoDetailOpeningSeed = staticCompositionLocalOf<VideoDetailOpeningSeed?> { null }

internal fun videoDetailOpeningSeed(
    bitmap: Bitmap,
    source: VideoCardTransitionSession,
): VideoDetailOpeningSeed? = resolveVideoDetailOpeningSeedCrop(
    source.cardBounds, source.coverBounds, source.sourceLayout, bitmap.width, bitmap.height,
)?.let { VideoDetailOpeningSeed(bitmap, it) }

/** 卡片和封面使用同一套根坐标；先取相对位置，再映射到实际截图像素。 */
internal fun resolveVideoDetailOpeningSeedCrop(
    cardBounds: Rect?,
    coverBounds: Rect?,
    sourceLayout: VideoCardSourceLayout,
    bitmapWidth: Int,
    bitmapHeight: Int,
): IntRect? {
    if (coverBounds == null) {
        return if (sourceLayout == VideoCardSourceLayout.COVER_ONLY) {
            IntRect(0, 0, bitmapWidth, bitmapHeight)
        } else null
    }
    val card = cardBounds ?: return null
    if (card.width <= 0f || card.height <= 0f) return null
    val scaleX = bitmapWidth / card.width
    val scaleY = bitmapHeight / card.height
    val pixels = IntRect(
        ((coverBounds.left - card.left) * scaleX).roundToInt().coerceIn(0, bitmapWidth),
        ((coverBounds.top - card.top) * scaleY).roundToInt().coerceIn(0, bitmapHeight),
        ((coverBounds.right - card.left) * scaleX).roundToInt().coerceIn(0, bitmapWidth),
        ((coverBounds.bottom - card.top) * scaleY).roundToInt().coerceIn(0, bitmapHeight),
    )
    return pixels.takeIf { it.width > 0 && it.height > 0 }
}
