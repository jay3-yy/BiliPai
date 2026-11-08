package com.android.purebilibili.feature.video.subtitle

import com.android.purebilibili.core.player.PlaybackPositionTimeline

/** 正偏移延后显示；endHoldMs 对应句尾空窗保留时间，结束毫秒仍属于当前句。 */
internal fun subtitlePositionTimeline(
    primary: List<SubtitleCue>,
    secondary: List<SubtitleCue> = emptyList(),
    offsetMs: Long = 0L,
    endHoldMs: Long = 0L,
): PlaybackPositionTimeline = PlaybackPositionTimeline(
    sequenceOf(primary, secondary).flatten().flatMap { cue ->
        sequenceOf(cue.startMs + offsetMs, cue.endMs + offsetMs + endHoldMs + 1L)
    }.asIterable(),
)
