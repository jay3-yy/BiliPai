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
package com.bytedance.danmaku.render.engine.render.draw.mask

import android.graphics.*
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.render.draw.IDrawItemFactory
import com.bytedance.danmaku.render.engine.utils.DRAW_TYPE_MASK
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_MASK

/**
 * Created by dss886 on 2021/08/20.
 */
class MaskData: DanmakuData() {
    override var drawType: Int = DRAW_TYPE_MASK
    override var layerType: Int = LAYER_TYPE_MASK

    var path: Path? = null
    var visibilityPicture: Picture? = null
    var pathHeight: Int = 0
    var pathWidth: Int = 0
    var start: Long = 0L
    var end: Long = 0L
}

class MaskDrawItem: DrawItem<MaskData>() {
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val mXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private val visibilityPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    var drawPath: Path? = null
    val drawBounds = RectF()
    val hasMask: Boolean
        get() = !drawBounds.isEmpty &&
            (data?.visibilityPicture != null || drawPath?.isEmpty == false)

    override fun getDrawType(): Int {
        return DRAW_TYPE_MASK
    }

    override fun onBindData(data: MaskData) {
        // do nothing
    }

    override fun onMeasure(config: DanmakuConfig) {
        // do nothing
    }

    override fun onDraw(canvas: Canvas, config: DanmakuConfig) {
        val picture = data?.visibilityPicture
        if (picture != null) {
            // Only the SVG viewport participates in the alpha mask.
            val clip = canvas.save()
            canvas.clipRect(drawBounds)
            val layer = canvas.saveLayer(drawBounds, visibilityPaint)
            canvas.drawPicture(picture, drawBounds)
            canvas.restoreToCount(layer)
            canvas.restoreToCount(clip)
            return
        }
        mPaint.xfermode = mXfermode
        drawPath?.let { canvas.drawPath(it, mPaint) }
        mPaint.xfermode = null
    }

    override fun recycle() {
        super.recycle()
        drawPath = null
        drawBounds.setEmpty()
        mPaint.reset()
    }
}

class MaskDanmakuFactory: IDrawItemFactory {
    override fun getDrawType(): Int {
        return DRAW_TYPE_MASK
    }

    override fun generateDrawItem(): DrawItem<out DanmakuData> {
        return MaskDrawItem()
    }
}
