package com.android.purebilibili.core.ui.transition

import kotlin.math.min

internal data class VideoCardMorphScale(val x: Float, val y: Float)

/** 透明行卡的整行外框为直角时，窗口外框使用来源封面的实际圆角。 */
internal fun resolveVideoCardSourceClipRadiusPx(
    anchorRadiusPx: Float,
    coverCornerDp: Float?,
    density: Float,
): Float = if (anchorRadiusPx > 0f) anchorRadiusPx else
    coverCornerDp?.takeIf { it > 0f }?.let { it * density } ?: anchorRadiusPx

/**
 * 来源尺寸按目标窗口归一化。宽高包络与长宽比同时变化，内容仍按宽度等比缩放；
 * 同窗与透明窗口共用此几何，横卡、竖卡及窄分屏均保留来源和满屏端点。
 */
internal fun resolveVideoCardMorphScale(
    sourceScaleX: Float,
    sourceScaleY: Float,
    progress: Float,
): VideoCardMorphScale {
    val p = progress.coerceIn(0f, 1f)
    if (p == 0f) return VideoCardMorphScale(sourceScaleX, sourceScaleY)
    if (p == 1f) return VideoCardMorphScale(1f, 1f)
    val widthEnvelope = sourceScaleX + (1f - sourceScaleX) * p
    val heightEnvelope = sourceScaleY + (1f - sourceScaleY) * p
    val sourceAspect = sourceScaleY / sourceScaleX
    val relativeAspect = sourceAspect + (1f - sourceAspect) * p
    val width = min(widthEnvelope, heightEnvelope / relativeAspect)
    return VideoCardMorphScale(width, width * relativeAspect)
}

/** 圆角在屏幕坐标中插值，调用方再按各自图层的缩放换算本地半径。 */
internal fun resolveVideoCardMorphCornerPx(
    sourceRadius: Float,
    windowRadius: Float,
    progress: Float,
    visibleWidth: Float,
    visibleHeight: Float,
): Float = min(
    sourceRadius + (windowRadius - sourceRadius) * progress.coerceIn(0f, 1f),
    min(visibleWidth, visibleHeight) / 2f,
)
