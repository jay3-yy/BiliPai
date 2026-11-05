package com.android.purebilibili.feature.home

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppSpacingTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeTopTabRevealPolicyTest {

    @Test
    fun defaultScrollBehavior_collapsesSearchRowButKeepsTopTabsDockVisible() {
        assertTrue(shouldAutoCollapseHomeSearchRow())
        assertFalse(shouldCollapseHomeTopTabsWithSearchRow())
    }

    @Test
    fun resolveEffectiveHomeTabRowHeight_whenHidden_returnsNone() {
        assertEquals(
            AppSpacingTokens.None,
            resolveEffectiveHomeTabRowHeight(
                hideTopTabs = true,
                defaultTabRowHeight = 44.dp
            )
        )
        assertEquals(
            44.dp,
            resolveEffectiveHomeTabRowHeight(
                hideTopTabs = false,
                defaultTabRowHeight = 44.dp
            )
        )
    }

    @Test
    fun resolveEffectiveHomeTopChromeHeight_accountsForHiddenTabs() {
        val unifiedHidden = resolveEffectiveHomeTopChromeHeight(
            hideTopTabs = true,
            useUnifiedPanel = true,
            searchBarHeight = 44.dp,
            tabRowHeight = 36.dp,
            unifiedPanelInnerPadding = 6.dp,
            searchToTabsSpacing = 4.dp
        )
        assertEquals(56.dp, unifiedHidden) // 44 + 6*2

        val nonUnifiedHidden = resolveEffectiveHomeTopChromeHeight(
            hideTopTabs = true,
            useUnifiedPanel = false,
            searchBarHeight = 44.dp,
            tabRowHeight = 36.dp,
            unifiedPanelInnerPadding = 6.dp,
            searchToTabsSpacing = 4.dp
        )
        assertEquals(44.dp, nonUnifiedHidden) // 44

        val unifiedShown = resolveEffectiveHomeTopChromeHeight(
            hideTopTabs = false,
            useUnifiedPanel = true,
            searchBarHeight = 44.dp,
            tabRowHeight = 36.dp,
            unifiedPanelInnerPadding = 6.dp,
            searchToTabsSpacing = 4.dp
        )
        assertEquals(96.dp, unifiedShown) // 44 + 36 + 12 + 4
    }
}
