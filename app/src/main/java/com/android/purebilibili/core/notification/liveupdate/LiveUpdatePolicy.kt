package com.android.purebilibili.core.notification.liveupdate

import android.os.Build

internal data class LiveUpdateDownloadState(
    val title: String,
    val progressPercent: Int,
    val remainingCount: Int = 0,
    val paused: Boolean = false,
)

internal data class LiveUpdateSleepTimerState(
    val title: String,
    val deadlineEpochMs: Long,
    val remainingMinutes: Int,
    val fadingOut: Boolean = false,
)

internal fun formatLiveUpdateDownloadShortText(
    progressPercent: Int,
    paused: Boolean,
    pausedLabel: String = "已暂停",
): String {
    if (paused) return pausedLabel.take(7)
    val clamped = progressPercent.coerceIn(0, 100)
    return if (clamped <= 0) "准备中" else "$clamped%"
}

internal fun formatLiveUpdateDownloadContentText(
    progressPercent: Int,
    remainingCount: Int,
    paused: Boolean,
): String {
    val clamped = progressPercent.coerceIn(0, 100)
    val base = if (paused) "已暂停 · $clamped%" else "下载中 $clamped%"
    return if (remainingCount > 1) "$base · 队列剩余 $remainingCount 项" else base
}

internal fun formatLiveUpdateSleepShortText(
    remainingMinutes: Int,
    fadingOut: Boolean,
): String = when {
    fadingOut -> "淡出中"
    remainingMinutes <= 1 -> "<1分"
    else -> "${remainingMinutes.coerceAtMost(999)}分"
}

internal fun resolveLiveUpdateShouldPromote(
    sdkInt: Int = Build.VERSION.SDK_INT,
    canPostPromoted: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.BAKLAVA && canPostPromoted
