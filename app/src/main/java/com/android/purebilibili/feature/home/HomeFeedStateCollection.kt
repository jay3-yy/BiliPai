package com.android.purebilibili.feature.home

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Freeze the displayed state while a navigation transition owns the home surface.
 * The producer keeps its latest value; resuming collects that value without replaying
 * intermediate pages. A different source starts from its own cache, even while paused.
 */
@Composable
internal fun <T> StateFlow<T>.collectAsHomeFeedState(updatesAllowed: Boolean): State<T> {
    val source = this
    return key(source) {
        val updates = remember(source, updatesAllowed) {
            if (updatesAllowed) source else emptyFlow<T>()
        }
        updates.collectAsStateWithLifecycle(initialValue = source.value)
    }
}

private fun isHomeChromeContentReady(content: CategoryContent): Boolean =
    !(content.isLoading && content.videos.isEmpty() && content.liveRooms.isEmpty())

/** Background consumers need readiness, not every page, loading flag or error update. */
@Composable
internal fun StateFlow<CategoryContent>.collectHomeChromeContentReady(
    updatesAllowed: Boolean,
): State<Boolean> {
    val source = this
    return key(source) {
        val updates = remember(source, updatesAllowed) {
            if (updatesAllowed) source.map(::isHomeChromeContentReady).distinctUntilChanged()
            else emptyFlow<Boolean>()
        }
        updates.collectAsStateWithLifecycle(initialValue = isHomeChromeContentReady(source.value))
    }
}

/**
 * 单个瞬态门控的看门狗：门控持续超过 [HOME_STALE_RETURN_GATE_RECOVERY_MS] 且没有
 * 进行中的返回手势时，强制按已恢复处理。
 *
 * 每个门控独立计时，互不继承对方的恢复结果，否则一个卡住的标记会连带跳过另一个
 * 门控下一次合法保护窗。门控清除时 [active] 变化会同步重置，避免多放行一帧；
 * [paused]（手势进行中或首页不在顶层）期间不计时，恢复计时从暂停结束重新累计。
 */
@Composable
internal fun rememberStaleGateRecovered(
    active: Boolean,
    paused: Boolean,
): Boolean {
    var recovered by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active, paused) {
        if (!active || paused) return@LaunchedEffect
        val armedAtMs = SystemClock.elapsedRealtime()
        delay(HOME_STALE_RETURN_GATE_RECOVERY_MS)
        if (shouldRecoverStaleHomeGate(
                active = active,
                elapsedMs = SystemClock.elapsedRealtime() - armedAtMs,
                gestureInProgress = paused,
            )
        ) {
            recovered = true
        }
    }
    return recovered
}
