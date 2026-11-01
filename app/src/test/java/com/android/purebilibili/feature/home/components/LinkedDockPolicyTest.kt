package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkedDockPolicyTest {
    @Test
    fun glassHandoffIsContinuousMonotonicAndBoundedInBothDirections() {
        assertEquals(0f, resolveLinkedDockGlassHandoffProgress(-0.1f))
        assertEquals(1f, resolveLinkedDockGlassHandoffProgress(1.1f))
        assertEquals(0.125f, resolveLinkedDockGlassHandoffProgress(0.17f), 0.0001f)
        val forward = (0..100).map { resolveLinkedDockGlassHandoffProgress(it / 100f) }
        assertTrue(forward.zipWithNext().all { (a, b) -> b >= a && b - a < 0.09f })
        assertEquals(forward.reversed(), (100 downTo 0).map { resolveLinkedDockGlassHandoffProgress(it / 100f) })
        forward.forEach { compactAlpha ->
            val navigationAlpha = 1f - compactAlpha
            assertEquals(1f, navigationAlpha + compactAlpha, 0.0001f)
        }
    }

    @Test
    fun expandedDockKeepsSearchBesideActualNavigationWidth() {
        assertEquals(
            196,
            resolveLinkedDockNavigationX(
                maximumWidth = 600,
                navigationWidth = 144,
                button = 56,
                gap = 8,
                searchEnabled = true,
            ),
        )
        assertEquals(
            348,
            resolveLinkedDockSearchX(
                maximumWidth = 600,
                navigationWidth = 144,
                searchWidth = 56,
                button = 56,
                gap = 8,
                mergeProgress = 0f,
                searchProgress = 0f,
            ),
        )
        assertEquals(
            544,
            resolveLinkedDockSearchX(
                maximumWidth = 600,
                navigationWidth = 144,
                searchWidth = 56,
                button = 56,
                gap = 8,
                mergeProgress = 1f,
                searchProgress = 0f,
            ),
        )
    }

    @Test
    fun globalCollapseRequestUsesCompactPlaybackPhaseWhenAudioIsPresent() {
        assertEquals(
            LinkedDockPhase.Playback,
            resolveLinkedDockRestingPhase(collapseRequested = true, hasAudio = true),
        )
    }

    @Test
    fun compactSearchDockKeepsHomeAndSearchOnOneRowWithoutAudio() {
        val geometry = resolveLinkedDockGeometry(
            width = 336,
            button = 56,
            barHeight = 64,
            gap = 8,
            hasAudio = false,
            searchEnabled = true,
            mergeProgress = 1f,
            searchProgress = 1f,
        )
        assertEquals(272, geometry.searchWidth)
        assertEquals(0, geometry.audioWidth)
        assertEquals(0, geometry.top)
        assertEquals(64, geometry.height)
    }

    @Test
    fun collapsedWithoutAudioKeepsSearchShrunk() {
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockRestingPhase(collapseRequested = true, hasAudio = false),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockRestingPhase(collapseRequested = false, hasAudio = true),
        )
    }

    @Test
    fun audioDismissalRestoresExpandedDockAndPreservesSearch() {
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockPhaseOnAudioChange(LinkedDockPhase.Playback, hasAudio = false),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockPhaseOnAudioChange(LinkedDockPhase.Compact, hasAudio = false),
        )
        assertEquals(
            LinkedDockPhase.Playback,
            resolveLinkedDockPhaseOnAudioChange(LinkedDockPhase.Compact, hasAudio = true),
        )
        assertEquals(
            LinkedDockPhase.Playback,
            resolveLinkedDockPhaseOnAudioChange(LinkedDockPhase.Playback, hasAudio = true),
        )
        assertEquals(
            LinkedDockPhase.Search,
            resolveLinkedDockPhaseOnAudioChange(LinkedDockPhase.Search, hasAudio = false),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockPhaseOnAudioChange(LinkedDockPhase.Expanded, hasAudio = false),
        )
    }

    @Test
    fun initialPhasePreservesSavedPhaseAcrossMounts() {
        assertEquals(
            LinkedDockPhase.Playback,
            resolveLinkedDockInitialPhase(
                currentItem = BottomNavItem.HOME,
                collapseRequested = false,
                hasAudio = true,
                savedPhase = LinkedDockPhase.Playback,
            ),
        )
        assertEquals(
            LinkedDockPhase.Search,
            resolveLinkedDockInitialPhase(
                currentItem = BottomNavItem.HOME,
                collapseRequested = false,
                hasAudio = true,
                savedPhase = LinkedDockPhase.Search,
            ),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockInitialPhase(
                currentItem = BottomNavItem.HOME,
                collapseRequested = false,
                hasAudio = false,
                savedPhase = LinkedDockPhase.Playback,
            ),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockInitialPhase(
                currentItem = BottomNavItem.HOME,
                collapseRequested = false,
                hasAudio = true,
                savedPhase = null,
            ),
        )
        assertEquals(
            LinkedDockPhase.Playback,
            resolveLinkedDockInitialPhase(
                currentItem = BottomNavItem.DYNAMIC,
                collapseRequested = true,
                hasAudio = true,
                savedPhase = null,
            ),
        )
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockInitialPhase(
                currentItem = BottomNavItem.DYNAMIC,
                collapseRequested = true,
                hasAudio = false,
                savedPhase = null,
            ),
        )
    }

    @Test
    fun expandedAudioOccupiesItsOwnRow() {
        val geometry = geometry(merge = 0f, search = 0f)
        assertEquals(336, geometry.audioWidth)
        assertEquals(0, geometry.audioX)
        assertEquals(0, geometry.audioY)
        assertEquals(72, geometry.top)
        assertEquals(136, geometry.height)
    }

    @Test
    fun compactPlaybackKeepsGapsBetweenThreeSeparateCapsules() {
        val geometry = geometry(merge = 1f, search = 0f)
        assertEquals(64, geometry.audioX)
        assertEquals(208, geometry.audioWidth)
        assertEquals(336, geometry.audioX + geometry.audioWidth + 8 + geometry.searchWidth)
        assertEquals(64, geometry.height)
    }

    @Test
    fun intermediateMergeKeepsAudioYZeroToAvoidQuadraticDropDistortion() {
        val half = geometry(merge = 0.5f, search = 0f)
        assertEquals(0, half.audioY)
        assertEquals(36, half.top)
        assertEquals(100, half.height)
        assertEquals(32, half.audioX)
        assertEquals(272, half.audioWidth)
    }

    @Test
    fun playbackGapsRemainVisibleAcrossWindowWidths() {
        for (width in listOf(240, 296, 336, 600)) {
            for (searchEnabled in listOf(false, true)) {
                val geometry = resolveLinkedDockGeometry(width, 56, 64, 8, true, searchEnabled, 1f, 0f)
                assertEquals(8, geometry.audioX - 56)
                val trailingGap = width - geometry.searchWidth - geometry.audioX - geometry.audioWidth
                assertEquals(if (searchEnabled) 8 else 0, trailingGap)
                assertTrue(geometry.audioWidth >= 112)
            }
        }
    }

    @Test
    fun searchLeavesOneAccessibleArtworkTarget() {
        val geometry = geometry(merge = 1f, search = 1f)
        assertEquals(56, geometry.audioWidth)
        assertEquals(208, geometry.searchWidth)
        assertEquals(64, geometry.height)
    }

    @Test
    fun narrowAndWideLayoutsDoNotOverlapAtRest() {
        for (width in listOf(240, 296, 336, 600)) {
            for (hasAudio in listOf(false, true)) {
                val geometry = resolveLinkedDockGeometry(width, 56, 64, 8, hasAudio, true, 1f, 1f)
                assertTrue(geometry.searchWidth >= 56)
                assertTrue(geometry.audioWidth >= 0)
                val occupiedWidth = 56 + geometry.audioWidth + geometry.searchWidth +
                    if (hasAudio) 16 else 8
                assertEquals(width, occupiedWidth)
            }
        }
    }

    @Test
    fun outOfRangeAnimationProgressCannotProduceNegativeSizes() {
        val geometry = geometry(merge = 1.05f, search = 1.04f)
        assertEquals(0, geometry.top)
        assertEquals(56, geometry.audioWidth)
        assertEquals(64, geometry.height)
    }

    @Test
    fun presenceCollapsesTowardTheRightEdgeWithoutMovingTheAnchor() {
        val full = resolveLinkedDockGeometry(
            width = 336, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 1f, searchProgress = 0f,
            presenceProgress = 1f,
        )
        val half = resolveLinkedDockGeometry(
            width = 336, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 1f, searchProgress = 0f,
            presenceProgress = 0.5f,
        )
        val gone = resolveLinkedDockGeometry(
            width = 336, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 1f, searchProgress = 0f,
            presenceProgress = 0f,
        )
        // 右缘锚定：胶囊收放时右边界不动，左缘随宽度移动。
        val rightEdge = full.audioX + full.audioWidth
        assertEquals(rightEdge, half.audioX + half.audioWidth)
        assertEquals(full.audioWidth / 2, half.audioWidth)
        assertEquals(0, gone.audioWidth)
        assertEquals(rightEdge, gone.audioX)
    }

    @Test
    fun expandedPresenceAnchorsToTheContainerRightEdge() {
        val half = resolveLinkedDockGeometry(
            width = 336, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 0f, searchProgress = 0f,
            presenceProgress = 0.5f,
        )
        assertEquals(336 / 2, half.audioWidth)
        assertEquals(336, half.audioX + half.audioWidth)
    }

    @Test
    fun homeScrollDirectionChangeStartsANewThreshold() {
        assertEquals(16f, accumulateDockScroll(10f, 6f))
        assertEquals(-3f, accumulateDockScroll(16f, -3f))
        assertEquals(-13f, accumulateDockScroll(-3f, -10f))
    }

    @Test
    fun backHandlerOnlyEnabledForSearchAtTopLevel() {
        assertTrue(shouldEnableLinkedDockBackHandler(LinkedDockPhase.Search, isTopLevelDestination = true))
        kotlin.test.assertFalse(shouldEnableLinkedDockBackHandler(LinkedDockPhase.Search, isTopLevelDestination = false))
        kotlin.test.assertFalse(shouldEnableLinkedDockBackHandler(LinkedDockPhase.Playback, isTopLevelDestination = true))
        kotlin.test.assertFalse(shouldEnableLinkedDockBackHandler(LinkedDockPhase.Playback, isTopLevelDestination = false))
        kotlin.test.assertFalse(shouldEnableLinkedDockBackHandler(LinkedDockPhase.Expanded, isTopLevelDestination = true))
        kotlin.test.assertFalse(shouldEnableLinkedDockBackHandler(LinkedDockPhase.Expanded, isTopLevelDestination = false))
    }

    @Test
    fun searchDismissRestoresPlaybackIfAudioActiveElseExpanded() {
        assertEquals(LinkedDockPhase.Playback, resolveLinkedDockPhaseOnSearchDismiss(hasAudio = true))
        assertEquals(LinkedDockPhase.Expanded, resolveLinkedDockPhaseOnSearchDismiss(hasAudio = false))
        assertTrue(shouldResetLinkedDockSearchQuery(LinkedDockPhase.Expanded))
        assertTrue(shouldResetLinkedDockSearchQuery(LinkedDockPhase.Compact))
        assertTrue(shouldResetLinkedDockSearchQuery(LinkedDockPhase.Playback))
        assertFalse(shouldResetLinkedDockSearchQuery(LinkedDockPhase.Search))
        assertEquals(
            LinkedDockPhase.Expanded,
            resolveLinkedDockPhaseOnSearchDismiss(
                hasAudio = false,
                previousPhase = LinkedDockPhase.Compact,
            ),
        )
    }

    @Test
    fun searchPlaybackTargetConsumesFirstTapToRestorePlayback() {
        assertTrue(shouldExpandPlaybackFromSearch(LinkedDockPhase.Search, hasAudio = true))
        kotlin.test.assertFalse(shouldExpandPlaybackFromSearch(LinkedDockPhase.Search, hasAudio = false))
        kotlin.test.assertFalse(shouldExpandPlaybackFromSearch(LinkedDockPhase.Playback, hasAudio = true))
        kotlin.test.assertFalse(shouldExpandPlaybackFromSearch(LinkedDockPhase.Expanded, hasAudio = true))
    }

    @Test
    fun expandedAudioMatchesBottomBarClusterLengthAndOrigin() {
        // 展开态小横条与底栏整簇（导航胶囊 + 搜索圆钮）同宽同起点，
        // 两行胶囊左右边缘对齐。
        val clusterWidth = 352
        val clusterX = 124
        val geometry = resolveLinkedDockGeometry(
            width = 600, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 0f, searchProgress = 0f,
            presenceProgress = 1f,
            expandedAudioWidth = clusterWidth,
            expandedAudioX = clusterX,
        )
        assertEquals(clusterWidth, geometry.audioWidth)
        assertEquals(clusterX, geometry.audioX)
    }

    @Test
    fun expandedAudioPresenceKeepsRightEdgeAnchoredAtClusterEdge() {
        val clusterWidth = 352
        val clusterX = 124
        val full = resolveLinkedDockGeometry(
            width = 600, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 0f, searchProgress = 0f,
            presenceProgress = 1f,
            expandedAudioWidth = clusterWidth,
            expandedAudioX = clusterX,
        )
        val half = resolveLinkedDockGeometry(
            width = 600, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 0f, searchProgress = 0f,
            presenceProgress = 0.5f,
            expandedAudioWidth = clusterWidth,
            expandedAudioX = clusterX,
        )
        assertEquals(clusterX + clusterWidth, full.audioX + full.audioWidth)
        assertEquals(full.audioX + full.audioWidth, half.audioX + half.audioWidth)
        assertEquals(full.audioWidth / 2, half.audioWidth)
    }

    @Test
    fun mergeAnimationStillCollapsesAudioTowardFirstButton() {
        // 对齐底栏簇只改展开态落点；merge 动画终点（并入首按钮旁的胶囊）不变。
        val geometry = resolveLinkedDockGeometry(
            width = 600, button = 56, barHeight = 64, gap = 8,
            hasAudio = true, searchEnabled = true,
            mergeProgress = 1f, searchProgress = 0f,
            presenceProgress = 1f,
            expandedAudioWidth = 352,
            expandedAudioX = 124,
        )
        assertEquals(64, geometry.audioX)
        assertEquals(600 - 56 - geometry.searchWidth - 16, geometry.audioWidth)
    }

    private fun geometry(merge: Float, search: Float) =
        resolveLinkedDockGeometry(336, 56, 64, 8, true, true, merge, search)

    @Test
    fun searchScrollMergeWorksWithoutPlaybackAndRespectsTheSwitch() {
        assertEquals(LinkedDockPhase.Compact, resolveLinkedDockScrollPhase(24f, 24f,
            hasAudio = false, searchEnabled = true, mergeOnScrollDownEnabled = true))
        assertEquals(LinkedDockPhase.Playback, resolveLinkedDockScrollPhase(24f, 24f,
            hasAudio = true, searchEnabled = true, mergeOnScrollDownEnabled = true))
        assertEquals(null, resolveLinkedDockScrollPhase(24f, 24f,
            hasAudio = false, searchEnabled = true, mergeOnScrollDownEnabled = false))
        assertEquals(null, resolveLinkedDockScrollPhase(24f, 24f,
            hasAudio = false, searchEnabled = false, mergeOnScrollDownEnabled = true))
        assertEquals(null, resolveLinkedDockScrollPhase(23f, 24f,
            hasAudio = false, searchEnabled = true, mergeOnScrollDownEnabled = true))
        assertEquals(LinkedDockPhase.Expanded, resolveLinkedDockScrollPhase(-24f, 24f,
            hasAudio = false, searchEnabled = true, mergeOnScrollDownEnabled = false))
    }

    @Test
    fun searchMergeTakesPrecedenceOverStandaloneNavigationCompacting() {
        assertFalse(shouldUseScrollCompactNavigation(enabled = true, hasAudio = false,
            retainingAudio = false, searchMergeEnabled = true))
        assertTrue(shouldUseScrollCompactNavigation(enabled = true, hasAudio = false,
            retainingAudio = false, searchMergeEnabled = false))
    }

    @Test
    fun mergedSearchSurvivesReconciliationRemountAndInputDismissal() {
        assertEquals(LinkedDockPhase.Compact, resolveLinkedDockPhaseOnAudioChange(
            LinkedDockPhase.Compact, hasAudio = false, searchMergeEnabled = true))
        assertEquals(LinkedDockPhase.Compact, resolveLinkedDockInitialPhase(
            BottomNavItem.HOME, collapseRequested = false, hasAudio = false,
            savedPhase = LinkedDockPhase.Compact, searchMergeEnabled = true))
        assertEquals(LinkedDockPhase.Compact, resolveLinkedDockPhaseOnSearchDismiss(
            hasAudio = false, previousPhase = LinkedDockPhase.Compact, searchMergeEnabled = true))
        assertEquals(LinkedDockPhase.Expanded, resolveLinkedDockPhaseOnAudioChange(
            LinkedDockPhase.Compact, hasAudio = false, searchMergeEnabled = false))
        assertEquals(LinkedDockPhase.Search, resolveLinkedDockPhaseOnAudioChange(
            LinkedDockPhase.Search, hasAudio = false, searchMergeEnabled = false))
    }

}
