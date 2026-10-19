package com.android.purebilibili.core.util

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryPolicyTest {
    @Test
    fun startupEventsReplayInOrderThenSendDirectly() {
        val buffer = StartupTelemetryBuffer<String>(3)
        val received = mutableListOf<String>()
        buffer.setEnabled(true)
        buffer.submit("home")
        buffer.submit("search")
        buffer.activate { received.add(it) }
        buffer.submit("video")
        assertEquals(listOf("home", "search", "video"), received)
    }

    @Test
    fun disablingDropsPendingAndDisabledEventsEvenAfterReenable() {
        val buffer = StartupTelemetryBuffer<String>(3)
        val received = mutableListOf<String>()
        assertFalse(buffer.submit("before_preference"))
        buffer.setEnabled(true)
        buffer.submit("pending")
        buffer.setEnabled(false)
        assertFalse(buffer.submit("disabled"))
        buffer.activate { received.add(it) }
        buffer.setEnabled(true)
        buffer.submit("new_consent")
        assertEquals(listOf("new_consent"), received)
    }

    @Test
    fun startupOverflowKeepsOnlyMostRecentEvents() {
        val buffer = StartupTelemetryBuffer<Int>(2)
        val received = mutableListOf<Int>()
        buffer.setEnabled(true)
        repeat(100) { buffer.submit(it) }
        buffer.activate { received.add(it) }
        assertEquals(listOf(98, 99), received)
    }

    @Test
    fun failedFlushRetainsUnsentEventsForRetry() {
        val buffer = StartupTelemetryBuffer<String>(3)
        val received = mutableListOf<String>()
        buffer.setEnabled(true)
        buffer.submit("first")
        buffer.submit("second")
        try {
            buffer.activate {
                if (it == "second") throw IllegalStateException("unavailable")
                received.add(it)
            }
        } catch (_: IllegalStateException) {
            // Retry must not replay the successfully submitted first event.
        }
        buffer.activate { received.add(it) }
        assertEquals(listOf("first", "second"), received)
    }

    @Test
    fun duplicateSuppressionIsAtomicAcrossThreads() {
        val limiter = TelemetryRateLimiter(maxKeys = 10)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val accepted = executor.invokeAll(List(100) {
                Callable { !limiter.shouldDrop("same_failure", 1000L, 60_000L) }
            }).count { it.get() }
            assertEquals(1, accepted)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun rollingBudgetBlocksDistinctErrorsAndRecoversAtWindowBoundary() {
        val limiter = TelemetryRateLimiter(maxKeys = 10, maxEventsPerWindow = 2, windowMs = 100L)
        assertFalse(limiter.shouldDrop("first", 0L, 100L))
        assertFalse(limiter.shouldDrop("second", 50L, 100L))
        assertTrue(limiter.shouldDrop("third", 99L, 100L))
        assertFalse(limiter.shouldDrop("third", 100L, 100L))
        assertTrue(limiter.shouldDrop("fourth", 149L, 100L))
        assertFalse(limiter.shouldDrop("fourth", 150L, 100L))
    }

    @Test
    fun fullCacheEvictsOldestKeyAndClearResetsBudget() {
        val limiter = TelemetryRateLimiter(maxKeys = 2, maxEventsPerWindow = 4)
        assertFalse(limiter.shouldDrop("oldest", 0L, 1000L))
        assertFalse(limiter.shouldDrop("second", 1L, 1000L))
        assertFalse(limiter.shouldDrop("third", 2L, 1000L))
        assertTrue(limiter.shouldDrop("second", 3L, 1000L))
        assertFalse(limiter.shouldDrop("oldest", 3L, 1000L))
        assertTrue(limiter.shouldDrop("new", 4L, 1000L))
        limiter.clear()
        assertFalse(limiter.shouldDrop("new", 4L, 1000L))
    }

    @Test
    fun connectivityAndThrottlingAreSampledLessFrequentlyThanServerErrors() {
        listOf(-1, 412, 429).forEach { code ->
            assertEquals(300_000L, apiFailureReportIntervalMs(code))
        }
        assertEquals(60_000L, apiFailureReportIntervalMs(500))
        val limiter = TelemetryRateLimiter(maxKeys = 10)
        assertFalse(limiter.shouldDrop("offline", 0L, apiFailureReportIntervalMs(-1)))
        assertTrue(limiter.shouldDrop("offline", 60_000L, apiFailureReportIntervalMs(-1)))
        assertFalse(limiter.shouldDrop("offline", 300_000L, apiFailureReportIntervalMs(-1)))
    }
}
