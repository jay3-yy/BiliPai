package com.android.purebilibili.core.ui.performance

/** 0 表示撤销内容帧率提示，不把未知帧率当作 30/60fps。 */
internal fun resolveVideoSurfaceFrameRate(frameRate: Float, speed: Float, playing: Boolean): Float {
    if (!playing || !frameRate.isFinite() || frameRate <= 0f || !speed.isFinite() || speed <= 0f) return 0f
    val playbackRate = frameRate * speed
    return if (playbackRate.isFinite()) playbackRate else 0f
}
