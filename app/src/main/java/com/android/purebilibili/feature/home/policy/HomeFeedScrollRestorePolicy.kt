package com.android.purebilibili.feature.home.policy

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.PopularSubCategory

/**
 * Snapshot of the active home feed scroll so secondary routes (UP space, etc.)
 * can restore the exact viewport after return.
 */
internal data class HomeFeedScrollAnchor(
    val category: HomeCategory,
    val popularSubCategory: PopularSubCategory,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val headerOffsetPx: Float,
    val regularGrid: Boolean = true,
)

internal val HomeFeedScrollAnchorSaver: Saver<HomeFeedScrollAnchor?, Any> = listSaver(
    save = { anchor ->
        if (anchor == null) {
            listOf(false)
        } else {
            listOf(
                true,
                anchor.category.name,
                anchor.popularSubCategory.name,
                anchor.firstVisibleItemIndex,
                anchor.firstVisibleItemScrollOffset,
                anchor.headerOffsetPx,
                anchor.regularGrid,
            )
        }
    },
    restore = { values ->
        if (values.isEmpty() || values[0] != true) {
            null
        } else {
            HomeFeedScrollAnchor(
                category = enumValueOf(values[1] as String),
                popularSubCategory = enumValueOf(values[2] as String),
                firstVisibleItemIndex = values[3] as Int,
                firstVisibleItemScrollOffset = values[4] as Int,
                headerOffsetPx = (values[5] as Number).toFloat(),
                // Saved anchors from the row-based feed belong to its former index space.
                regularGrid = values.getOrNull(6) as? Boolean ?: false,
            )
        }
    }
)

internal fun captureHomeFeedScrollAnchor(
    category: HomeCategory,
    popularSubCategory: PopularSubCategory,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    headerOffsetPx: Float,
    regularGrid: Boolean = true,
): HomeFeedScrollAnchor {
    return HomeFeedScrollAnchor(
        category = category,
        popularSubCategory = popularSubCategory,
        firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
        headerOffsetPx = headerOffsetPx,
        regularGrid = regularGrid,
    )
}

internal fun shouldRestoreHomeFeedScrollAnchor(
    isTopLevelActive: Boolean,
    hasSyncedPagerWithState: Boolean,
    anchor: HomeFeedScrollAnchor?
): Boolean {
    return isTopLevelActive && hasSyncedPagerWithState && anchor != null
}

internal data class HomeFeedScrollRestorePlan(
    val restoreGrid: Boolean,
    val restoreHeader: Boolean,
)

/** 顶栏和列表分别校正，避免只有顶栏漂移时重置瀑布流的各列锚点。 */
internal fun resolveHomeFeedScrollRestorePlan(
    currentIndex: Int,
    currentOffset: Int,
    currentHeaderOffsetPx: Float,
    anchor: HomeFeedScrollAnchor,
    offsetTolerancePx: Int = 1,
    headerTolerancePx: Float = 0.5f,
    currentRegularGrid: Boolean = true,
): HomeFeedScrollRestorePlan {
    return HomeFeedScrollRestorePlan(
        restoreGrid = currentRegularGrid == anchor.regularGrid && (
            currentIndex != anchor.firstVisibleItemIndex ||
                kotlin.math.abs(currentOffset - anchor.firstVisibleItemScrollOffset) > offsetTolerancePx
            ),
        restoreHeader = kotlin.math.abs(currentHeaderOffsetPx - anchor.headerOffsetPx) > headerTolerancePx,
    )
}

/**
 * Home feed should keep a stable bottom inset while the bottom bar may auto-hide
 * or briefly remount after secondary navigation. Changing contentPadding on every
 * visibility toggle reflows LazyGrid and reads as an automatic scroll jump.
 */
internal fun shouldReserveHomeBottomBarListPadding(
    useSideNavigation: Boolean,
    bottomBarVisibilityMode: SettingsManager.BottomBarVisibilityMode
): Boolean {
    if (useSideNavigation) return false
    return when (bottomBarVisibilityMode) {
        SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN -> false
        SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE,
        SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE -> true
    }
}
