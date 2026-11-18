package com.android.purebilibili.feature.live

/** 同一留言可能同时出现在多个区域；最后一个菜单关闭后才恢复到期计时。 */
internal class LiveSuperChatMenuPausePolicy {
    private data class Hold(val startedAtMillis: Long, val startedAtEpochSeconds: Long, val count: Int)
    private val holds = mutableMapOf<Long, Hold>()

    fun open(id: Long, nowMillis: Long, nowEpochSeconds: Long) {
        if (id <= 0L) return
        val hold = holds[id]
        holds[id] = if (hold == null) Hold(nowMillis, nowEpochSeconds, 1) else hold.copy(count = hold.count + 1)
    }

    fun close(id: Long, nowMillis: Long): Long? {
        val hold = holds[id] ?: return null
        if (hold.count > 1) {
            holds[id] = hold.copy(count = hold.count - 1)
            return null
        }
        holds.remove(id)
        return ((nowMillis - hold.startedAtMillis).coerceAtLeast(0L) + 999L) / 1_000L
    }

    fun isPaused(id: Long): Boolean = id in holds
    fun countdownTime(id: Long, nowEpochSeconds: Long): Long =
        holds[id]?.startedAtEpochSeconds ?: nowEpochSeconds

    fun clear() = holds.clear()
}
