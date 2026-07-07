package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsTabletLayoutPolicyTest {

    @Test
    fun expandedTablet_usesBalancedMasterDetailRatio() {
        val policy = resolveSettingsTabletLayoutPolicy(widthDp = 1024)

        assertEquals(0.35f, policy.primaryRatio)
        assertEquals(16, policy.masterPanePaddingDp)
        assertEquals(24, policy.detailPanePaddingDp)
        assertEquals(800, policy.detailMaxWidthDp)
    }

    @Test
    fun ultraWideTablet_reducesMasterRatio_andExpandsDetailWidth() {
        val policy = resolveSettingsTabletLayoutPolicy(widthDp = 1700)

        assertEquals(0.30f, policy.primaryRatio)
        assertEquals(20, policy.masterPanePaddingDp)
        assertEquals(28, policy.detailPanePaddingDp)
        assertEquals(920, policy.detailMaxWidthDp)
        assertTrue(policy.rootPanelMaxWidthDp >= 680)
    }

    @Test
    fun ultraWide_keepsComfortableReadingWidth() {
        val policy = resolveSettingsTabletLayoutPolicy(widthDp = 1920)

        assertEquals(0.30f, policy.primaryRatio)
        assertEquals(20, policy.masterPanePaddingDp)
        assertEquals(28, policy.detailPanePaddingDp)
        assertEquals(920, policy.detailMaxWidthDp)
    }

    @Test
    fun splitLayout_threshold_isExpanded_only() {
        assertEquals(false, shouldUseSettingsSplitLayout(widthDp = 720))
        assertEquals(true, shouldUseSettingsSplitLayout(widthDp = 1024))
    }

    @Test
    fun tabletLandscape_keepsMasterAndDetailContentScrollable() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt")
        ).first { it.exists() }.readText()

        assertTrue(source.contains("AdaptiveSplitLayout("))
        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("rightPane"))
    }
}
