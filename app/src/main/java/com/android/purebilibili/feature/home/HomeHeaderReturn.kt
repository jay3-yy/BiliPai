package com.android.purebilibili.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.purebilibili.navigation3.SystemWindowMotionPhase

internal enum class HomeHeaderControl {
    AVATAR,
    SEARCH,
    END_ACTION,
}

// Each committed return is a distinct event, including consecutive visits to the same page.
internal class HomeHeaderReturn(
    val source: HomeHeaderControl,
    val landingFromSystemWindow: Boolean = false,
) {
    var systemPhase by mutableStateOf<SystemWindowMotionPhase?>(null)
        private set
    private var consumed = false

    fun updateSystemPhase(phase: SystemWindowMotionPhase) {
        // 部分系统在取消后仍会发结束回调，取消的这一次不能重新变成已落位。
        if (systemPhase == SystemWindowMotionPhase.RETURN_CANCELLED &&
            phase == SystemWindowMotionPhase.RETURNED
        ) return
        systemPhase = phase
    }

    /** 系统收回或应用内导航落稳后才接收冲量；取消返回不触发落位反馈。 */
    fun canLand(navigationRunning: Boolean): Boolean =
        !consumed && !navigationRunning &&
            (!landingFromSystemWindow || systemPhase == SystemWindowMotionPhase.RETURNED)

    fun consumeWhenLanded(navigationRunning: Boolean): Boolean {
        if (!canLand(navigationRunning)) return false
        return consume()
    }

    private fun consume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }
}
