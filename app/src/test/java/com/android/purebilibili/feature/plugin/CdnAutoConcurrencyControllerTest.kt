package com.android.purebilibili.feature.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CdnAutoConcurrencyControllerTest {
    @Test
    fun `startup boost raises initial threads and settles back after comfortable buffer`() {
        var clock = 1_000L
        val controller = CdnAutoConcurrencyController(nowMs = { clock })

        controller.onNewSession(maxCap = 32)
        val (startupThreads, startupReason) = controller.resolveTargetConnections(
            bufferMs = 1_000L,
            bitrateBps = 8_000_000L,
            maxCap = 32,
            isAuto = true,
        )
        assertEquals(16, startupThreads)
        assertEquals("起播加速", startupReason)

        // Comfortable buffer ends startup boost back to base level (8).
        clock += 5_000L
        controller.onPlaybackSample(bufferMs = 16_000L, bitrateBps = 8_000_000L, saturated = false, maxCap = 32)
        val (settledThreads, _) = controller.resolveTargetConnections(
            bufferMs = 16_000L,
            bitrateBps = 8_000_000L,
            maxCap = 32,
            isAuto = true,
        )
        assertEquals(8, settledThreads)
    }

    @Test
    fun `trial rolls back when throughput fails to improve and keeps step when throughput grows`() {
        var clock = 10_000L
        val controller = CdnAutoConcurrencyController(nowMs = { clock })

        // Feed baseline throughput ~1 MB/s over 2s.
        controller.onBytesDelivered(1_000_000)
        clock += 1_000L
        controller.onBytesDelivered(1_000_000)
        clock += 3_000L

        // Low buffer triggers step up 8 -> 12 with 10s trial.
        controller.onPlaybackSample(bufferMs = 3_000L, bitrateBps = 20_000_000L, saturated = true, maxCap = 32)
        assertEquals(12, controller.resolveTargetConnections(3_000L, 20_000_000L, 32, isAuto = true).first)

        // During trial, throughput stays flat (~1 MB/s). After 10s trial rolls back to 8.
        clock += 5_000L
        controller.onBytesDelivered(1_000_000)
        clock += 5_500L
        controller.onBytesDelivered(1_000_000)
        controller.onPlaybackSample(bufferMs = 3_000L, bitrateBps = 20_000_000L, saturated = true, maxCap = 32)
        val (rolledBack, reason) = controller.resolveTargetConnections(3_000L, 20_000_000L, 32, isAuto = true)
        assertEquals(8, rolledBack)
        assertEquals("增益不足回退", reason)

        // Level 12 is resting for 90s so immediate pressure does not re-try it right away.
        clock += 5_000L
        controller.onPlaybackSample(bufferMs = 2_000L, bitrateBps = 20_000_000L, saturated = true, maxCap = 32)
        assertEquals(8, controller.resolveTargetConnections(2_000L, 20_000_000L, 32, isAuto = true).first)

        // After 90s rest expires, step up again and deliver 2.5x throughput -> trial succeeds!
        clock += 95_000L
        controller.onBytesDelivered(500_000)
        clock += 1_000L
        controller.onBytesDelivered(500_000)
        clock += 3_000L
        controller.onPlaybackSample(bufferMs = 3_000L, bitrateBps = 20_000_000L, saturated = true, maxCap = 32)
        assertEquals(12, controller.resolveTargetConnections(3_000L, 20_000_000L, 32, isAuto = true).first)

        clock += 9_000L
        controller.onBytesDelivered(4_000_000)
        clock += 2_000L
        controller.onBytesDelivered(4_000_000)
        controller.onPlaybackSample(bufferMs = 3_000L, bitrateBps = 20_000_000L, saturated = true, maxCap = 32)
        assertEquals(12, controller.resolveTargetConnections(3_000L, 20_000_000L, 32, isAuto = true).first)
    }

    @Test
    fun `http 412 and 429 pushback drops concurrency and caps climbing during rest`() {
        var clock = 1_000L
        val controller = CdnAutoConcurrencyController(nowMs = { clock })

        controller.onNewSession(maxCap = 32) // level 4 (16)
        controller.onPushback(429)
        val (dropped, reason) = controller.resolveTargetConnections(2_000L, 8_000_000L, 32, isAuto = true)
        assertTrue(dropped <= 4)
        assertEquals("限流回退", reason)

        // Cannot climb past hard cap before 180s rest expires.
        clock += 10_000L
        controller.onPlaybackSample(bufferMs = 1_000L, bitrateBps = 8_000_000L, saturated = true, maxCap = 32)
        assertTrue(controller.resolveTargetConnections(1_000L, 8_000_000L, 32, isAuto = true).first <= 4)
    }
}
