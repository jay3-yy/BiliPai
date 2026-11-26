package com.android.purebilibili.feature.audio.player

import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos

internal const val SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC = 12
internal const val SLEEP_TIMER_FADE_DURATION_MIN_SEC = 3
internal const val SLEEP_TIMER_FADE_DURATION_MAX_SEC = 120
internal const val SLEEP_TIMER_FADE_DURATION_MS = SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC * 1_000L
internal val SLEEP_TIMER_FADE_DURATION_PRESETS_SEC = listOf(5, 12, 20, 30)
internal const val SLEEP_TIMER_FADE_STEP_MS = 200L
internal const val SLEEP_TIMER_QUICK_EXTEND_MINUTES = 15

internal fun normalizeSleepTimerFadeDurationSec(seconds: Int): Int =
    seconds.coerceIn(SLEEP_TIMER_FADE_DURATION_MIN_SEC, SLEEP_TIMER_FADE_DURATION_MAX_SEC)

internal fun parseSleepTimerFadeDurationInput(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val parsed = trimmed.toIntOrNull() ?: return null
    return parsed.takeIf { it in SLEEP_TIMER_FADE_DURATION_MIN_SEC..SLEEP_TIMER_FADE_DURATION_MAX_SEC }
}

/**
 * 计算倒计时最后阶段应执行的渐弱淡出时长。
 * 剩余时间不足配置时长时按实际剩余时长等比缩放，关闭渐弱或已到期返回 0。
 */
internal fun resolveSleepTimerFadeWindowMs(
    remainingMs: Long,
    fadeEnabled: Boolean,
    configuredFadeMs: Long = SLEEP_TIMER_FADE_DURATION_MS,
): Long {
    if (!fadeEnabled || remainingMs <= 0L || configuredFadeMs <= 0L) return 0L
    return minOf(remainingMs, configuredFadeMs)
}

/**
 * 余弦平滑淡出系数，范围 [0f, 1f]，随时间单调递减，起点为 1f、终点为 0f。
 */
internal fun computeSleepTimerFadeVolumeFactor(
    elapsedFadeMs: Long,
    totalFadeMs: Long,
): Float {
    if (totalFadeMs <= 0L) return 0f
    val progress = (elapsedFadeMs.toDouble() / totalFadeMs.toDouble()).coerceIn(0.0, 1.0)
    return (0.5 * (1.0 + cos(PI * progress))).toFloat().coerceIn(0f, 1f)
}

/**
 * 深夜时段判定：22:30 至次日 05:00（不含 05:00）。
 */
internal fun isLateNightSleepSuggestionHour(
    hourOfDay: Int,
    minuteOfHour: Int = 0,
): Boolean {
    if (hourOfDay !in 0..23 || minuteOfHour !in 0..59) return false
    return hourOfDay > 22 || (hourOfDay == 22 && minuteOfHour >= 30) || hourOfDay < 5
}

internal fun isCurrentlyLateNightForSleepSuggestion(
    now: LocalTime = LocalTime.now(),
): Boolean = isLateNightSleepSuggestionHour(now.hour, now.minute)

internal fun resolveSleepTimerWindDownMessage(
    wasAfterCurrent: Boolean,
    isLateNight: Boolean,
): String = when {
    isLateNight && wasAfterCurrent -> "本集已播完，晚安好梦"
    isLateNight -> "晚安，今晚就先到这里啦"
    wasAfterCurrent -> "当前内容已播完，已暂停播放"
    else -> "定时结束，已轻柔暂停播放"
}

/**
 * 前台定时结束时的一次性温柔提示事件，携带单调递增 ID 以便幂等消费。
 */
data class SleepTimerFinishedEvent(
    val id: Long,
    val message: String,
    val wasAfterCurrent: Boolean,
    val isLateNight: Boolean,
)
