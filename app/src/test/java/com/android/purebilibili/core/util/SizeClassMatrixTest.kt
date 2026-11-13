package com.android.purebilibili.core.util

import com.android.purebilibili.feature.settings.shouldUseSettingsSplitLayout
import com.android.purebilibili.feature.video.screen.resolveTabletVideoLayoutPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 标准设备档位矩阵：锁定每一档上全部产品级布局决策的整体表现。
 * 单点断点映射由 WindowSizeUtilsTest 覆盖；本测试保证某个断点调整
 * 不会只改一处映射而静默翻转其它档位的产品行为。
 */
class SizeClassMatrixTest {

    /** (窗口宽, 窗口高, 设备最小宽 dp)：模拟 MainActivity 不重建时旋转/折叠后的真实窗口组合。 */
    private fun phonePortrait() = sizeClass(windowWidthDp = 411, windowHeightDp = 891, deviceSmallestWidthDp = 411)
    private fun tabletPortrait() = sizeClass(windowWidthDp = 600, windowHeightDp = 960, deviceSmallestWidthDp = 600)
    private fun tabletLandscape() = sizeClass(windowWidthDp = 840, windowHeightDp = 800, deviceSmallestWidthDp = 800)
    private fun expandedLandscape() = sizeClass(windowWidthDp = 1280, windowHeightDp = 800, deviceSmallestWidthDp = 800)

    private fun sizeClass(
        windowWidthDp: Int,
        windowHeightDp: Int,
        deviceSmallestWidthDp: Int,
    ): WindowSizeClass {
        val width = resolveWindowWidthSizeClass(windowWidthDp.dp)
        val height = resolveWindowHeightSizeClass(windowHeightDp.dp)
        return WindowSizeClass(
            widthSizeClass = width,
            heightSizeClass = height,
            widthDp = windowWidthDp.dp,
            heightDp = windowHeightDp.dp,
            deviceWidthSizeClass = resolveStableDeviceWidthSizeClass(deviceSmallestWidthDp),
        )
    }

    @Test
    fun `phone portrait keeps bottom bar and single column layouts`() {
        val sc = phonePortrait()

        assertEquals(WindowWidthSizeClass.Compact, sc.widthSizeClass)
        assertFalse(sc.isTabletDevice)
        assertFalse(sc.shouldUseSideNavigation)
        assertFalse(sc.shouldUseSplitLayout)
        assertFalse(sc.shouldUseThreePaneLayout)
        assertFalse(sc.shouldUseExpandedNavigationRail)
        assertFalse(shouldUseSettingsSplitLayout(sc.widthDp.value.toInt()))
    }

    @Test
    fun `tablet portrait keeps side navigation and split layouts without three panes`() {
        val sc = tabletPortrait()

        assertEquals(WindowWidthSizeClass.Medium, sc.widthSizeClass)
        assertTrue(sc.isTabletDevice)
        assertTrue(sc.shouldUseSideNavigation)
        assertTrue(sc.shouldUseSplitLayout)
        assertFalse(sc.shouldUseThreePaneLayout)
        assertFalse(sc.shouldUseExpandedNavigationRail)
        assertFalse(shouldUseSettingsSplitLayout(sc.widthDp.value.toInt()))
    }

    @Test
    fun `tablet landscape enables settings split and keeps two pane detail`() {
        val sc = tabletLandscape()

        assertEquals(WindowWidthSizeClass.Expanded, sc.widthSizeClass)
        assertTrue(sc.isTabletDevice)
        assertTrue(sc.shouldUseSideNavigation)
        assertTrue(sc.shouldUseSplitLayout)
        assertFalse(sc.shouldUseThreePaneLayout)
        assertFalse(sc.shouldUseExpandedNavigationRail)
        assertTrue(shouldUseSettingsSplitLayout(sc.widthDp.value.toInt()))
    }

    @Test
    fun `expanded landscape enables navigation rail and three pane layouts`() {
        val sc = expandedLandscape()

        assertEquals(WindowWidthSizeClass.Large, sc.widthSizeClass)
        assertTrue(sc.shouldUseSideNavigation)
        assertTrue(sc.shouldUseSplitLayout)
        assertTrue(sc.shouldUseThreePaneLayout)
        assertTrue(sc.shouldUseExpandedNavigationRail)
        assertTrue(shouldUseSettingsSplitLayout(sc.widthDp.value.toInt()))
    }

    @Test
    fun `home feed grid columns grow with width and respect the cap`() {
        // 首页信息流列数由 rememberAdaptiveGridColumns(min 160dp, max 6) 的纯函数派生。
        fun columns(widthDp: Int): Int = resolveAdaptiveGridColumns(
            widthDp = widthDp.dp,
            minColumnWidth = 160.dp,
            maxColumns = 6,
        )

        assertEquals(2, columns(phonePortrait().widthDp.value.toInt()))
        assertEquals(3, columns(tabletPortrait().widthDp.value.toInt()))
        assertEquals(5, columns(tabletLandscape().widthDp.value.toInt()))
        assertEquals(6, columns(expandedLandscape().widthDp.value.toInt()))
    }

    @Test
    fun `video detail tablet policy stays single branch without fold posture`() {
        // 非折叠姿态下四个档位都不进入 tabletop 布局；比例只随宽度档位变化。
        val tabletPortraitPolicy = resolveTabletVideoLayoutPolicy(
            widthDp = tabletPortrait().widthDp.value.toInt(),
            foldPosture = AppFoldPosture.None,
        )
        val expandedPolicy = resolveTabletVideoLayoutPolicy(
            widthDp = expandedLandscape().widthDp.value.toInt(),
            foldPosture = AppFoldPosture.None,
        )

        assertFalse(tabletPortraitPolicy.useTabletopLayout)
        assertFalse(expandedPolicy.useTabletopLayout)
        assertEquals(0.72f, tabletPortraitPolicy.primaryRatio, 0.0001f)
        assertEquals(0.66f, expandedPolicy.primaryRatio, 0.0001f)
    }
}
