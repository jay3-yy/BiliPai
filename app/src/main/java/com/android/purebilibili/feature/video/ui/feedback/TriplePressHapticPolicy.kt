package com.android.purebilibili.feature.video.ui.feedback

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay

/**
 * 间隔逐步收紧的蓄力进度节点（0.18 -> 0.18 -> 0.16 -> 0.14 -> 0.12 -> 0.10 -> 0.07），
 * 让长按三连拥有「能量逐步加速蓄满」的实体机械节奏感。
 */
internal val TRIPLE_PRESS_CHARGE_CHECKPOINTS = floatArrayOf(
    0.18f, 0.36f, 0.52f, 0.66f, 0.78f, 0.88f, 0.95f,
)

internal const val TRIPLE_PRESS_COMPLETE_SECOND_PULSE_DELAY_MS = 65L

internal enum class TriplePressHapticCue {
    ChargeTick,
    Complete,
}

/**
 * 追踪一次长按三连蓄力过程中的进度节点跨越；单调递增、不重复触发，中途松手调用 [reset] 清零。
 */
internal class TriplePressHapticCadenceTracker(
    private val checkpoints: FloatArray = TRIPLE_PRESS_CHARGE_CHECKPOINTS,
) {
    private var lastTriggeredIndex: Int = -1
    private var completed: Boolean = false

    fun reset() {
        lastTriggeredIndex = -1
        completed = false
    }

    fun onProgress(progress: Float): TriplePressHapticCue? {
        if (completed) return null
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped >= 1f) {
            completed = true
            lastTriggeredIndex = checkpoints.lastIndex
            return TriplePressHapticCue.Complete
        }
        var highestCrossed = -1
        for (index in checkpoints.indices) {
            if (clamped >= checkpoints[index]) {
                highestCrossed = index
            } else {
                break
            }
        }
        if (highestCrossed > lastTriggeredIndex) {
            lastTriggeredIndex = highestCrossed
            return TriplePressHapticCue.ChargeTick
        }
        return null
    }
}

internal suspend fun performTriplePressHapticCue(
    haptics: HapticFeedback,
    cue: TriplePressHapticCue,
) {
    when (cue) {
        TriplePressHapticCue.ChargeTick -> {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        TriplePressHapticCue.Complete -> {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(TRIPLE_PRESS_COMPLETE_SECOND_PULSE_DELAY_MS)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
