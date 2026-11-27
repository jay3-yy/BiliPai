/*
 * Copyright (C) 2022 ByteDance Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bytedance.danmaku.render.engine.render.draw.text

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.utils.DRAW_TYPE_TEXT
import kotlin.math.ceil

/**
 * Created by dss886 on 2019/4/19.
 *
 * FontMetrics: Top - Ascent - Baseline - Descent - Bottom
 *
 * In ASCII or common Asia characters,
 * the space between Top and Ascent are usually unused,
 * which causes the text to be visually not centered.
 *
 * Turn TextData.includeFontPadding to false will cut the space between Top and Ascent.
 */
internal fun scaleDanmakuColorRgb(color: Int, brightnessFactor: Float): Int {
    if (!brightnessFactor.isFinite() || brightnessFactor >= 0.999f) return color
    val factor = brightnessFactor.coerceIn(0.4f, 1f)
    val a = color ushr 24
    val r = (((color ushr 16) and 0xFF) * factor + 0.5f).toInt().coerceIn(0, 255)
    val g = (((color ushr 8) and 0xFF) * factor + 0.5f).toInt().coerceIn(0, 255)
    val b = ((color and 0xFF) * factor + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

open class TextDrawItem: DrawItem<TextData>() {

    private val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val mUnderlinePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val mUpBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val mUpBadgeRect = android.graphics.RectF()

    // UP 徽章随文字缩放；宽度计入 measure，避免弹幕轨道排布时与后续弹幕重叠。
    private var upBadgeAdvance = 0f
    private var upBadgeTextWidth = 0f
    private var upBadgeWidth = 0f
    private var upBadgeBaselineOffset = 0f
    private val mUpBadgeFontMetrics = Paint.FontMetrics()
    private var textWidth = 0f

    private val gradientMatrix = Matrix()
    private var vipGradient: LinearGradient? = null
    private var vipGradientWidth = 0f
    private var vipGradientBrightness = 1f

    private val mFontMetrics = Paint.FontMetrics()
    private var mMetricsValid = false
    private var mMetricsTextSize = 0f
    private var mMetricsTypeface: Typeface? = null

    // API 29+ 硬件显示列表缓存：在屏移动数百帧仅复放 RenderNode，避免逐帧双次 drawText 描边与排版。
    private var cachedRenderNode: Any? = null
    private var nodeValid = false
    private var nodePadPx = 0
    private var nodeText: String? = null
    private var nodeTextSize = 0f
    private var nodeTypeface: Typeface? = null
    private var nodeFillColor = 0
    private var nodeStrokeWidth = 0f
    private var nodeStrokeColor = 0
    private var nodeBrightness = 1f
    private var nodeIncludeFontPadding = false
    private var nodeUpOwner = false
    private var nodeVipGradual = false
    private var nodeHasUnderline = false
    private var nodeUnderlineWidth = 0f
    private var nodeUnderlineColor = 0
    private var nodeUnderlineStrokeWidth = 0f
    private var nodeUnderlineStrokeColor = 0
    private var nodeUnderlineMarginTop = 0f
    private var nodeWidth = 0f
    private var nodeHeight = 0f

    private fun fontMetrics(paint: Paint): Paint.FontMetrics {
        if (!mMetricsValid || mMetricsTextSize != paint.textSize || mMetricsTypeface != paint.typeface) {
            paint.getFontMetrics(mFontMetrics)
            mMetricsTextSize = paint.textSize
            mMetricsTypeface = paint.typeface
            mMetricsValid = true
        }
        return mFontMetrics
    }

    override fun getDrawType(): Int {
        return DRAW_TYPE_TEXT
    }

    override fun onBindData(data: TextData) {
        mMetricsValid = false
        nodeValid = false
        vipGradient = null
        vipGradientWidth = 0f
        vipGradientBrightness = 1f
        mTextPaint.shader = null
        mTextPaint.flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        mUnderlinePaint.flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
    }

    override fun onMeasure(config: DanmakuConfig) {
        if (!TextUtils.isEmpty(data?.text)) {
            mTextPaint.textSize = data?.textSize ?: config.text.size
            mTextPaint.typeface = data?.typeface ?: config.text.typeface
            textWidth = mTextPaint.measureText(data?.text)
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            height = getFontHeight(includeFontPadding, mTextPaint)
            width = textWidth
            if (data?.isUpOwner == true) {
                upBadgeAdvance = resolveUpBadgeAdvance(height)
                width += upBadgeAdvance
            } else {
                upBadgeAdvance = 0f
            }
        } else {
            width = 0F
            height = 0F
            upBadgeAdvance = 0f
            textWidth = 0f
        }
    }

    override fun onDraw(canvas: Canvas, config: DanmakuConfig) {
        val brightness = config.common.brightnessFactor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            drawWithRenderNodeApi29(canvas, config, brightness)
            return
        }
        drawItemAt(canvas, config, brightness, originX = x, originY = y)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun drawWithRenderNodeApi29(canvas: Canvas, config: DanmakuConfig, brightness: Float) {
        val text = data?.text
        if (TextUtils.isEmpty(text) || width <= 0f || height <= 0f) return
        val textSize = data?.textSize ?: config.text.size
        val typeface = data?.typeface ?: config.text.typeface
        val fillColor = data?.textColor ?: config.text.color
        val strokeWidth = data?.textStrokeWidth ?: config.text.strokeWidth
        val strokeColor = data?.textStrokeColor ?: config.text.strokeColor
        val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
        val isUpOwner = upBadgeAdvance > 0f
        val isVipGradual = data?.isVipGradualColor == true
        val hasUnderline = data?.hasUnderline == true
        val underlineWidth = config.underline.width
        val underlineColor = data?.textColor ?: config.underline.color
        val underlineStrokeWidth = config.underline.strokeWidth
        val underlineStrokeColor = config.underline.strokeColor
        val underlineMarginTop = config.underline.marginTop

        val node = (cachedRenderNode as? RenderNode)
            ?: RenderNode("DanmakuTextItem").also { cachedRenderNode = it }
        val needsRecord = !nodeValid ||
            !node.hasDisplayList() ||
            nodeText != text ||
            nodeTextSize != textSize ||
            nodeTypeface !== typeface ||
            nodeFillColor != fillColor ||
            nodeStrokeWidth != strokeWidth ||
            nodeStrokeColor != strokeColor ||
            nodeBrightness != brightness ||
            nodeIncludeFontPadding != includeFontPadding ||
            nodeUpOwner != isUpOwner ||
            nodeVipGradual != isVipGradual ||
            nodeHasUnderline != hasUnderline ||
            nodeWidth != width ||
            nodeHeight != height ||
            (hasUnderline && (
                nodeUnderlineWidth != underlineWidth ||
                    nodeUnderlineColor != underlineColor ||
                    nodeUnderlineStrokeWidth != underlineStrokeWidth ||
                    nodeUnderlineStrokeColor != underlineStrokeColor ||
                    nodeUnderlineMarginTop != underlineMarginTop
                ))

        if (needsRecord) {
            val strokePad = ceil(
                maxOf(
                    strokeWidth.coerceAtLeast(0f),
                    if (hasUnderline) underlineStrokeWidth.coerceAtLeast(0f) else 0f,
                ) * 0.5f,
            ).toInt() + 1
            val extraBottom = if (hasUnderline) {
                ceil(underlineMarginTop + underlineWidth + underlineStrokeWidth).toInt().coerceAtLeast(0)
            } else {
                0
            }
            val recordWidth = ceil(width).toInt() + strokePad * 2
            val recordHeight = ceil(height).toInt() + extraBottom + strokePad * 2
            if (recordWidth <= 0 || recordHeight <= 0) return

            node.setPosition(0, 0, recordWidth, recordHeight)
            node.setClipToBounds(false)
            val recordingCanvas = node.beginRecording(recordWidth, recordHeight)
            try {
                drawItemAt(
                    canvas = recordingCanvas,
                    config = config,
                    brightness = brightness,
                    originX = strokePad.toFloat(),
                    originY = strokePad.toFloat(),
                )
            } finally {
                node.endRecording()
            }
            nodePadPx = strokePad
            nodeText = text
            nodeTextSize = textSize
            nodeTypeface = typeface
            nodeFillColor = fillColor
            nodeStrokeWidth = strokeWidth
            nodeStrokeColor = strokeColor
            nodeBrightness = brightness
            nodeIncludeFontPadding = includeFontPadding
            nodeUpOwner = isUpOwner
            nodeVipGradual = isVipGradual
            nodeHasUnderline = hasUnderline
            nodeUnderlineWidth = underlineWidth
            nodeUnderlineColor = underlineColor
            nodeUnderlineStrokeWidth = underlineStrokeWidth
            nodeUnderlineStrokeColor = underlineStrokeColor
            nodeUnderlineMarginTop = underlineMarginTop
            nodeWidth = width
            nodeHeight = height
            nodeValid = true
        }

        val saveCount = canvas.save()
        canvas.translate(x - nodePadPx, y - nodePadPx)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(saveCount)
    }

    private fun drawItemAt(
        canvas: Canvas,
        config: DanmakuConfig,
        brightness: Float,
        originX: Float,
        originY: Float,
    ) {
        if (upBadgeAdvance > 0f) {
            drawUpBadge(canvas, brightness, originX, originY)
        }
        drawText(canvas, mTextPaint, config, brightness, originX, originY)
        drawUnderline(canvas, mTextPaint, mUnderlinePaint, config, brightness, originX, originY)
    }

    private fun resolveUpBadgeAdvance(textHeight: Float): Float {
        val badgeHeight = textHeight * UP_BADGE_HEIGHT_RATIO
        val badgeTextSize = badgeHeight * UP_BADGE_TEXT_RATIO
        mUpBadgePaint.textSize = badgeTextSize
        mUpBadgePaint.typeface = Typeface.DEFAULT_BOLD
        upBadgeTextWidth = mUpBadgePaint.measureText(UP_BADGE_TEXT)
        upBadgeWidth = upBadgeTextWidth + badgeHeight * UP_BADGE_H_PADDING_RATIO * 2f
        mUpBadgePaint.getFontMetrics(mUpBadgeFontMetrics)
        upBadgeBaselineOffset = (badgeHeight - mUpBadgeFontMetrics.bottom - mUpBadgeFontMetrics.top) / 2f
        return upBadgeTextWidth + badgeHeight + UP_BADGE_TEXT_GAP_RATIO * textHeight
    }

    private fun drawUpBadge(canvas: Canvas, brightness: Float, originX: Float, originY: Float) {
        val badgeHeight = height * UP_BADGE_HEIGHT_RATIO
        // 徽章与文字在行内垂直居中
        val badgeTop = originY + (height - badgeHeight) / 2f
        val badgeTextSize = badgeHeight * UP_BADGE_TEXT_RATIO
        mUpBadgePaint.textSize = badgeTextSize
        mUpBadgePaint.typeface = Typeface.DEFAULT_BOLD
        mUpBadgePaint.color = scaleDanmakuColorRgb(UP_BADGE_COLOR, brightness)
        mUpBadgeRect.set(originX, badgeTop, originX + upBadgeWidth, badgeTop + badgeHeight)
        canvas.drawRoundRect(mUpBadgeRect, badgeHeight * 0.22f, badgeHeight * 0.22f, mUpBadgePaint)
        mUpBadgePaint.color = scaleDanmakuColorRgb(UP_BADGE_TEXT_COLOR, brightness)
        canvas.drawText(
            UP_BADGE_TEXT,
            originX + (upBadgeWidth - upBadgeTextWidth) / 2f,
            badgeTop + upBadgeBaselineOffset,
            mUpBadgePaint,
        )
    }

    override fun recycle() {
        super.recycle()
        mMetricsValid = false
        mMetricsTypeface = null
        nodeValid = false
        nodeText = null
        nodeTypeface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (cachedRenderNode as? RenderNode)?.discardDisplayList()
        }
        vipGradient = null
        vipGradientWidth = 0f
        vipGradientBrightness = 1f
        upBadgeAdvance = 0f
        upBadgeTextWidth = 0f
        upBadgeWidth = 0f
        upBadgeBaselineOffset = 0f
        textWidth = 0f
        gradientMatrix.reset()
        mTextPaint.reset()
        mUnderlinePaint.reset()
        mUpBadgePaint.reset()
    }

    /**
     * Canvas.drawText() is positioning the text by baseline
     */
    private fun drawText(
        canvas: Canvas,
        paint: Paint,
        config: DanmakuConfig,
        brightness: Float,
        originX: Float,
        originY: Float,
    ) {
        data?.text?.let { text ->
            // UP 徽章占位在文字前方；渐变与描边按纯文字宽度计算。
            val textX = originX + upBadgeAdvance
            // 描边保持单色，复用的画笔不能带上上一条弹幕的渐变。
            paint.shader = null
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            // draw stroke
            (data?.textStrokeWidth ?: config.text.strokeWidth).takeIf { it > 0 }?.let { width ->
                paint.style = Paint.Style.STROKE
                paint.color = scaleDanmakuColorRgb(data?.textStrokeColor ?: config.text.strokeColor, brightness)
                paint.typeface = data?.typeface ?: config.text.typeface
                paint.textSize = data?.textSize ?: config.text.size
                paint.strokeWidth = width
                val baseline = getBaseline(includeFontPadding, originY, paint)
                canvas.drawText(text, textX, baseline, paint)
            }
            // draw drawText
            paint.style = Paint.Style.FILL
            paint.color = scaleDanmakuColorRgb(data?.textColor ?: config.text.color, brightness)
            paint.typeface = data?.typeface ?: config.text.typeface
            paint.textSize = data?.textSize ?: config.text.size
            paint.strokeWidth = 0f
            val baseline = getBaseline(includeFontPadding, originY, paint)
            if (data?.isVipGradualColor == true && textWidth > 0f) {
                if (vipGradient == null || vipGradientWidth != textWidth || vipGradientBrightness != brightness) {
                    val gradientColors = if (brightness >= 0.999f) {
                        VIP_GRADIENT_COLORS
                    } else {
                        IntArray(VIP_GRADIENT_COLORS.size) { index ->
                            scaleDanmakuColorRgb(VIP_GRADIENT_COLORS[index], brightness)
                        }
                    }
                    vipGradient = LinearGradient(
                        0f, 0f, textWidth, 0f,
                        gradientColors, null, Shader.TileMode.CLAMP,
                    )
                    vipGradientWidth = textWidth
                    vipGradientBrightness = brightness
                }
                // 渐变跟随文字起点；只更新矩阵，不在每帧创建 Shader。
                gradientMatrix.setTranslate(textX, 0f)
                vipGradient?.setLocalMatrix(gradientMatrix)
                paint.shader = vipGradient
            }
            canvas.drawText(text, textX, baseline, paint)
            paint.shader = null
        }
    }

    private fun drawUnderline(
        canvas: Canvas,
        textPaint: Paint,
        underlinePaint: Paint,
        config: DanmakuConfig,
        brightness: Float,
        originX: Float,
        originY: Float,
    ) {
        takeIf { data?.hasUnderline == true }?.let {
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            val underlineY = originY + getFontHeight(includeFontPadding, textPaint) + config.underline.marginTop
            // draw underline stroke
            takeIf {config.underline.strokeWidth > 0 }?.let {
                underlinePaint.style = Paint.Style.STROKE
                underlinePaint.color = scaleDanmakuColorRgb(config.underline.strokeColor, brightness)
                underlinePaint.strokeWidth = config.underline.strokeWidth
                canvas.drawRect(originX, underlineY, originX + width, underlineY + config.underline.width, underlinePaint)
            }
            // draw underline
            underlinePaint.style = Paint.Style.FILL
            underlinePaint.color = scaleDanmakuColorRgb(data?.textColor ?: config.underline.color, brightness)
            underlinePaint.strokeWidth = 0f
            canvas.drawRect(originX, underlineY, originX + width, underlineY + config.underline.width, underlinePaint)
        }
    }

    private companion object {
        val VIP_GRADIENT_COLORS = intArrayOf(
            0xFFFFD86F.toInt(),
            0xFFFF80B5.toInt(),
            0xFF8A9FFF.toInt(),
        )

        // UP 主徽章（对齐 B 站官方的粉色徽章样式），随弹幕字号等比缩放。
        const val UP_BADGE_TEXT = "UP"
        const val UP_BADGE_COLOR = 0xFFFB7299.toInt()
        const val UP_BADGE_TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val UP_BADGE_HEIGHT_RATIO = 0.42f
        const val UP_BADGE_TEXT_RATIO = 0.62f
        const val UP_BADGE_H_PADDING_RATIO = 0.28f
        const val UP_BADGE_TEXT_GAP_RATIO = 0.14f
    }

    private fun getFontHeight(includeFontPadding: Boolean, paint: Paint): Float {
        val metrics = fontMetrics(paint)
        return if (includeFontPadding) metrics.bottom - metrics.top else metrics.bottom - metrics.ascent
    }

    private fun getBaseline(includeFontPadding: Boolean, top: Float, paint: Paint): Float {
        val metrics = fontMetrics(paint)
        return if (includeFontPadding) top - metrics.top else top - metrics.ascent
    }

}
