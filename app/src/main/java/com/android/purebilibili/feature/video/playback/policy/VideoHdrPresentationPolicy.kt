package com.android.purebilibili.feature.video.playback.policy

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.media3.common.C

internal data class VideoHdrPresentationState(
    val isHdrContent: Boolean = false,
    /** 传递给 Window.setDesiredHdrHeadroom(Float) 的目标上限；0f 表示恢复系统默认不限制。 */
    val desiredHdrHeadroom: Float = 0f,
    /** 弹幕 RGB 亮度压制系数（0..1）；1f 表示不衰减。 */
    val danmakuBrightnessFactor: Float = 1f,
)

internal fun isHdrColorTransfer(colorTransfer: Int): Boolean =
    colorTransfer == C.COLOR_TRANSFER_ST2084 || colorTransfer == C.COLOR_TRANSFER_HLG

/**
 * 协调 HDR 视频播放时的窗口高光裕度与弹幕亮度：
 * - 控制栏展开时温和约束窗口 HDR Headroom，避免明暗反差过强；
 * - 无控制栏但有弹幕时轻压弹幕纯白亮度（不改动用户透明度偏好）并留出足够高光；
 * - 纯净沉浸观影（无控制栏且无弹幕）完全释放屏幕 HDR 峰值亮度。
 */
internal fun resolveVideoHdrPresentationState(
    isHdrContent: Boolean,
    controlsVisible: Boolean,
    danmakuVisible: Boolean,
    softenEnabled: Boolean = true,
): VideoHdrPresentationState {
    if (!isHdrContent || !softenEnabled) {
        return VideoHdrPresentationState(
            isHdrContent = isHdrContent,
            desiredHdrHeadroom = 0f,
            danmakuBrightnessFactor = 1f,
        )
    }
    return when {
        controlsVisible && danmakuVisible -> VideoHdrPresentationState(
            isHdrContent = true,
            desiredHdrHeadroom = 1.6f,
            danmakuBrightnessFactor = 0.78f,
        )
        controlsVisible -> VideoHdrPresentationState(
            isHdrContent = true,
            desiredHdrHeadroom = 1.8f,
            danmakuBrightnessFactor = 1f,
        )
        danmakuVisible -> VideoHdrPresentationState(
            isHdrContent = true,
            desiredHdrHeadroom = 2.5f,
            danmakuBrightnessFactor = 0.78f,
        )
        else -> VideoHdrPresentationState(
            isHdrContent = true,
            desiredHdrHeadroom = 0f,
            danmakuBrightnessFactor = 1f,
        )
    }
}

/** 构造仅衰减 RGB 亮度、保持 Alpha 透明度不变的硬件合成滤镜。 */
internal fun createDanmakuBrightnessRenderEffect(
    brightnessFactor: Float,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val clamped = brightnessFactor.coerceIn(0.4f, 1f)
    if (clamped >= 0.999f) return null
    val matrix = ColorMatrix().apply {
        setScale(clamped, clamped, clamped, 1f)
    }
    return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix)).asComposeRenderEffect()
}

internal fun createPlatformDanmakuBrightnessRenderEffect(
    brightnessFactor: Float,
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val clamped = brightnessFactor.coerceIn(0.4f, 1f)
    if (clamped >= 0.999f) return null
    val matrix = ColorMatrix().apply {
        setScale(clamped, clamped, clamped, 1f)
    }
    return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
}
