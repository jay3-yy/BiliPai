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
package com.bytedance.danmaku.render.engine.render.draw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.data.DanmakuData

/**
 * Created by dss886 on 2019-05-13.
 * Define how a danmaku will be drawn, and some drawing related properties.
 * Information of the danmaku properties is in the [DanmakuData].
 * DrawItem will be recycled and reused after disappearing from screen.
 */
abstract class DrawItem<T: DanmakuData> {

    open var data: T? = null

    val isVisible: Boolean get() = data?.isVisible == true

    /** Shared horizontal hit test for scrolling and pinned render lines. */
    fun containsX(pointX: Float): Boolean = isVisible && pointX >= x && pointX <= x + width

    open var x: Float = 0F
    open var y: Float = 0F
    open var rotate: Float = 0F
    open var width: Float = 0F
    open var height: Float = 0F
    open var layerZIndex: Int = 0

    private val mBoundsPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    }

    /**
     * When this item has been put on the screen.
     * Noticed that [showTime] is the play time of video, not a real timestamp.
     */

    open var showTime: Long = 0L
    /**
     * How long this item has been shown on the screen.
     */

    open var showDuration: Long = 0L

    /** Baseline on the controller's pause-aware render clock; set when entering a line. */
    internal var lastRenderTimeMs: Long = 0L

    /**
     * If paused, [showDuration] will not increase.
     */
    open var isPaused: Boolean = false

    /**
     * DrawType, DrawItem, and DanmakuData are one-to-one correspondence.
     */
    abstract fun getDrawType(): Int

    /**
     * Tell DrawItem which data should be drawn.
     */
    fun bindData(data: T) {
        this.data = data
        onBindData(data)
    }

    abstract fun onBindData(data: T)

    /**
     * Measure width/height according to the content of [data].
     */
    fun measure(config: DanmakuConfig) {
        onMeasure(config)
    }

    abstract fun onMeasure(config: DanmakuConfig)

    /**
     * Actually do the drawing work in main thread.
     */
    fun draw(canvas: Canvas, config: DanmakuConfig) {
        if (!isVisible) return
        if (rotate != 0F) {
            canvas.save()
            canvas.rotate(rotate, x, y)
            onDraw(canvas, config)
            canvas.restore()
        } else {
            onDraw(canvas, config)
        }
    }

    fun drawBounds(canvas: Canvas) {
        if (!isVisible) return
        canvas.save()
        canvas.rotate(rotate, x, y)
        mBoundsPaint.color = Color.argb(100, 255, 0, 0)
        canvas.drawRect(x, y, x + width, y + height, mBoundsPaint)
        canvas.restore()
    }

    /**
     * Do the custom drawing work in main thread.
     */
    abstract fun onDraw(canvas: Canvas, config: DanmakuConfig)

    /**
     * If you define extra fields in subclass,
     * make sure overriding this method to reset them.
     */
    open fun recycle() {
        data = null
        x = 0F
        y = 0F
        rotate = 0F
        width = 0F
        height = 0F
        layerZIndex = 0
        showTime = 0L
        showDuration = 0L
        isPaused = false
    }

}
