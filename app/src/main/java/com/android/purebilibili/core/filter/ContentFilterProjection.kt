package com.android.purebilibili.core.filter

import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.ContentFilterSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/** 原始状态保留分页及交互语义；只有数据或规则变化才生成展示状态。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun <S> StateFlow<S>.contentFiltered(
    scope: CoroutineScope,
    rules: Flow<ContentFilterSnapshot> = ContentFilterRules.current.filterNotNull(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    project: suspend (S, ContentFilterSnapshot) -> S,
): StateFlow<S> = combine(this, rules) { state, rules ->
    state to rules
}.mapLatest { (state, rules) ->
    withContext(dispatcher) {
        project(state, rules).also { currentCoroutineContext().ensureActive() }
    }
}.stateIn(scope, SharingStarted.Eagerly, value)

/** 每个当前列表只保留一次投影；点赞、加载标志等更新不重新扫描同一份列表。 */
internal class ContentListProjection<T : Any> {
    private var source: List<T>? = null
    private var rules: ContentFilterSnapshot? = null
    private var result: List<T> = emptyList()

    fun filter(items: List<T>, snapshot: ContentFilterSnapshot, keep: (T) -> Boolean): List<T> {
        return project(items, snapshot) { it.takeIf(keep) }
    }

    fun project(items: List<T>, snapshot: ContentFilterSnapshot, transform: (T) -> T?): List<T> {
        if (items === source && snapshot == rules) return result
        val filtered = items.mapNotNull(transform)
        result = if (filtered == items) items else filtered
        source = items
        rules = snapshot
        return result
    }
}
