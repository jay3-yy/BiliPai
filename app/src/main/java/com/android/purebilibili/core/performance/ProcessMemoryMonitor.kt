package com.android.purebilibili.core.performance

import com.android.purebilibili.core.coroutines.AppScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

internal object ProcessMemoryMonitor {
    private const val SAMPLE_INTERVAL_MS = 2_000L

    val snapshots: StateFlow<ProcessMemorySnapshot?> = flow {
        while (true) {
            emit(readProcessMemorySnapshot())
            delay(SAMPLE_INTERVAL_MS)
        }
    }.stateIn(
        scope = AppScope.ioScope,
        started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0L),
        initialValue = null,
    )
}
