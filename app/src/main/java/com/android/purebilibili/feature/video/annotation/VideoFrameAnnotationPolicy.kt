package com.android.purebilibili.feature.video.annotation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

internal data class NormalizedStrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
)

internal data class VideoAnnotationStroke(
    val colorArgb: Int,
    val widthRatio: Float,
    val points: List<NormalizedStrokePoint>,
)

internal val VIDEO_ANNOTATION_PALETTE = listOf(
    Color(0xFFFF3B30), // 批注红
    Color(0xFFFFCC00), // 重点黄
    Color(0xFF0A84FF), // 标注蓝
    Color(0xFFFFFFFF), // 亮白
)

/** 仅允许手写笔或鼠标触发进度条悬停缩略图预览，绝不干扰触屏按压与拖动。 */
internal fun shouldActivatePointerHoverPreview(
    isStylusOrMouse: Boolean,
    isSeekScrubbing: Boolean,
    durationMs: Long,
): Boolean = isStylusOrMouse && !isSeekScrubbing && durationMs > 0L

/** 根据笔压感平滑调节线宽系数（0.65x..1.55x），手指或无压感设备维持 1.0x。 */
internal fun resolveStylusStrokeWidthScale(pressure: Float?): Float {
    val safe = pressure?.takeIf { it.isFinite() && it > 0f } ?: return 1f
    return (0.65f + safe.coerceIn(0f, 1.2f) * 0.75f).coerceIn(0.65f, 1.55f)
}

/** 将归一化坐标的批注路径无损合成到原始分辨率截图上，返回新位图由调用方回收。 */
internal fun renderAnnotatedBitmap(
    source: Bitmap,
    strokes: List<VideoAnnotationStroke>,
): Bitmap {
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    if (strokes.isEmpty()) return output
    val canvas = Canvas(output)
    val width = output.width.toFloat()
    val height = output.height.toFloat()
    val minSide = minOf(width, height).coerceAtLeast(1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        val pts = stroke.points
        if (pts.isEmpty()) return@forEach
        paint.color = stroke.colorArgb
        val avgPressure = pts.map { it.pressure }.average().toFloat()
        paint.strokeWidth = (stroke.widthRatio * minSide * resolveStylusStrokeWidthScale(avgPressure))
            .coerceIn(2f, minSide * 0.06f)
        if (pts.size == 1) {
            val pt = pts[0]
            canvas.drawPoint(pt.x * width, pt.y * height, paint)
        } else {
            val path = Path()
            path.moveTo(pts[0].x * width, pts[0].y * height)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val curr = pts[i]
                val midX = (prev.x + curr.x) * 0.5f * width
                val midY = (prev.y + curr.y) * 0.5f * height
                path.quadTo(prev.x * width, prev.y * height, midX, midY)
            }
            val last = pts.last()
            path.lineTo(last.x * width, last.y * height)
            canvas.drawPath(path, paint)
        }
    }
    return output
}

internal fun defaultAnnotationColorArgb(): Int = VIDEO_ANNOTATION_PALETTE.first().toArgb()
