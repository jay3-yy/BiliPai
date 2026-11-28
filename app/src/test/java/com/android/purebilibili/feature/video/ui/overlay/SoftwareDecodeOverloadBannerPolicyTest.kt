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
    fun `weak network stall tracker ignores brief glitches, coalesces rapid jitter, and requires cumulative duration`() {
        val tracker = WeakNetworkStallTracker(
            minEffectiveStallMs = 1_200L,
            singleStallThresholdMs = 7_500L,
            rebufferWindowMs = 45_000L,
            rebufferCountThreshold = 3,
            rebufferMinTotalDurationMs = 4_500L,
            stallCoalesceGapMs = 1_500L,
        )

        fun simulateStall(startMs: Long, durationMs: Long, bufferedAheadMs: Long? = 500L): String? {
            val enter = tracker.onPlaybackState(
                nowMs = startMs,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = bufferedAheadMs,
            )
            if (!enter.trackingActiveBuffering) return enter.warningMessage
            return tracker.onPlaybackState(
                nowMs = startMs + durationMs,
                playbackState = androidx.media3.common.Player.STATE_READY,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = bufferedAheadMs,
            ).warningMessage
        }

        // 连续 4 次短于 1.2s 的瞬态抖动全部忽略
        assertNull(simulateStall(1_000L, 400L))
        assertNull(simulateStall(4_000L, 900L))
        assertNull(simulateStall(8_000L, 1_199L))
        assertNull(simulateStall(12_000L, 600L))

        // 手里已有充足缓冲（> 2.5s）的 BUFFERING 属于非网络卡顿，忽略
        assertNull(simulateStall(15_000L, 3_000L, bufferedAheadMs = 6_000L))

        // 第 1 次有效卡顿（1.6s），以及 1.5s 内的二次抖动（1.4s）归并算 1 次
        assertNull(simulateStall(20_000L, 1_600L))
        assertNull(simulateStall(22_500L, 1_400L))

        // 第 2 次有效卡顿（1.3s），累计 4.3s 但仅 2 次 -> 不触发
        assertNull(simulateStall(28_000L, 1_300L))

        // 第 3 次有效卡顿（1.5s），窗口内 3 次且累计 5.8s >= 4.5s -> 触发
        val warning = simulateStall(35_000L, 1_500L)
        assertNotNull(warning)
        assertTrue(warning.contains("频繁缓冲卡顿"))
    }

    @Test
    fun `weak network stall tracker filters startup, offline, seek grace, and triggers on sustained long stall`() {
        val tracker = WeakNetworkStallTracker()

        // 首帧前起播不跟踪
        assertFalse(
            tracker.onPlaybackState(
                nowMs = 1_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = false,
                externallySuppressed = false,
                bufferedAheadMs = 0L,
            ).trackingActiveBuffering
        )

        // 离线状态不跟踪降画质
        assertFalse(
            tracker.onPlaybackState(
                nowMs = 2_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = 0L,
                isNetworkAvailable = false,
            ).trackingActiveBuffering
        )

        // 缓存内跳转（仍为 READY）：宽限期内（< 1.8s）的缓冲被过滤，宽限过期后的真实缓冲正常跟踪
        tracker.onSeekDiscontinuity(
            nowMs = 5_000L,
            playbackState = androidx.media3.common.Player.STATE_READY,
            externallySuppressed = false,
        )
        assertFalse(
            tracker.onPlaybackState(
                nowMs = 5_500L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = 300L,
            ).trackingActiveBuffering
        )
        // 恢复 READY 清除该次宽限抑制
        tracker.onPlaybackState(
            nowMs = 6_000L,
            playbackState = androidx.media3.common.Player.STATE_READY,
            playWhenReady = true,
            sawReadyInSession = true,
            externallySuppressed = false,
            bufferedAheadMs = 1_000L,
        )

        // 宽限过期后发生单次持续 7.5s 的真实网络欠载 -> 触发长卡顿提示
        assertTrue(
            tracker.onPlaybackState(
                nowMs = 10_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = 200L,
            ).trackingActiveBuffering
        )
        assertNull(
            tracker.evaluateOngoingStall(
                nowMs = 15_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                externallySuppressed = false,
                bufferedAheadMs = 200L,
            )
        )
        val longWarning = tracker.evaluateOngoingStall(
            nowMs = 17_500L,
            playbackState = androidx.media3.common.Player.STATE_BUFFERING,
            playWhenReady = true,
            externallySuppressed = false,
            bufferedAheadMs = 200L,
        )
        assertNotNull(longWarning)
        assertTrue(longWarning.contains("缓冲时间较长"))
    }

    @Test
    fun `releasing seek scrub while already ready clears suppression after grace window`() {
        val tracker = WeakNetworkStallTracker()
        tracker.onExternalSuppressionChanged(
            nowMs = 1_000L,
            suppressed = true,
            playbackState = androidx.media3.common.Player.STATE_READY,
        )
        tracker.onExternalSuppressionChanged(
            nowMs = 1_500L,
            suppressed = false,
            playbackState = androidx.media3.common.Player.STATE_READY,
        )
        // 松手后 1.8s 宽限期内不误报，宽限过期后真实网络欠载可正常跟踪
        assertTrue(
            tracker.onPlaybackState(
                nowMs = 4_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = 100L,
            ).trackingActiveBuffering
        )
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

    @Test
    fun seekWithinBufferedRangeDoesNotLeaveStaleWeakNetworkSuppression() {
        assertEquals(
            false,
            resolveWeakNetworkSeekSuppression(
                playbackState = androidx.media3.common.Player.STATE_READY,
                externallySuppressed = false,
            ),
        )
        assertEquals(
            true,
            resolveWeakNetworkSeekSuppression(
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                externallySuppressed = false,
            ),
        )
        assertEquals(
            true,
            resolveWeakNetworkSeekSuppression(
                playbackState = androidx.media3.common.Player.STATE_READY,
                externallySuppressed = true,
            ),
        )
    }

    @Test
    fun `weak network learning relaxes thresholds and backs off on dismiss or ignore and recovers on accept`() {
        var state = WeakNetworkLearningState()
        val baseline = resolveWeakNetworkPromptProfile(state)
        assertEquals(WEAK_NETWORK_MIN_EFFECTIVE_STALL_MS, baseline.minEffectiveStallMs)
        assertEquals(WEAK_NETWORK_SINGLE_STALL_MS, baseline.singleStallThresholdMs)
        assertEquals("标准", baseline.summaryLabel)
        assertFalse(isWeakNetworkPromptInCooldown(state, nowEpochMs = 1_000L))

        // 手动关闭一次 -> 严格度 +2，冷却 10 分钟
        state = recordWeakNetworkPromptOutcome(state, WeakNetworkPromptOutcome.DISMISSED, nowEpochMs = 10_000L)
        assertEquals(2, state.strictnessScore)
        assertEquals(1, state.consecutiveNegativeCount)
        assertTrue(isWeakNetworkPromptInCooldown(state, nowEpochMs = 10_000L + 5 * 60_000L))
        assertFalse(isWeakNetworkPromptInCooldown(state, nowEpochMs = 10_000L + 10 * 60_000L))
        val restrained = resolveWeakNetworkPromptProfile(state)
        assertTrue(restrained.singleStallThresholdMs > baseline.singleStallThresholdMs)
        assertEquals("偏克制", restrained.summaryLabel)

        // 连续忽略 + 多次关闭 -> 钳位到上限（极少打扰）
        state = recordWeakNetworkPromptOutcome(state, WeakNetworkPromptOutcome.AUTO_DISMISSED_IGNORED, nowEpochMs = 1_000_000L)
        state = recordWeakNetworkPromptOutcome(state, WeakNetworkPromptOutcome.DISMISSED, nowEpochMs = 5_000_000L)
        state = recordWeakNetworkPromptOutcome(state, WeakNetworkPromptOutcome.DISMISSED, nowEpochMs = 20_000_000L)
        assertEquals(WEAK_NETWORK_STRICTNESS_MAX, state.strictnessScore)
        val minimal = resolveWeakNetworkPromptProfile(state)
        assertEquals("极少打扰", minimal.summaryLabel)
        assertEquals(15_000L, minimal.singleStallThresholdMs)

        // 采纳提示后迅速回落严格度并解除冷却
        state = recordWeakNetworkPromptOutcome(state, WeakNetworkPromptOutcome.ACCEPTED, nowEpochMs = 20_001_000L)
        assertEquals(4, state.strictnessScore)
        assertEquals(0, state.consecutiveNegativeCount)
        assertFalse(isWeakNetworkPromptInCooldown(state, nowEpochMs = 20_001_000L))

        // 多次主动卡顿降画质后最多收敛到下限 -2（仍保留 1s 防微抖动底线）
        repeat(8) {
            state = recordWeakNetworkPromptOutcome(
                state,
                WeakNetworkPromptOutcome.MANUAL_DOWNGRADE_DURING_STALL,
                nowEpochMs = 30_000_000L + it * 1_000L,
            )
        }
        assertEquals(WEAK_NETWORK_STRICTNESS_MIN, state.strictnessScore)
        val sensitive = resolveWeakNetworkPromptProfile(state)
        assertEquals("更灵敏", sensitive.summaryLabel)
        assertTrue(sensitive.minEffectiveStallMs >= 1_000L)
        assertTrue(sensitive.singleStallThresholdMs < baseline.singleStallThresholdMs)
    }

    @Test
    fun `weak network tracker applies updated profile thresholds and subtitle reflects learned habit`() {
        val restrainedProfile = resolveWeakNetworkPromptProfile(
            WeakNetworkLearningState(strictnessScore = 4, dismissedCount = 2)
        )
        val tracker = WeakNetworkStallTracker()
        tracker.updateProfile(restrainedProfile)

        assertTrue(
            tracker.onPlaybackState(
                nowMs = 10_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                sawReadyInSession = true,
                externallySuppressed = false,
                bufferedAheadMs = 100L,
            ).trackingActiveBuffering
        )
        // 较克制档下 7.5s 不再误弹，需达到 12s 才触发
        assertNull(
            tracker.evaluateOngoingStall(
                nowMs = 17_500L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                externallySuppressed = false,
                bufferedAheadMs = 100L,
            )
        )
        assertNotNull(
            tracker.evaluateOngoingStall(
                nowMs = 22_000L,
                playbackState = androidx.media3.common.Player.STATE_BUFFERING,
                playWhenReady = true,
                externallySuppressed = false,
                bufferedAheadMs = 100L,
            )
        )

        assertTrue(
            resolveWeakNetworkDegradeSettingSubtitle(
                enabled = false,
                state = WeakNetworkLearningState(),
            ).contains("已关闭")
        )
        assertTrue(
            resolveWeakNetworkDegradeSettingSubtitle(
                enabled = true,
                state = WeakNetworkLearningState(),
            ).contains("自动调整灵敏度")
        )
        assertTrue(
            resolveWeakNetworkDegradeSettingSubtitle(
                enabled = true,
                state = WeakNetworkLearningState(strictnessScore = 4, dismissedCount = 2),
            ).contains("较克制")
        )
    }

    @Test
    fun `weak network adaptive state isolates wifi and cellular buckets and formats combined subtitle`() {
        var adaptive = WeakNetworkAdaptiveState()
        assertFalse(adaptive.hasLearnedFeedback)
        assertTrue(
            resolveWeakNetworkDegradeSettingSubtitle(enabled = true, state = adaptive)
                .contains("Wi-Fi 与移动网络分别学习")
        )

        // Wi-Fi 下关闭提示 -> Wi-Fi 变偏克制，移动网络仍保持标准且无冷却
        adaptive = adaptive.recordOutcome(
            bucket = WeakNetworkBucket.WIFI,
            outcome = WeakNetworkPromptOutcome.DISMISSED,
            nowEpochMs = 10_000L,
        )
        assertTrue(adaptive.hasLearnedFeedback)
        assertEquals("偏克制", resolveWeakNetworkPromptProfile(adaptive.stateFor(WeakNetworkBucket.WIFI)).summaryLabel)
        assertEquals("标准", resolveWeakNetworkPromptProfile(adaptive.stateFor(WeakNetworkBucket.CELLULAR)).summaryLabel)
        assertTrue(isWeakNetworkPromptInCooldown(adaptive.stateFor(WeakNetworkBucket.WIFI), nowEpochMs = 20_000L))
        assertFalse(isWeakNetworkPromptInCooldown(adaptive.stateFor(WeakNetworkBucket.CELLULAR), nowEpochMs = 20_000L))

        val wifiOnlySubtitle = resolveWeakNetworkDegradeSettingSubtitle(enabled = true, state = adaptive)
        assertTrue(wifiOnlySubtitle.contains("Wi-Fi：偏克制"))
        assertFalse(wifiOnlySubtitle.contains("移动网络："))

        // 移动网络下采纳提示 -> 移动网络变更灵敏，双方互不干扰
        adaptive = adaptive.recordOutcome(
            bucket = WeakNetworkBucket.CELLULAR,
            outcome = WeakNetworkPromptOutcome.ACCEPTED,
            nowEpochMs = 30_000L,
        )
        assertEquals("更灵敏", resolveWeakNetworkPromptProfile(adaptive.stateFor(WeakNetworkBucket.CELLULAR)).summaryLabel)
        val combinedSubtitle = resolveWeakNetworkDegradeSettingSubtitle(enabled = true, state = adaptive)
        assertTrue(combinedSubtitle.contains("Wi-Fi：偏克制"))
        assertTrue(combinedSubtitle.contains("移动网络：更灵敏"))
    }
}
