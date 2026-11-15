package com.android.purebilibili.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PerformanceNavigationTrackerTest {
    @Test
    fun `late source frame between destination and motion callbacks does not rewrite the navigation origin`() {
        val tracker = PerformanceNavigationTracker("home")
        tracker.pageChanged("video", 100)
        tracker.uiFrame(20, 80, 60, false)
        tracker.motion(120, true, "push", null)
        tracker.motion(200, false, "push", null)
        val record = tracker.snapshot().single()
        assertEquals("home", record.fromPage)
        assertEquals("video", record.toPage)
    }

    @Test
    fun `destination and motion boundaries can arrive in either order`() {
        val destinationFirst = PerformanceNavigationTracker("home")
        destinationFirst.pageChanged("settings", 10)
        destinationFirst.motion(20, true, "push", "Programmatic")
        destinationFirst.motion(100, false, "push", null)
        val motionFirst = PerformanceNavigationTracker("home")
        motionFirst.motion(20, true, "push", "Programmatic")
        motionFirst.pageChanged("settings", 30)
        motionFirst.motion(100, false, "push", null)
        for (record in listOf(destinationFirst.snapshot().single(), motionFirst.snapshot().single())) {
            assertEquals("home", record.fromPage)
            assertEquals("settings", record.toPage)
            assertEquals(80L, record.endedNs!! - record.startedNs)
            assertEquals("settled", record.outcome)
        }
    }

    @Test
    fun `late UI and HWUI callbacks update the finished transition they overlap`() {
        val tracker = PerformanceNavigationTracker("home")
        tracker.pageChanged("settings", 1)
        tracker.motion(10, true, "push", null)
        tracker.motion(100, false, "push", null)
        tracker.pageChanged("search", 110)
        tracker.motion(120, true, "push", null)
        tracker.uiFrame(20, 40, 20, true)
        tracker.hwuiFrame(20, 40, 30, 15, -1, 25, 2)
        val records = tracker.snapshot()
        assertEquals(1L, records[0].ui.jankyFrames)
        assertEquals(1L, records[0].missedDeadlineFrames)
        assertEquals(2L, records[0].droppedMetricCallbacks)
        assertEquals(null, records[0].maxGpuNs)
        assertEquals(0L, records[1].ui.frames)
        assertEquals(0L, records[1].hwuiFrames)
    }

    @Test
    fun `cancelled back and paused motion preserve different outcomes`() {
        val tracker = PerformanceNavigationTracker("settings")
        tracker.motion(10, true, "pop", null)
        tracker.motion(20, true, "pop", "Cancel")
        tracker.motion(30, false, "pop", null)
        assertEquals("cancelled", tracker.snapshot().single().outcome)
        tracker.motion(40, true, "pop", null, "window_resumed_mid_motion")
        tracker.finish(50, "window_paused")
        val partial = tracker.snapshot().last()
        assertEquals("window_paused", partial.outcome)
        assertEquals("window_resumed_mid_motion", partial.startReason)
    }

    @Test
    fun `new navigation instance interrupts an active same type destination`() {
        val tracker = PerformanceNavigationTracker("home")
        tracker.pageChanged("space", 1)
        tracker.motion(10, true, "push", null)
        tracker.pageChanged("space", 20)
        tracker.motion(30, false, "push", null)
        val records = tracker.snapshot()
        assertEquals(2, records.size)
        assertEquals("superseded", records[0].outcome)
        assertEquals("space", records[1].fromPage)
        assertEquals("space", records[1].toPage)
    }

    @Test
    fun `destination without a motion signal does not fabricate an animation`() {
        val tracker = PerformanceNavigationTracker("home")
        tracker.pageChanged("settings", 10)
        tracker.uiFrame(20, 30, 10, false)
        assertEquals(0, tracker.snapshot().size)
        tracker.motion(40, true, "pop", null)
        assertEquals("settings", tracker.snapshot().single().fromPage)
    }

    @Test
    fun `bounded snapshots remain unchanged when delayed frames arrive`() {
        val tracker = PerformanceNavigationTracker("home")
        repeat(45) { index ->
            val start = index * 100L
            tracker.motion(start, true, "push", null)
            tracker.motion(start + 50, false, "push", null)
        }
        val report = tracker.snapshot()
        assertEquals(40, report.size)
        assertEquals(45L, tracker.observedMotions)
        tracker.uiFrame(4_410, 4_420, 10, true)
        assertEquals(0L, report.last().ui.frames)
        assertEquals(1L, tracker.snapshot().last().ui.frames)
        assertFalse(report.last().outcome == "running")
    }
}
