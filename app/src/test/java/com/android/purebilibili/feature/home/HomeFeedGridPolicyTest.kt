package com.android.purebilibili.feature.home

import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
import com.android.purebilibili.core.util.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFeedGridPolicyTest {

    @Test
    fun expandedFeedWidth_usesNamedLayoutLimit() {
        assertEquals(1280.dp, resolveHomeFeedMaxContentWidth())
    }

    @Test
    fun autoPresetKeepsExistingAutomaticColumns() {
        assertEquals(
            2,
            resolveHomeFeedGridColumns(
                contentWidthDp = 393,
                displayMode = 0,
                fixedColumnCount = 0,
                cardWidthPreset = HomeFeedCardWidthPreset.AUTO
            )
        )
        assertEquals(
            6,
            resolveHomeFeedGridColumns(
                contentWidthDp = 1280,
                displayMode = 0,
                fixedColumnCount = 0,
                cardWidthPreset = HomeFeedCardWidthPreset.AUTO
            )
        )
    }

    @Test
    fun fixedColumnCountTakesPriorityOverCardWidthPreset() {
        assertEquals(
            5,
            resolveHomeFeedGridColumns(
                contentWidthDp = 1280,
                displayMode = 0,
                fixedColumnCount = 5,
                cardWidthPreset = HomeFeedCardWidthPreset.ULTRA_WIDE
            )
        )
    }

    @Test
    fun doubleGridKeepsTwoColumnsOnCompactPhones() {
        assertEquals(
            2,
            resolveHomeFeedGridColumns(
                contentWidthDp = 320,
                displayMode = 0,
                fixedColumnCount = 0,
                cardWidthPreset = HomeFeedCardWidthPreset.AUTO
            )
        )
    }

    @Test
    fun widePresetsReduceTabletColumnsAndMakeCardsWider() {
        val auto = resolveHomeFeedGridColumns(
            contentWidthDp = 1280,
            displayMode = 0,
            fixedColumnCount = 0,
            cardWidthPreset = HomeFeedCardWidthPreset.AUTO
        )
        val wide = resolveHomeFeedGridColumns(
            contentWidthDp = 1280,
            displayMode = 0,
            fixedColumnCount = 0,
            cardWidthPreset = HomeFeedCardWidthPreset.WIDE
        )
        val ultraWide = resolveHomeFeedGridColumns(
            contentWidthDp = 1280,
            displayMode = 0,
            fixedColumnCount = 0,
            cardWidthPreset = HomeFeedCardWidthPreset.ULTRA_WIDE
        )

        assertTrue(wide < auto)
        assertTrue(ultraWide <= wide)
        assertEquals(4, wide)
        assertEquals(4, ultraWide)
    }

    @Test
    fun storyDisplayModeKeepsSingleColumnPolicyAndIgnoresPreset() {
        assertEquals(
            1,
            resolveHomeFeedGridColumns(
                contentWidthDp = 393,
                displayMode = 1,
                fixedColumnCount = 0,
                cardWidthPreset = HomeFeedCardWidthPreset.ULTRA_WIDE
            )
        )
        assertEquals(
            2,
            resolveHomeFeedGridColumns(
                contentWidthDp = 1280,
                displayMode = 1,
                fixedColumnCount = 0,
                cardWidthPreset = HomeFeedCardWidthPreset.ULTRA_WIDE
            )
        )
    }

    @Test
    fun compactScreensUseIndependentColumnMemory() {
        // 折叠屏外屏 / 手机竖屏走独立记忆，内屏捏出的固定列数不再串到窄屏
        assertEquals(
            0,
            resolveHomeFeedStoredColumnCount(
                widthSizeClass = WindowWidthSizeClass.Compact,
                compactColumnCount = 0,
                defaultColumnCount = 3,
            )
        )
        assertEquals(
            2,
            resolveHomeFeedStoredColumnCount(
                widthSizeClass = WindowWidthSizeClass.Compact,
                compactColumnCount = 2,
                defaultColumnCount = 3,
            )
        )
    }

    @Test
    fun wideScreensKeepTheOriginalColumnMemory() {
        assertEquals(
            3,
            resolveHomeFeedStoredColumnCount(
                widthSizeClass = WindowWidthSizeClass.Medium,
                compactColumnCount = 2,
                defaultColumnCount = 3,
            )
        )
        assertEquals(
            3,
            resolveHomeFeedStoredColumnCount(
                widthSizeClass = WindowWidthSizeClass.Expanded,
                compactColumnCount = 2,
                defaultColumnCount = 3,
            )
        )
    }

    @Test
    fun onlyCompactWidthCountsAsNarrowScreen() {
        assertTrue(isCompactHomeFeedScreen(WindowWidthSizeClass.Compact))
        assertTrue(!isCompactHomeFeedScreen(WindowWidthSizeClass.Medium))
        assertTrue(!isCompactHomeFeedScreen(WindowWidthSizeClass.Expanded))
    }
}
