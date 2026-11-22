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
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.utils.DRAW_TYPE_TEXT

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

    private val mFontMetrics = Paint.FontMetrics()
    private var mMetricsValid = false
    private var mMetricsTextSize = 0f
    private var mMetricsTypeface: Typeface? = null

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
        vipGradient = null
        vipGradientWidth = 0f
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
        if (upBadgeAdvance > 0f) {
            drawUpBadge(canvas)
        }
        drawText(canvas, mTextPaint, config)
        drawUnderline(canvas, mTextPaint, mUnderlinePaint, config)
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

    private fun drawUpBadge(canvas: Canvas) {
        val badgeHeight = height * UP_BADGE_HEIGHT_RATIO
        // 徽章与文字在行内垂直居中
        val badgeTop = y + (height - badgeHeight) / 2f
        val badgeTextSize = badgeHeight * UP_BADGE_TEXT_RATIO
        mUpBadgePaint.textSize = badgeTextSize
        mUpBadgePaint.typeface = Typeface.DEFAULT_BOLD
        mUpBadgePaint.color = UP_BADGE_COLOR
        mUpBadgeRect.set(x, badgeTop, x + upBadgeWidth, badgeTop + badgeHeight)
        canvas.drawRoundRect(mUpBadgeRect, badgeHeight * 0.22f, badgeHeight * 0.22f, mUpBadgePaint)
        mUpBadgePaint.color = UP_BADGE_TEXT_COLOR
        canvas.drawText(
            UP_BADGE_TEXT,
            x + (upBadgeWidth - upBadgeTextWidth) / 2f,
            badgeTop + upBadgeBaselineOffset,
            mUpBadgePaint,
        )
    }

    override fun recycle() {
        super.recycle()
        mMetricsValid = false
        mMetricsTypeface = null
        vipGradient = null
        vipGradientWidth = 0f
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
    private fun drawText(canvas: Canvas, paint: Paint, config: DanmakuConfig) {
        data?.text?.let { text ->
            // UP 徽章占位在文字前方；渐变与描边按纯文字宽度计算。
            val textX = x + upBadgeAdvance
            // 描边保持单色，复用的画笔不能带上上一条弹幕的渐变。
            paint.shader = null
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            // draw stroke
            (data?.textStrokeWidth ?: config.text.strokeWidth).takeIf { it > 0 }?.let { width ->
                paint.style = Paint.Style.STROKE
                paint.color = data?.textStrokeColor ?: config.text.strokeColor
                paint.typeface = data?.typeface ?: config.text.typeface
                paint.textSize = data?.textSize ?: config.text.size
                paint.strokeWidth = width
                val baseline = getBaseline(includeFontPadding, y, paint)
                canvas.drawText(text, textX, baseline, paint)
            }
            // draw drawText
            paint.style = Paint.Style.FILL
            paint.color = data?.textColor ?: config.text.color
            paint.typeface = data?.typeface ?: config.text.typeface
            paint.textSize = data?.textSize ?: config.text.size
            paint.strokeWidth = 0f
            val baseline = getBaseline(includeFontPadding, y, paint)
            if (data?.isVipGradualColor == true && textWidth > 0f) {
                if (vipGradient == null || vipGradientWidth != textWidth) {
                    vipGradient = LinearGradient(
                        0f, 0f, textWidth, 0f,
                        VIP_GRADIENT_COLORS, null, Shader.TileMode.CLAMP,
                    )
                    vipGradientWidth = textWidth
                }
                // 渐变跟随文字移动；只更新矩阵，不在每帧创建 Shader。
                gradientMatrix.setTranslate(x, 0f)
                vipGradient?.setLocalMatrix(gradientMatrix)
                paint.shader = vipGradient
            }
            canvas.drawText(text, textX, baseline, paint)
            paint.shader = null
        }
    }

    private fun drawUnderline(canvas: Canvas, textPaint: Paint, underlinePaint: Paint,config: DanmakuConfig) {
        takeIf { data?.hasUnderline == true }?.let {
            val includeFontPadding = data?.includeFontPadding ?: config.text.includeFontPadding
            val underlineY = y + getFontHeight(includeFontPadding, textPaint) + config.underline.marginTop
            // draw underline stroke
            takeIf {config.underline.strokeWidth > 0 }?.let {
                underlinePaint.style = Paint.Style.STROKE
                underlinePaint.color = config.underline.strokeColor
                underlinePaint.strokeWidth = config.underline.strokeWidth
                canvas.drawRect(x, underlineY, x + width, underlineY + config.underline.width, underlinePaint)
            }
            // draw underline
            underlinePaint.style = Paint.Style.FILL
            underlinePaint.color = data?.textColor ?: config.underline.color
            underlinePaint.strokeWidth = 0f
            canvas.drawRect(x, underlineY, x + width, underlineY + config.underline.width, underlinePaint)
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
