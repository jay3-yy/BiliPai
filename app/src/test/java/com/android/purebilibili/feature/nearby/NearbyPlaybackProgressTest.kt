package com.android.purebilibili.feature.nearby

import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbyPlaybackProgressTest {
    private val video = NearbyVideo("BV1xx411c7mD", 123, 10_000, 2f, "视频", "")

    @Test fun preparationCanBufferOrAdvanceAtTheTransferredSpeed() {
        assertTrue(nearbyPlaybackPositionMatches(video, 10_000, 5_000))
        assertTrue(nearbyPlaybackPositionMatches(video, 20_000, 5_000))
        assertFalse(nearbyPlaybackPositionMatches(video, 50_000, 5_000))
        assertFalse(nearbyPlaybackPositionMatches(video, 1_000, 5_000))
    }

    @Test fun zeroProgressAndClockBoundsDoNotPermitNegativePositions() {
        assertTrue(nearbyPlaybackPositionMatches(video.copy(positionMs = 0), 0, 0))
        assertFalse(nearbyPlaybackPositionMatches(video.copy(positionMs = 0), -1, 0))
        assertTrue(nearbyPlaybackPositionMatches(video, 10_000, -1))
        assertFalse(nearbyPlaybackPositionMatches(video, 20_000, -1))
    }

    @Test fun livePlaybackMatchesWithoutSeekPositionCheck() {
        val live = NearbyVideo(mediaType = NEARBY_MEDIA_LIVE, roomId = 9988L, title = "直播间")
        assertTrue(nearbyPlaybackPositionMatches(live, 0L, 10_000L))
        assertEquals(0L, estimateNearbyPlaybackPositionMs(live, 50_000L))
    }

    @Test fun estimatedPositionAdvancesOnlyWhilePlayingAndClampsWindow() {
        val playing = video.copy(positionMs = 10_000L, speed = 1.5f, playing = true, updatedAtMs = 1_000L)
        assertEquals(25_000L, estimateNearbyPlaybackPositionMs(playing, 11_000L))
        val paused = playing.copy(playing = false)
        assertEquals(10_000L, estimateNearbyPlaybackPositionMs(paused, 11_000L))
        assertEquals("01:05", formatNearbyDuration(65_000L))
        assertEquals("1:01:05", formatNearbyDuration(3_665_000L))
    }

    @Test fun autoConnectDirectionIsAsymmetricToPreventSimultaneousCollision() {
        val a = "0".repeat(64)
        val b = "f".repeat(64)
        assertTrue(shouldInitiateNearbyAutoConnect(a, b))
        assertFalse(shouldInitiateNearbyAutoConnect(b, a))
        assertFalse(shouldInitiateNearbyAutoConnect(a, a))
    }

    @Test fun bannerSelectionSkipsSameContentAndDismissedItemsAndPrefersPlaying() {
        val phone = NearbyPeer("a".repeat(32), "手机", "a".repeat(64), "192.168.1.2", NEARBY_PORT, NEARBY_DEVICE_PHONE)
        val tablet = NearbyPeer("b".repeat(32), "平板", "b".repeat(64), "192.168.1.3", NEARBY_PORT, NEARBY_DEVICE_TABLET)
        val pausedVideo = video.copy(bvid = "BV1xx411c7mA", cid = 1L, playing = false)
        val playingBangumi = NearbyVideo(
            mediaType = NEARBY_MEDIA_BANGUMI,
            seasonId = 100L,
            epId = 200L,
            title = "番剧",
            playing = true,
        )
        val remote = mapOf(
            phone.pin to NearbyRemotePlayback(phone, pausedVideo, receivedAtMs = 2_000L),
            tablet.pin to NearbyRemotePlayback(tablet, playingBangumi, receivedAtMs = 1_000L),
        )
        assertEquals(
            tablet.pin,
            selectNearbyBannerCandidate(remote, localMedia = null, dismissedKeys = emptySet())?.peer?.pin,
        )
        assertEquals(
            phone.pin,
            selectNearbyBannerCandidate(remote, localMedia = playingBangumi, dismissedKeys = emptySet())?.peer?.pin,
        )
        val dismissed = setOf(
            remoteBannerDismissKey(phone.pin, pausedVideo),
            remoteBannerDismissKey(tablet.pin, playingBangumi),
        )
        assertNull(selectNearbyBannerCandidate(remote, localMedia = null, dismissedKeys = dismissed))
    }

    @Test fun flightTransformAlignsOutgoingAndIncomingAndRespectsReduceMotion() {
        val outgoingStart = resolveNearbyFlightTransform(progress = 0f, incoming = false, travelPx = 500f, reduceMotion = false)
        val outgoingLiftPeak = resolveNearbyFlightTransform(progress = 0.32f, incoming = false, travelPx = 500f, reduceMotion = false)
        val outgoingEnd = resolveNearbyFlightTransform(progress = 1f, incoming = false, travelPx = 500f, reduceMotion = false)
        assertEquals(0f, outgoingStart.translationYPx)
        assertTrue(outgoingLiftPeak.scale > 1f)
        assertTrue(outgoingLiftPeak.haloAlpha > 0f)
        assertTrue(outgoingLiftPeak.scrimAlpha > 0f)
        assertEquals(-500f, outgoingEnd.translationYPx)
        assertEquals(0f, outgoingEnd.alpha)
        assertEquals(0f, outgoingEnd.scrimAlpha)

        val incomingStart = resolveNearbyFlightTransform(progress = 0f, incoming = true, travelPx = 500f, reduceMotion = false)
        val incomingHold = resolveNearbyFlightTransform(progress = 0.70f, incoming = true, travelPx = 500f, reduceMotion = false)
        val incomingEnd = resolveNearbyFlightTransform(progress = 1f, incoming = true, travelPx = 500f, reduceMotion = false)
        assertEquals(-500f, incomingStart.translationYPx)
        assertTrue(incomingStart.blurFraction > 0f)
        assertTrue(incomingStart.rotationXDeg < 0f)
        assertEquals(0f, incomingHold.translationYPx)
        assertEquals(1f, incomingHold.scale)
        assertEquals(1f, incomingHold.alpha)
        assertEquals(0f, incomingHold.blurFraction)
        assertTrue(incomingHold.backdropBlurFraction > 0f)
        assertTrue(incomingHold.ambientGlowAlpha > 0f)
        assertEquals(0f, incomingEnd.alpha)
        assertEquals(0f, incomingEnd.scrimAlpha)
        assertEquals(0f, incomingEnd.backdropBlurFraction)

        val reduced = resolveNearbyFlightTransform(progress = 0.5f, incoming = false, travelPx = 500f, reduceMotion = true)
        assertEquals(0f, reduced.translationYPx)
        assertEquals(1f, reduced.scale)
        assertEquals(0f, reduced.blurFraction)
        assertEquals(0f, reduced.scrimAlpha)
        assertEquals(0f, reduced.backdropBlurFraction)
        assertEquals(0f, reduced.haloAlpha)
    }

    @Test fun stageLaunchContinuesFromGesturePositionIntoTopTarget() {
        val start = resolveNearbyStageLaunchFrame(
            launchProgress = 0f,
            startY = -120f,
            startScale = 0.88f,
            targetY = -480f,
            reduceMotion = false,
            startRotationXDeg = 4f,
        )
        val impact = resolveNearbyStageLaunchFrame(
            launchProgress = 0.75f,
            startY = -120f,
            startScale = 0.88f,
            targetY = -480f,
            reduceMotion = false,
            startRotationXDeg = 4f,
        )
        val end = resolveNearbyStageLaunchFrame(
            launchProgress = 1f,
            startY = -120f,
            startScale = 0.88f,
            targetY = -480f,
            reduceMotion = false,
            startRotationXDeg = 4f,
        )
        assertEquals(-120f, start.translationYPx)
        assertEquals(0.88f, start.scale)
        assertEquals(4f, start.rotationXDeg)
        assertEquals(1f, start.alpha)
        assertEquals(1f, start.backdropBlurFraction)
        assertTrue(impact.targetPillImpactScale < 1f)
        assertEquals(-480f, end.translationYPx)
        assertEquals(0.24f, end.scale)
        assertEquals(16f, end.rotationXDeg)
        assertEquals(0f, end.alpha)
        assertEquals(0f, end.scrimAlpha)
        assertEquals(0f, end.backdropBlurFraction)

        val reduced = resolveNearbyStageLaunchFrame(
            launchProgress = 0.5f,
            startY = -120f,
            startScale = 0.88f,
            targetY = -480f,
            reduceMotion = true,
        )
        assertEquals(0f, reduced.translationYPx)
        assertEquals(1f, reduced.scale)
        assertEquals(0f, reduced.backdropBlurFraction)
        assertEquals(0.5f, reduced.alpha)
    }

    @Test fun rubberBandAndFlingPhysicsRespondSmoothlyToDragAndVelocity() {
        // 柔和区内 1:1 线性跟手，超过柔和区后平滑渐进阻尼且永不越界
        assertEquals(-50f, applyNearbyRubberBand(-50f, limitPx = 200f))
        val over = applyNearbyRubberBand(-400f, limitPx = 200f)
        assertTrue(over < -124f && over > -200f)

        // 快甩即使未到距离阈值，只要位移过最小起手距离即可触发发送
        assertTrue(
            shouldTriggerNearbyFlingSend(
                dragYPx = -40f,
                velocityYPxPerSec = -1_200f,
                distanceThresholdPx = 100f,
                velocityThresholdPxPerSec = 750f,
                minFlingDistancePx = 28f,
            )
        )
        assertFalse(
            shouldTriggerNearbyFlingSend(
                dragYPx = -12f,
                velocityYPxPerSec = -1_200f,
                distanceThresholdPx = 100f,
                velocityThresholdPxPerSec = 750f,
                minFlingDistancePx = 28f,
            )
        )
        assertTrue(resolveNearbyLaunchDurationMs(-2_400f, fromButton = false) < resolveNearbyLaunchDurationMs(-200f, fromButton = false))
    }

    @Test fun directStageShortcutActivatesOnlyWithSingleConnectedTrustedPeerAndMedia() {
        val phone = NearbyPeer("a".repeat(32), "手机", "a".repeat(64), "192.168.1.2", NEARBY_PORT, NEARBY_DEVICE_PHONE)
        val tablet = NearbyPeer("b".repeat(32), "平板", "b".repeat(64), "192.168.1.3", NEARBY_PORT, NEARBY_DEVICE_TABLET)

        assertEquals(
            tablet,
            resolveDirectStageTargetPeer(
                enabled = true,
                peers = listOf(phone, tablet),
                connectedPins = setOf(tablet.pin),
                localMedia = video,
            )
        )
        assertNull(
            resolveDirectStageTargetPeer(
                enabled = true,
                peers = listOf(phone, tablet),
                connectedPins = setOf(phone.pin, tablet.pin),
                localMedia = video,
            )
        )
        assertNull(
            resolveDirectStageTargetPeer(
                enabled = true,
                peers = listOf(tablet),
                connectedPins = setOf(tablet.pin),
                localMedia = null,
            )
        )
        assertNull(
            resolveDirectStageTargetPeer(
                enabled = false,
                peers = listOf(tablet),
                connectedPins = setOf(tablet.pin),
                localMedia = video,
            )
        )
    }

    @Test fun sliceQueueContextCentersAroundCurrentItemAndSkipsSingleItem() {
        val single = listOf(
            PlaylistItem(
                bvid = video.bvid,
                cid = video.cid,
                title = "单集",
                cover = "",
                owner = "作者",
            )
        )
        assertNull(
            sliceNearbyQueueContext(
                playlist = single,
                currentIndex = 0,
                playMode = PlayMode.SEQUENTIAL,
                isExternal = false,
                externalSource = ExternalPlaylistSource.UNKNOWN,
                activeMedia = video,
            )
        )

        val longList = (0 until 36).map { idx ->
            PlaylistItem(
                bvid = video.bvid,
                cid = 100L + idx,
                title = "P${idx + 1}",
                cover = "http://i0.hdslb.com/$idx.jpg",
                owner = "作者",
                duration = 120L,
            )
        }
        val active = video.copy(cid = 110L)
        val sliced = sliceNearbyQueueContext(
            playlist = longList,
            currentIndex = 10,
            playMode = PlayMode.REPEAT_ONE,
            isExternal = true,
            externalSource = ExternalPlaylistSource.FAVORITE,
            activeMedia = active,
        )
        assertTrue(sliced != null)
        assertEquals(NEARBY_MAX_QUEUE_ITEMS, sliced.items.size)
        assertEquals(6, sliced.currentIndex)
        assertEquals(110L, sliced.items[sliced.currentIndex].cid)
        assertEquals("https://i0.hdslb.com/10.jpg", sliced.items[sliced.currentIndex].cover)
        assertEquals("REPEAT_ONE", sliced.playMode)
        assertTrue(sliced.isExternal)
        assertEquals("FAVORITE", sliced.externalSource)
    }

    @Test fun preferenceHintReportsOnlyRealDifferencesAndGatesQualitySwitch() {
        val remote = video.copy(qualityId = 120, qualityLabel = "4K", danmakuEnabled = false)
        val sameLocal = video.copy(qualityId = 120, qualityLabel = "4K", danmakuEnabled = false)

        // 完全一致时不弹出冗余提示
        assertNull(
            buildNearbyPreferenceHint(
                id = "h1",
                peerName = "客厅平板",
                source = remote,
                localActual = sameLocal,
                localDanmakuEnabled = false,
                availableQualityIds = listOf(120, 80, 64),
            )
        )

        // 画质与弹幕均不同，且本机支持 4K → 同时提供切换画质与同步关弹幕动作
        val bothDiff = buildNearbyPreferenceHint(
            id = "h2",
            peerName = "客厅平板",
            source = remote,
            localActual = video.copy(qualityId = 80, qualityLabel = "1080P", danmakuEnabled = true),
            localDanmakuEnabled = true,
            availableQualityIds = listOf(120, 80, 64),
        )
        assertTrue(bothDiff != null)
        assertEquals("客厅平板 为 4K · 弹幕关", bothDiff.headline)
        assertEquals("本机当前 1080P · 弹幕开 · 已同步 2x", bothDiff.detail)
        assertEquals(false, bothDiff.targetDanmakuEnabled)
        assertEquals(120, bothDiff.targetQualityId)
        assertEquals("4K", bothDiff.targetQualityLabel)

        // 本机无 4K 权限时（availableQualityIds 不含 120）仅提示不提供无效切换按钮
        val nonVipQualityDiff = buildNearbyPreferenceHint(
            id = "h3",
            peerName = "客厅平板",
            source = remote.copy(danmakuEnabled = true),
            localActual = video.copy(qualityId = 80, qualityLabel = "1080P", danmakuEnabled = true),
            localDanmakuEnabled = true,
            availableQualityIds = listOf(80, 64, 32),
        )
        assertTrue(nonVipQualityDiff != null)
        assertEquals("客厅平板 为 4K", nonVipQualityDiff.headline)
        assertEquals("本机当前 1080P · 已同步 2x", nonVipQualityDiff.detail)
        assertNull(nonVipQualityDiff.targetDanmakuEnabled)
        assertNull(nonVipQualityDiff.targetQualityId)

        // 杜比/Hi-Res 音质与多语言音轨差异时提供一键切换，并附带倍速同步确认
        val audioDiff = buildNearbyPreferenceHint(
            id = "h4",
            peerName = "平板",
            source = video.copy(
                speed = 1.5f,
                qualityId = 80,
                qualityLabel = "1080P",
                danmakuEnabled = true,
                audioQualityId = 30250,
                audioQualityLabel = "杜比全景声",
                audioLang = "en",
                audioLangLabel = "AI 英语",
            ),
            localActual = video.copy(
                speed = 1.5f,
                qualityId = 80,
                qualityLabel = "1080P",
                danmakuEnabled = true,
                audioQualityId = 30280,
                audioQualityLabel = "192K",
                audioLang = "",
            ),
            localDanmakuEnabled = true,
            availableQualityIds = listOf(80, 64),
            availableAudioQualityIds = listOf(30250, 30280),
            availableAudioLangs = listOf("en", "ja"),
        )
        assertTrue(audioDiff != null)
        assertEquals("平板 为 杜比全景声 · AI 英语", audioDiff.headline)
        assertEquals("本机当前 192K · 原声 · 已同步 1.5x", audioDiff.detail)
        assertEquals(30250, audioDiff.targetAudioQualityId)
        assertEquals("杜比全景声", audioDiff.targetAudioQualityLabel)
        assertEquals("en", audioDiff.targetAudioLang)
        assertEquals("AI 英语", audioDiff.targetAudioLangLabel)
    }

    @Test fun audioOnlyNavigationRoutePreservesResumePositionAndVideoModeUsesHandoffRoute() {
        val audioRoute = NearbyPlaybackBridge.toNavigationRoute(
            video.copy(audioOnly = true, positionMs = 42_500L)
        )
        assertEquals("audio_mode?bvid=BV1xx411c7mD&cid=123&resumePositionMs=42500", audioRoute)

        val videoRoute = NearbyPlaybackBridge.toNavigationRoute(
            video.copy(audioOnly = false, positionMs = 42_500L)
        )
        assertTrue(!videoRoute.startsWith("audio_mode"))
    }
}
