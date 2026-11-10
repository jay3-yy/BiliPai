package com.android.purebilibili.feature.home

import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedGridColumnPreferencePolicyTest {
    @Test
    fun tabletRotationUsesSeparateCountsAndNarrowSplitWindowUsesCompactCount() {
        val settings = HomeSettings(
            gridColumnCount = 4,
            gridColumnCountWidePortrait = 3,
            gridColumnCountCompact = 2,
        )
        val portrait = WindowSizeClass(
            WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Expanded,
            900.dp, 1200.dp,
        )
        val landscape = portrait.copy(widthDp = 1200.dp, heightDp = 900.dp)
        val split = portrait.copy(widthSizeClass = WindowWidthSizeClass.Compact, widthDp = 450.dp)
        assertEquals(
            listOf(3, 4, 2, 3),
            listOf(portrait, landscape, split, portrait).map(settings::effectiveGridColumnCountForWindow),
        )
        assertEquals(0, settings.copy(gridColumnCustomizationEnabled = false)
            .effectiveGridColumnCountForWindow(portrait))
        assertEquals(0, settings.copy(gridColumnCustomizationEnabled = false)
            .effectiveGridColumnCountForWindow(landscape))
    }

    @Test
    fun disablingCustomizationRestoresAutomaticLayoutWithoutLosingSavedValues() {
        val saved = HomeSettings(gridColumnCount = 6, gridColumnCountCompact = 3)
        val wide = WindowSizeClass(
            WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Medium, 1200.dp, 800.dp,
        )
        val disabled = saved.copy(gridColumnCustomizationEnabled = false)
        assertEquals(0, disabled.effectiveGridColumnCountForWindow(wide))
        assertEquals(0, disabled.effectiveGridColumnCountCompact)
        assertFalse(disabled.gridColumnPinchEnabled)
        assertEquals(2, resolveFeedGridColumnPreference(false, 2, 4, 1..4))
        val restored = disabled.copy(gridColumnCustomizationEnabled = true)
        assertEquals(6, restored.effectiveGridColumnCountForWindow(wide))
        assertEquals(3, restored.effectiveGridColumnCountCompact)
        assertTrue(restored.gridColumnPinchEnabled)
    }

    @Test
    fun disablingPinchKeepsManualColumnPreferencesAvailable() {
        val settings = HomeSettings(gridColumnCountCompact = 3, pinchToChangeGridColumnsEnabled = false)
        assertEquals(3, settings.effectiveGridColumnCountCompact)
        assertFalse(settings.gridColumnPinchEnabled)
    }

    @Test
    fun changingWindowUsesItsOwnSavedCountAndLimitsToAvailableWidth() {
        val settings = HomeSettings(gridColumnCount = 7, gridColumnCountCompact = 4)
        val narrow = resolveHomeFeedStoredColumnCount(
            WindowWidthSizeClass.Compact, settings.gridColumnCountCompact, settings.gridColumnCount,
        )
        val wide = resolveHomeFeedStoredColumnCount(
            WindowWidthSizeClass.Expanded, settings.gridColumnCountCompact, settings.gridColumnCount,
        )
        assertEquals(3, resolveFeedGridColumnPreference(true, narrow, null, 1..3))
        assertEquals(7, resolveFeedGridColumnPreference(true, wide, null, 2..7))
        assertEquals(4, settings.gridColumnCountCompact)
    }

    @Test
    fun pluginPosterGridRetainsItsMinimumWhenSharedCountIsOne() {
        assertEquals(2, resolveFeedGridColumnPreference(true, 1, null, 2..4, minimumColumns = 2))
        assertEquals(4, resolveFeedGridColumnPreference(true, 7, null, 2..4, minimumColumns = 2))
    }

    @Test
    fun resizingTemporarilyLimitsBothDefaultAndCustomColumnsWithoutChangingPreference() {
        val widths = listOf(412, 340, 220, 340, 412)
        for (customized in listOf(false, true)) {
            val columns = widths.map { width ->
                resolveFeedGridColumnPreference(
                    customizationEnabled = customized,
                    defaultColumns = if (customized) 4 else 2,
                    pageColumns = if (customized) 4 else null,
                    bounds = resolveHomeFeedPinchColumnBounds(WindowWidthSizeClass.Compact, width),
                )
            }
            assertEquals(if (customized) listOf(4, 2, 1, 2, 4) else listOf(2, 2, 1, 2, 2), columns)
        }
    }

    @Test
    fun narrowWindowMaximumOverridesMinimumWithoutInvalidClampRange() {
        assertEquals(1, resolveFeedGridColumnPreference(true, 4, null, 1..1, minimumColumns = 2))
    }

    @Test
    fun singleColumnPresentationIgnoresEarlierGridGesture() {
        assertEquals(1, resolveFeedGridColumnPreference(false, 1, 4, 1..2))
        assertEquals(2, resolveFeedGridColumnPreference(false, 2, 4, 1..2))
    }
}
