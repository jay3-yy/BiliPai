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
package com.bytedance.danmaku.render.engine.render.layer.mask

import android.graphics.Canvas
import android.graphics.Matrix
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.IRenderLayer
import com.bytedance.danmaku.render.engine.render.cache.IDrawCachePool
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskDrawItem
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_MASK
import com.bytedance.danmaku.render.engine.utils.LAYER_Z_INDEX_MASK

/**
 * Created by dss886 on 2021/08/20.
 */
class MaskLayer: IRenderLayer {

    private lateinit var mCachePool: IDrawCachePool
    private var mWidth = 0
    private var mHeight = 0
    private var mCurrentItem: DrawItem<DanmakuData>? = null
    private var mPreDrawItemList = mutableListOf<DrawItem<DanmakuData>>()
    private val mMatrix = Matrix()

    override fun init(controller: DanmakuController, cachePool: IDrawCachePool) {
        mCachePool = cachePool
    }

    override fun getLayerType(): Int {
        return LAYER_TYPE_MASK
    }

    override fun getLayerZIndex(): Int {
        return LAYER_Z_INDEX_MASK
    }

    override fun onLayoutSizeChanged(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        (mCurrentItem as? MaskDrawItem)?.let {
            generateDrawPath(it)
        }
    }

    override fun addItems(playTime: Long, list: List<DrawItem<DanmakuData>>) {
        if (list.isNotEmpty()) {
            mCurrentItem?.let(::releaseItem)
            for (index in 0 until list.lastIndex) releaseItem(list[index])
            mCurrentItem = list.last()
            (mCurrentItem as? MaskDrawItem)?.let {
                generateDrawPath(it)
            }
        }
    }

    override fun addData(
        playTime: Long,
        list: List<DanmakuData>,
        createItem: (DanmakuData) -> DrawItem<DanmakuData>,
    ) {
        // Only the latest due mask is visible; intermediate paths need no DrawItem.
        list.lastOrNull()?.let { addItems(playTime, listOf(createItem(it))) }
    }

    override fun releaseItem(item: DrawItem<DanmakuData>) {
        mCachePool.release(item)
    }

    override fun typesetting(playTime: Long, isPlaying: Boolean, configChanged: Boolean): Int {
        val data = (mCurrentItem as? MaskDrawItem)?.data
        if (data == null || data.start > playTime || data.end < playTime) {
            mCurrentItem?.let(::releaseItem)
            mCurrentItem = null
        }
        return if (mCurrentItem != null) 1 else 0
    }

    override fun drawBounds(canvas: Canvas) {
        // do nothing
    }

    override fun getPreDrawItems(): List<DrawItem<DanmakuData>> {
        mPreDrawItemList.clear()
        mCurrentItem?.let {
            mPreDrawItemList.add(it)
        }
        return mPreDrawItemList
    }

    private val mReusableDrawPath = android.graphics.Path()

    /**
     * Scale the original svg path according to the screen size
     */
    private fun generateDrawPath(item: MaskDrawItem) {
        item.data?.let { data ->
            if (data.pathWidth <= 0 || data.pathHeight <= 0) return
            mMatrix.reset()
            val scaleX: Float = mWidth.toFloat() / data.pathWidth
            val scaleY: Float = mHeight.toFloat() / data.pathHeight
            if (scaleX < scaleY) {
                mMatrix.postScale(scaleX, scaleX, 0F, 0F)
            } else {
                mMatrix.postScale(scaleY, scaleY, 0F, 0F)
            }
            val source = data.path
            if (source != null) {
                mReusableDrawPath.rewind()
                mReusableDrawPath.set(source)
                mReusableDrawPath.transform(mMatrix)
                item.drawPath = mReusableDrawPath
            } else {
                item.drawPath = null
            }
        }
    }


    override fun clear() {
        mCurrentItem?.let(::releaseItem)
        mCurrentItem = null
        mPreDrawItemList.clear()
    }

}
