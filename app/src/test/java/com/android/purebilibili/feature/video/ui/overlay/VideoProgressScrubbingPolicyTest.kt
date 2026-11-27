package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.progress.PbpRidgeDensity
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.video.ui.components.FullscreenDrawerTab
import com.android.purebilibili.feature.video.ui.components.resolveEndScreenCountdownSeconds
import com.android.purebilibili.feature.video.ui.components.resolveEndScreenNextVideo
import com.android.purebilibili.feature.video.ui.components.resolveFullscreenDrawerInitialTab
import com.android.purebilibili.feature.video.ui.components.shouldShowEndScreenNextCard
import com.android.purebilibili.feature.video.ui.section.formatPinchZoomRestoreLabel
import com.android.purebilibili.feature.video.ui.section.resolveAdjacentChapterTarget
import com.android.purebilibili.feature.video.ui.section.resolveFillZoomScale
import com.android.purebilibili.core.store.LongPressSpeedSlideMode
import com.android.purebilibili.feature.video.ui.section.formatLongPressSpeedLabel
import com.android.purebilibili.feature.video.ui.section.isLongPressSpeedAnchorTick
import com.android.purebilibili.feature.video.ui.section.resolveLongPressContinuousSlideSpeed
import com.android.purebilibili.feature.video.ui.section.resolveLongPressSlideSpeed
import com.android.purebilibili.feature.video.ui.section.resolvePinchZoomReleaseScale
import com.android.purebilibili.feature.video.ui.section.shouldTriggerPortraitSwipeDownMinimize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoProgressScrubbingPolicyTest {

    @Test
    fun `fine scrubbing enters and exits with hysteresis and respects toggle`() {
        assertFalse(
            resolveFineScrubbingActive(
                currentlyActive = false,
                enabled = false,
                upwardDragPx = 200f,
                enterThresholdPx = 100f,
                exitThresholdPx = 30f,
            )
        )
        assertTrue(
            resolveFineScrubbingActive(
                currentlyActive = false,
                enabled = true,
                upwardDragPx = 100f,
                enterThresholdPx = 100f,
                exitThresholdPx = 30f,
            )
        )
        // 已进入精准模式后在滞回带内保持激活，直到下拉低于退出阈值。
        assertTrue(
            resolveFineScrubbingActive(
                currentlyActive = true,
                enabled = true,
                upwardDragPx = 50f,
                enterThresholdPx = 100f,
                exitThresholdPx = 30f,
            )
        )
        assertFalse(
            resolveFineScrubbingActive(
                currentlyActive = true,
                enabled = true,
                upwardDragPx = 25f,
                enterThresholdPx = 100f,
                exitThresholdPx = 30f,
            )
        )
    }

    @Test
    fun `fine scrubbing scales horizontal sensitivity to 4x precision`() {
        // 400s 视频全宽在精准模式下对应 100s，滑动 10% 宽度仅移动 10s。
        val pos = resolveFineScrubPositionMs(
            anchorPositionMs = 120_000L,
            anchorTouchX = 500f,
            currentTouchX = 600f,
            containerWidthPx = 1000f,
            durationMs = 400_000L,
        )
        assertEquals(130_000L, pos)
    }

    @Test
    fun `scrubbing snaps to chapter and peak highlights and cancels at origin`() {
        val durationMs = 300_000L
        val snapPoints = extractProgressSnapPoints(
            durationMs = durationMs,
            viewPoints = listOf(ViewPoint(content = "开箱评测", from = 60, to = 120)),
            pbpRidgeSamples = listOf(
                PbpRidgeSample(0.45f, 0.3f, PbpRidgeDensity.NORMAL),
                PbpRidgeSample(0.50f, 0.9f, PbpRidgeDensity.HOT),
                PbpRidgeSample(0.55f, 0.4f, PbpRidgeDensity.NORMAL),
            ),
        )

        val chapterHit = resolveProgressScrubTarget(
            rawTargetPositionMs = 61_500L,
            originPositionMs = 10_000L,
            durationMs = durationMs,
            hasLeftOrigin = true,
            fineScrubbingActive = false,
            snapAndOriginCancelEnabled = true,
            snapPoints = snapPoints,
        )
        assertEquals(60_000L, chapterHit.positionMs)
        assertEquals(ProgressScrubSnapKind.CHAPTER, chapterHit.snapKind)
        assertEquals("开箱评测", chapterHit.snapLabel)

        val peakHit = resolveProgressScrubTarget(
            rawTargetPositionMs = 151_000L,
            originPositionMs = 10_000L,
            durationMs = durationMs,
            hasLeftOrigin = true,
            fineScrubbingActive = false,
            snapAndOriginCancelEnabled = true,
            snapPoints = snapPoints,
        )
        assertEquals(150_000L, peakHit.positionMs)
        assertEquals(ProgressScrubSnapKind.PEAK_HIGHLIGHT, peakHit.snapKind)

        val originCancel = resolveProgressScrubTarget(
            rawTargetPositionMs = 12_000L,
            originPositionMs = 10_000L,
            durationMs = durationMs,
            hasLeftOrigin = true,
            fineScrubbingActive = false,
            snapAndOriginCancelEnabled = true,
            snapPoints = snapPoints,
        )
        assertTrue(originCancel.cancelledAtOrigin)
        assertEquals(10_000L, originCancel.positionMs)

        // 关闭开关后保持原始线性位置
        val linear = resolveProgressScrubTarget(
            rawTargetPositionMs = 61_500L,
            originPositionMs = 10_000L,
            durationMs = durationMs,
            hasLeftOrigin = true,
            fineScrubbingActive = false,
            snapAndOriginCancelEnabled = false,
            snapPoints = snapPoints,
        )
        assertEquals(61_500L, linear.positionMs)
        assertEquals(ProgressScrubSnapKind.NONE, linear.snapKind)
    }

    @Test
    fun `double tap jump ahead prefers upcoming peak then chapter`() {
        val durationMs = 300_000L
        val peaks = listOf(
            PbpRidgeSample(0.18f, 0.3f, PbpRidgeDensity.NORMAL),
            PbpRidgeSample(0.20f, 0.88f, PbpRidgeDensity.HOT),
            PbpRidgeSample(0.22f, 0.4f, PbpRidgeDensity.NORMAL),
        )
        val chapters = listOf(ViewPoint(content = "总结对比", from = 90, to = 150))

        val peakCandidate = resolveDoubleTapJumpAheadCandidate(
            currentPositionMs = 30_000L,
            durationMs = durationMs,
            viewPoints = chapters,
            pbpRidgeSamples = peaks,
        )
        assertNotNull(peakCandidate)
        assertFalse(peakCandidate.isChapter)
        assertEquals(60_000L, peakCandidate.targetPositionMs)

        val chapterCandidate = resolveDoubleTapJumpAheadCandidate(
            currentPositionMs = 65_000L,
            durationMs = durationMs,
            viewPoints = chapters,
            pbpRidgeSamples = peaks,
        )
        assertNotNull(chapterCandidate)
        assertTrue(chapterCandidate.isChapter)
        assertEquals(90_000L, chapterCandidate.targetPositionMs)

        val noneCandidate = resolveDoubleTapJumpAheadCandidate(
            currentPositionMs = 200_000L,
            durationMs = durationMs,
            viewPoints = chapters,
            pbpRidgeSamples = peaks,
        )
        assertNull(noneCandidate)
    }

    @Test
    fun `long press slide speed steps through presets and clamps bounds`() {
        assertEquals(2.0f, resolveLongPressSlideSpeed(2.0f, accumulatedDragXPx = 20f, stepPx = 100f))
        assertEquals(2.5f, resolveLongPressSlideSpeed(2.0f, accumulatedDragXPx = 120f, stepPx = 100f))
        assertEquals(5.0f, resolveLongPressSlideSpeed(2.0f, accumulatedDragXPx = 900f, stepPx = 100f))
        assertEquals(1.5f, resolveLongPressSlideSpeed(2.0f, accumulatedDragXPx = -120f, stepPx = 100f))
        assertEquals(0.5f, resolveLongPressSlideSpeed(2.0f, accumulatedDragXPx = -900f, stepPx = 100f))
    }

    @Test
    fun `continuous long press slide speed honors deadzone 0_1x steps and 0_5x to 5_0x bounds`() {
        // 起手死区内保持原速，防止下滑锁速时误蹭偏倍速
        assertEquals(
            2.0f,
            resolveLongPressContinuousSlideSpeed(
                initialSpeed = 2.0f,
                accumulatedDragXPx = 18f,
                deadzonePx = 20f,
                stepPx = 15f,
            ),
        )
        // 向右跨过死区 3 步 -> 2.3x
        assertEquals(
            2.3f,
            resolveLongPressContinuousSlideSpeed(
                initialSpeed = 2.0f,
                accumulatedDragXPx = 20f + 45f,
                deadzonePx = 20f,
                stepPx = 15f,
            ),
        )
        // 向左跨过死区 12 步 -> 0.8x（支持 < 1.0x 临时慢放）
        assertEquals(
            0.8f,
            resolveLongPressContinuousSlideSpeed(
                initialSpeed = 2.0f,
                accumulatedDragXPx = -(20f + 180f),
                deadzonePx = 20f,
                stepPx = 15f,
            ),
        )
        // 上下限钳制在 0.5x ~ 5.0x
        assertEquals(
            5.0f,
            resolveLongPressContinuousSlideSpeed(
                initialSpeed = 2.0f,
                accumulatedDragXPx = 1000f,
                deadzonePx = 20f,
                stepPx = 15f,
            ),
        )
        assertEquals(
            0.5f,
            resolveLongPressContinuousSlideSpeed(
                initialSpeed = 2.0f,
                accumulatedDragXPx = -1000f,
                deadzonePx = 20f,
                stepPx = 15f,
            ),
        )
        assertTrue(isLongPressSpeedAnchorTick(2.0f))
        assertTrue(isLongPressSpeedAnchorTick(0.5f))
        assertFalse(isLongPressSpeedAnchorTick(2.3f))
        assertEquals("2.0x", formatLongPressSpeedLabel(2.0f))
        assertEquals("1.25x", formatLongPressSpeedLabel(1.25f))
        assertEquals("0.8x", formatLongPressSpeedLabel(0.8000001f))
        assertEquals(LongPressSpeedSlideMode.PRESETS, LongPressSpeedSlideMode.fromStorageValue(null, legacyEnabled = true))
        assertEquals(LongPressSpeedSlideMode.OFF, LongPressSpeedSlideMode.fromStorageValue(null, legacyEnabled = false))
        assertEquals(
            LongPressSpeedSlideMode.CONTINUOUS,
            LongPressSpeedSlideMode.fromStorageValue("continuous", legacyEnabled = false),
        )
    }

    @Test
    fun `end screen countdown triggers only near end of non-short video and skips current bvid`() {
        val next = resolveEndScreenNextVideo(
            currentBvid = "BV1current",
            relatedVideos = listOf(
                RelatedVideo(bvid = "BV1current", title = "self"),
                RelatedVideo(bvid = "BV1next", title = "next"),
            ),
        )
        assertEquals("BV1next", next?.bvid)
        // 短视频（< 30s）不弹出倒计时
        assertNull(resolveEndScreenCountdownSeconds(positionMs = 18_000L, durationMs = 20_000L))
        // 未进入最后 8 秒不弹出
        assertNull(resolveEndScreenCountdownSeconds(positionMs = 50_000L, durationMs = 60_000L))
        // 最后 5.2 秒向上取整为 6 秒
        assertEquals(6, resolveEndScreenCountdownSeconds(positionMs = 54_800L, durationMs = 60_000L))
        assertTrue(
            shouldShowEndScreenNextCard(
                enabled = true,
                isInPipMode = false,
                isScreenLocked = false,
                dismissed = false,
                nextVideo = next,
                countdownSeconds = 6,
            )
        )
        assertFalse(
            shouldShowEndScreenNextCard(
                enabled = false,
                isInPipMode = false,
                isScreenLocked = false,
                dismissed = false,
                nextVideo = next,
                countdownSeconds = 6,
            )
        )
        // 单集循环/播完暂停或存在下一分 P 时不弹片尾推荐卡片
        assertFalse(
            shouldShowEndScreenNextCard(
                enabled = true,
                isInPipMode = false,
                isScreenLocked = false,
                dismissed = false,
                nextVideo = next,
                countdownSeconds = 6,
                allowAutoNext = false,
            )
        )
        assertFalse(
            shouldShowEndScreenNextCard(
                enabled = true,
                isInPipMode = false,
                isScreenLocked = false,
                dismissed = false,
                nextVideo = next,
                countdownSeconds = 6,
                hasNextPage = true,
            )
        )
    }

    @Test
    fun `pinch zoom snaps to fill scale or resets micro zoom and formats restore badge`() {
        // 20:9 手机横屏播放 16:9 视频：铺满倍率为 (20/9)/(16/9) = 1.25f
        val fillScale = resolveFillZoomScale(
            containerWidth = 2400,
            containerHeight = 1080,
            videoWidth = 1920,
            videoHeight = 1080,
        )
        assertEquals(1.25f, fillScale, 0.01f)

        val snapped = resolvePinchZoomReleaseScale(currentScale = 1.20f, fillScale = fillScale, snapEnabled = true)
        assertTrue(snapped.snappedToFill)
        assertEquals(fillScale, snapped.scale)
        assertTrue(snapped.resetPan)

        val resetMicro = resolvePinchZoomReleaseScale(currentScale = 1.04f, fillScale = fillScale, snapEnabled = true)
        assertFalse(resetMicro.snappedToFill)
        assertEquals(1f, resetMicro.scale)
        assertTrue(resetMicro.resetPan)

        val freeZoom = resolvePinchZoomReleaseScale(currentScale = 2.4f, fillScale = fillScale, snapEnabled = true)
        assertFalse(freeZoom.snappedToFill)
        assertEquals(2.4f, freeZoom.scale)
        assertFalse(freeZoom.resetPan)

        val disabled = resolvePinchZoomReleaseScale(currentScale = 1.20f, fillScale = fillScale, snapEnabled = false)
        assertFalse(disabled.snappedToFill)
        assertEquals(1.20f, disabled.scale)

        assertEquals("2.4x · 还原", formatPinchZoomRestoreLabel(2.4f, showScaleBadge = true))
        assertEquals("还原画面", formatPinchZoomRestoreLabel(2.4f, showScaleBadge = false))
    }

    @Test
    fun `end screen prioritizes manually queued next item over related fallback`() {
        val queued = com.android.purebilibili.feature.video.player.PlaylistItem(
            bvid = "BV1queued",
            cid = 100L,
            title = "队列里的视频",
            cover = "https://example.com/q.jpg",
            owner = "UP",
            duration = 120L,
        )
        val resolved = resolveEndScreenNextVideo(
            currentBvid = "BV1current",
            relatedVideos = listOf(RelatedVideo(bvid = "BV1related", title = "推荐视频")),
            queuedNext = queued,
        )
        assertEquals("BV1queued", resolved?.bvid)
        assertEquals("队列里的视频", resolved?.title)
    }

    @Test
    fun `fullscreen drawer defaults to queue tab when only queued items exist`() {
        assertEquals(
            FullscreenDrawerTab.RELATED,
            resolveFullscreenDrawerInitialTab(hasRelatedVideos = true, hasQueuedItems = true),
        )
        assertEquals(
            FullscreenDrawerTab.QUEUE,
            resolveFullscreenDrawerInitialTab(hasRelatedVideos = false, hasQueuedItems = true),
        )
    }

    @Test
    fun `two finger chapter skip jumps to next chapter or restarts current then previous`() {
        val chapters = listOf(
            ViewPoint(content = "开场", from = 0, to = 30),
            ViewPoint(content = "核心体验", from = 30, to = 90),
            ViewPoint(content = "总结", from = 90, to = 150),
        )
        val next = resolveAdjacentChapterTarget(currentPositionMs = 45_000L, viewPoints = chapters, forward = true)
        assertNotNull(next)
        assertTrue(next.isNext)
        assertEquals(90_000L, next.positionMs)
        assertEquals("总结", next.title)

        // 在当前章播放超过 3 秒：先回到当前章起点
        val restartCurrent = resolveAdjacentChapterTarget(currentPositionMs = 45_000L, viewPoints = chapters, forward = false)
        assertNotNull(restartCurrent)
        assertFalse(restartCurrent.isNext)
        assertEquals(30_000L, restartCurrent.positionMs)

        // 刚进入当前章（<= 3 秒）：跳回上一章起点
        val prev = resolveAdjacentChapterTarget(currentPositionMs = 31_000L, viewPoints = chapters, forward = false)
        assertNotNull(prev)
        assertEquals(0L, prev.positionMs)
        assertEquals("开场", prev.title)
    }

    @Test
    fun `portrait swipe down minimize yields when reverse gesture or fullscreen is active`() {
        assertTrue(
            shouldTriggerPortraitSwipeDownMinimize(
                enabled = true,
                isFullscreen = false,
                isPortraitFullscreen = false,
                isInPipMode = false,
                reverseGesture = false,
                totalDragDistanceY = 200f,
                thresholdPx = 150f,
            )
        )
        // 开启全屏手势反向时让位给下拉全屏，避免冲突
        assertFalse(
            shouldTriggerPortraitSwipeDownMinimize(
                enabled = true,
                isFullscreen = false,
                isPortraitFullscreen = false,
                isInPipMode = false,
                reverseGesture = true,
                totalDragDistanceY = 200f,
                thresholdPx = 150f,
            )
        )
        assertFalse(
            shouldTriggerPortraitSwipeDownMinimize(
                enabled = false,
                isFullscreen = false,
                isPortraitFullscreen = false,
                isInPipMode = false,
                reverseGesture = false,
                totalDragDistanceY = 200f,
                thresholdPx = 150f,
            )
        )
    }
}
