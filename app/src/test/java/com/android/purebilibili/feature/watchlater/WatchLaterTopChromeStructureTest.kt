package com.android.purebilibili.feature.watchlater

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchLaterTopChromeStructureTest {
    @Test
    fun `watch later follows global immersive top chrome and progressive blur`() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt"
        ).readText()

        assertTrue(source.contains("homeSettings.homeHeaderCollapseMode.hasAnyCollapse"))
        assertTrue(source.contains("TopAppBarDefaults.enterAlwaysScrollBehavior()"))
        assertTrue(source.contains("TopAppBarDefaults.pinnedScrollBehavior()"))
        assertTrue(source.contains("BiliPaiImmersiveTopBar("))
        assertTrue(source.contains("layerBackdrop(watchLaterChromeBackdrop)"))
        assertTrue(source.contains("AppLiquidAwareSearchField("))
        assertTrue(source.contains("AppThemeAdaptiveTabRow("))
        assertTrue(source.contains("dragSelectionEnabled = watchLaterFilterChrome.dragSelectionEnabled"))
        assertTrue(source.contains("tapPressRefractionEnabled = true"))
        assertTrue(source.contains("backdrop = watchLaterChromeBackdrop"))
        assertTrue(source.contains("miuixBackdrop = watchLaterChromeBackdrop"))
        assertTrue(source.contains("AppWindowActionMenu("))
        assertTrue(source.contains("label = \"全部听\""))
        // PiliPlus 批量操作：复制/移动平铺为文字按钮，对话框保留"复制到收藏夹"标题
        assertTrue(source.contains("\"复制到收藏夹\" else \"移动到收藏夹\""))
        assertTrue(source.contains("AppFloatingActionButton("))
        assertFalse(source.contains("AppDropdownMenu("))
        assertFalse(source.contains("AppDropdownMenuItem("))
    }
}
