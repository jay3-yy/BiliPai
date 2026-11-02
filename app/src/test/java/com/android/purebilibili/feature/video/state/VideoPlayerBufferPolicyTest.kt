package com.android.purebilibili.feature.video.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoPlayerBufferPolicyTest {

    @Test
    fun resilientPolicyDeepensBufferWithoutIncreasingStartupThreshold() {
        for (wifi in listOf(true, false)) {
            val normal = resolvePlayerBufferPolicy(isOnWifi = wifi)
            val resilient = resolvePlayerBufferPolicy(isOnWifi = wifi, resilient = true)
            assertEquals(normal.bufferForPlaybackMs, resilient.bufferForPlaybackMs)
            assertTrue(resilient.earlyPlaybackMaxBufferMs > normal.earlyPlaybackMaxBufferMs)
            assertTrue(resilient.minBufferMs >= normal.minBufferMs)
            assertTrue(resilient.maxBufferMs >= normal.maxBufferMs)
            assertTrue(resilient.bufferForPlaybackAfterRebufferMs >= normal.bufferForPlaybackAfterRebufferMs)
            listOf(1f, 2f, 3f).forEach { speed ->
                val limitUs = resolveEarlyPlaybackBufferLimitUs(
                    resilient.earlyPlaybackMaxBufferMs, resilient.bufferForPlaybackMs,
                    resilient.bufferForPlaybackAfterRebufferMs, speed,
                )
                assertTrue(limitUs > resilient.bufferForPlaybackAfterRebufferMs * 1_000L * speed)
            }
        }
    }


    @Test
    fun firstQuarterLimitsForwardBufferButLaterPlaybackDoesNot() {
        assertTrue(
            shouldLimitEarlyPlaybackBuffer(
                playbackPositionUs = 59_999_999L,
                mediaPeriodDurationUs = 240_000_000L
            )
        )
        assertTrue(
            !shouldLimitEarlyPlaybackBuffer(
                playbackPositionUs = 60_000_000L,
                mediaPeriodDurationUs = 240_000_000L
            )
        )
        assertTrue(
            !shouldLimitEarlyPlaybackBuffer(
                playbackPositionUs = 0L,
                mediaPeriodDurationUs = -1L
            )
        )
    }

    @Test
    fun missingMediaPeriodStillLimitsEarlyBufferToAvoidEntryBurst() {
        // prepare 一开始 period 未就绪（mediaPeriodId == null）也必须限流，
        // 否则进详情即按 maxBuffer 全力预缓冲
        assertTrue(
            shouldLimitEarlyPlaybackBufferForPeriod(
                hasKnownMediaPeriod = false,
                playbackPositionUs = 0L,
                mediaPeriodDurationUs = -1L
            )
        )
        // 有 period 且时长未知（直播等）维持常规策略，不强限
        assertTrue(
            !shouldLimitEarlyPlaybackBufferForPeriod(
                hasKnownMediaPeriod = true,
                playbackPositionUs = 0L,
                mediaPeriodDurationUs = -1L
            )
        )
        // 有 period 的普通点播按前 1/4 规则
        assertTrue(
            shouldLimitEarlyPlaybackBufferForPeriod(
                hasKnownMediaPeriod = true,
                playbackPositionUs = 59_999_999L,
                mediaPeriodDurationUs = 240_000_000L
            )
        )
    }
    @Test
    fun earlyLimitAlwaysAllowsStartupAndRebufferAtSupportedSpeeds() {
        listOf(true, false).forEach { wifi ->
            val policy = resolvePlayerBufferPolicy(wifi)
            listOf(0.1f, 0.5f, 1f, 1.5f, 2f, 3f).forEach { speed ->
                val limitUs = resolveEarlyPlaybackBufferLimitUs(
                    policy.earlyPlaybackMaxBufferMs,
                    policy.bufferForPlaybackMs,
                    policy.bufferForPlaybackAfterRebufferMs,
                    speed,
                )
                val startupUs = policy.bufferForPlaybackMs * 1_000.0 * speed
                val rebufferUs = policy.bufferForPlaybackAfterRebufferMs * 1_000.0 * speed
                assertTrue(limitUs > startupUs, "startup wifi=$wifi speed=$speed")
                assertTrue(limitUs > rebufferUs, "rebuffer wifi=$wifi speed=$speed")
            }
        }
    }

    @Test
    fun earlyLimitPreservesWallTimeAtSlowAndFastSpeeds() {
        val normalLimit = resolveEarlyPlaybackBufferLimitUs(8_000, 600, 1_600, 1f)
        assertEquals(normalLimit / 2, resolveEarlyPlaybackBufferLimitUs(8_000, 600, 1_600, 0.5f))
        assertEquals(normalLimit * 3, resolveEarlyPlaybackBufferLimitUs(8_000, 600, 1_600, 3f))
    }

    @Test
    fun invalidSpeedUsesNormalPlaybackBudget() {
        val normalLimit = resolveEarlyPlaybackBufferLimitUs(8_000, 600, 1_600, 1f)
        for (speed in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(normalLimit, resolveEarlyPlaybackBufferLimitUs(8_000, 600, 1_600, speed))
        }
    }

    @Test
    fun normalForwardBufferHonorsNetworkPlaybackTimeTargets() {
        for ((wifi, playbackTimeUs) in listOf(true to 6_000_000L, false to 4_000_000L)) {
            val policy = resolvePlayerBufferPolicy(isOnWifi = wifi)
            val mediaTimeUs = resolveEarlyPlaybackBufferLimitUs(
                policy.earlyPlaybackMaxBufferMs,
                policy.bufferForPlaybackMs,
                policy.bufferForPlaybackAfterRebufferMs,
                playbackSpeed = 2f,
            )
            assertEquals(playbackTimeUs, mediaTimeUs / 2, "wall-clock buffer wifi=$wifi")
        }
    }

}
