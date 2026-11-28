package com.android.purebilibili.core.store

/**
 * 片尾下一集提示展示模式。
 * 统一收口原「接下来观看卡片」（右上角最后 5s）与「片尾即将播放倒计时」（右下角最后 8s）。
 */
enum class EndScreenNextPromptMode(val value: Int, val label: String) {
    OFF(0, "关闭"),
    TOP_RIGHT_HINT(1, "右上角提示"),
    BOTTOM_RIGHT_COUNTDOWN(2, "右下角倒计时");

    companion object {
        fun fromValue(value: Int): EndScreenNextPromptMode =
            entries.firstOrNull { it.value == value } ?: BOTTOM_RIGHT_COUNTDOWN
    }
}

data class LegacyEndScreenPromptFlags(
    val nextWatchHintEnabled: Boolean,
    val endScreenNextCountdownEnabled: Boolean,
)

fun resolveEndScreenNextPromptMode(
    storedModeValue: Int?,
    legacyNextWatchHintEnabled: Boolean?,
    legacyEndScreenCountdownEnabled: Boolean?,
): EndScreenNextPromptMode {
    if (storedModeValue != null) {
        EndScreenNextPromptMode.entries.firstOrNull { it.value == storedModeValue }?.let { return it }
    }
    val hintEnabled = legacyNextWatchHintEnabled ?: true
    val countdownEnabled = legacyEndScreenCountdownEnabled ?: true
    return when {
        countdownEnabled -> EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN
        hintEnabled -> EndScreenNextPromptMode.TOP_RIGHT_HINT
        else -> EndScreenNextPromptMode.OFF
    }
}

fun resolveLegacyEndScreenPromptFlags(mode: EndScreenNextPromptMode): LegacyEndScreenPromptFlags =
    when (mode) {
        EndScreenNextPromptMode.OFF -> LegacyEndScreenPromptFlags(
            nextWatchHintEnabled = false,
            endScreenNextCountdownEnabled = false,
        )
        EndScreenNextPromptMode.TOP_RIGHT_HINT -> LegacyEndScreenPromptFlags(
            nextWatchHintEnabled = true,
            endScreenNextCountdownEnabled = false,
        )
        EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN -> LegacyEndScreenPromptFlags(
            nextWatchHintEnabled = false,
            endScreenNextCountdownEnabled = true,
        )
    }

fun shouldShowTopRightNextWatchOverlay(
    mode: EndScreenNextPromptMode,
    fallbackFromCountdown: Boolean,
): Boolean = when (mode) {
    EndScreenNextPromptMode.OFF -> false
    EndScreenNextPromptMode.TOP_RIGHT_HINT -> true
    EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN -> fallbackFromCountdown
}

fun shouldShowBottomRightEndScreenCountdown(
    mode: EndScreenNextPromptMode,
): Boolean = mode == EndScreenNextPromptMode.BOTTOM_RIGHT_COUNTDOWN
