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
package com.bytedance.danmaku.render.engine.render.layer.scroll

import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.IRenderLayer
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.render.layer.line.BaseRenderLine

/**
 * Created by dss886 on 2018/11/8.
 */
class ScrollLine(controller: DanmakuController,
                 private val mLayer: IRenderLayer) : BaseRenderLine(controller, mLayer) {

    override fun onLayoutChanged(width: Float, height: Float, x: Float, y: Float) {
        super.onLayoutChanged(width, height, x, y)
        measureAndLayout()
    }

    override fun addItem(playTime: Long, item: DrawItem<DanmakuData>): Boolean {
        mDrawingItems.maxByOrNull { it.x + it.width }
                ?.takeUnless { hasEnoughSpace(it, item) }
                ?.let {
                    return false
                }
        item.x = this.width
        item.y = this.y
        item.showTime = playTime
        beginItemTiming(item)
        mDrawingItems.add(item)
        return true
    }

    /**
     * Do the typesetting work
     * @param isPlaying move item forward if is playing
     * @param configChanged need to re-measure and re-layout items if config changed
     */
    override fun typesetting(playTime: Long, isPlaying: Boolean, configChanged: Boolean): Int {
        if (isPlaying) {
            // Single pass: advance drawing items and evict offscreen items without a second traversal/iterator.
            val iterator = mDrawingItems.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val elapsedMs = itemElapsedTimeMs(item)
                item.x -= getItemSpeed(item) * elapsedMs
                item.showDuration += elapsedMs
                if (item.x + item.width <= 0) {
                    mLayer.releaseItem(item)
                    iterator.remove()
                }
            }
        }

        if (configChanged) {
            measureAndLayout()
        }
        return mDrawingItems.size
    }

    private fun hasEnoughSpace(item: DrawItem<DanmakuData>, newItem: DrawItem<DanmakuData>): Boolean {
        val nowSpace = width - item.x - item.width
        if (nowSpace < mConfig.scroll.itemMargin) {
            return false
        }
        val speed = getItemSpeed(item)
        val newSpeed = getItemSpeed(newItem)
        return if (speed >= newSpeed) true else {
            nowSpace - (newSpeed - speed) * mConfig.scroll.moveTime >= mConfig.scroll.itemMargin
        }
    }

    private fun getItemSpeed(item: DrawItem<DanmakuData>): Float {
        return (item.width + width) / mConfig.scroll.moveTime
    }

    /**
     * Re-measure and re-layout current drawing items
     */
    private fun measureAndLayout() {
        var lastItem: DrawItem<DanmakuData>? = null
        var lastWidthDiff = 0F
        mDrawingItems.forEach { item ->
            item.y = y
            val oldWidth = item.width
            item.measure(mConfig)
            // if width of the last item increased, check whether there is enough space
            if (lastWidthDiff > 0 && lastItem != null && !hasEnoughSpace(lastItem!!, item)) {
                item.x += lastWidthDiff
            }
            lastItem = item
            lastWidthDiff = item.width - oldWidth
        }
    }
}
