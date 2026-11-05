package com.android.purebilibili.feature.home

import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.ui.AppSpacingTokens

fun resolveEffectiveHomeTabRowHeight(
    hideTopTabs: Boolean,
    defaultTabRowHeight: Dp
): Dp {
    return if (hideTopTabs) AppSpacingTokens.None else defaultTabRowHeight
}

fun resolveEffectiveHomeTopChromeHeight(
    hideTopTabs: Boolean,
    useUnifiedPanel: Boolean,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    unifiedPanelInnerPadding: Dp,
    searchToTabsSpacing: Dp
): Dp {
    return if (hideTopTabs) {
        if (useUnifiedPanel) {
            searchBarHeight + (unifiedPanelInnerPadding * 2)
        } else {
            searchBarHeight
        }
    } else if (useUnifiedPanel) {
        searchBarHeight + tabRowHeight + (unifiedPanelInnerPadding * 2) + searchToTabsSpacing
    } else {
        searchBarHeight + searchToTabsSpacing + tabRowHeight
    }
}

internal fun shouldAutoCollapseHomeSearchRow(): Boolean = true

internal fun shouldCollapseHomeTopTabsWithSearchRow(): Boolean = false
