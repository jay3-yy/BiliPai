package com.android.purebilibili.feature.video.danmaku

const val DANMAKU_MIN_OPACITY = 0f
const val DANMAKU_MAX_OPACITY = 1.0f
const val DANMAKU_DEFAULT_OPACITY = 0.8f
const val DANMAKU_DEFAULT_FONT_SCALE = 1.0f
const val DANMAKU_DEFAULT_SPEED = 1.0f
const val DANMAKU_DEFAULT_DISPLAY_AREA = 0.5f
const val DANMAKU_DEFAULT_FONT_WEIGHT = 5
const val DANMAKU_DEFAULT_STROKE_WIDTH = 1.0f
const val DANMAKU_DEFAULT_LINE_HEIGHT = 1.2f
const val DANMAKU_DEFAULT_SCROLL_DURATION_SECONDS = 8.0f
const val DANMAKU_DEFAULT_STATIC_DURATION_SECONDS = 4.0f
const val DANMAKU_DEFAULT_DUPLICATE_MERGE_WINDOW_MS = 15_000
const val DANMAKU_DEFAULT_DUPLICATE_MERGE_COUNT_THRESHOLD = 2

fun normalizeDanmakuOpacity(value: Float?): Float {
    val fallback = DANMAKU_DEFAULT_OPACITY
    val raw = value ?: fallback
    if (!raw.isFinite()) return fallback
    return raw.coerceIn(DANMAKU_MIN_OPACITY, DANMAKU_MAX_OPACITY)
}
