package com.android.purebilibili.feature.live

/**
 * SC 期限在消息进入状态层时归一化一次；仅在菜单暂停结束时补偿暂停时长，不因页面重入续期。
 */
internal const val DEFAULT_LIVE_SUPER_CHAT_DURATION_SEC = 60

internal fun resolveLiveSuperChatDurationSec(durationSec: Int): Int {
    return durationSec.takeIf { it > 0 } ?: DEFAULT_LIVE_SUPER_CHAT_DURATION_SEC
}

internal fun resolveLiveSuperChatEndTime(
    endTime: Long,
    startTime: Long,
    duration: Int,
    nowEpochSeconds: Long,
): Long {
    if (endTime > 0L) return endTime
    if (startTime > 0L && duration > 0) return startTime + duration
    return nowEpochSeconds + resolveLiveSuperChatDurationSec(duration)
}

internal fun remainingLiveSuperChatSeconds(
    endTime: Long,
    nowEpochSeconds: Long,
): Int = (endTime - nowEpochSeconds).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

internal fun shouldExpireLiveSuperChat(
    endTime: Long,
    nowEpochSeconds: Long,
): Boolean = remainingLiveSuperChatSeconds(endTime, nowEpochSeconds) == 0

/** 普通实时浮层最多显示 10 秒，截止时间在接收消息时固定。常驻模式不使用此期限。 */
internal fun resolveLiveSuperChatFlashEndTime(endTime: Long, receivedAt: Long): Long =
    minOf(endTime, receivedAt + 10L)

internal fun formatLiveSuperChatCountdown(remainingSec: Int): String {
    if (remainingSec <= 0) return "0s"
    val minutes = remainingSec / 60
    val seconds = remainingSec % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "${seconds}s"
    }
}

/** 横屏留言条独立显示，竖屏实时弹窗跟随普通弹幕开关。 */
internal fun shouldShowLiveSuperChatFlash(
    showMediaOverlays: Boolean,
    isLandscape: Boolean,
    isDanmakuEnabled: Boolean,
    flashEnabled: Boolean,
): Boolean = showMediaOverlays && flashEnabled && (isLandscape || isDanmakuEnabled)
