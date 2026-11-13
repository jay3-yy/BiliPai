package com.android.purebilibili.core.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal val AppImageLoadPriority = ImageLoadPriority()

/** 预加载由调用页面持有；前台图片到达即取消，让出带宽后按当前范围重新执行。 */
internal class ImageLoadPriority {
    private val foregroundRequests = MutableStateFlow(0)
    private val preloadPermit = Semaphore(1)

    suspend fun <T> foreground(load: suspend () -> T): T {
        foregroundRequests.update { it + 1 }
        return try {
            load()
        } finally {
            foregroundRequests.update { it - 1 }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <T> prefetch(settleMillis: Long = 360L, load: suspend () -> T): T =
        preloadPermit.withPermit {
            foregroundRequests.map { it == 0 }.distinctUntilChanged()
                .transformLatest { idle ->
                    if (idle) {
                        delay(settleMillis)
                        emit(load())
                    }
                }.first()
        }
}
