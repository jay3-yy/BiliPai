package com.android.purebilibili.core.performance

/** 原始 intended-vsync 才是帧身份；耗时相同不代表重复帧。每个窗口独立持有。 */
internal class PerformanceFrameIdentity {
    private var lastStartNs: Long? = null
    var duplicateCallbacks = 0L
        private set

    fun accept(startNs: Long): Boolean {
        if (startNs == lastStartNs) {
            duplicateCallbacks++
            return false
        }
        lastStartNs = startNs
        return true
    }
}

/** 保留系统截止时间的两倍启发式；固定 100ms 长帧另计，不受截止时间放宽影响。 */
internal fun isPerformanceUiJank(durationNs: Long, deadlineNs: Long): Boolean =
    durationNs > deadlineNs * 2
