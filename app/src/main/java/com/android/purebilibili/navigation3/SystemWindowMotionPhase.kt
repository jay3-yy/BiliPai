package com.android.purebilibili.navigation3

/** 系统只报告阶段，不提供可用于逐帧插值的窗口进度。 */
internal enum class SystemWindowMotionPhase(val returning: Boolean, val running: Boolean) {
    OPENING(false, true),
    OPENED(false, false),
    OPEN_CANCELLED(false, false),
    RETURNING(true, true),
    RETURNED(true, false),
    RETURN_CANCELLED(true, false),
}
