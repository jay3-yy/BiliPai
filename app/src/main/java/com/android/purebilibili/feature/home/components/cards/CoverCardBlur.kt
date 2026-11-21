package com.android.purebilibili.feature.home.components.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val CoverSampleLongEdge = 36
private const val KernelRadius = 10
private const val KernelSigma = 3.5

private val gaussianWeights = DoubleArray(KernelRadius + 1) { distance ->
    exp(-(distance * distance) / (2.0 * KernelSigma * KernelSigma))
}.let { weights ->
    val total = 2.0 * weights.sum() - weights[0]
    DoubleArray(weights.size) { weights[it] / total }
}

/** 保留图像在横纵方向上的颜色变化，生成可放大绘制的高斯模糊位图。 */
internal fun createBlurredCardImage(
    image: Bitmap,
    longEdge: Int = CoverSampleLongEdge,
    sourceRegion: Rect = Rect(0, 0, image.width, image.height),
    background: Int = Color.TRANSPARENT,
): Bitmap {
    val scale = longEdge.toFloat() / maxOf(sourceRegion.width(), sourceRegion.height())
    val width = (sourceRegion.width() * scale).roundToInt().coerceAtLeast(1)
    val height = (sourceRegion.height() * scale).roundToInt().coerceAtLeast(1)
    val sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    sample.eraseColor(background)
    Canvas(sample).drawBitmap(image, sourceRegion, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
    val pixels = IntArray(width * height)
    sample.getPixels(pixels, 0, width, 0, 0, width, height)
    sample.recycle()

    val horizontal = IntArray(pixels.size)
    blurCoverLines(pixels, horizontal, lineCount = height, lineLength = width, lineStep = width, pixelStep = 1)
    blurCoverLines(horizontal, pixels, lineCount = width, lineLength = height, lineStep = 1, pixelStep = width)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun blurCoverLines(
    source: IntArray,
    output: IntArray,
    lineCount: Int,
    lineLength: Int,
    lineStep: Int,
    pixelStep: Int,
) {
    for (line in 0 until lineCount) {
        val start = line * lineStep
        for (position in 0 until lineLength) {
            var opacity = 0.0
            var red = 0.0
            var green = 0.0
            var blue = 0.0
            for (offset in -KernelRadius..KernelRadius) {
                val pixel = source[start + (position + offset).coerceIn(0, lineLength - 1) * pixelStep]
                val weight = gaussianWeights[abs(offset)] * Color.alpha(pixel)
                opacity += weight
                red += Color.red(pixel) * weight
                green += Color.green(pixel) * weight
                blue += Color.blue(pixel) * weight
            }
            output[start + position * pixelStep] = if (opacity < 0.5) Color.TRANSPARENT else Color.argb(
                opacity.roundToInt(),
                (red / opacity).roundToInt(),
                (green / opacity).roundToInt(),
                (blue / opacity).roundToInt(),
            )
        }
    }
}
