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
package com.bytedance.danmaku.render.engine.render

import android.graphics.Canvas
import android.view.MotionEvent
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.cache.DrawCachePool
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.render.draw.IDrawItemFactory
import com.bytedance.danmaku.render.engine.render.draw.bitmap.BitmapDrawItemFactory
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskDrawItem
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskDanmakuFactory
import com.bytedance.danmaku.render.engine.render.draw.text.TextDrawItemFactory
import com.bytedance.danmaku.render.engine.render.layer.bottom.BottomCenterLayer
import com.bytedance.danmaku.render.engine.render.layer.mask.MaskLayer
import com.bytedance.danmaku.render.engine.render.layer.scroll.ScrollLayer
import com.bytedance.danmaku.render.engine.render.layer.top.TopCenterLayer
import com.bytedance.danmaku.render.engine.touch.ITouchDelegate
import com.bytedance.danmaku.render.engine.touch.ITouchTarget
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_UNDEFINE
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_MASK
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by dss886 on 2018/11/6.
 */
class RenderEngine(private val mController: DanmakuController) : ITouchDelegate {

    private val mRenderLayers = CopyOnWriteArrayList<IRenderLayer>()
    private val mDrawCachePool = DrawCachePool()
    private var mWidth = 0
    private var mHeight = 0
    private var mSaveLayerValue = 0
    private val mDrawItems = ArrayList<DrawItem<DanmakuData>>()
    private val mIncomingData = ArrayList<DanmakuData>()
    private val mDrawOrderComparator = compareBy<DrawItem<DanmakuData>>(
        { it.data?.drawOrder },
        { it.layerZIndex },
        { it.showTime }
    )

    private var onDrawingDanmakuCount = 0

    init {
        mRenderLayers.add(ScrollLayer().apply { init(mController, mDrawCachePool) })
        mRenderLayers.add(TopCenterLayer().apply { init(mController, mDrawCachePool) })
        mRenderLayers.add(BottomCenterLayer().apply { init(mController, mDrawCachePool) })
        mRenderLayers.add(MaskLayer().apply { init(mController, mDrawCachePool) })
        registerDrawItemFactory(TextDrawItemFactory())
        registerDrawItemFactory(BitmapDrawItemFactory())
        registerDrawItemFactory(MaskDanmakuFactory())
    }

    fun addRenderLayer(layer: IRenderLayer) {
        if (mRenderLayers.contains(layer)) {
            return
        }
        mRenderLayers.add(layer.apply { init(mController, mDrawCachePool) })
        sortRenderLayers()
    }

    private fun sortRenderLayers() {
        val renderLayers = mutableListOf<IRenderLayer>()
        renderLayers.addAll(mRenderLayers)
        renderLayers.sortBy { it.getLayerZIndex() }
        mRenderLayers.clear()
        mRenderLayers.addAll(renderLayers)
    }

    fun registerDrawItemFactory(factory: IDrawItemFactory) {
        mDrawCachePool.registerFactory(factory)
    }

    fun onLayoutSizeChanged(width: Int, height: Int) {
        mRenderLayers.forEach {
            it.onLayoutSizeChanged(width, height)
        }
        mWidth = width
        mHeight = height
    }

    fun addItems(playTime: Long, items: List<DanmakuData>) {
        if (items.isEmpty()) return
        for (layer in mRenderLayers) {
            try {
                for (data in items) {
                    if (data.layerType == layer.getLayerType()) mIncomingData.add(data)
                }
                if (mIncomingData.isNotEmpty()) {
                    layer.addData(playTime, mIncomingData) { wrapData(layer, it) }
                }
            } finally {
                mIncomingData.clear()
            }
        }
    }

    fun typesetting(playTime: Long, isPlaying: Boolean, configChanged: Boolean = false): Int {
        onDrawingDanmakuCount = 0
        mRenderLayers.forEach {
            val count = it.typesetting(playTime, isPlaying, configChanged)
            // A mask alone draws no visible danmaku and must not keep the overlay animating.
            if (it.getLayerType() != LAYER_TYPE_MASK) onDrawingDanmakuCount += count
        }
        return onDrawingDanmakuCount
    }

    fun draw(canvas: Canvas) {
        if (mController.config.debug.showBounds) {
            mRenderLayers.forEach {
                it.drawBounds(canvas)
            }
        }
        mDrawItems.clear()
        mRenderLayers.forEach {
            mDrawItems.addAll(it.getPreDrawItems())
        }

        sortOnlyIfOutOfOrder(mDrawItems, mDrawOrderComparator)

        var hasVisibleDanmaku = false
        var hasMaskPath = false
        var layerLeft = mWidth.toFloat()
        var layerTop = mHeight.toFloat()
        var layerRight = 0F
        var layerBottom = 0F
        if (mController.config.mask.enable) {
            for (i in 0 until mDrawItems.size) {
                val item = mDrawItems[i]
                if (item.data?.layerType == LAYER_TYPE_MASK) {
                    if ((item as? MaskDrawItem)?.drawPath?.isEmpty == false) {
                        hasMaskPath = true
                    }
                } else if (item.isVisible) {
                    hasVisibleDanmaku = true
                    if (item.x < layerLeft) layerLeft = item.x
                    if (item.y < layerTop) layerTop = item.y
                    val right = item.x + item.width
                    val bottom = item.y + item.height
                    if (right > layerRight) layerRight = right
                    if (bottom > layerBottom) layerBottom = bottom
                }
            }
        }
        val hasActiveMask = hasVisibleDanmaku && hasMaskPath
        if (hasActiveMask) {
            val left = layerLeft.coerceIn(0F, mWidth.toFloat())
            val top = layerTop.coerceIn(0F, mHeight.toFloat())
            val right = layerRight.coerceIn(left, mWidth.toFloat())
            val bottom = layerBottom.coerceIn(top, mHeight.toFloat())
            @Suppress("DEPRECATION")
            mSaveLayerValue = canvas.saveLayer(left, top, right, bottom, null, Canvas.ALL_SAVE_FLAG)
        }

        mDrawItems.forEach {
            if (it.data?.layerType == LAYER_TYPE_MASK && !hasActiveMask) return@forEach
            it.draw(canvas, mController.config)
            if (mController.config.debug.showBounds) {
                it.drawBounds(canvas)
            }
        }

        if (hasActiveMask) {
            canvas.restoreToCount(mSaveLayerValue)
        }
        mDrawItems.clear()
    }

    override fun findTouchTarget(event: MotionEvent): ITouchTarget? {
        mRenderLayers.asReversed().forEach { layer ->
            (layer as? ITouchDelegate)?.findTouchTarget(event)?.let {
                return it
            }
        }
        return null
    }

    fun clear(layerType: Int = LAYER_TYPE_UNDEFINE) {
        if (layerType == LAYER_TYPE_UNDEFINE) {
            for (layer in mRenderLayers) {
                layer.clear()
            }
        } else {
            mRenderLayers.forEach {
                if (it.getLayerType() == layerType) {
                    it.clear()
                }
            }
        }
    }

    private fun wrapData(layer: IRenderLayer, data: DanmakuData): DrawItem<DanmakuData> {
        return mDrawCachePool.acquire(data.drawType).apply {
            layerZIndex = layer.getLayerZIndex()
            bindData(data)
            measure(mController.config)
        }
    }

}
