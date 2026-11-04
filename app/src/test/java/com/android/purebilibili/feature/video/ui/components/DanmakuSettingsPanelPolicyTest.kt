package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.DanmakuPanelWidthMode
import com.android.purebilibili.feature.video.danmaku.DanmakuCloudSyncStatus
import com.android.purebilibili.feature.video.danmaku.DanmakuCloudSyncUiState
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuCloudSyncToggleSubtitle
import com.android.purebilibili.feature.video.danmaku.DanmakuBlockRuleSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuSettingsPanelPolicyTest {

    @Test
    fun portraitPanelAnchorsToBottomAndUsesWiderSheetWidth() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = false,
            screenWidthDp = 411,
            screenHeightDp = 915
        )

        assertEquals(
            DanmakuSettingsPanelPresentation.BottomSheet,
            policy.presentation
        )
        assertEquals(12, policy.horizontalPaddingDp)
        assertEquals(12, policy.bottomPaddingDp)
        assertEquals(411, policy.maxWidthDp)
    }

    @Test
    fun landscapeFullscreenPanelUsesRightSheet() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = true,
            screenWidthDp = 915,
            screenHeightDp = 411
        )

        assertEquals(
            DanmakuSettingsPanelPresentation.CenteredDialog,
            policy.presentation
        )
        assertEquals(12, policy.bottomPaddingDp)
        assertEquals(387, policy.maxHeightDp)
        assertEquals(
            DanmakuSettingsPanelAnchor.End,
            policy.anchor
        )
    }

    @Test
    fun fullscreenPanelWidth_isNarrowSideRail() {
        val fullWidth = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = true,
            screenWidthDp = 915,
            screenHeightDp = 411,
            fullscreenWidthMode = DanmakuPanelWidthMode.FULL
        )
        val halfWidth = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = true,
            screenWidthDp = 915,
            screenHeightDp = 411,
            fullscreenWidthMode = DanmakuPanelWidthMode.HALF
        )
        val thirdWidth = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = true,
            screenWidthDp = 915,
            screenHeightDp = 411,
            fullscreenWidthMode = DanmakuPanelWidthMode.THIRD
        )

        assertEquals(fullWidth.maxWidthDp, halfWidth.maxWidthDp)
        assertEquals(fullWidth.maxWidthDp, thirdWidth.maxWidthDp)
        assertEquals(457, fullWidth.maxWidthDp)
        assertEquals(0, fullWidth.minWidthDp)
        assertEquals(DanmakuSettingsPanelAnchor.End, fullWidth.anchor)
    }

    @Test
    fun landscapeInlinePanelUsesSameRightSheetAsFullscreen() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = false,
            screenWidthDp = 1280,
            screenHeightDp = 800
        )

        assertEquals(
            DanmakuSettingsPanelPresentation.CenteredDialog,
            policy.presentation
        )
        assertEquals(512, policy.maxWidthDp)
        assertEquals(12, policy.bottomPaddingDp)
        assertEquals(DanmakuSettingsPanelAnchor.End, policy.anchor)
    }

    @Test
    fun portraitPanelKeepsBottomAnchor() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(
            isFullscreen = false,
            screenWidthDp = 411,
            screenHeightDp = 915
        )

        assertEquals(DanmakuSettingsPanelAnchor.Bottom, policy.anchor)
    }

    @Test
    fun orientationNotFullscreenDeterminesSheetAnchor() {
        for (fullscreen in listOf(false, true)) {
            val portrait = resolveDanmakuSettingsPanelLayoutPolicy(fullscreen, 411, 915)
            val landscape = resolveDanmakuSettingsPanelLayoutPolicy(fullscreen, 915, 411)
            assertEquals(DanmakuSettingsPanelAnchor.Bottom, portrait.anchor)
            assertEquals(DanmakuSettingsPanelAnchor.End, landscape.anchor)
            assertTrue(portrait.maxWidthDp <= 411)
            assertTrue(landscape.maxWidthDp <= 915)
            assertTrue(portrait.maxHeightDp < 915)
            assertTrue(landscape.maxHeightDp < 411)
        }
    }

    @Test
    fun shortViewportNeverForcesContentBeyondWindow() {
        val policy = resolveDanmakuSettingsPanelLayoutPolicy(true, 640, 240)
        assertEquals(216, policy.maxHeightDp)
        assertTrue(policy.maxWidthDp <= 640)
    }

    @Test
    fun fullscreenPanelSurfaceColors_followDarkThemeTokens() {
        val darkScheme = darkColorScheme()
        val colors = resolveDanmakuSettingsPanelSurfaceColors(
            colorScheme = darkScheme
        )

        assertTrue(colors.panelColor.luminance() < 0.5f)
        assertTrue(colors.titleColor.luminance() > colors.panelColor.luminance())
        assertTrue(colors.titleColor.alpha > colors.supportingColor.alpha)
        assertEquals(darkScheme.primary, colors.sliderActiveTrackColor)
    }

    @Test
    fun fullscreenPanelSurfaceColors_followLightThemeTokens() {
        val lightScheme = lightColorScheme()
        val colors = resolveDanmakuSettingsPanelSurfaceColors(
            colorScheme = lightScheme
        )

        assertTrue(colors.panelColor.luminance() > 0.5f)
        assertTrue(colors.titleColor.luminance() < colors.panelColor.luminance())
        assertTrue(colors.titleColor.alpha > colors.supportingColor.alpha)
        assertEquals(lightScheme.primary, colors.sliderThumbColor)
    }

    @Test
    fun backdropTapDismissesPanelWhenPointerStaysWithinTouchSlop() {
        assertTrue(
            shouldDismissDanmakuSettingsPanelFromBackdropGesture(
                maxDragDistancePx = 4f,
                touchSlopPx = 8f
            )
        )
    }

    @Test
    fun backdropDragDoesNotDismissPanel() {
        assertFalse(
            shouldDismissDanmakuSettingsPanelFromBackdropGesture(
                maxDragDistancePx = 18f,
                touchSlopPx = 8f
            )
        )
    }

    @Test
    fun syncStatusBadge_usesExpectedLabels() {
        assertEquals(
            "待同步",
            resolveDanmakuSyncStatusBadgeText(
                DanmakuCloudSyncUiState(status = DanmakuCloudSyncStatus.PENDING)
            )
        )
        assertEquals(
            "同步中",
            resolveDanmakuSyncStatusBadgeText(
                DanmakuCloudSyncUiState(status = DanmakuCloudSyncStatus.SYNCING)
            )
        )
        assertEquals(
            "已同步",
            resolveDanmakuSyncStatusBadgeText(
                DanmakuCloudSyncUiState(status = DanmakuCloudSyncStatus.SUCCESS)
            )
        )
        assertEquals(
            "同步失败",
            resolveDanmakuSyncStatusBadgeText(
                DanmakuCloudSyncUiState(status = DanmakuCloudSyncStatus.FAILURE)
            )
        )
    }

    @Test
    fun cloudSyncToggleSubtitle_mentionsWebImpactWhenEnabled() {
        assertTrue(resolveDanmakuCloudSyncToggleSubtitle(enabled = true).contains("网页版"))
        assertTrue(resolveDanmakuCloudSyncToggleSubtitle(enabled = false).contains("本机"))
    }

    @Test
    fun syncRetryVisibility_onlyAppearsForFailure() {
        assertFalse(shouldShowDanmakuSyncRetry(DanmakuCloudSyncStatus.IDLE))
        assertFalse(shouldShowDanmakuSyncRetry(DanmakuCloudSyncStatus.PENDING))
        assertFalse(shouldShowDanmakuSyncRetry(DanmakuCloudSyncStatus.SYNCING))
        assertFalse(shouldShowDanmakuSyncRetry(DanmakuCloudSyncStatus.SUCCESS))
        assertTrue(shouldShowDanmakuSyncRetry(DanmakuCloudSyncStatus.FAILURE))
    }

    @Test
    fun blockManagerSections_parseRawRulesIntoThreeBuckets() {
        val sections = resolveDanmakuBlockManagerSections(
            "剧透\nregex:第\\d+集\nuid:abc123\n哈哈"
        )

        assertEquals(
            DanmakuBlockRuleSections(
                keywordRules = listOf("剧透", "哈哈"),
                regexRules = listOf("regex:第\\d+集"),
                userHashRules = listOf("uid:abc123")
            ),
            sections
        )
    }

    @Test
    fun blockManagerSections_saveSectionsAsNormalizedRawText() {
        val raw = persistDanmakuBlockManagerSections(
            DanmakuBlockRuleSections(
                keywordRules = listOf("剧透", "哈哈"),
                regexRules = listOf("regex:第\\d+集"),
                userHashRules = listOf("abc123", "uid:xyz")
            )
        )

        assertEquals(
            "剧透\n哈哈\nregex:第\\d+集\nuid:abc123\nuid:xyz",
            raw
        )
    }

    @Test
    fun blockRuleCountBadge_sumsAllRuleGroups() {
        val count = resolveDanmakuBlockRuleCount(
            DanmakuBlockRuleSections(
                keywordRules = listOf("剧透", "哈哈"),
                regexRules = listOf("regex:第\\d+集"),
                userHashRules = listOf("uid:abc123", "uid:xyz")
            )
        )

        assertEquals(5, count)
        assertEquals("5", resolveDanmakuBlockRuleBadgeText(count))
    }

    @Test
    fun blockRuleBadgeText_capsAtNinetyNinePlus() {
        assertEquals("99+", resolveDanmakuBlockRuleBadgeText(120))
    }

    @Test
    fun blockManagerTabLabel_includesCountOnlyWhenPresent() {
        assertEquals("关键词 2", resolveDanmakuBlockManagerTabLabel("关键词", 2))
        assertEquals("正则", resolveDanmakuBlockManagerTabLabel("正则", 0))
    }
}
