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
package com.bytedance.danmaku.render.engine.data

import android.os.SystemClock
import androidx.annotation.UiThread
import com.bytedance.danmaku.render.engine.render.sortOnlyIfOutOfOrder
import com.bytedance.danmaku.render.engine.render.draw.mask.MaskData
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.render.cache.LayerBuffer
import kotlin.math.max
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_MASK
import java.util.function.LongSupplier

/**
 * Created by dss886 on 2018/11/6.
 * DataManager is the timeline controller for the danmakus
 */
class DataManager(
    private val mConfig: DanmakuConfig,
    private val clockMs: () -> Long = { SystemClock.uptimeMillis() },
) {
    constructor(controller: DanmakuController) : this(controller.config)

    // The upstream implementation used LinkedList but indexed it from the frame loop.
    // ArrayList keeps cursor access O(1) for long timelines.
    private val mList = ArrayList<DanmakuData>()
    private val mQueryList = ArrayList<DanmakuData>()
    /**
     * Fake items will be added to the return list in [queryDanmaku] immediately.
     * Even so, there is no guarantee that them will be displayed 100%,
     * as the render layers will discard data when the amount of data is too large.
     * See [LayerBuffer]
     */
    private val mFakeList = ArrayList<DanmakuData>()
    private val mMaskQueryList = ArrayList<DanmakuData>()
    private val mMasks = ArrayList<DanmakuData>()
    private var mMaskIndex = 0
    private val timeOrder = compareBy(DanmakuData::showAtTime)

    private var mIsPlaying = false
    private var mCurrentIndex = 0
    private var mStartPlayTime = 0L
    private var mStartTimestamp = 0L
    private var mCurrentPlayTime = 0L
    private var mPlaySpeed = mConfig.common.playSpeed
    private var mPositionProvider: LongSupplier? = null

    @UiThread
    fun setPlaybackPositionProvider(provider: LongSupplier?) {
        if (mPositionProvider === provider) return
        // Preserve the last authoritative position when an owner detaches.
        queryPlayTime()
        mPositionProvider = provider
        mStartPlayTime = mCurrentPlayTime
        mStartTimestamp = clockMs()
    }

    @UiThread
    fun setData(dataList: List<DanmakuData>) {
        mList.clear()
        mMasks.clear()
        for (item in dataList) {
            if (item.layerType == LAYER_TYPE_MASK) mMasks.add(item) else mList.add(item)
        }
        sortOnlyIfOutOfOrder(mList, timeOrder)
        sortOnlyIfOutOfOrder(mMasks, timeOrder)
        mCurrentIndex = lowerBound(mCurrentPlayTime)
        mMaskIndex = maskIndexForTime(mCurrentPlayTime)
    }

    @UiThread
    fun appendData(dataList: List<DanmakuData>) {
        if (dataList.isEmpty()) return
        var previousTextTime = mList.lastOrNull()?.showAtTime ?: Long.MIN_VALUE
        var previousMaskTime = mMasks.lastOrNull()?.showAtTime ?: Long.MIN_VALUE
        var textOutOfOrder = false
        var masksOutOfOrder = false
        var needsReindex = false
        for (item in dataList) {
            if (item.layerType == LAYER_TYPE_MASK) {
                masksOutOfOrder = masksOutOfOrder || item.showAtTime < previousMaskTime
                previousMaskTime = item.showAtTime
                mMasks.add(item)
            } else {
                textOutOfOrder = textOutOfOrder || item.showAtTime < previousTextTime
                needsReindex = needsReindex || item.showAtTime < mCurrentPlayTime
                previousTextTime = item.showAtTime
                mList.add(item)
            }
        }
        // Chronological segment/live appends do not rescan the retained timeline.
        if (textOutOfOrder) mList.sortWith(timeOrder)
        if (masksOutOfOrder) {
            mMasks.sortWith(timeOrder)
            mMaskIndex = maskIndexForTime(mCurrentPlayTime)
        }
        if (needsReindex) mCurrentIndex = lowerBound(mCurrentPlayTime)
    }

    /** Updating mask prefetch must not copy, sort or reset the ordinary timeline. */
    @UiThread
    fun replaceMasks(dataList: List<DanmakuData>, playTime: Long) {
        mMasks.clear()
        mMasks.addAll(dataList)
        sortOnlyIfOutOfOrder(mMasks, timeOrder)
        mMaskIndex = maskIndexForTime(playTime)
    }

    /** Explicit seeks reset both cursors; soft sync never replays consumed ordinary items. */
    @UiThread
    fun seekTo(playTime: Long) {
        mStartPlayTime = max(0, playTime)
        mCurrentPlayTime = mStartPlayTime
        mStartTimestamp = clockMs()
        mCurrentIndex = lowerBound(mCurrentPlayTime)
        mMaskIndex = maskIndexForTime(mCurrentPlayTime)
    }

    /**
     * Removes already-consumed history while preserving the active render layers and clock.
     * This is used by unbounded live streams to keep memory independent of stream duration.
     */
    @UiThread
    fun discardBefore(playTime: Long) {
        val firstRetained = lowerBound(playTime)
        if (firstRetained > 0) {
            mList.subList(0, firstRetained).clear()
            mCurrentIndex = (mCurrentIndex - firstRetained).coerceAtLeast(0)
        }
        val firstMaskRetained = maskIndexForTime(playTime)
        if (firstMaskRetained > 0) {
            mMasks.subList(0, firstMaskRetained).clear()
            mMaskIndex = (mMaskIndex - firstMaskRetained).coerceAtLeast(0)
        }
    }

    @UiThread
    fun addFakeData(data: DanmakuData) {
        mFakeList.add(data)
    }

    @UiThread
    fun getData() : List<DanmakuData>{
        return mList
    }

    @UiThread
    fun onPlay(playTime: Long) {
        mIsPlaying = true
        seekTo(playTime)
    }

    /**
     * Corrects small clock drift without replaying consumed data or touching render layers.
     * Large jumps continue to use the explicit seek path.
     */
    @UiThread
    fun synchronizePlayTime(playTime: Long) {
        val safePlayTime = max(0, playTime)
        mStartPlayTime = safePlayTime
        mStartTimestamp = clockMs()
        mCurrentPlayTime = safePlayTime
        mCurrentIndex = max(mCurrentIndex, lowerBound(safePlayTime))
        mMaskIndex = maskIndexForTime(safePlayTime)
    }

    @UiThread
    fun onPlaySpeedChanged() {
        val now = clockMs()
        // Config already contains the new speed. Settle the unsampled interval using
        // the previous speed, even when the blank renderer has not drawn for seconds.
        if (mIsPlaying) {
            val provider = mPositionProvider
            mCurrentPlayTime = if (provider != null) provider.asLong.coerceAtLeast(0L) else playTimeAt(now)
        }
        mStartPlayTime = mCurrentPlayTime
        mStartTimestamp = now
        mPlaySpeed = mConfig.common.playSpeed
    }

    @UiThread
    fun queryPlayTime(): Long {
        if (!mIsPlaying) {
            return mCurrentPlayTime
        }
        val provider = mPositionProvider
        mCurrentPlayTime = if (provider != null) {
            provider.asLong.coerceAtLeast(0L)
        } else {
            playTimeAt(clockMs())
        }
        return mCurrentPlayTime
    }

    private fun playTimeAt(now: Long): Long =
        mStartPlayTime + ((now - mStartTimestamp) * mPlaySpeed / 100.0).toLong()

    @UiThread
    fun queryDanmaku(): List<DanmakuData> {
        if (!mIsPlaying) {
            return mQueryList.apply { clear() }
        }
        mQueryList.clear()
        mQueryList.addAll(mFakeList)
        mFakeList.clear()
        while (true) {
            if (mCurrentIndex < 0 || mCurrentIndex >= mList.size) {
                break
            }
            val item = mList[mCurrentIndex]
            if (item.showAtTime > mCurrentPlayTime) {
                break
            }
            mQueryList.add(item)
            mCurrentIndex++
        }
        mQueryList.addAll(queryMasks(mCurrentPlayTime))
        return mQueryList
    }

    @UiThread
    fun queryMasks(playTime: Long): List<DanmakuData> {
        mMaskQueryList.clear()
        while (mMaskIndex < mMasks.size && mMasks[mMaskIndex].showAtTime <= playTime) {
            mMaskQueryList.add(mMasks[mMaskIndex++])
        }
        return mMaskQueryList
    }

    @UiThread
    fun getRemainDanmakuCount(): Int {
        return mList.size - mCurrentIndex + mMasks.size - mMaskIndex
    }

    @UiThread
    fun nextDanmakuShowAfter(includeMask: Boolean = true): Long {
        val nextText = mList.getOrNull(mCurrentIndex)?.showAtTime
        val nextMask = if (includeMask) mMasks.getOrNull(mMaskIndex)?.showAtTime else null
        val next = when {
            nextText == null -> nextMask
            nextMask == null -> nextText
            else -> minOf(nextText, nextMask)
        }
        return next?.minus(mCurrentPlayTime) ?: -1L
    }

    @UiThread
    fun onPause() {
        queryPlayTime()
        mIsPlaying = false
    }

    @UiThread
    fun onStop() {
        mIsPlaying = false
        mList.clear()
        mMasks.clear()
        mMaskQueryList.clear()
        mMaskIndex = 0
        mQueryList.clear()
        mFakeList.clear()
        mCurrentIndex = 0
        mStartPlayTime = 0L
        mStartTimestamp = 0L
        mCurrentPlayTime = 0L
    }

    private fun maskIndexForTime(playTime: Long): Int {
        val next = lowerBound(mMasks, playTime)
        val previous = mMasks.getOrNull(next - 1) as? MaskData
        return if (previous != null && previous.end >= playTime) next - 1 else next
    }

    private fun lowerBound(playTime: Long): Int = lowerBound(mList, playTime)

    private fun lowerBound(items: List<DanmakuData>, playTime: Long): Int {
        var low = 0
        var high = items.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (items[middle].showAtTime < playTime) low = middle + 1 else high = middle
        }
        return low
    }

}
