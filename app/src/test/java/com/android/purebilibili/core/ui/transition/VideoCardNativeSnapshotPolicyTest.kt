package com.android.purebilibili.core.ui.transition

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardNativeSnapshotPolicyTest {
    @Test
    fun duplicateVideoOnlyHidesTheCapturedCard() {
        val key = "search:BVsame"
        assertTrue(isRecordedNativeCardSource("BVsame", "search", key, key, ownsRecordedLayer = true))
        assertFalse(isRecordedNativeCardSource("BVsame", "search", key, key, ownsRecordedLayer = false))
    }

    @Test
    fun repeatedRelatedClickDoesNotBorrowTheParentHeldTransition() {
        val relatedKey = "video:BVrelated"
        val parentKey = "home:BVparent"
        // Clicking again writes the related key while the restored parent is still HELD.
        assertFalse(isRecordedNativeCardSource("BVrelated", "video", parentKey, relatedKey))
        assertFalse(isRecordedNativeCardSource("BVrelated", "video", null, relatedKey))
        assertTrue(isRecordedNativeCardSource("BVrelated", "video", relatedKey, relatedKey))
        assertFalse(isRecordedNativeCardSource("BVother", "video", relatedKey, relatedKey))
        assertFalse(isRecordedNativeCardSource("BVrelated", "video", relatedKey, parentKey))
    }

    @Test
    fun sameVideoOnAnotherRouteDoesNotBorrowTheHeldSourceCard() {
        val heldHomeSource = "home:BVsame"

        assertFalse(
            isRecordedNativeCardSource(
                bvid = "BVsame",
                sourceRoute = "space/123",
                activeSourceKey = heldHomeSource,
                recordedSourceKey = heldHomeSource,
            )
        )
        assertTrue(
            isRecordedNativeCardSource(
                bvid = "BVsame",
                sourceRoute = "home",
                activeSourceKey = heldHomeSource,
                recordedSourceKey = heldHomeSource,
            )
        )
    }

    @Test
    fun unrecordedNativeLayerFallsBackToReconstructedChrome() {
        assertFalse(isNativeVideoCardLayerDrawable(widthPx = 0, heightPx = 0))
        assertFalse(isNativeVideoCardLayerDrawable(widthPx = 320, heightPx = 1))
        assertTrue(isNativeVideoCardLayerDrawable(widthPx = 320, heightPx = 240))
    }

    @Test
    fun clickKeepsTheListCardUntilTheFlyingOverlayCoversIt() {
        // 未置锁（刚点击、overlay 尚未覆盖）：卡槽保持。
        assertFalse(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = true,
                exposure = VideoCardTransitionExposure.Opening,
                isCoveredLatch = false,
            ),
        )
        assertFalse(
            isVideoCardFlyingOverlayCoveringSource(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                depthProgress = 0f,
                isReturnGestureInProgress = false,
            ),
        )
    }

    @Test
    fun stationarySourceCardIsEmptyWhileTheFlyingCardOwnsTheSlot() {
        // 锁存置位后，Opening 期间一律空槽——不再受逐帧 depth 抖动影响。
        assertTrue(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = true,
                exposure = VideoCardTransitionExposure.Opening,
                isCoveredLatch = true,
            ),
        )
        assertTrue(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = true,
                exposure = VideoCardTransitionExposure.SettledHidden,
                isCoveredLatch = true,
            ),
        )
        assertTrue(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = true,
                exposure = VideoCardTransitionExposure.BackPreview,
                isCoveredLatch = true,
            ),
        )
        assertTrue(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = true,
                exposure = VideoCardTransitionExposure.Returning,
                isCoveredLatch = true,
            ),
        )
    }

    @Test
    fun stationarySourceCardReturnsAfterLandAndNeverHidesUnrelatedCards() {
        assertFalse(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = true,
                exposure = VideoCardTransitionExposure.Idle,
                isCoveredLatch = true,
            ),
        )
        assertFalse(
            shouldHideStationarySourceCard(
                isSharedMorphSourceCard = false,
                exposure = VideoCardTransitionExposure.Opening,
                isCoveredLatch = true,
            ),
        )
    }

    @Test
    fun coverLatchIsSingleDirectionalUntilIdle() {
        // 提前 arm 的 OPENING 不算「已覆盖」：点击瞬间不能空出卡槽（避免卡片空洞）。
        assertFalse(
            advanceSourceCardCoverLatch(
                latch = false,
                exposure = VideoCardTransitionExposure.Opening,
            ),
        )
        // 离开 OPENING（整卡已盖住源位）→ 置位。
        assertTrue(
            advanceSourceCardCoverLatch(
                latch = false,
                exposure = VideoCardTransitionExposure.SettledHidden,
            ),
        )
        // 覆盖期内保持置位：不会因为相位在隐藏态之间切换而回落。
        assertTrue(
            advanceSourceCardCoverLatch(
                latch = true,
                exposure = VideoCardTransitionExposure.Opening,
            ),
        )
        assertTrue(
            advanceSourceCardCoverLatch(
                latch = true,
                exposure = VideoCardTransitionExposure.Returning,
            ),
        )
        // 仅 Idle 清除。
        assertFalse(advanceSourceCardCoverLatch(latch = true, exposure = VideoCardTransitionExposure.Idle))
        // 未接线（null）保持现状，不凭空改写。
        assertTrue(advanceSourceCardCoverLatch(latch = true, exposure = null))
        assertFalse(advanceSourceCardCoverLatch(latch = false, exposure = null))
    }

    @Test
    fun coverLatchSetsImmediatelyWhenTheReturnGestureTakesOver() {
        // 预测返回手势接管时，覆盖语义立即成立（无需等相位离开 Opening）。
        assertTrue(
            advanceSourceCardCoverLatch(
                latch = false,
                exposure = VideoCardTransitionExposure.Opening,
                isReturnGestureInProgress = true,
            ),
        )
    }

    @Test
    fun coveredSourceCardStopsReRecordingItsDisplayList() {
        // 覆盖期停止重录：这是源页在详情往返期间最大的可避免开销。
        assertFalse(shouldRecordNativeVideoCardLayer(VideoCardTransitionExposure.SettledHidden, isSharedMorphSourceCard = true))
        assertFalse(shouldRecordNativeVideoCardLayer(VideoCardTransitionExposure.BackPreview, isSharedMorphSourceCard = true))
        assertFalse(shouldRecordNativeVideoCardLayer(VideoCardTransitionExposure.Returning, isSharedMorphSourceCard = true))
        assertFalse(shouldRecordNativeVideoCardLayer(VideoCardTransitionExposure.Restoring, isSharedMorphSourceCard = true))
        // 仍可见/刚被覆盖：保持点击时像素最新。
        assertTrue(shouldRecordNativeVideoCardLayer(VideoCardTransitionExposure.Idle, isSharedMorphSourceCard = true))
        assertTrue(shouldRecordNativeVideoCardLayer(VideoCardTransitionExposure.Opening, isSharedMorphSourceCard = true))
        assertTrue(shouldRecordNativeVideoCardLayer(null, isSharedMorphSourceCard = true))
    }

    @Test
    fun visibleRelatedCardsKeepTheirSnapshotsWhileAnotherCardOwnsTheTransition() {
        for (exposure in VideoCardTransitionExposure.entries) {
            assertTrue(shouldRecordNativeVideoCardLayer(exposure, isSharedMorphSourceCard = false))
        }
    }

}
