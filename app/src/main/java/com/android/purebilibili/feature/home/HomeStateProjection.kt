package com.android.purebilibili.feature.home

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * 首页字段的同步只读投影。selector 仅作无副作用的字段或 Map 读取，不用于排序、过滤等重计算。
 * 没有订阅就没有派生协程；value 直接取当前总状态，暂停收集也不会读到旧账号或旧分类。
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class HomeStateProjection<S, T>(
    private val source: StateFlow<S>,
    private val selector: (S) -> T,
) : StateFlow<T> {
    override val value: T get() = selector(source.value)
    override val replayCache: List<T> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        var emitted = false
        var previous: T? = null
        return source.collect(object : FlowCollector<S> {
            override suspend fun emit(value: S) {
                val selected = selector(value)
                if (!emitted || selected != previous) {
                    emitted = true
                    previous = selected
                    collector.emit(selected)
                }
            }
        })
    }
}
