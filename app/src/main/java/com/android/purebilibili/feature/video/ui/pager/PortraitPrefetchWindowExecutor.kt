package com.android.purebilibili.feature.video.ui.pager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 管理竖屏滑动预取窗口的在途任务：
 * 仅保留当前目标窗口内的预取协程，离开窗口立即取消，已在途任务不重复启动。
 */
internal class PortraitPrefetchWindowExecutor<T>(
    private val keyOf: (T) -> String,
) {
    private val activeJobs = linkedMapOf<String, Job>()

    val activeKeys: Set<String>
        get() = activeJobs.keys.toSet()

    fun updateTargets(
        scope: CoroutineScope,
        targets: List<T>,
        launchTarget: CoroutineScope.(T) -> Job,
    ) {
        val desiredKeys = targets.mapTo(linkedSetOf(), keyOf)
        val staleKeys = activeJobs.keys.filter { it !in desiredKeys }
        staleKeys.forEach { key ->
            activeJobs.remove(key)?.cancel()
        }
        targets.forEach { target ->
            val key = keyOf(target)
            val existing = activeJobs[key]
            if (existing != null && !existing.isCancelled) return@forEach
            activeJobs[key] = scope.launchTarget(target)
        }
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}
