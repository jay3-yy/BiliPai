package com.android.purebilibili.feature.message

import com.android.purebilibili.core.store.AccountSessionIdentity

/** 仅向当前前台同步会话转交刷新，不创建独立请求或常驻协程。 */
internal class ForegroundMessageRefreshRequests {
    private class Target(
        val owner: Any,
        val identity: AccountSessionIdentity,
        val refresh: () -> Unit,
    ) {
        var fetchInFlight = true
    }
    private var target: Target? = null

    fun bind(owner: Any, identity: AccountSessionIdentity, refresh: () -> Unit) {
        target = Target(owner, identity, refresh)
    }

    fun unbind(owner: Any) {
        if (target?.owner === owner) target = null
    }

    fun setFetchInFlight(owner: Any, fetching: Boolean) {
        target?.takeIf { it.owner === owner }?.fetchInFlight = fetching
    }

    fun request(identity: AccountSessionIdentity, afterCurrentFetch: Boolean): Boolean {
        val current = target?.takeIf { it.identity == identity } ?: return false
        // 页面恢复可复用在途查询；已读操作要求在当前查询之后再同步。
        if (!current.fetchInFlight || afterCurrentFetch) current.refresh()
        return true
    }
}
