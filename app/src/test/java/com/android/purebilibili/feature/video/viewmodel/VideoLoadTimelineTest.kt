package com.android.purebilibili.feature.video.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoLoadTimelineTest {
    @Test
    fun recommendationRequestAndResultStagesAreOnlyRecordedOnce() {
        var nowMs = 0L
        val timeline = VideoLoadTimeline { nowMs }
        timeline.start(1L)
        nowMs = 100L
        assertEquals(100L, timeline.mark(VideoLoadStage.RELATED_REQUEST_STARTED, 1L))
        assertNull(timeline.mark(VideoLoadStage.RELATED_REQUEST_STARTED, 1L))
        nowMs = 300L
        assertEquals(300L, timeline.mark(VideoLoadStage.RELATED_RESULT, 1L))
        assertNull(timeline.mark(VideoLoadStage.RELATED_RESULT, 1L))
        timeline.start(2L)
        assertEquals(0L, timeline.mark(VideoLoadStage.RELATED_REQUEST_STARTED, 2L))
    }

    @Test
    fun eachStageIsRecordedOncePerRequest() {
        var nowMs = 100L
        val timeline = VideoLoadTimeline { nowMs }
        timeline.start(1L)
        nowMs = 140L
        assertEquals(40L, timeline.mark(VideoLoadStage.DETAILS_READY, 1L))
        assertNull(timeline.mark(VideoLoadStage.DETAILS_READY, 1L))
        assertNull(timeline.mark(VideoLoadStage.RELATED_RESULT, 2L))
    }

    @Test
    fun oldPlayerFramesCannotBeRecordedBeforeNewPreparation() {
        var nowMs = 100L
        val timeline = VideoLoadTimeline { nowMs }
        timeline.start(1L)
        assertNull(timeline.mark(VideoLoadStage.FIRST_FRAME, 1L))
        nowMs = 200L
        assertEquals(100L, timeline.mark(VideoLoadStage.PLAYER_PREPARE_REQUESTED, 1L))
        nowMs = 300L
        assertEquals(200L, timeline.mark(VideoLoadStage.FIRST_FRAME, 1L))
    }

    @Test
    fun newRequestsResetStagesAndIgnoreStaleResults() {
        var nowMs = 100L
        val timeline = VideoLoadTimeline { nowMs }
        timeline.start(1L)
        timeline.mark(VideoLoadStage.DETAILS_READY, 1L)
        nowMs = 200L
        timeline.start(2L)
        assertNull(timeline.mark(VideoLoadStage.DETAILS_READY, 1L))
        nowMs = 240L
        assertEquals(40L, timeline.mark(VideoLoadStage.DETAILS_READY, 2L))
    }
}
