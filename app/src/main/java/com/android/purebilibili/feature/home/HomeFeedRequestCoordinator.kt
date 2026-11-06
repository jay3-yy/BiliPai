package com.android.purebilibili.feature.home

import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HomeFeedRequestTarget(
    val category: HomeCategory,
    val popularSubCategory: PopularSubCategory?,
)

internal fun homeFeedRequestTarget(category: HomeCategory, subCategory: PopularSubCategory) =
    HomeFeedRequestTarget(category, subCategory.takeIf { category == HomeCategory.POPULAR })

internal enum class HomeFeedRequestKind { INITIAL, REFRESH, APPEND }

/** 主线程请求所有者；同一分类连同取消清理串行执行，不持有协程作用域。 */
internal class HomeFeedRequestCoordinator {
    private val ownership = mutableMapOf<HomeFeedRequestTarget, Mutex>()
    private val active = mutableMapOf<HomeFeedRequestTarget, Request>()
    private var initial: Request? = null
    // 刷新反馈与撤销仍只有一份，手动刷新与分页保留单次交互占位。
    private var interaction: Request? = null

    fun start(
        target: HomeFeedRequestTarget,
        kind: HomeFeedRequestKind,
        launchRequest: (suspend () -> Unit) -> Job,
        load: suspend () -> Unit,
    ): Boolean {
        if (kind == HomeFeedRequestKind.INITIAL) {
            cancelInitial()
            if (active[target]?.kind?.let { it != HomeFeedRequestKind.INITIAL } == true) return false
        } else {
            if (interaction != null) return false
            val previous = active[target]
            // 分页不能接替尚未完成的首次加载。
            if (previous != null && kind == HomeFeedRequestKind.APPEND) return false
            previous?.job?.cancel()
        }
        val request = Request(kind)
        val mutex = ownership.getOrPut(target) { Mutex() }
        active[target] = request
        if (kind == HomeFeedRequestKind.INITIAL) initial = request else interaction = request
        request.job = launchRequest {
            mutex.withLock { load() }
        }
        request.job.invokeOnCompletion {
            if (active[target] === request) active.remove(target)
            if (initial === request) initial = null
            if (interaction === request) interaction = null
        }
        return true
    }

    fun cancelInitial() {
        initial?.job?.cancel()
        initial = null
    }

    /** 恢复已持久化列表前，等待启动请求的取消清理完成，避免 finally 覆盖新列表。 */
    suspend fun cancelAndJoinForMemoryRecovery() {
        val jobs = active.values.map { it.job }.distinct()
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
        // 被后续请求替换的旧 Job 已不在 active 中，但仍可能持锁执行取消清理。
        ownership.values.toList().forEach { mutex -> mutex.withLock { } }
    }

    private class Request(val kind: HomeFeedRequestKind) {
        lateinit var job: Job
    }
}
