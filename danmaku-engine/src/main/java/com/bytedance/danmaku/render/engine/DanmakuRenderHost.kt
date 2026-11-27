package com.bytedance.danmaku.render.engine

import android.view.View
import com.bytedance.danmaku.render.engine.control.DanmakuController

/** Common renderer host contract shared by View-backed and SurfaceView-backed danmaku renderers. */
internal interface DanmakuRenderHost {
    val controller: DanmakuController

    fun requestRenderFrame(delayMs: Long = 0L)

    fun cancelRenderFrames()

    fun setTargetFrameRateHz(fps: Float)

    fun applyDanmakuAlpha(alpha: Int)

    fun asView(): View
}
