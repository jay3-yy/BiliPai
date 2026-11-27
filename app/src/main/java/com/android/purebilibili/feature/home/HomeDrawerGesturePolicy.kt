package com.android.purebilibili.feature.home

import androidx.compose.material3.DrawerValue

/**
 * 首页抽屉关闭时不参与全屏手势竞争，只允许头像点击发起打开。
 * 抽屉已经打开或正在打开时保留横向拖拽，供用户自然地侧滑关闭。
 */
internal fun shouldEnableHomeDrawerGestures(
    currentValue: DrawerValue,
    targetValue: DrawerValue,
): Boolean {
    return currentValue == DrawerValue.Open || targetValue == DrawerValue.Open
}

/**
 * 仅在首页顶部标签可见、处于首个顶部标签页且侧边栏未展开时，
 * 允许向右滑动手势自然展开首页个人侧边栏。
 */
internal fun shouldAllowHomeFirstTabSwipeToOpenDrawer(
    isSwipeDrawerEnabled: Boolean,
    isHomeDrawerAvailable: Boolean,
    hideTopTabs: Boolean,
    isTopLevelActive: Boolean,
    currentPage: Int,
    isPagerScrollInProgress: Boolean,
    isDrawerClosed: Boolean,
    isSubscriptionArticleOpen: Boolean = false,
): Boolean {
    return isSwipeDrawerEnabled &&
        isHomeDrawerAvailable &&
        !hideTopTabs &&
        isTopLevelActive &&
        currentPage == 0 &&
        !isPagerScrollInProgress &&
        isDrawerClosed &&
        !isSubscriptionArticleOpen
}
