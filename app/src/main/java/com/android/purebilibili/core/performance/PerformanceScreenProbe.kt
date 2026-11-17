package com.android.purebilibili.core.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import com.android.purebilibili.core.ui.animation.LocalAppNavigationEntranceReady

/** 随调用根节点的成功提交记录；自身不建立可跳过的重组边界，也不启动帧任务。 */
@Composable
@NonRestartableComposable
internal fun PerformanceScreenProbe(screen: String) {
    val owner = LocalView.current.rootView
    val navigationReady = LocalAppNavigationEntranceReady.current
    SideEffect { PerformanceCapture.recordScreenCommit(owner, screen, navigationReady.value) }
}

internal data class PerformanceScreenCommit(val windowId: Int, val screen: String, val navigationReady: Boolean, val commits: Long)

internal class PerformanceScreenCommitCounts {
    private data class Key(val windowId: Int, val screen: String, val navigationReady: Boolean)
    private val counts = linkedMapOf<Key, Long>()

    fun record(windowId: Int, screen: String, navigationReady: Boolean) {
        val key = Key(windowId, screen, navigationReady)
        counts[key] = (counts[key] ?: 0) + 1
    }

    fun snapshot(): List<PerformanceScreenCommit> = counts.map { (key, count) ->
        PerformanceScreenCommit(key.windowId, key.screen, key.navigationReady, count)
    }
}
