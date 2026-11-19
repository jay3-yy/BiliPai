package com.android.purebilibili.feature.video.danmaku

import com.android.purebilibili.danmaku.engine.DanmakuItem

private val hotDanmakuRank = compareByDescending<IndexedValue<DanmakuItem>> { it.value.likeCount }
    .thenBy { it.value.danmakuId }
    .thenBy { it.index }

/** 按当前分 P 的过滤结果建立索引，查询范围包含当前时间和前 15 秒的边界。 */
internal class HotDanmakuIndex(items: List<DanmakuItem>) {
    private val candidates = items.mapIndexedNotNull { index, item ->
        if (item.danmakuId > 0L && !item.text.isNullOrBlank() && item.likeCount >= 10L) {
            IndexedValue(index, item)
        } else null
    }.sortedBy { it.value.showAtTime }

    val isEmpty: Boolean get() = candidates.isEmpty()

    fun select(positionMs: Long): List<DanmakuItem> {
        val endMs = positionMs.coerceAtLeast(0L)
        val startMs = (endMs - 15_000L).coerceAtLeast(0L)
        val start = timeBoundary(startMs, afterEqual = false)
        val end = timeBoundary(endMs, afterEqual = true)
        return candidates.subList(start, end).asSequence()
            .sortedWith(hotDanmakuRank)
            .map { it.value }
            .distinctBy { it.danmakuId }
            .distinctBy { it.text }
            .toList()
    }

    private fun timeBoundary(timeMs: Long, afterEqual: Boolean): Int {
        var low = 0
        var high = candidates.size
        while (low < high) {
            val middle = low + (high - low) / 2
            val itemTime = candidates[middle].value.showAtTime
            if (itemTime < timeMs || (afterEqual && itemTime == timeMs)) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }
}

/** Fit up to three complete entries, reserving an equal gap between entries and at both edges. */
internal fun selectFittingHotDanmaku(
    items: List<DanmakuItem>,
    availableWidthPx: Float,
    minimumGapPx: Float,
    measureWidthPx: (DanmakuItem) -> Float,
): List<DanmakuItem> {
    var first: DanmakuItem? = null
    var second: DanmakuItem? = null
    var third: DanmakuItem? = null
    var firstWidth = Float.POSITIVE_INFINITY
    var secondWidth = Float.POSITIVE_INFINITY
    var thirdWidth = Float.POSITIVE_INFINITY
    for (item in items) {
        val width = measureWidthPx(item)
        if (!width.isFinite() || width <= 0f || width > availableWidthPx) continue
        when {
            width < firstWidth -> {
                third = second
                thirdWidth = secondWidth
                second = first
                secondWidth = firstWidth
                first = item
                firstWidth = width
            }
            width < secondWidth -> {
                third = second
                thirdWidth = secondWidth
                second = item
                secondWidth = width
            }
            width < thirdWidth -> {
                third = item
                thirdWidth = width
            }
        }
    }
    val shortest = first ?: return emptyList()
    val next = second
    val last = third
    return when {
        next != null && last != null &&
            firstWidth + secondWidth + thirdWidth + 4 * minimumGapPx <= availableWidthPx ->
            listOf(shortest, next, last)
        next != null && firstWidth + secondWidth + 3 * minimumGapPx <= availableWidthPx ->
            listOf(shortest, next)
        else -> listOf(shortest)
    }
}
