package com.android.purebilibili.feature.home.policy

import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.HomeTopTabEntry

internal enum class HomePagerSettledAction {
    NONE,
    SWITCH_CATEGORY,
}

/**
 * 即使开启隐藏顶部标签，依然允许用户在屏幕中央左右滑动切换页面。
 */
internal fun shouldEnableHomeTopPagerUserScroll(
    isTopLevelActive: Boolean,
    hideTopTabs: Boolean = false
): Boolean {
    return isTopLevelActive
}

/**
 * 是否在首页 Pager 内渲染该分类内容。
 * 直播 / 追番也作为独立页留在顶栏分页里，不再切走导航。
 */
internal fun shouldDisplayHomeTopCategoryInline(category: HomeCategory?): Boolean {
    return category != null
}

internal fun shouldSwitchHomeCategoryFromPager(
    isTopLevelActive: Boolean,
    hasSyncedPagerWithState: Boolean,
    pagerCurrentPage: Int,
    pagerScrolling: Boolean,
    currentCategoryIndex: Int,
    programmaticPageSwitchInProgress: Boolean = false
): Boolean {
    if (!isTopLevelActive) return false
    if (!hasSyncedPagerWithState) return false
    if (pagerScrolling) return false
    if (programmaticPageSwitchInProgress) return false
    return pagerCurrentPage != currentCategoryIndex
}

internal fun resolveHomePagerSettledAction(
    isTopLevelActive: Boolean,
    hasSyncedPagerWithState: Boolean,
    pagerCurrentPage: Int,
    pagerScrolling: Boolean,
    currentCategoryIndex: Int,
    settledCategory: HomeCategory?,
    programmaticPageSwitchInProgress: Boolean = false
): HomePagerSettledAction {
    if (!shouldSwitchHomeCategoryFromPager(
            isTopLevelActive = isTopLevelActive,
            hasSyncedPagerWithState = hasSyncedPagerWithState,
            pagerCurrentPage = pagerCurrentPage,
            pagerScrolling = pagerScrolling,
            currentCategoryIndex = currentCategoryIndex,
            programmaticPageSwitchInProgress = programmaticPageSwitchInProgress
        )
    ) {
        return HomePagerSettledAction.NONE
    }

    return when {
        settledCategory == null -> HomePagerSettledAction.NONE
        shouldDisplayHomeTopCategoryInline(settledCategory) -> HomePagerSettledAction.SWITCH_CATEGORY
        else -> HomePagerSettledAction.NONE
    }
}

internal fun shouldUseInitialHomePagerSnap(
    hasSyncedPagerWithState: Boolean,
    targetPage: Int
): Boolean {
    return !hasSyncedPagerWithState && targetPage >= 0
}

internal fun shouldSkipHomePagerStateDrive(
    hasSyncedPagerWithState: Boolean,
    lastDrivenCategory: HomeCategory?,
    currentCategory: HomeCategory
): Boolean {
    return hasSyncedPagerWithState && lastDrivenCategory == currentCategory
}

internal fun shouldAnimateHomePagerToCategory(
    hasSyncedPagerWithState: Boolean,
    targetPage: Int,
    pagerCurrentPage: Int,
    pagerScrolling: Boolean,
    programmaticPageSwitchInProgress: Boolean
): Boolean {
    if (!hasSyncedPagerWithState) return false
    if (targetPage < 0) return false
    if (targetPage == pagerCurrentPage) return false
    if (pagerScrolling) return false
    if (programmaticPageSwitchInProgress) return false
    return true
}

internal fun resolveHomeInitialTopTabPage(
    topTabEntries: List<HomeTopTabEntry>,
    currentCategory: HomeCategory,
    displayedTabIndex: Int
): Int {
    if (topTabEntries.isEmpty()) return 0
    val safeDisplayedIndex = displayedTabIndex.coerceIn(0, topTabEntries.lastIndex)
    val displayedEntry = topTabEntries[safeDisplayedIndex]
    if (
        displayedEntry == HomeTopTabEntry.Partition ||
        displayedEntry == HomeTopTabEntry.Subscriptions ||
        displayedEntry == HomeTopTabEntry.Category(currentCategory)
    ) {
        return safeDisplayedIndex
    }
    return topTabEntries
        .indexOf(HomeTopTabEntry.Category(currentCategory))
        .takeIf { it >= 0 }
        ?: 0
}

internal fun shouldTreatInitialHomePagerPageAsSyncedWithState(
    initialEntry: HomeTopTabEntry?,
    currentCategory: HomeCategory
): Boolean {
    return initialEntry == HomeTopTabEntry.Partition ||
        initialEntry == HomeTopTabEntry.Subscriptions ||
        initialEntry == HomeTopTabEntry.Category(currentCategory)
}

internal fun resolveHomePagerTargetPage(
    topTabEntries: List<HomeTopTabEntry>,
    retainedEntry: HomeTopTabEntry?,
    currentCategory: HomeCategory,
    hasSyncedPagerWithState: Boolean
): Int {
    if (topTabEntries.isEmpty()) return -1
    val targetEntry = when {
        retainedEntry == HomeTopTabEntry.Partition -> HomeTopTabEntry.Partition
        retainedEntry == HomeTopTabEntry.Subscriptions -> HomeTopTabEntry.Subscriptions
        else -> HomeTopTabEntry.Category(currentCategory)
    }
    val targetIndex = topTabEntries.indexOf(targetEntry)
    if (targetIndex >= 0) return targetIndex
    return topTabEntries.indexOf(HomeTopTabEntry.Category(currentCategory))
}
