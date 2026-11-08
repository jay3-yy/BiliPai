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
package com.bytedance.danmaku.render.engine.control

import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.data.DataManager
import com.bytedance.danmaku.render.engine.render.IRenderLayer
import com.bytedance.danmaku.render.engine.render.RenderEngine
import com.bytedance.danmaku.render.engine.render.RenderClock
import com.bytedance.danmaku.render.engine.render.cache.LayerBuffer
import com.bytedance.danmaku.render.engine.render.draw.IDrawItemFactory
import com.bytedance.danmaku.render.engine.touch.IItemClickListener
import com.bytedance.danmaku.render.engine.touch.TouchHelper
import com.bytedance.danmaku.render.engine.utils.*

/**
 * Created by dss886 on 2018/11/6.
 * Main entrance for controlling Danmaku
 */
class DanmakuController(private var mDanmakuView: View): ConfigChangeListener, ICommandMonitor {

    val config: DanmakuConfig = DanmakuConfig().apply {
        addListener(this@DanmakuController)
    }
    var itemClickListener: IItemClickListener? = null

    private val mCmdMonitors = mutableListOf<ICommandMonitor>()
    private val mEventListeners = mutableListOf<IEventListener>()
    private val mRenderEngine: RenderEngine = RenderEngine(this)
    private val mDataManager: DataManager = DataManager(this)
    private val mTouchHelper: TouchHelper = TouchHelper()
    private val mProfiler: Profiler = Profiler(config)

    private var mIsPlaying = false
    private val renderClock = RenderClock()
    internal val renderTimeMs: Long get() = renderClock.timeMs
    private var mIsTouchable = true

    init {
        mCmdMonitors.add(this)
    }

    //////////////////////////////////////////////////////
    //                 Public Interface                 //
    //////////////////////////////////////////////////////

    /**
     * Play or resume from paused
     */
    fun start(playTime: Long = 0L) {
        if (mIsPlaying) {
            return
        }
        mIsPlaying = true
        renderClock.resume(SystemClock.uptimeMillis())
        mDataManager.onPlay(playTime)
        mDanmakuView.postInvalidateCompat()
    }

    /**
     * Pause Danmakus and keep them on the screen
     */
    fun pause() {
        renderClock.pause(SystemClock.uptimeMillis())
        mIsPlaying = false
        mDataManager.onPause()
        mDanmakuView.cancelDanmakuFrames()
    }

    /**
     * Stop playing and clear all items on the screen
     */
    fun stop() {
        mIsPlaying = false
        mDanmakuView.cancelDanmakuFrames()
        renderClock.reset()
        mDataManager.onStop()
        clear()
    }

    /**
     * Simply clear the items on the screen,
     * and has no effect on the data set or timeline.
     */
    fun clear(layerType: Int = LAYER_TYPE_UNDEFINE) {
        mRenderEngine.clear(layerType)
        if (layerType == LAYER_TYPE_UNDEFINE) {
            mDanmakuView.postInvalidateCompat()
        }
    }

    /**
     * Add your custom RenderLayer to RenderEngine.
     * Make sure your LayerType is not the same as the others.
     * Changing the LayerZIndex to adjust the order of your RenderLayer.
     */
    @Suppress("unused")
    fun addRenderLayer(layer: IRenderLayer) {
        if (layer.getLayerType() < 2000) {
            throw IllegalArgumentException("The custom LayerType must not be less than 2000.")
        }
        if (LAYER_Z_INDEXES.contains(layer.getLayerZIndex())) {
            throw IllegalArgumentException("The custom Z-Index conflicts with the built-in Z-Index.")
        }
        mRenderEngine.addRenderLayer(layer)
    }

    /**
     * Register the factory of your custom DrawItem.
     * Make sure your DrawType is not the same as the others.
     */
    fun registerDrawItemFactory(factory: IDrawItemFactory) {
        if (factory.getDrawType() < 2000) {
            throw IllegalArgumentException("The custom DrawType must not be less than 2000.")
        }
        mRenderEngine.registerDrawItemFactory(factory)
    }

    /**
     * Set data to the [DataManager].
     * Replacing the data source when started is allowed.
     */
    fun setData(dataList: List<DanmakuData>, current: Long = 0) {
        mDataManager.setData(dataList)
        if (current > 0) {
            mDataManager.onPlay(current)
        }
        if (mIsPlaying) mDanmakuView.postInvalidateCompat()
    }

    /** Replace only mask data; active ordinary lines and their cursor stay untouched. */
    fun replaceMaskData(dataList: List<DanmakuData>, playTime: Long) {
        mRenderEngine.clear(LAYER_TYPE_MASK)
        mDataManager.replaceMasks(dataList, playTime)
        mRenderEngine.addItems(playTime, mDataManager.queryMasks(playTime))
        mDanmakuView.postInvalidateCompat()
    }

    fun seekTimeline(playTime: Long) {
        mDataManager.seekTo(playTime)
        mDanmakuView.postInvalidateCompat()
    }

    /** Reanchors the media clock while preserving the active render lines. */
    fun synchronizeTo(playTime: Long) {
        mDataManager.synchronizePlayTime(playTime)
        mDanmakuView.postInvalidateCompat()
    }

    fun setPlaybackPositionProvider(provider: java.util.function.LongSupplier?) {
        mDataManager.setPlaybackPositionProvider(provider)
        mDanmakuView.postInvalidateCompat()
    }

    /**
     * Append data to end of the [DataManager].
     * You need to ensure the chronological order of the data by yourself.
     */
    fun appendData(dataList: List<DanmakuData>) {
        mDataManager.appendData(dataList)
        if (mIsPlaying && dataList.isNotEmpty()) mDanmakuView.postInvalidateCompat()
    }

    /** Remove consumed history without pause/setData/start. */
    fun discardDataBefore(playTime: Long) {
        mDataManager.discardBefore(playTime)
    }

    /**
     * Fake item will be added to the DataManager and pushed into RenderEngine immediately.
     * Even so, there is no guarantee that it will be displayed 100%,
     * as the render layers will discard data when the amount of data is too large.
     * See [LayerBuffer]
     */
    fun addFakeData(data: DanmakuData) {
        mDataManager.addFakeData(data)
        if (config.common.pauseInvalidateWhenBlank) {
            mDanmakuView.postInvalidateCompat()
        }
    }

    /** Update retained data without clearing active lines or advancing the paused media clock. */
    fun remeasureData(update: (DanmakuData) -> Unit) {
        mDataManager.getData().forEach(update)
        mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
        mDanmakuView.postInvalidateCompat()
    }

    /**
     * If you change the configs of Danmakus when paused, please invalidate the view.
     */
    fun invalidateView() {
        if (Logger.isVerboseLoggable()) {
            Logger.v(LOG_TAG_PERFORMANCE, "invalidateView")
        }
        mDanmakuView.postInvalidateCompat()
    }

    /**
     * Tell [DanmakuController] to do something.
     */
    fun executeCommand(cmd: Int, data: DanmakuData? = null, param: Any? = null) {
        executeCommand(DanmakuCommand(cmd, data, param))
    }

    /**
     * Add event listener to the [DanmakuController].
     * Event is used by [DanmakuController] to tell you what is happening.
     */
    fun addEventListener(listener: IEventListener) {
        mEventListeners.add(listener)
    }

    /**
     * Remove event listener to the [DanmakuController].
     * Event is used by [DanmakuController] to tell you what is happening.
     */
    @Suppress("unused")
    fun removeEventListener(listener: IEventListener) {
        mEventListeners.remove(listener)
    }

    //////////////////////////////////////////////////////
    //                Internal Methods                  //
    //////////////////////////////////////////////////////

    override fun onConfigChanged(type: Int) {
        when (type) {
            DanmakuConfig.TYPE_COMMON_PLAY_SPEED -> mDataManager.onPlaySpeedChanged()
            DanmakuConfig.TYPE_TEXT_SIZE -> mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
            DanmakuConfig.TYPE_COMMON_ALPHA -> mDanmakuView.alpha = config.common.alpha / 255f
            DanmakuConfig.TYPE_COMMON_TOP_CENTER_VISIBLE_CHANGE -> {
                if (!config.common.topVisible) {
                    mRenderEngine.clear(LAYER_TYPE_TOP_CENTER)
                }
            }
            DanmakuConfig.TYPE_COMMON_BOTTOM_CENTER_VISIBLE_CHANGE -> {
                if (!config.common.bottomVisible) {
                    mRenderEngine.clear(LAYER_TYPE_BOTTOM_CENTER)
                }
            }
        }
        mDanmakuView.postInvalidateCompat()
    }

    private var lastLayoutWidth = -1
    private var lastLayoutHeight = -1

    internal fun onLayoutSizeChanged(width: Int, height: Int) {
        if (width == lastLayoutWidth && height == lastLayoutHeight) return
        lastLayoutWidth = width
        lastLayoutHeight = height
        mRenderEngine.onLayoutSizeChanged(width, height)
        mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
        mDanmakuView.postInvalidateCompat()
    }

    internal fun draw(view: View, canvas: Canvas) {
        val playTime = mDataManager.queryPlayTime()
        if (mIsPlaying) {
            renderClock.advance(SystemClock.uptimeMillis())
            val t0 = System.nanoTime()
            val newItems = mDataManager.queryDanmaku()
            val t1 = System.nanoTime()
            mRenderEngine.addItems(playTime, newItems)
            val t2 = System.nanoTime()
            val typesettingCount = mRenderEngine.typesetting(playTime, true)
            val t3 = System.nanoTime()
            mRenderEngine.draw(canvas)
            val t4 = System.nanoTime()
            if (!config.common.pauseInvalidateWhenBlank) {
                view.postInvalidateCompat()
            } else if (typesettingCount > 0) {
                view.postInvalidateCompat()
            } else if (mDataManager.getRemainDanmakuCount() > 0) {
                mDataManager.nextDanmakuShowAfter(includeMask = false).let { after ->
                    val showAfter = after * 100 / config.common.playSpeed
                    if (showAfter in 0..NEXT_DANMAKU_SHOW_MIN_INTERVAL) {
                        view.postInvalidateCompat()
                    } else if (showAfter >= 0) {
                        view.postDanmakuFrameAfter(showAfter - NEXT_DANMAKU_SHOW_MIN_INTERVAL / 2)
                    }
                }
            }
            mProfiler.profilerDrawTimeCost(canvas, t0, t1, t2, t3, t4)
        } else {
            mRenderEngine.typesetting(playTime, false)
            mRenderEngine.draw(canvas)
        }
    }

    /** Capture the touched item before Compose waits to distinguish single and double taps. */
    fun captureDanmakuClick(x: Float, y: Float): Pair<() -> Unit, () -> Unit>? {
        val listener = itemClickListener ?: return null
        var click: (() -> Unit)? = null
        var capturedData: com.bytedance.danmaku.render.engine.data.DanmakuData? = null
        itemClickListener = object : IItemClickListener {
            override fun onDanmakuClick(
                data: com.bytedance.danmaku.render.engine.data.DanmakuData,
                itemRect: android.graphics.RectF,
                clickPoint: android.graphics.PointF
            ) {
                // Renderer reuses these geometry objects between frames.
                val rect = android.graphics.RectF(itemRect)
                val point = android.graphics.PointF(clickPoint.x, clickPoint.y)
                capturedData = data
                click = { listener.onDanmakuClick(data, rect, point) }
            }
        }
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            val target = mRenderEngine.findTouchTarget(event) ?: return null
            event.action = MotionEvent.ACTION_UP
            target.onTouchEvent(event)
            val action = click ?: return null
            val data = capturedData ?: return null
            executeCommand(com.bytedance.danmaku.render.engine.utils.CMD_PAUSE_ITEM, data = data)
            return action to {
                executeCommand(com.bytedance.danmaku.render.engine.utils.CMD_RESUME_ITEM, data = data)
            }
        } finally {
            event.recycle()
            itemClickListener = listener
        }
    }

    internal fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mIsTouchable) {
            return false
        }
        return mTouchHelper.onTouchEvent(event, mRenderEngine)
    }

    fun registerCmdMonitor(monitor: ICommandMonitor) {
        mCmdMonitors.add(monitor)
    }

    fun unRegisterCmdMonitor(monitor: ICommandMonitor) {
        mCmdMonitors.remove(monitor)
    }

    fun notifyEvent(event: DanmakuEvent) {
        mEventListeners.forEach {
            it.onEvent(event)
        }
        Events.recycleEvent(event)
    }

    //////////////////////////////////////////////////////
    //                 Private Methods                  //
    //////////////////////////////////////////////////////

    private fun executeCommand(cmd: DanmakuCommand) {
        mCmdMonitors.forEach {
            it.onCommand(cmd)
        }
    }

    override fun onCommand(cmd: DanmakuCommand) {
        when (cmd.what) {
            CMD_SET_TOUCHABLE -> (cmd.param as? Boolean)?.let { mIsTouchable = it }
            CMD_PAUSE_ITEM -> mDanmakuView.postInvalidateCompat()
            CMD_RESUME_ITEM -> mDanmakuView.postInvalidateCompat()
        }
    }
}
