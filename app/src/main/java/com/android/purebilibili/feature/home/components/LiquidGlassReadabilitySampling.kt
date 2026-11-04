package com.android.purebilibili.feature.home.components

import android.view.ViewTreeObserver
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay

private const val READABILITY_INITIAL_DELAY_MILLIS = 350L
private const val READABILITY_MIN_SAMPLE_INTERVAL_MILLIS = 1500L

/** 合并等待期间的绘制通知；没有新绘制时挂起，不靠定时器唤醒静止窗口。 */
internal suspend fun sampleLiquidGlassReadabilityOnDraw(
    draws: ReceiveChannel<Unit>,
    nowMillis: () -> Long,
    sample: suspend () -> Unit,
) {
    var nextSampleAt = nowMillis() + READABILITY_INITIAL_DELAY_MILLIS
    for (draw in draws) {
        delay((nextSampleAt - nowMillis()).coerceAtLeast(0L))
        while (draws.tryReceive().isSuccess) {
            // 等待期间的绘制共用这次最新画面采样，不补采旧帧。
        }
        sample()
        nextSampleAt = nowMillis() + READABILITY_MIN_SAMPLE_INTERVAL_MILLIS
    }
}

/** 同一窗口的顶栏和底栏共享监听；最后一个可见消费者退出时立即解除。 */
private class SharedReadabilityDrawObserver : ViewTreeObserver.OnDrawListener {
    val consumers = mutableSetOf<() -> Unit>()

    override fun onDraw() {
        consumers.forEach { it() }
    }
}

private val readabilityDrawObservers = mutableMapOf<ViewTreeObserver, SharedReadabilityDrawObserver>()

internal fun observeLiquidGlassReadabilityDraws(
    observer: ViewTreeObserver,
    onDraw: () -> Unit,
): () -> Unit {
    val shared = readabilityDrawObservers.getOrPut(observer) {
        SharedReadabilityDrawObserver().also(observer::addOnDrawListener)
    }
    shared.consumers.add(onDraw)
    return {
        shared.consumers.remove(onDraw)
        if (shared.consumers.isEmpty()) {
            if (observer.isAlive) observer.removeOnDrawListener(shared)
            readabilityDrawObservers.remove(observer)
        }
    }
}
