package com.android.purebilibili.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionPaneStateTest {
    @Test
    fun windowResizeTemporarilyShowsVideoAloneAndRestoresCommittedSplit() {
        val state = CompanionPaneState(0.38f)
        state.selectFraction(0.48f, 1000f)

        assertEquals(CompanionPaneMode.Video, state.visibleMode(canSplit = false, immersivePlayback = false))
        assertEquals(CompanionPaneMode.Split, state.visibleMode(canSplit = true, immersivePlayback = false))
        assertEquals(0.48f, state.measuredFeedFraction(1000f), 0.001f)

        state.selectFraction(1f, 1000f)
        assertEquals(CompanionPaneMode.Feed, state.visibleMode(canSplit = false, immersivePlayback = false))
    }

    @Test
    fun dragResizesLayoutAndCommitsMetadataOnlyOnRelease() {
        val state = CompanionPaneState(0.38f)
        state.beginDrag()
        state.dragBy(0.12f)
        assertEquals(0.5f, state.layoutFeedFraction(1000f), 0.001f)
        assertEquals(0.38f, state.metadataFeedFraction(1000f), 0.001f)
        state.endDrag(1000f)
        assertEquals(0.5f, state.metadataFeedFraction(1000f), 0.001f)
        assertEquals(CompanionPaneMode.Split, state.mode)
    }

    @Test
    fun cancelledDragRestoresPreviousWidth() {
        val state = CompanionPaneState(0.38f)
        state.beginDrag()
        state.dragBy(0.2f)
        state.cancelDrag()
        assertEquals(0.38f, state.layoutFeedFraction(1000f), 0.001f)
        assertEquals(CompanionPaneMode.Split, state.mode)
    }

    @Test
    fun edgesSelectSinglePaneAndRestoreLastSplitWidth() {
        val state = CompanionPaneState(0.38f)
        state.selectFraction(0.5f, 1000f)
        state.beginDrag()
        state.dragBy(-0.5f)
        state.endDrag(1000f)
        assertEquals(CompanionPaneMode.Video, state.mode)
        assertEquals(0f, state.layoutFeedFraction(1000f))
        assertEquals(1f, state.measuredVideoFraction(1000f))
        assertEquals(0.5f, state.measuredFeedFraction(1000f))
        state.restoreSplit()
        assertEquals(0.5f, state.layoutFeedFraction(1000f))
        state.beginDrag()
        state.dragBy(0.5f)
        state.endDrag(1000f)
        assertEquals(CompanionPaneMode.Feed, state.mode)
        assertEquals(1f, state.layoutFeedFraction(1000f))
        assertEquals(0.5f, state.measuredVideoFraction(1000f))
        state.restoreSplit()
        assertEquals(0.5f, state.layoutFeedFraction(1000f))
    }

    @Test
    fun draggingInwardFromEitherEdgeRestoresSplitView() {
        val state = CompanionPaneState(0.38f)
        for (edge in listOf(0f, 1f)) {
            state.selectFraction(edge, 1000f)
            state.beginDrag()
            state.dragBy(if (edge == 0f) 0.45f else -0.55f)
            state.endDrag(1000f)
            assertEquals(CompanionPaneMode.Split, state.mode)
            assertEquals(0.45f, state.layoutFeedFraction(1000f), 0.001f)
        }
    }

    @Test
    fun draggingMovesDividerLiveWhilePanesStayAtCommittedWidth() {
        val state = CompanionPaneState(0.38f)
        state.selectFraction(0f, 1000f)
        assertEquals(CompanionPaneMode.Video, state.mode)
        state.beginDrag()
        state.dragBy(0.35f)
        // 拖动中线条与把手实时跟随手指；面板测量宽度锁定在已提交值，
        // 避免逐帧重测整栏（首页网格 + 播放器 Surface）造成拖动卡顿，松手后一次性重排。
        assertEquals(0.35f, state.layoutFeedFraction(1000f), 0.001f)
        assertEquals(1f, state.measuredVideoFraction(1000f), 0.001f)
        assertEquals(0.38f, state.measuredFeedFraction(1000f), 0.001f)
        state.cancelDrag()
        assertEquals(0f, state.layoutFeedFraction(1000f))
    }

    @Test
    fun splitWidthsStayReadableAcrossWindowChanges() {
        val state = CompanionPaneState(0.38f)
        state.selectFraction(0.2f, 840f)
        assertTrue(state.layoutFeedFraction(840f) * 840f >= 280f - 0.01f)
        state.selectFraction(0.8f, 840f)
        assertTrue((1f - state.layoutFeedFraction(840f)) * 840f >= 360f - 0.01f)
        assertTrue(state.layoutFeedFraction(500f) in 0.45f..0.55f)
        state.restoreSplit()
        assertEquals(0.38f, state.layoutFeedFraction(1200f), 0.001f)
    }
}
