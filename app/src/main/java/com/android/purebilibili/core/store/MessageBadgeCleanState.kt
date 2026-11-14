package com.android.purebilibili.core.store

/**
 * 首页消息角标的「已清理」状态，对齐官方 cleanUnread：
 * 进消息中心置位，退出不复位；有新消息或冷启动才恢复显示。
 *
 * 前台按消息 ID 或分类未读增长恢复角标；其余查询仍可按总数上涨恢复。
 * lastTotal 只用于比较，不参与显示：角标始终是 0 或服务端完整总数。
 */
internal object MessageBadgeCleanState {
    private const val UNKNOWN = -1

    @Volatile private var accountMid: Long = 0L
    @Volatile private var cleaned = false
    @Volatile private var lastTotal = UNKNOWN

    /** 进消息中心：只置位，不请求、不记数字。 */
    fun clean(mid: Long) {
        accountMid = mid
        cleaned = true
        lastTotal = UNKNOWN
    }

    /** 新消息已由会话身份确认，即使总数因其他消息已读而未增长也恢复显示。 */
    fun onNewMessage(mid: Long) {
        if (mid == accountMid) cleaned = false
    }

    /** 返回角标应显示的数字：0（清理中）或服务端完整总数。 */
    fun resolve(mid: Long, total: Int): Int {
        if (mid != accountMid) {
            accountMid = mid
            cleaned = false
            lastTotal = total
            return total
        }
        if (!cleaned) {
            lastTotal = total
            return total
        }
        return when {
            lastTotal == UNKNOWN -> {
                lastTotal = total
                0
            }
            total > lastTotal -> {
                cleaned = false
                lastTotal = total
                total
            }
            total < lastTotal -> {
                lastTotal = total
                0
            }
            else -> 0
        }
    }
}
