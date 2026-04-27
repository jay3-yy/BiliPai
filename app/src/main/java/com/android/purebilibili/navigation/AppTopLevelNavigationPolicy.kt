package com.android.purebilibili.navigation

import com.android.purebilibili.feature.home.components.BottomNavItem

internal enum class TopLevelNavigationAction {
    SKIP,
    POP_EXISTING,
    NAVIGATE_WITH_RESTORE
}

internal enum class BottomBarSelectionAction {
    NAVIGATE,
    RESELECT
}

internal fun resolveTopLevelNavigationAction(
    currentRoute: String?,
    targetRoute: String,
    hasTargetInBackStack: Boolean
): TopLevelNavigationAction {
    if (currentRoute == targetRoute) {
        return TopLevelNavigationAction.SKIP
    }

    if (hasTargetInBackStack) {
        return TopLevelNavigationAction.POP_EXISTING
    }

    return TopLevelNavigationAction.NAVIGATE_WITH_RESTORE
}

internal fun resolveBottomBarSelectionAction(
    currentItem: BottomNavItem,
    tappedItem: BottomNavItem
): BottomBarSelectionAction {
    return if (currentItem == tappedItem) {
        BottomBarSelectionAction.RESELECT
    } else {
        BottomBarSelectionAction.NAVIGATE
    }
}

internal fun shouldBypassNavigationDebounceForRoute(targetRoute: String): Boolean {
    return BottomNavItem.entries.any { item -> item.route == targetRoute }
}

internal fun canProceedWithNavigation(
    currentTimeMillis: Long,
    lastNavigationTimeMillis: Long,
    debounceWindowMillis: Long,
    bypassDebounce: Boolean
): Boolean {
    return bypassDebounce || currentTimeMillis - lastNavigationTimeMillis > debounceWindowMillis
}

internal fun shouldUseTopLevelNavigationFromProfile(targetRoute: String): Boolean {
    return targetRoute == ScreenRoutes.Settings.route
}
