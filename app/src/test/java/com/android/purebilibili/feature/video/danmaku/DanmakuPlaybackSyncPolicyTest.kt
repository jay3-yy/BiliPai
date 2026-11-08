package com.android.purebilibili.feature.video.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuPlaybackSyncPolicyTest {

    @Test
    fun `cancelled back preserves a synced timeline but rebuilds a replacement renderer`() {
        for (synced in listOf(true, false)) {
            assertEquals(
                if (synced) DanmakuSyncAction.SoftResync else DanmakuSyncAction.HardResync,
                resolveDanmakuActionForForegroundRecovery(
                    playWhenReady = true,
                    isPlayerPlaying = true,
                    playbackState = androidx.media3.common.Player.STATE_READY,
                    danmakuEnabled = true,
                    hasData = true,
                    preserveTimeline = true,
                    timelineAlreadySynced = synced
                )
            )
        }
    }

    @Test
    fun `cancelled back never enables disabled danmaku or restarts an ended video`() {
        assertEquals(
            DanmakuSyncAction.None,
            resolveDanmakuActionForForegroundRecovery(
                true, true, androidx.media3.common.Player.STATE_READY,
                danmakuEnabled = false, hasData = true,
                preserveTimeline = true, timelineAlreadySynced = true
            )
        )
        assertEquals(
            DanmakuSyncAction.PauseOnly,
            resolveDanmakuActionForForegroundRecovery(
                true, false, androidx.media3.common.Player.STATE_ENDED,
                danmakuEnabled = true, hasData = true,
                preserveTimeline = true, timelineAlreadySynced = true
            )
        )
    }

    @Test
    fun `engine play speed should follow video playback speed percent`() {
        assertEquals(100, resolveDanmakuEnginePlaySpeedPercent(1.0f))
        assertEquals(150, resolveDanmakuEnginePlaySpeedPercent(1.5f))
        assertEquals(25, resolveDanmakuEnginePlaySpeedPercent(0.25f))
        assertEquals(300, resolveDanmakuEnginePlaySpeedPercent(3.0f))
    }

    @Test
    fun `playback adjusted duration should scale visual durations inversely to speed`() {
        assertEquals(3500L, resolveDanmakuPlaybackAdjustedDurationMillis(7000L, 2.0f))
        assertEquals(16000L, resolveDanmakuPlaybackAdjustedDurationMillis(4000L, 0.25f))
        assertEquals(4000L, resolveDanmakuPlaybackAdjustedDurationMillis(4000L, Float.NaN))
    }

    @Test
    fun `drift sync interval should be aggressive for high speed`() {
        assertEquals(900L, resolveDanmakuDriftSyncIntervalMs(2.0f))
        assertEquals(1200L, resolveDanmakuDriftSyncIntervalMs(1.5f))
        assertEquals(2000L, resolveDanmakuDriftSyncIntervalMs(1.1f))
    }

    @Test
    fun `drift sync interval should keep moderate frequency around normal speed`() {
        assertEquals(3200L, resolveDanmakuDriftSyncIntervalMs(1.0f))
        assertEquals(3200L, resolveDanmakuDriftSyncIntervalMs(1.01f))
        assertEquals(3200L, resolveDanmakuDriftSyncIntervalMs(0.99f))
    }

    @Test
    fun `drift correction should trigger periodically for both normal and non-normal speed`() {
        assertFalse(shouldScheduleDanmakuDriftCorrection(1.0f, 3))
        assertFalse(shouldScheduleDanmakuDriftCorrection(1.0f, 5))
        assertTrue(shouldScheduleDanmakuDriftCorrection(1.0f, 6))
        assertFalse(shouldScheduleDanmakuDriftCorrection(1.3f, 1))
        assertFalse(shouldScheduleDanmakuDriftCorrection(1.3f, 2))
        assertTrue(shouldScheduleDanmakuDriftCorrection(1.3f, 3))
        assertTrue(shouldScheduleDanmakuDriftCorrection(0.8f, 6))
    }

    @Test
    fun `drift correction should be less frequent at high playback speed`() {
        assertFalse(shouldScheduleDanmakuDriftCorrection(2.0f, 3))
        assertFalse(shouldScheduleDanmakuDriftCorrection(2.0f, 6))
        assertTrue(shouldScheduleDanmakuDriftCorrection(2.0f, 9))
    }

    @Test
    fun `periodic guard action should always preserve visible danmaku`() {
        assertEquals(
            DanmakuSyncAction.SoftResync,
            resolveDanmakuGuardAction(
                videoSpeed = 2.0f,
                tickCount = 9,
                danmakuEnabled = true,
                isPlaying = true,
                hasData = true
            )
        )
        assertEquals(
            DanmakuSyncAction.SoftResync,
            resolveDanmakuGuardAction(
                videoSpeed = 1.3f,
                tickCount = 3,
                danmakuEnabled = true,
                isPlaying = true,
                hasData = true
            )
        )
    }

    @Test
    fun `attach reapply is required when controller changed or pending after related navigation`() {
        // 新 view 接管：即使此前有 controller，也必须重放已就绪缓存
        assertTrue(
            shouldReapplyDanmakuTimelineOnAttach(
                hasCachedList = true,
                pendingTimelineResync = false,
                previousControllerSameAsCurrent = false,
                timelineAlreadySyncedToCurrent = false,
            )
        )
        // load 完成时 controller=null，等 attach 再补
        assertTrue(
            shouldReapplyDanmakuTimelineOnAttach(
                hasCachedList = true,
                pendingTimelineResync = true,
                previousControllerSameAsCurrent = true,
                timelineAlreadySyncedToCurrent = false,
            )
        )
        // 同 controller 且已同步：不必重复
        assertFalse(
            shouldReapplyDanmakuTimelineOnAttach(
                hasCachedList = true,
                pendingTimelineResync = false,
                previousControllerSameAsCurrent = true,
                timelineAlreadySyncedToCurrent = true,
            )
        )
        // 无缓存：不重放
        assertFalse(
            shouldReapplyDanmakuTimelineOnAttach(
                hasCachedList = false,
                pendingTimelineResync = true,
                previousControllerSameAsCurrent = false,
                timelineAlreadySyncedToCurrent = false,
            )
        )
    }

    @Test
    fun `danmaku should start on data ready when player is playing or will play`() {
        // 正在播放：必须启动
        assertTrue(
            shouldStartDanmakuOnDataReady(
                isPlaying = true,
                playWhenReady = true
            )
        )
        // ExoPlayer 不变量下 isPlaying=true 时 playWhenReady 必为 true；显式覆盖以防策略误判
        assertTrue(
            shouldStartDanmakuOnDataReady(
                isPlaying = true,
                playWhenReady = false
            )
        )
        // 缓冲中但即将播放（相关推荐/切集后数据先就绪、播放器后开始）：必须启动，
        // 否则 onIsPlayingChanged(true) 事件已在数据加载完成前被 None 分支吃掉时引擎停在 paused。
        assertTrue(
            shouldStartDanmakuOnDataReady(
                isPlaying = false,
                playWhenReady = true
            )
        )
        // 用户主动暂停：不启动
        assertFalse(
            shouldStartDanmakuOnDataReady(
                isPlaying = false,
                playWhenReady = false
            )
        )
    }

    @Test
    fun `episode data ready uses current player position instead of stale request position`() {
        assertEquals(
            4_800L,
            resolveDanmakuDataReadyPositionMs(
                currentPlayerPositionMs = 4_800L,
                requestedPositionMs = 0L,
            )
        )
        assertEquals(
            1_200L,
            resolveDanmakuDataReadyPositionMs(
                currentPlayerPositionMs = null,
                requestedPositionMs = 1_200L,
            )
        )
    }

    @Test
    fun `player attach resyncs cached data only for an enabled bound session`() {
        assertTrue(
            shouldResyncDanmakuAfterPlayerAttach(
                danmakuEnabled = true,
                hasData = true,
                hasController = true
            )
        )
        assertFalse(
            shouldResyncDanmakuAfterPlayerAttach(
                danmakuEnabled = false,
                hasData = true,
                hasController = true
            )
        )
        assertFalse(
            shouldResyncDanmakuAfterPlayerAttach(
                danmakuEnabled = true,
                hasData = false,
                hasController = true
            )
        )
    }

    @Test
    fun `explicit resync should pause and clear before setData and start`() {
        val calls = mutableListOf<String>()

        executeExplicitDanmakuResync(
            pause = { calls += "pause" },
            clear = { calls += "clear" },
            setData = { calls += "setData" },
            start = { calls += "start" }
        )

        assertEquals(listOf("pause", "clear", "setData", "start"), calls)
    }

    @Test
    fun `explicit seek scrub start should pause before clearing stale danmaku`() {
        val calls = mutableListOf<String>()

        executeDanmakuSeekScrubStart(
            pause = { calls += "pause" },
            clear = { calls += "clear" }
        )

        assertEquals(listOf("pause", "clear"), calls)
    }

    @Test
    fun `explicit seek suppression should be invalidated after timeline pauses`() {
        assertFalse(
            resolveExplicitSeekStartedPlaybackAfterSyncAction(
                explicitSeekStartedPlayback = true,
                action = DanmakuSyncAction.PauseOnly
            ) ?: true
        )
        assertTrue(
            resolveExplicitSeekStartedPlaybackAfterSyncAction(
                explicitSeekStartedPlayback = true,
                action = DanmakuSyncAction.HardResync
            ) ?: false
        )
    }

    @Test
    fun `danmaku load result applies only to current cid and generation`() {
        assertTrue(
            shouldApplyDanmakuLoadResult(
                expectedCid = 1001L,
                expectedGeneration = 3L,
                currentCid = 1001L,
                currentGeneration = 3L
            )
        )
        assertFalse(
            shouldApplyDanmakuLoadResult(
                expectedCid = 1001L,
                expectedGeneration = 3L,
                currentCid = 2002L,
                currentGeneration = 3L
            )
        )
        assertFalse(
            shouldApplyDanmakuLoadResult(
                expectedCid = 1001L,
                expectedGeneration = 3L,
                currentCid = 1001L,
                currentGeneration = 4L
            )
        )
    }

    @Test
    fun `follow-up hard resync should be suppressed right after explicit user seek to same position`() {
        assertTrue(
            shouldSuppressFollowupDanmakuHardResync(
                positionMs = 48_200L,
                explicitSeekPositionMs = 48_000L,
                explicitSeekStartedPlayback = true,
                nowElapsedRealtimeMs = 8_800L,
                explicitSeekElapsedRealtimeMs = 8_000L
            )
        )
    }

    @Test
    fun `follow-up hard resync should not be suppressed when explicit seek resync left danmaku paused`() {
        assertFalse(
            shouldSuppressFollowupDanmakuHardResync(
                positionMs = 48_200L,
                explicitSeekPositionMs = 48_000L,
                explicitSeekStartedPlayback = false,
                nowElapsedRealtimeMs = 8_800L,
                explicitSeekElapsedRealtimeMs = 8_000L
            )
        )
    }

    @Test
    fun `follow-up hard resync should not be suppressed when explicit seek window already expired`() {
        assertFalse(
            shouldSuppressFollowupDanmakuHardResync(
                positionMs = 48_050L,
                explicitSeekPositionMs = 48_000L,
                explicitSeekStartedPlayback = true,
                nowElapsedRealtimeMs = 12_000L,
                explicitSeekElapsedRealtimeMs = 8_000L
            )
        )
    }

    @Test
    fun `follow-up hard resync should not be suppressed for different seek target`() {
        assertFalse(
            shouldSuppressFollowupDanmakuHardResync(
                positionMs = 54_000L,
                explicitSeekPositionMs = 48_000L,
                explicitSeekStartedPlayback = true,
                nowElapsedRealtimeMs = 8_500L,
                explicitSeekElapsedRealtimeMs = 8_000L
            )
        )
    }
}
