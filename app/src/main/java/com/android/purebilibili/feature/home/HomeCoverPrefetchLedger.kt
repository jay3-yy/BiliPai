package com.android.purebilibili.feature.home

/**
 * 已完成预取的封面记录，用来把"同一个窗口被反复重算"折成"每个 cacheKey 只请求一次"。
 *
 * 为什么必须有它：预取泵的窗口是**行级**的，一次慢速滑动会在相邻两个可见区之间来回抖动，
 * 同一个封面可能被算出五六次。Coil 自己的内存缓存只能省掉解码，省不掉"再次排队 + 查缓存"的
 * 往返；而在 NEAR 车道只有 1 路许可的情况下，重复项会直接把真正需要的那张挤到后面。
 *
 * 语义是 LRU 而不是无界集合：容量固定为 [capacity]，**重插即移到尾部**（`accessOrder = true`），
 * 因此连续刷过上百屏之后，淘汰的是最久没被提到的那批 key。取 256 是因为一屏通常 4~12 张，
 * 256 张足够覆盖 20 屏以上的回滚距离，同时把常驻内存压在几十 KB 量级。
 *
 * 线程约束：只在预取泵的消费协程里访问（Compose 主线程），因此不加锁也不做并发容器。
 * 若将来把消费搬到别的调度器，必须重新评估这个前提。
 */
internal class HomeCoverPrefetchLedger(
    private val capacity: Int = HOME_COVER_PREFETCH_LEDGER_CAPACITY,
) {
    private val entries = object : LinkedHashMap<String, Unit>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > capacity
    }

    /**
     * 该 key 是否已经预取完成。
     *
     * `accessOrder = true` 下 `containsKey` 也算一次访问，因此"再查一次"本身就把这条记录推到
     * 最近使用端——这正是我们要的：越常被窗口命中的封面越不该被淘汰。
     */
    fun isPrefetched(cacheKey: String): Boolean = entries.containsKey(cacheKey)

    /** 记录一次**成功完成**的预取。失败不记录，这样下次窗口命中时还能重试。 */
    fun recordPrefetched(cacheKey: String) {
        entries[cacheKey] = Unit
    }

    /** 当前记录条数，仅供测试与排查使用。 */
    val size: Int
        get() = entries.size
}

/** ledger 容量：约 20 屏封面的回滚距离，常驻内存几十 KB。 */
internal const val HOME_COVER_PREFETCH_LEDGER_CAPACITY = 256
