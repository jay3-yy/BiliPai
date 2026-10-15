package com.android.purebilibili.feature.list

import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppTopChromePolicy
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.CompactCapsuleChromeSpec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFilterTabChromePolicyTest {
    @Test
    fun liquidDock_usesBottomBarMatchedSizingWhenGlobalLiquidGlassEnabled() {
        val spec = resolveHistoryFilterTabChromeSpec(
            homeSettings = HomeSettings(androidNativeLiquidGlassEnabled = true),
            topChromePolicy = testTopChromePolicy(),
        )

        assertTrue(spec.useLiquidDock)
        assertEquals(AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp, spec.heightDp)
        assertEquals(14, spec.horizontalPaddingDp)
        assertEquals(
            com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(spec.heightDp.toFloat()),
            spec.indicatorHeightDp,
        )
        assertTrue(spec.dragSelectionEnabled)
    }

    @Test
    fun liquidDock_disabledWhenGlobalLiquidGlassReuseOff() {
        val spec = resolveHistoryFilterTabChromeSpec(
            homeSettings = HomeSettings(androidNativeLiquidGlassEnabled = false),
            topChromePolicy = testTopChromePolicy(),
        )

        assertFalse(spec.useLiquidDock)
        assertFalse(spec.dragSelectionEnabled)
        assertEquals(36, spec.heightDp)
        assertEquals(30, spec.indicatorHeightDp)
        assertEquals(56, spec.itemWidthDp)
    }

    @Test
    fun itemWidth_scalesWithFilterCount() {
        assertEquals(56, resolveHistoryFilterTabItemWidthDp(filterCount = 5))
        assertEquals(60, resolveHistoryFilterTabItemWidthDp(filterCount = 4))
        assertEquals(66, resolveHistoryFilterTabItemWidthDp(filterCount = 3))
    }

    private fun testTopChromePolicy() = AppTopChromePolicy(
        tabPresentation = AppTopTabPresentation.MOVING_CAPSULE,
        iconFamily = AppSemanticIconFamily.MATERIAL,
        compactChromeSpec = CompactCapsuleChromeSpec(
            primaryHeightDp = 44,
            secondaryButtonSizeDp = 40,
            chipHeightDp = 36,
            compactChipHeightDp = 32,
            primaryCornerRadiusDp = 22,
            secondaryButtonCornerRadiusDp = 20,
            chipCornerRadiusDp = 18,
            compactChipCornerRadiusDp = 16,
            iconSizeDp = 20,
            smallIconSizeDp = 16,
            inputHorizontalPaddingDp = 12,
            chipHorizontalPaddingDp = 12,
            compactChipHorizontalPaddingDp = 10,
            standardGapDp = 8,
        ),
    )
}
