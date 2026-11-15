package com.android.purebilibili.feature.home

import com.android.purebilibili.core.util.AppImageLoadPriority
import com.android.purebilibili.core.util.BackgroundLane
import com.android.purebilibili.core.util.ImageLoadPriority
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 两条车道独立推进；窗口变化不取消在途请求，后续请求只使用仍有效的窗口。 */
internal suspend fun consumeHomeCoverPreloadWindows(
    windowFlow: StateFlow<HomeCoverPreloadWindow>,
    priority: ImageLoadPriority = AppImageLoadPriority,
    needsPreload: (index: Int) -> Boolean = { true },
    preload: suspend (lane: BackgroundLane, index: Int, isCurrent: () -> Boolean) -> Unit,
) = coroutineScope {
    for (lane in BackgroundLane.entries) {
        launch {
            windowFlow.map { it.rangeFor(lane) }.distinctUntilChanged().collect { range ->
                if (range == null) return@collect
                val isCurrent = { windowFlow.value.rangeFor(lane) == range }
                for (index in range) {
                    if (!isCurrent()) break
                    if (!needsPreload(index)) continue
                    priority.background(lane) {
                        if (isCurrent()) preload(lane, index, isCurrent)
                    }
                }
            }
        }
    }
}

private fun HomeCoverPreloadWindow.rangeFor(lane: BackgroundLane): IntRange? = when (lane) {
    BackgroundLane.NEAR -> near
    BackgroundLane.FAR -> far
}
