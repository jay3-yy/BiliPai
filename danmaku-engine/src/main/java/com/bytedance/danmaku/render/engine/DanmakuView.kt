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
package com.bytedance.danmaku.render.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.bytedance.danmaku.render.engine.control.DanmakuController

/**
 * Created by dss886 on 2018/11/6.
 * The view danmaku will be drawn on.
 */
@Suppress("unused")
class DanmakuView @JvmOverloads constructor(context: Context,
                                            attrs: AttributeSet? = null,
                                            defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr), DanmakuRenderHost {

    @Suppress("MemberVisibilityCanBePrivate")
    override val controller: DanmakuController = DanmakuController(this)

    private val cadence = RenderFrameCadence()
    private var targetFrameRateHz = 0f
    private var cachedDisplayRefreshRateHz = 0f
    private var framePending = false
    private var needsFrame = false
    private var wakeAtMs = 0L
    private val wakeFrame = Runnable {
        wakeAtMs = 0L
        enqueueFrame()
    }
    private val renderFrame = Choreographer.FrameCallback { timeNs ->
        framePending = false
        if (canRender() && needsFrame) {
            if (cadence.shouldRender(timeNs)) {
                needsFrame = false
                cadence.onFrame(timeNs)
                invalidate()
            } else enqueueFrame()
        }
    }

    override fun asView(): View = this

    override fun setTargetFrameRateHz(fps: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { setTargetFrameRateHz(fps) }
            return
        }
        targetFrameRateHz = if (fps.isFinite() && fps > 0f) fps else 0f
        updateCadenceForDisplay()
    }

    override fun applyDanmakuAlpha(alpha: Int) {
        this.alpha = alpha.coerceIn(0, 255) / 255f
    }

    override fun requestRenderFrame(delayMs: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { requestRenderFrame(delayMs) }
            return
        }
        needsFrame = true
        if (!canRender()) return
        if (delayMs <= 0L) {
            removeCallbacks(wakeFrame)
            wakeAtMs = 0L
            enqueueFrame()
        } else if (!framePending) {
            val deadline = SystemClock.uptimeMillis() + delayMs
            if (wakeAtMs == 0L || deadline < wakeAtMs) {
                removeCallbacks(wakeFrame)
                wakeAtMs = deadline
                postDelayed(wakeFrame, delayMs)
            }
        }
    }

    override fun cancelRenderFrames() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { cancelRenderFrames() }
            return
        }
        removeCallbacks(wakeFrame)
        if (framePending) Choreographer.getInstance().removeFrameCallback(renderFrame)
        framePending = false
        wakeAtMs = 0L
        needsFrame = false
        cadence.reset()
    }

    private fun canRender() = isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun enqueueFrame() {
        if (!canRender() || framePending) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(renderFrame)
    }

    private fun updateCadenceForDisplay() {
        val displayHz = display?.refreshRate ?: 0f
        if (displayHz.isFinite() && displayHz > 0f) {
            cachedDisplayRefreshRateHz = displayHz
        }
        cadence.updateCadence(targetFps = targetFrameRateHz, displayHz = cachedDisplayRefreshRateHz)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateCadenceForDisplay()
        requestRenderFrame()
    }

    override fun onDetachedFromWindow() {
        cancelRenderFrames()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View's constructor may call this before Kotlin fields have been initialized.
        if (controllerReady) updateVisibilityScheduling()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (controllerReady) updateVisibilityScheduling()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (controllerReady && hasWindowFocus) {
            updateCadenceForDisplay()
        }
    }

    private var controllerReady = false
    init { controllerReady = true }

    private fun updateVisibilityScheduling() {
        if (canRender()) {
            updateCadenceForDisplay()
            requestRenderFrame()
        } else {
            cancelRenderFrames()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateCadenceForDisplay()
        }
        controller.onLayoutSizeChanged(right - left, bottom - top)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        controller.draw(this, canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (controller.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

}
