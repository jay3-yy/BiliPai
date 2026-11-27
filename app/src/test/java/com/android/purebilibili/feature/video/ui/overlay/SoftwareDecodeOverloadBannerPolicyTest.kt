package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoftwareDecodeOverloadBannerPolicyTest {

    @Test
    fun `resolveLowerQualityTarget selects highest switchable quality below current`() {
        val target = resolveLowerQualityTarget(
            currentQuality = 116,
            qualityIds = listOf(120, 116, 80, 64, 32),
            qualityLabels = listOf("4K", "1080P60", "1080P", "720P", "480P"),
            switchableQualityIds = listOf(116, 64, 32),
        )

        assertEquals(LowerQualityTarget(index = 3, qualityId = 64, label = "720P"), target)
    }

    @Test
    fun `resolveLowerQualityTarget returns null when already at lowest quality`() {
        assertNull(
            resolveLowerQualityTarget(
                currentQuality = 32,
                qualityIds = listOf(80, 64, 32),
                qualityLabels = listOf("1080P", "720P", "480P"),
            )
        )
    }

    @Test
    fun `banner shows only for software decode drop warnings and offers matching actions`() {
        val warning = "软件解码负载较高（近期掉帧 36 帧），建议开启硬件解码或降低画质/帧率。"
        val softwareInfo = PlaybackDebugInfo(
            videoDecoder = "c2.android.av1-dav1d.decoder",
            videoDecoderType = "软件解码",
            videoDecoderDropWarning = warning,
        )

        val stateWhenSwPreferred = resolveSoftwareDecodeOverloadBannerState(
            debugInfo = softwareInfo,
            hwDecodeEnabled = false,
            currentQuality = 116,
            qualityIds = listOf(116, 80, 64),
            qualityLabels = listOf("1080P60", "1080P", "720P"),
            switchableQualityIds = emptyList(),
            isScreenLocked = false,
            suppressTransientOverlays = false,
            dismissed = false,
        )
        assertNotNull(stateWhenSwPreferred)
        assertTrue(stateWhenSwPreferred.showEnableHardwareAction)
        assertEquals(LowerQualityTarget(index = 1, qualityId = 80, label = "1080P"), stateWhenSwPreferred.lowerQualityTarget)

        val stateWhenHwPreferred = resolveSoftwareDecodeOverloadBannerState(
            debugInfo = softwareInfo,
            hwDecodeEnabled = true,
            currentQuality = 116,
            qualityIds = listOf(116, 80),
            qualityLabels = listOf("1080P60", "1080P"),
            switchableQualityIds = emptyList(),
            isScreenLocked = false,
            suppressTransientOverlays = false,
            dismissed = false,
        )
        assertNotNull(stateWhenHwPreferred)
        assertFalse(stateWhenHwPreferred.showEnableHardwareAction)
        assertEquals(80, stateWhenHwPreferred.lowerQualityTarget?.qualityId)
    }

    @Test
    fun `banner stays hidden for hardware decode, lock screen, transitions, or dismissed state`() {
        val hardwareInfo = PlaybackDebugInfo(
            videoDecoder = "c2.qti.av1.decoder",
            videoDecoderType = "硬件解码",
            videoDecoderDropWarning = "视频渲染出现持续掉帧（近期掉帧 36 帧），可尝试降低画质、帧率或倍速。",
        )
        assertNull(
            resolveSoftwareDecodeOverloadBannerState(
                debugInfo = hardwareInfo,
                hwDecodeEnabled = false,
                currentQuality = 80,
                qualityIds = listOf(80, 64),
                qualityLabels = listOf("1080P", "720P"),
                switchableQualityIds = emptyList(),
                isScreenLocked = false,
                suppressTransientOverlays = false,
                dismissed = false,
            )
        )

        val softwareInfo = hardwareInfo.copy(videoDecoderType = "软件解码")
        assertNull(
            resolveSoftwareDecodeOverloadBannerState(
                debugInfo = softwareInfo,
                hwDecodeEnabled = false,
                currentQuality = 80,
                qualityIds = listOf(80, 64),
                qualityLabels = listOf("1080P", "720P"),
                switchableQualityIds = emptyList(),
                isScreenLocked = true,
                suppressTransientOverlays = false,
                dismissed = false,
            )
        )
        assertNull(
            resolveSoftwareDecodeOverloadBannerState(
                debugInfo = softwareInfo,
                hwDecodeEnabled = false,
                currentQuality = 80,
                qualityIds = listOf(80, 64),
                qualityLabels = listOf("1080P", "720P"),
                switchableQualityIds = emptyList(),
                isScreenLocked = false,
                suppressTransientOverlays = true,
                dismissed = false,
            )
        )
        assertNull(
            resolveSoftwareDecodeOverloadBannerState(
                debugInfo = softwareInfo,
                hwDecodeEnabled = false,
                currentQuality = 80,
                qualityIds = listOf(80, 64),
                qualityLabels = listOf("1080P", "720P"),
                switchableQualityIds = emptyList(),
                isScreenLocked = false,
                suppressTransientOverlays = false,
                dismissed = true,
            )
        )
    }

    @Test
    fun `weak network stall tracker triggers on three rebuffers inside window and expires stale events`() {
        val tracker = WeakNetworkStallTracker(rebufferWindowMs = 30_000L, rebufferCountThreshold = 3)
        assertNull(tracker.recordRebuffer(nowMs = 1_000L))
        assertNull(tracker.recordRebuffer(nowMs = 10_000L))
        // 超过 30 秒窗口，1_000L 过期，窗口内只有 2 次
        assertNull(tracker.recordRebuffer(nowMs = 35_000L))
        val warning = tracker.recordRebuffer(nowMs = 39_000L)
        assertNotNull(warning)
        assertTrue(warning.contains("频繁缓冲卡顿"))
    }

    @Test
    fun `weak network degrade banner hides when no lower quality or fallback action exists or suppressed`() {
        val warning = "当前网络较慢，缓冲时间较长，建议降低画质以减少卡顿。"
        assertNull(
            resolveWeakNetworkDegradeBannerState(
                warningMessage = warning,
                currentQuality = 32,
                qualityIds = listOf(80, 64, 32),
                qualityLabels = listOf("1080P", "720P", "480P"),
                switchableQualityIds = emptyList(),
                secondaryActionLabel = null,
                isScreenLocked = false,
                suppressTransientOverlays = false,
                suppressedByHigherPriorityBanner = false,
                dismissed = false,
            )
        )

        val liveAtLowestWithLineSwitch = resolveWeakNetworkDegradeBannerState(
            warningMessage = warning,
            currentQuality = 80,
            qualityIds = listOf(80),
            qualityLabels = listOf("流畅"),
            switchableQualityIds = emptyList(),
            secondaryActionLabel = "切换线路",
            isScreenLocked = false,
            suppressTransientOverlays = false,
            suppressedByHigherPriorityBanner = false,
            dismissed = false,
        )
        assertNotNull(liveAtLowestWithLineSwitch)
        assertNull(liveAtLowestWithLineSwitch.lowerQualityTarget)
        assertEquals("切换线路", liveAtLowestWithLineSwitch.secondaryActionLabel)

        assertNull(
            resolveWeakNetworkDegradeBannerState(
                warningMessage = warning,
                currentQuality = 116,
                qualityIds = listOf(116, 80),
                qualityLabels = listOf("1080P60", "1080P"),
                switchableQualityIds = emptyList(),
                secondaryActionLabel = null,
                isScreenLocked = false,
                suppressTransientOverlays = false,
                suppressedByHigherPriorityBanner = true,
                dismissed = false,
            )
        )
    }
}
