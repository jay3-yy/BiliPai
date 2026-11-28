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

    /**
     * 串行保护时间轴、排版、配置监听与渲染层状态，允许 [com.bytedance.danmaku.render.engine.DanmakuSurfaceView]
     * 在独立后台渲染线程调用 [draw]，同时主线程安全更新数据、配置与处理点击命中。
     * 所有宿主 View 帧调度与透明度更新均在锁外触发，避免与主线程/Surface 回调形成锁顺序反转。
     */
    private val renderLock = Any()

    val config: DanmakuConfig = DanmakuConfig().apply {
        notificationLock = renderLock
        onAfterConfigChanged = ::onAfterConfigChangedUnlocked
        addListener(this@DanmakuController)
    }
    @Volatile
    var itemClickListener: IItemClickListener? = null

    private val mCmdMonitors = mutableListOf<ICommandMonitor>()
    private val mEventListeners = mutableListOf<IEventListener>()
    private val mRenderEngine: RenderEngine = RenderEngine(this)
    private val mDataManager: DataManager = DataManager(this)
    private val mTouchHelper: TouchHelper = TouchHelper()
    private val mProfiler: Profiler = Profiler(config)

    @Volatile
    private var mIsPlaying = false
    private val renderClock = RenderClock()
    internal val renderTimeMs: Long get() = renderClock.timeMs
    @Volatile
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
        val started = synchronized(renderLock) {
            if (mIsPlaying) {
                false
            } else {
                mIsPlaying = true
                renderClock.resume(SystemClock.uptimeMillis())
                mDataManager.onPlay(playTime)
                true
            }
        }
        if (started) {
            mDanmakuView.postInvalidateCompat()
        }
    }

    /**
     * Pause Danmakus and keep them on the screen
     */
    fun pause() {
        synchronized(renderLock) {
            renderClock.pause(SystemClock.uptimeMillis())
            mIsPlaying = false
            mDataManager.onPause()
        }
        mDanmakuView.cancelDanmakuFrames()
    }

    /**
     * Stop playing and clear all items on the screen
     */
    fun stop() {
        synchronized(renderLock) {
            mIsPlaying = false
            renderClock.reset()
            mDataManager.onStop()
            mRenderEngine.clear(LAYER_TYPE_UNDEFINE)
        }
        mDanmakuView.cancelDanmakuFrames()
        mDanmakuView.postInvalidateCompat()
    }

    /**
     * Simply clear the items on the screen,
     * and has no effect on the data set or timeline.
     */
    fun clear(layerType: Int = LAYER_TYPE_UNDEFINE) {
        synchronized(renderLock) {
            mRenderEngine.clear(layerType)
        }
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
        synchronized(renderLock) {
            mRenderEngine.addRenderLayer(layer)
        }
    }

    /**
     * Register the factory of your custom DrawItem.
     * Make sure your DrawType is not the same as the others.
     */
    fun registerDrawItemFactory(factory: IDrawItemFactory) {
        if (factory.getDrawType() < 2000) {
            throw IllegalArgumentException("The custom DrawType must not be less than 2000.")
        }
        synchronized(renderLock) {
            mRenderEngine.registerDrawItemFactory(factory)
        }
    }

    /**
     * Set data to the [DataManager].
     * Replacing the data source when started is allowed.
     */
    fun setData(dataList: List<DanmakuData>, current: Long = 0) {
        val shouldInvalidate = synchronized(renderLock) {
            mDataManager.setData(dataList)
            if (current > 0) {
                mDataManager.onPlay(current)
            }
            mIsPlaying
        }
        if (shouldInvalidate) mDanmakuView.postInvalidateCompat()
    }

    /** Replace only mask data; active ordinary lines and their cursor stay untouched. */
    fun replaceMaskData(dataList: List<DanmakuData>, playTime: Long) {
        synchronized(renderLock) {
            mRenderEngine.clear(LAYER_TYPE_MASK)
            mDataManager.replaceMasks(dataList, playTime)
            mRenderEngine.addItems(playTime, mDataManager.queryMasks(playTime))
        }
        mDanmakuView.postInvalidateCompat()
    }

    fun seekTimeline(playTime: Long) {
        synchronized(renderLock) {
            mDataManager.seekTo(playTime)
        }
        mDanmakuView.postInvalidateCompat()
    }

    /** Reanchors the media clock while preserving the active render lines. */
    fun synchronizeTo(playTime: Long) {
        synchronized(renderLock) {
            mDataManager.synchronizePlayTime(playTime)
        }
        mDanmakuView.postInvalidateCompat()
    }

    fun setPlaybackPositionProvider(provider: java.util.function.LongSupplier?) {
        synchronized(renderLock) {
            mDataManager.setPlaybackPositionProvider(provider)
        }
        mDanmakuView.postInvalidateCompat()
    }

    internal fun hasPlaybackPositionProvider(): Boolean =
        mDataManager.hasPlaybackPositionProvider()

    internal fun refreshPlaybackAnchorFromMainThread() {
        synchronized(renderLock) {
            mDataManager.refreshPlaybackAnchorFromProvider()
        }
    }

    /**
     * Append data to end of the [DataManager].
     * You need to ensure the chronological order of the data by yourself.
     */
    fun appendData(dataList: List<DanmakuData>) {
        val shouldInvalidate = synchronized(renderLock) {
            mDataManager.appendData(dataList)
            mIsPlaying && dataList.isNotEmpty()
        }
        if (shouldInvalidate) mDanmakuView.postInvalidateCompat()
    }

    /** Remove consumed history without pause/setData/start. */
    fun discardDataBefore(playTime: Long) {
        synchronized(renderLock) {
            mDataManager.discardBefore(playTime)
        }
    }

    /**
     * Fake item will be added to the DataManager and pushed into RenderEngine immediately.
     * Even so, there is no guarantee that it will be displayed 100%,
     * as the render layers will discard data when the amount of data is too large.
     * See [LayerBuffer]
     */
    fun addFakeData(data: DanmakuData) {
        synchronized(renderLock) {
            mDataManager.addFakeData(data)
        }
        if (config.common.pauseInvalidateWhenBlank) {
            mDanmakuView.postInvalidateCompat()
        }
    }

    /** Update retained data without clearing active lines or advancing the paused media clock. */
    fun remeasureData(update: (DanmakuData) -> Unit) {
        synchronized(renderLock) {
            mDataManager.getData().forEach(update)
            mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
        }
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
        synchronized(renderLock) {
            mEventListeners.add(listener)
        }
    }

    /**
     * Remove event listener to the [DanmakuController].
     * Event is used by [DanmakuController] to tell you what is happening.
     */
    @Suppress("unused")
    fun removeEventListener(listener: IEventListener) {
        synchronized(renderLock) {
            mEventListeners.remove(listener)
        }
    }

    //////////////////////////////////////////////////////
    //                Internal Methods                  //
    //////////////////////////////////////////////////////

    override fun onConfigChanged(type: Int) {
        // 由 AbsConfig.notifyConfigChanged 在 renderLock 互斥保护下与各 RenderLayer 同步分发。
        synchronized(renderLock) {
            when (type) {
                DanmakuConfig.TYPE_COMMON_PLAY_SPEED -> mDataManager.onPlaySpeedChanged()
                DanmakuConfig.TYPE_TEXT_SIZE -> mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
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
                DanmakuConfig.TYPE_MASK_VALUE_CHANGE -> {
                    mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
                }
            }
        }
    }

    private fun onAfterConfigChangedUnlocked(type: Int) {
        if (type == DanmakuConfig.TYPE_COMMON_ALPHA) {
            val alpha = config.common.alpha
            val host = mDanmakuView as? com.bytedance.danmaku.render.engine.DanmakuRenderHost
            if (host != null) {
                host.applyDanmakuAlpha(alpha)
            } else {
                mDanmakuView.alpha = alpha / 255f
            }
        }
        mDanmakuView.postInvalidateCompat()
    }

    private var lastLayoutWidth = -1
    private var lastLayoutHeight = -1

    internal fun onLayoutSizeChanged(width: Int, height: Int) {
        val changed = synchronized(renderLock) {
            if (width == lastLayoutWidth && height == lastLayoutHeight) {
                false
            } else {
                lastLayoutWidth = width
                lastLayoutHeight = height
                mRenderEngine.onLayoutSizeChanged(width, height)
                mRenderEngine.typesetting(mDataManager.queryPlayTime(), false, true)
                true
            }
        }
        if (changed) {
            mDanmakuView.postInvalidateCompat()
        }
    }

    internal fun draw(view: View, canvas: Canvas) {
        var scheduleImmediateNextFrame = false
        var scheduleDelayedNextFrameMs = -1L
        synchronized(renderLock) {
            val playTime = mDataManager.queryPlayTime()
            if (mIsPlaying) {
                renderClock.advance(SystemClock.uptimeMillis())
                val profileEnabled = mProfiler.isProfilingEnabled()
                val stageBreakdown = profileEnabled && mProfiler.needsStageBreakdown()
                val t0 = if (profileEnabled) System.nanoTime() else 0L
                val newItems = mDataManager.queryDanmaku()
                val t1 = if (stageBreakdown) System.nanoTime() else t0
                mRenderEngine.addItems(playTime, newItems)
                val t2 = if (stageBreakdown) System.nanoTime() else t1
                val typesettingCount = mRenderEngine.typesetting(playTime, true)
                val t3 = if (stageBreakdown) System.nanoTime() else t2
                mRenderEngine.draw(canvas)
                val t4 = if (profileEnabled) System.nanoTime() else t3
                if (!config.common.pauseInvalidateWhenBlank) {
                    scheduleImmediateNextFrame = true
                } else if (typesettingCount > 0) {
                    scheduleImmediateNextFrame = true
                } else if (mDataManager.getRemainDanmakuCount() > 0) {
                    mDataManager.nextDanmakuShowAfter(includeMask = false).let { after ->
                        val showAfter = after * 100 / config.common.playSpeed
                        if (showAfter in 0..NEXT_DANMAKU_SHOW_MIN_INTERVAL) {
                            scheduleImmediateNextFrame = true
                        } else if (showAfter >= 0) {
                            scheduleDelayedNextFrameMs = showAfter - NEXT_DANMAKU_SHOW_MIN_INTERVAL / 2
                        }
                    }
                }
                if (profileEnabled) {
                    mProfiler.profilerDrawTimeCost(canvas, t0, t1, t2, t3, t4)
                }
            } else {
                mRenderEngine.typesetting(playTime, false)
                mRenderEngine.draw(canvas)
            }
        }
        if (scheduleImmediateNextFrame) {
            view.postInvalidateCompat()
        } else if (scheduleDelayedNextFrameMs >= 0L) {
            view.postDanmakuFrameAfter(scheduleDelayedNextFrameMs)
        }
    }

    /** Capture the touched item before Compose waits to distinguish single and double taps. */
    fun captureDanmakuClick(x: Float, y: Float): Pair<() -> Unit, () -> Unit>? {
        val captured: Pair<() -> Unit, DanmakuData>? = synchronized(renderLock) {
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
                action to data
            } finally {
                event.recycle()
                itemClickListener = listener
            }
        }
        val (action, data) = captured ?: return null
        executeCommand(com.bytedance.danmaku.render.engine.utils.CMD_PAUSE_ITEM, data = data)
        return action to {
            executeCommand(com.bytedance.danmaku.render.engine.utils.CMD_RESUME_ITEM, data = data)
        }
    }

    internal fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mIsTouchable) {
            return false
        }
        return synchronized(renderLock) {
            mTouchHelper.onTouchEvent(event, mRenderEngine)
        }
    }

    fun registerCmdMonitor(monitor: ICommandMonitor) {
        synchronized(renderLock) {
            mCmdMonitors.add(monitor)
        }
    }

    fun unRegisterCmdMonitor(monitor: ICommandMonitor) {
        synchronized(renderLock) {
            mCmdMonitors.remove(monitor)
        }
    }

    fun notifyEvent(event: DanmakuEvent) {
        synchronized(renderLock) {
            mEventListeners.forEach {
                it.onEvent(event)
            }
            Events.recycleEvent(event)
        }
    }

    //////////////////////////////////////////////////////
    //                 Private Methods                  //
    //////////////////////////////////////////////////////

    private fun executeCommand(cmd: DanmakuCommand) {
        val shouldInvalidate = synchronized(renderLock) {
            mCmdMonitors.forEach {
                it.onCommand(cmd)
            }
            cmd.what == CMD_PAUSE_ITEM || cmd.what == CMD_RESUME_ITEM
        }
        if (shouldInvalidate) {
            mDanmakuView.postInvalidateCompat()
        }
    }

    override fun onCommand(cmd: DanmakuCommand) {
        when (cmd.what) {
            CMD_SET_TOUCHABLE -> (cmd.param as? Boolean)?.let { mIsTouchable = it }
            // CMD_PAUSE_ITEM / CMD_RESUME_ITEM 的刷帧通知由 executeCommand 在锁外统一触发。
        }
    }
}
