package com.android.purebilibili.feature.live

import com.android.purebilibili.data.model.response.LiveAreaChild
import com.android.purebilibili.data.model.response.LiveAreaParent
import com.android.purebilibili.data.model.response.LiveFavoriteTagEntry

internal const val LIVE_FAVORITE_TAG_LIMIT = Int.MAX_VALUE

internal fun sortLiveAreaChildrenForDisplay(children: List<LiveAreaChild>): List<LiveAreaChild> {
    return children
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<LiveAreaChild>>(
                { if (it.value.hot_status == 1) 0 else 1 },
                { it.index }
            )
        )
        .map { it.value }
}

internal fun LiveAreaChild.toLiveFavoriteTagEntry(parentArea: LiveAreaParent): LiveFavoriteTagEntry {
    val areaId = id.toIntOrNull() ?: 0
    val parentId = parent_id.toIntOrNull() ?: parentArea.id
    return LiveFavoriteTagEntry(
        parentAreaId = parentId,
        areaId = areaId,
        title = name,
        coverUrl = pic,
        parentTitle = parent_name.ifBlank { parentArea.name }
    )
}

internal fun toggleLiveFavoriteTag(
    current: List<LiveFavoriteTagEntry>,
    entry: LiveFavoriteTagEntry,
    maxSize: Int = LIVE_FAVORITE_TAG_LIMIT
): List<LiveFavoriteTagEntry> {
    val withoutEntry = current.filterNot {
        it.parentAreaId == entry.parentAreaId && it.areaId == entry.areaId
    }
    if (withoutEntry.size != current.size) return withoutEntry
    return (withoutEntry + entry).takeLast(maxSize.coerceAtLeast(1))
}

internal fun resolveLiveAreaParentTabItemWidthDp(
    containerWidthDp: Float,
    itemCount: Int,
    maxVisibleItems: Int = 4,
    minScrollableItemWidthDp: Float = 112f
): Float {
    if (containerWidthDp <= 0f || itemCount <= 0) return 0f
    val visibleCount = itemCount.coerceAtMost(maxVisibleItems).coerceAtLeast(1)
    val fittedWidth = containerWidthDp / visibleCount
    return if (itemCount > maxVisibleItems) {
        fittedWidth.coerceAtLeast(minScrollableItemWidthDp)
    } else {
        fittedWidth
    }
}

internal fun shouldUseLiveAreaParentLiquidIndicator(
    itemCount: Int,
    bottomBarLiquidGlassEnabled: Boolean
): Boolean {
    return itemCount > 1 && bottomBarLiquidGlassEnabled
}
