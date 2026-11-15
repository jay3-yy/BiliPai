package com.android.purebilibili.core.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import coil3.ImageLoader
import coil3.asImage
import com.android.purebilibili.R

/** 全局图片占位；每次请求独立持有绘制状态，共享静态像素，不启动逐帧回调。 */
internal fun ImageLoader.Builder.biliImagePlaceholders(context: Context): ImageLoader.Builder {
    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bili_image_placeholder)
    val maxSize = 160f * context.resources.displayMetrics.density
    return placeholder { BiliImagePlaceholderDrawable(bitmap, maxSize).asImage() }
        .error { BiliImagePlaceholderDrawable(bitmap, maxSize).asImage() }
        .fallback { BiliImagePlaceholderDrawable(bitmap, maxSize).asImage() }
}

/** 无固有尺寸，让占位按实际图片槽位居中，避免被封面的 Crop 拉伸或裁掉。 */
private class BiliImagePlaceholderDrawable(
    private val bitmap: Bitmap,
    private val maxSize: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    override fun draw(canvas: Canvas) {
        val side = minOf(bounds.width().toFloat(), bounds.height().toFloat(), maxSize)
        val left = bounds.exactCenterX() - side / 2f
        val top = bounds.exactCenterY() - side / 2f
        destination.set(left, top, left + side, top + side)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
