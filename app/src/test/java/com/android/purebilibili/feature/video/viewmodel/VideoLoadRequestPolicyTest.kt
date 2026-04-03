package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.feature.video.playback.policy.PlaybackQualityMode
import com.android.purebilibili.feature.video.controller.QualityPermissionResult
import com.android.purebilibili.data.model.VideoLoadError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

class VideoLoadRequestPolicyTest {

    @Test
    fun `accepts result when request token and bvid both match current`() {
        assertTrue(
            shouldApplyVideoLoadResult(
                activeRequestToken = 5L,
                resultRequestToken = 5L,
                expectedBvid = "BV1abc",
                currentBvid = "BV1abc"
            )
        )
    }

    @Test
    fun `rejects stale result when token mismatches`() {
        assertFalse(
            shouldApplyVideoLoadResult(
                activeRequestToken = 6L,
                resultRequestToken = 5L,
                expectedBvid = "BV1abc",
                currentBvid = "BV1abc"
            )
        )
    }

    @Test
    fun `rejects stale result when current bvid already switched`() {
        assertFalse(
            shouldApplyVideoLoadResult(
                activeRequestToken = 7L,
                resultRequestToken = 7L,
                expectedBvid = "BV1old",
                currentBvid = "BV1new"
            )
        )
    }

    @Test
    fun `requested start position prefers local cache before route resume fallback`() {
        assertEquals(
            36_000L,
            resolveRequestedStartPositionMs(
                cachedPositionMs = 36_000L,
                fallbackResumePositionMs = 12_000L
            )
        )
    }

    @Test
    fun `requested start position falls back to route resume when local cache missing`() {
        assertEquals(
            12_000L,
            resolveRequestedStartPositionMs(
                cachedPositionMs = 0L,
                fallbackResumePositionMs = 12_000L
            )
        )
    }

    @Test
    fun `requested start position clamps invalid inputs to zero`() {
        assertEquals(
            0L,
            resolveRequestedStartPositionMs(
                cachedPositionMs = -1L,
                fallbackResumePositionMs = -20L
            )
        )
    }

    @Test
    fun `initial playback quality mode defaults to auto`() {
        assertEquals(
            PlaybackQualityMode.AUTO,
            resolveInitialPlaybackQualityMode()
        )
    }

    @Test
    fun `manual quality selection locks positive quality ids`() {
        assertEquals(
            PlaybackQualityMode.LOCKED(80),
            resolvePlaybackQualityModeForQualitySelection(80)
        )
    }

    @Test
    fun `manual auto selection clears quality lock`() {
        assertEquals(
            PlaybackQualityMode.AUTO,
            resolvePlaybackQualityModeForQualitySelection(-1)
        )
    }

    @Test
    fun `quality switch failure message explains permission denial`() {
        val message = resolveQualitySwitchFailureMessage(
            requestedQualityLabel = "1080P60",
            permissionResult = QualityPermissionResult.RequiresVip("1080P60")
        )

        assertContains(message, "需要大会员")
    }

    @Test
    fun `quality switch failure message explains network style load error`() {
        val message = resolveQualitySwitchFailureMessage(
            requestedQualityLabel = "1080P",
            loadError = VideoLoadError.Timeout
        )

        assertContains(message, "超时")
    }

    @Test
    fun `quality switch failure message explains app api cooldown for premium quality`() {
        val message = resolveQualitySwitchFailureMessage(
            requestedQualityLabel = "1080P60",
            hasCachedDashTracks = true,
            cacheContainsRequestedQuality = false,
            qualityRefetchCooldownRemainingMs = 95_000L
        )

        assertContains(message, "接口风控")
        assertContains(message, "1 分 35 秒")
    }

    @Test
    fun `premium quality switch is blocked when cooldown active and cache misses target`() {
        assertTrue(
            shouldBlockPremiumQualitySwitchDuringCooldown(
                requestedQualityId = 116,
                cacheContainsRequestedQuality = false,
                appApiCooldownRemainingMs = 20_000L
            )
        )
        assertFalse(
            shouldBlockPremiumQualitySwitchDuringCooldown(
                requestedQualityId = 80,
                cacheContainsRequestedQuality = false,
                appApiCooldownRemainingMs = 20_000L
            )
        )
        assertFalse(
            shouldBlockPremiumQualitySwitchDuringCooldown(
                requestedQualityId = 116,
                cacheContainsRequestedQuality = true,
                appApiCooldownRemainingMs = 20_000L
            )
        )
    }

    @Test
    fun `effective av1 support is disabled when current session has blocked av1`() {
        assertFalse(
            resolveEffectiveAv1Support(
                deviceSupportsAv1 = true,
                sessionBlockedCodecs = setOf("av01")
            )
        )
    }

    @Test
    fun `effective codec preference rewrites av1 setting to avc when current session has blocked av1`() {
        assertEquals(
            "avc1",
            resolveEffectiveVideoCodecPreference(
                requestCodecOverride = null,
                settingsCodecPreference = "av01",
                sessionBlockedCodecs = setOf("av01")
            )
        )
    }

    @Test
    fun `explicit request codec override wins over session codec block fallback`() {
        assertEquals(
            "hev1",
            resolveEffectiveVideoCodecPreference(
                requestCodecOverride = "hev1",
                settingsCodecPreference = "av01",
                sessionBlockedCodecs = setOf("av01")
            )
        )
    }

    @Test
    fun `player info result requires token and exact video context`() {
        assertTrue(
            shouldApplyPlayerInfoResult(
                activeRequestToken = 11L,
                resultRequestToken = 11L,
                expectedBvid = "BV1ok",
                expectedCid = 2233L,
                currentBvid = "BV1ok",
                currentCid = 2233L
            )
        )

        assertFalse(
            shouldApplyPlayerInfoResult(
                activeRequestToken = 12L,
                resultRequestToken = 11L,
                expectedBvid = "BV1ok",
                expectedCid = 2233L,
                currentBvid = "BV1ok",
                currentCid = 2233L
            )
        )

        assertFalse(
            shouldApplyPlayerInfoResult(
                activeRequestToken = 11L,
                resultRequestToken = 11L,
                expectedBvid = "BV1old",
                expectedCid = 2233L,
                currentBvid = "BV1new",
                currentCid = 2233L
            )
        )

        assertFalse(
            shouldApplyPlayerInfoResult(
                activeRequestToken = 11L,
                resultRequestToken = 11L,
                expectedBvid = "BV1ok",
                expectedCid = 2233L,
                currentBvid = "BV1ok",
                currentCid = 3344L
            )
        )
    }

    @Test
    fun `subtitle load result requires subtitle token and exact bvid cid`() {
        assertTrue(
            shouldApplySubtitleLoadResult(
                activeSubtitleToken = 5L,
                resultSubtitleToken = 5L,
                expectedBvid = "BV1sub",
                expectedCid = 100L,
                currentBvid = "BV1sub",
                currentCid = 100L
            )
        )

        assertFalse(
            shouldApplySubtitleLoadResult(
                activeSubtitleToken = 6L,
                resultSubtitleToken = 5L,
                expectedBvid = "BV1sub",
                expectedCid = 100L,
                currentBvid = "BV1sub",
                currentCid = 100L
            )
        )

        assertFalse(
            shouldApplySubtitleLoadResult(
                activeSubtitleToken = 5L,
                resultSubtitleToken = 5L,
                expectedBvid = "BV1sub",
                expectedCid = 100L,
                currentBvid = "BV1other",
                currentCid = 100L
            )
        )

        assertFalse(
            shouldApplySubtitleLoadResult(
                activeSubtitleToken = 5L,
                resultSubtitleToken = 5L,
                expectedBvid = "BV1sub",
                expectedCid = 100L,
                currentBvid = "BV1sub",
                currentCid = 101L
            )
        )
    }

    @Test
    fun `subtitle binding key prefers id_str then id then fallback`() {
        assertEquals(
            "abc123|zh-Hans",
            buildSubtitleTrackBindingKey(
                subtitleId = 42L,
                subtitleIdStr = "abc123",
                languageCode = "zh-Hans"
            )
        )
        assertEquals(
            "42|en-US",
            buildSubtitleTrackBindingKey(
                subtitleId = 42L,
                subtitleIdStr = "",
                languageCode = "en-US"
            )
        )
        assertEquals(
            "no-id|unknown",
            buildSubtitleTrackBindingKey(
                subtitleId = 0L,
                subtitleIdStr = "",
                languageCode = ""
            )
        )
    }

    @Test
    fun `subtitle binding key distinguishes same language track by subtitle url path`() {
        val keyA = buildSubtitleTrackBindingKey(
            subtitleId = 0L,
            subtitleIdStr = "",
            languageCode = "zh-Hans",
            subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/track_a.json?auth_key=foo"
        )
        val keyB = buildSubtitleTrackBindingKey(
            subtitleId = 0L,
            subtitleIdStr = "",
            languageCode = "zh-Hans",
            subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/track_b.json?auth_key=bar"
        )

        assertFalse(keyA == keyB)
    }

    @Test
    fun `subtitle binding requires matching track key and language when provided`() {
        assertTrue(
            shouldApplySubtitleTrackBinding(
                expectedTrackKey = "123|zh-Hans",
                currentTrackKey = "123|zh-Hans",
                expectedLanguage = "zh-Hans",
                currentLanguage = "zh-Hans"
            )
        )
        assertFalse(
            shouldApplySubtitleTrackBinding(
                expectedTrackKey = "123|zh-Hans",
                currentTrackKey = "124|zh-Hans",
                expectedLanguage = "zh-Hans",
                currentLanguage = "zh-Hans"
            )
        )
        assertFalse(
            shouldApplySubtitleTrackBinding(
                expectedTrackKey = "123|zh-Hans",
                currentTrackKey = "123|zh-Hans",
                expectedLanguage = "zh-Hans",
                currentLanguage = "en-US"
            )
        )
    }

    @Test
    fun `subtitle binding mismatch reason distinguishes language and track`() {
        assertEquals(
            "language-mismatch expected=zh-Hans current=en-US",
            resolveSubtitleTrackBindingMismatchReason(
                expectedTrackKey = "123|zh-Hans",
                currentTrackKey = "123|zh-Hans",
                expectedLanguage = "zh-Hans",
                currentLanguage = "en-US"
            )
        )
        assertEquals(
            "track-key-mismatch expected=123|zh-Hans current=124|zh-Hans",
            resolveSubtitleTrackBindingMismatchReason(
                expectedTrackKey = "123|zh-Hans",
                currentTrackKey = "124|zh-Hans",
                expectedLanguage = "zh-Hans",
                currentLanguage = "zh-Hans"
            )
        )
        assertNull(
            resolveSubtitleTrackBindingMismatchReason(
                expectedTrackKey = "123|zh-Hans",
                currentTrackKey = "123|zh-Hans",
                expectedLanguage = "zh-Hans",
                currentLanguage = "zh-Hans"
            )
        )
    }

    @Test
    fun `subtitle refresh retry only for auth-like http failures`() {
        assertTrue(shouldRetrySubtitleLoadWithPlayerInfo("字幕请求失败: HTTP 403"))
        assertTrue(shouldRetrySubtitleLoadWithPlayerInfo("字幕请求失败: HTTP 410"))
        assertFalse(shouldRetrySubtitleLoadWithPlayerInfo("字幕请求失败: HTTP 500"))
        assertFalse(shouldRetrySubtitleLoadWithPlayerInfo(null))
    }

    @Test
    fun `treats request as same playback only when bvid and cid both match`() {
        assertTrue(
            shouldTreatAsSamePlaybackRequest(
                requestBvid = "BV1abc",
                requestCid = 1001L,
                currentBvid = "BV1abc",
                currentCid = 1001L,
                uiBvid = null,
                uiCid = 0L,
                miniPlayerBvid = null,
                miniPlayerCid = 0L,
                miniPlayerActive = false
            )
        )

        assertFalse(
            shouldTreatAsSamePlaybackRequest(
                requestBvid = "BV1abc",
                requestCid = 1002L,
                currentBvid = "BV1abc",
                currentCid = 1001L,
                uiBvid = null,
                uiCid = 0L,
                miniPlayerBvid = null,
                miniPlayerCid = 0L,
                miniPlayerActive = false
            )
        )
    }

    @Test
    fun `does not treat unknown currently playing bvid as same request`() {
        assertFalse(
            shouldTreatAsSamePlaybackRequest(
                requestBvid = "BV1abc",
                requestCid = 0L,
                currentBvid = "",
                currentCid = 0L,
                uiBvid = null,
                uiCid = 0L,
                miniPlayerBvid = null,
                miniPlayerCid = 0L,
                miniPlayerActive = false
            )
        )
    }

    @Test
    fun `does not treat unknown request cid as same playback even when bvid matches`() {
        assertFalse(
            shouldTreatAsSamePlaybackRequest(
                requestBvid = "BV1same",
                requestCid = 0L,
                currentBvid = "BV1same",
                currentCid = 445566L,
                uiBvid = null,
                uiCid = 0L,
                miniPlayerBvid = null,
                miniPlayerCid = 0L,
                miniPlayerActive = false
            )
        )
    }

    @Test
    fun `can recover same playback detection from active mini player metadata`() {
        assertTrue(
            shouldTreatAsSamePlaybackRequest(
                requestBvid = "BV1mini",
                requestCid = 3344L,
                currentBvid = "",
                currentCid = 0L,
                uiBvid = null,
                uiCid = 0L,
                miniPlayerBvid = "BV1mini",
                miniPlayerCid = 3344L,
                miniPlayerActive = true
            )
        )

        assertFalse(
            shouldTreatAsSamePlaybackRequest(
                requestBvid = "BV1mini",
                requestCid = 4455L,
                currentBvid = "",
                currentCid = 0L,
                uiBvid = null,
                uiCid = 0L,
                miniPlayerBvid = "BV1mini",
                miniPlayerCid = 3344L,
                miniPlayerActive = true
            )
        )
    }

    @Test
    fun `clearSubtitleFields removes all subtitle data from success state`() {
        val state = PlayerUiState.Success(
            info = com.android.purebilibili.data.model.response.ViewInfo(
                bvid = "BV1test",
                cid = 2233L
            ),
            playUrl = "https://example.com/video.mp4",
            subtitleEnabled = true,
            subtitlePrimaryLanguage = "zh-CN",
            subtitleSecondaryLanguage = "en-US",
            subtitlePrimaryCues = listOf(
                com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                    startMs = 0L,
                    endMs = 1000L,
                    content = "你好"
                )
            ),
            subtitleSecondaryCues = listOf(
                com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                    startMs = 0L,
                    endMs = 1000L,
                    content = "hello"
                )
            )
        )

        val cleared = clearSubtitleFields(state)
        assertFalse(cleared.subtitleEnabled)
        assertNull(cleared.subtitlePrimaryLanguage)
        assertNull(cleared.subtitleSecondaryLanguage)
        assertNull(cleared.subtitlePrimaryTrackKey)
        assertNull(cleared.subtitleSecondaryTrackKey)
        assertTrue(cleared.subtitlePrimaryCues.isEmpty())
        assertTrue(cleared.subtitleSecondaryCues.isEmpty())
    }

    @Test
    fun `clearTransientPlaybackPreviewData removes stale videoshot data when switching playback target`() {
        val state = PlayerUiState.Success(
            info = com.android.purebilibili.data.model.response.ViewInfo(
                bvid = "BV1test",
                cid = 2233L
            ),
            playUrl = "https://example.com/video.mp4",
            videoshotData = com.android.purebilibili.data.model.response.VideoshotData(
                image = listOf("https://example.com/p1.jpg"),
                index = listOf(0L, 1000L)
            )
        )

        val cleared = clearTransientPlaybackPreviewData(state)

        assertNull(cleared.videoshotData)
    }

    @Test
    fun `shouldApplyVideoshotResult only accepts videoshot matching current playback target`() {
        val current = PlayerUiState.Success(
            info = com.android.purebilibili.data.model.response.ViewInfo(
                bvid = "BV1test",
                cid = 4455L
            ),
            playUrl = "https://example.com/video.mp4"
        )

        assertTrue(
            shouldApplyVideoshotResult(
                currentState = current,
                videoshotBvid = "BV1test",
                videoshotCid = 4455L
            )
        )
        assertFalse(
            shouldApplyVideoshotResult(
                currentState = current,
                videoshotBvid = "BV1test",
                videoshotCid = 2233L
            )
        )
        assertFalse(
            shouldApplyVideoshotResult(
                currentState = current,
                videoshotBvid = "BV_other",
                videoshotCid = 4455L
            )
        )
    }

    @Test
    fun `clearTransientPlaybackPreviewData keeps same instance when no videoshot exists`() {
        val state = PlayerUiState.Success(
            info = com.android.purebilibili.data.model.response.ViewInfo(
                bvid = "BV1test",
                cid = 2233L
            ),
            playUrl = "https://example.com/video.mp4"
        )

        val cleared = clearTransientPlaybackPreviewData(state)

        assertSame(state, cleared)
    }

    @Test
    fun `subtitle decision promotes secondary when primary is low quality sparse track`() {
        val primary = listOf(
            com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                startMs = 0L,
                endMs = 27_000L,
                content = "敲重点 ↓↓↓敲重点 投降 包村 拥 威信 扫"
            )
        )
        val secondary = (1..20).map { i ->
            com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                startMs = i * 1000L,
                endMs = i * 1000L + 800L,
                content = "line-$i"
            )
        }

        val decision = resolveSubtitleTrackLoadDecision(
            primaryLanguage = "zh-Hans",
            primaryCues = primary,
            secondaryLanguage = "ai-zh",
            secondaryCues = secondary
        )

        assertEquals("ai-zh", decision.primaryLanguage)
        assertNull(decision.secondaryLanguage)
        assertEquals(secondary.size, decision.primaryCues.size)
        assertTrue(decision.secondaryCues.isEmpty())
    }

    @Test
    fun `subtitle decision keeps bilingual when both tracks look healthy`() {
        val primary = (1..12).map { i ->
            com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                startMs = i * 1200L,
                endMs = i * 1200L + 900L,
                content = "zh-$i"
            )
        }
        val secondary = (1..12).map { i ->
            com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                startMs = i * 1200L,
                endMs = i * 1200L + 900L,
                content = "en-$i"
            )
        }

        val decision = resolveSubtitleTrackLoadDecision(
            primaryLanguage = "zh-Hans",
            primaryCues = primary,
            secondaryLanguage = "en-US",
            secondaryCues = secondary
        )

        assertEquals("zh-Hans", decision.primaryLanguage)
        assertEquals("en-US", decision.secondaryLanguage)
        assertEquals(primary.size, decision.primaryCues.size)
        assertEquals(secondary.size, decision.secondaryCues.size)
    }

    @Test
    fun `subtitle decision removes low quality secondary track`() {
        val primary = (1..14).map { i ->
            com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                startMs = i * 1000L,
                endMs = i * 1000L + 700L,
                content = "zh-$i"
            )
        }
        val secondary = listOf(
            com.android.purebilibili.feature.video.subtitle.SubtitleCue(
                startMs = 0L,
                endMs = 30_000L,
                content = "广告联系方式"
            )
        )

        val decision = resolveSubtitleTrackLoadDecision(
            primaryLanguage = "zh-Hans",
            primaryCues = primary,
            secondaryLanguage = "ai-zh",
            secondaryCues = secondary
        )

        assertEquals("zh-Hans", decision.primaryLanguage)
        assertNull(decision.secondaryLanguage)
        assertEquals(primary.size, decision.primaryCues.size)
        assertTrue(decision.secondaryCues.isEmpty())
    }
}
