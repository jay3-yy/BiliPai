package com.android.purebilibili.feature.live

import com.android.purebilibili.core.store.DanmakuSettings
import kotlin.math.roundToInt

internal enum class LiveDanmakuStyleOption {
    NormalFontScale, FullscreenFontScale, Opacity, Speed,
    FontWeight, StrokeWidth, LineHeight, ScrollDuration, StaticDuration,
    SuperChatWidth, SuperChatOpacity, SuperChatScale,
}

/** 字号随全屏状态选择，其他样式保留窗口方向对应的存量设置。 */
internal fun resolveLiveDanmakuSettings(
    isLandscape: Boolean,
    isFullscreen: Boolean,
    normal: DanmakuSettings,
    fullscreen: DanmakuSettings,
): DanmakuSettings {
    val windowSettings = if (isLandscape) fullscreen else normal
    val fontSettings = if (isFullscreen) fullscreen else normal
    return windowSettings.copy(fontScale = fontSettings.fontScale)
}

/** 拖动仅覆盖样式字段，保留开关、过滤规则及另一种观看状态的字号。 */
internal fun applyLiveDanmakuStylePreview(
    saved: DanmakuSettings,
    isFullscreen: Boolean,
    preview: Map<LiveDanmakuStyleOption, Float>,
): DanmakuSettings = saved.copy(
    fontScale = preview[if (isFullscreen) LiveDanmakuStyleOption.FullscreenFontScale else LiveDanmakuStyleOption.NormalFontScale] ?: saved.fontScale,
    opacity = preview[LiveDanmakuStyleOption.Opacity] ?: saved.opacity,
    speed = preview[LiveDanmakuStyleOption.Speed] ?: saved.speed,
    fontWeight = preview[LiveDanmakuStyleOption.FontWeight]?.roundToInt() ?: saved.fontWeight,
    strokeWidth = preview[LiveDanmakuStyleOption.StrokeWidth] ?: saved.strokeWidth,
    lineHeight = preview[LiveDanmakuStyleOption.LineHeight] ?: saved.lineHeight,
    scrollDurationSeconds = preview[LiveDanmakuStyleOption.ScrollDuration] ?: saved.scrollDurationSeconds,
    staticDurationSeconds = preview[LiveDanmakuStyleOption.StaticDuration] ?: saved.staticDurationSeconds,
)

internal fun savedLiveDanmakuStyleValue(
    option: LiveDanmakuStyleOption,
    normal: DanmakuSettings,
    fullscreen: DanmakuSettings,
    isLandscape: Boolean,
    superChatWidth: Int,
    superChatOpacity: Float = 1f,
    superChatScale: Float = 1f,
): Float {
    val window = if (isLandscape) fullscreen else normal
    return when (option) {
        LiveDanmakuStyleOption.NormalFontScale -> normal.fontScale
        LiveDanmakuStyleOption.FullscreenFontScale -> fullscreen.fontScale
        LiveDanmakuStyleOption.Opacity -> window.opacity
        LiveDanmakuStyleOption.Speed -> window.speed
        LiveDanmakuStyleOption.FontWeight -> window.fontWeight.toFloat()
        LiveDanmakuStyleOption.StrokeWidth -> window.strokeWidth
        LiveDanmakuStyleOption.LineHeight -> window.lineHeight
        LiveDanmakuStyleOption.ScrollDuration -> window.scrollDurationSeconds
        LiveDanmakuStyleOption.StaticDuration -> window.staticDurationSeconds
        LiveDanmakuStyleOption.SuperChatWidth -> superChatWidth.toFloat()
        LiveDanmakuStyleOption.SuperChatOpacity -> superChatOpacity
        LiveDanmakuStyleOption.SuperChatScale -> superChatScale
    }
}
