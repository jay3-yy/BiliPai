package com.android.purebilibili.feature.video.subtitle

import com.android.purebilibili.data.model.response.PlayerInfoResponse
import com.android.purebilibili.data.model.response.SubtitleItem
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiliSubtitlePolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun normalizeBilibiliSubtitleUrl_supportsProtocolRelativeUrl() {
        val normalized = normalizeBilibiliSubtitleUrl("//aisubtitle.hdslb.com/bfs/subtitle/abc.json")

        assertEquals(
            "https://aisubtitle.hdslb.com/bfs/subtitle/abc.json",
            normalized
        )
    }

    @Test
    fun isTrustedBilibiliSubtitleUrl_allowsBilibiliSubtitleHostOnly() {
        assertTrue(
            isTrustedBilibiliSubtitleUrl("//aisubtitle.hdslb.com/bfs/subtitle/abc.json")
        )
        assertFalse(
            isTrustedBilibiliSubtitleUrl("https://example.com/bfs/subtitle/abc.json")
        )
    }

    @Test
    fun orderSubtitleTracksByPreference_prefersNonAiTrackForSameLanguage() {
        val tracks = listOf(
            SubtitleTrackMeta(
                id = 1L,
                lan = "zh-Hans",
                lanDoc = "中文（自动生成）",
                subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/ai.json",
                aiStatus = 1,
                aiType = 1
            ),
            SubtitleTrackMeta(
                id = 2L,
                lan = "zh-Hans",
                lanDoc = "中文（简体）",
                subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/official.json",
                aiStatus = 0,
                aiType = 0
            )
        )

        val ordered = orderSubtitleTracksByPreference(tracks)
        assertEquals(2L, ordered.first().id)
        assertFalse(isLikelyAiSubtitleTrack(ordered.first()))
        assertTrue(isLikelyAiSubtitleTrack(ordered.last()))
    }

    @Test
    fun parseBiliSubtitleBody_parsesAndSortsSubtitleBody() {
        val rawJson = """
            {
              "body": [
                { "from": 2.0, "to": 3.0, "content": "world" },
                { "from": 0.5, "to": 1.5, "content": "hello" }
              ]
            }
        """.trimIndent()

        val cues = parseBiliSubtitleBody(rawJson)

        assertEquals(2, cues.size)
        assertEquals("hello", cues[0].content)
        assertEquals(500L, cues[0].startMs)
        assertEquals(1500L, cues[0].endMs)
        assertEquals("world", cues[1].content)
        assertEquals(2000L, cues[1].startMs)
    }

    @Test
    fun playerInfoResponse_decodesApiSubtitleContract() {
        val response = json.decodeFromString<PlayerInfoResponse>(
            """
            {
              "code": 0,
              "message": "0",
              "data": {
                "bvid": "BV1xx",
                "cid": 123,
                "need_login_subtitle": false,
                "subtitle": {
                  "allow_submit": true,
                  "lan": "",
                  "lan_doc": "",
                  "subtitles": [
                    {
                      "id": 11,
                      "id_str": "subtitle-11",
                      "lan": "zh-Hans",
                      "lan_doc": "中文（简体）",
                      "subtitle_url": "//aisubtitle.hdslb.com/bfs/subtitle/track.json?auth_key=abc",
                      "ai_status": 0,
                      "ai_type": 0,
                      "is_lock": false,
                      "type": 0
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val subtitle = response.data?.subtitle?.subtitles?.single()
        assertEquals("zh-Hans", subtitle?.lan)
        assertEquals("中文（简体）", subtitle?.lanDoc)
        assertEquals("//aisubtitle.hdslb.com/bfs/subtitle/track.json?auth_key=abc", subtitle?.subtitleUrl)
    }

    @Test
    fun mapPlayerInfoSubtitleTracks_filtersUntrustedAndBuildsStableTrackKeys() {
        val tracks = mapPlayerInfoSubtitleTracks(
            listOf(
                SubtitleItem(
                    id = 2L,
                    idStr = "official",
                    lan = "zh-Hans",
                    lanDoc = "中文（简体）",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/official.json",
                    aiStatus = 0,
                    aiType = 0
                ),
                SubtitleItem(
                    id = 1L,
                    idStr = "ai",
                    lan = "zh-Hans",
                    lanDoc = "中文（自动生成）",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/ai.json",
                    aiStatus = 1,
                    aiType = 1
                ),
                SubtitleItem(
                    id = 3L,
                    idStr = "bad",
                    lan = "en-US",
                    lanDoc = "英语",
                    subtitleUrl = "https://example.com/bfs/subtitle/bad.json"
                )
            )
        )

        assertEquals(2, tracks.size)
        assertEquals("official", tracks.first().idStr)
        assertEquals(
            "2|official|zh-Hans|https://aisubtitle.hdslb.com/bfs/subtitle/official.json",
            tracks.first().trackKey
        )
    }

    @Test
    fun resolveDefaultSubtitleLanguages_prefersChineseAndEnglish() {
        val tracks = listOf(
            SubtitleTrackMeta(lan = "ja-JP", lanDoc = "日语", subtitleUrl = "https://a"),
            SubtitleTrackMeta(lan = "en-US", lanDoc = "英语", subtitleUrl = "https://b"),
            SubtitleTrackMeta(lan = "zh-Hans", lanDoc = "中文（简体）", subtitleUrl = "https://c")
        )

        val selection = resolveDefaultSubtitleLanguages(tracks)

        assertEquals("zh-Hans", selection.primaryLanguage)
        assertEquals("en-US", selection.secondaryLanguage)
    }

    @Test
    fun resolveDefaultSubtitleLanguages_keepsChineseAheadOfPlayerInfoLanguage() {
        val tracks = listOf(
            SubtitleTrackMeta(lan = "zh-Hans", lanDoc = "中文（简体）", subtitleUrl = "https://a"),
            SubtitleTrackMeta(lan = "ja-JP", lanDoc = "日语", subtitleUrl = "https://b"),
            SubtitleTrackMeta(lan = "en-US", lanDoc = "英语", subtitleUrl = "https://c")
        )

        val selection = resolveDefaultSubtitleLanguages(
            tracks = tracks,
            preferredPrimaryLanguage = "ja"
        )

        assertEquals("zh-Hans", selection.primaryLanguage)
        assertEquals("en-US", selection.secondaryLanguage)
    }

    @Test
    fun resolveDefaultSubtitleLanguages_usesPlayerInfoLanguageOnlyWhenChineseMissing() {
        val tracks = listOf(
            SubtitleTrackMeta(lan = "ar-SA", lanDoc = "阿拉伯语", subtitleUrl = "https://a"),
            SubtitleTrackMeta(lan = "ja-JP", lanDoc = "日语", subtitleUrl = "https://b"),
            SubtitleTrackMeta(lan = "en-US", lanDoc = "英语", subtitleUrl = "https://c")
        )

        val selection = resolveDefaultSubtitleLanguages(
            tracks = tracks,
            preferredPrimaryLanguage = "ja"
        )

        assertEquals("ja-JP", selection.primaryLanguage)
        assertEquals("en-US", selection.secondaryLanguage)
    }

    @Test
    fun resolveDefaultSubtitleLanguages_keepsAiChineseAheadOfOtherLanguages() {
        val tracks = listOf(
            SubtitleTrackMeta(lan = "en-US", lanDoc = "英语", subtitleUrl = "https://a"),
            SubtitleTrackMeta(
                lan = "ai-zh",
                lanDoc = "中文（自动生成）",
                subtitleUrl = "https://b",
                aiStatus = 1
            )
        )

        val selection = resolveDefaultSubtitleLanguages(tracks)

        // AI 中文也算中文字幕，优先于其它语言
        assertEquals("ai-zh", selection.primaryLanguage)
    }

    @Test
    fun resolveDefaultSubtitleLanguages_prefersOfficialChineseOverAiChinese() {
        val tracks = listOf(
            SubtitleTrackMeta(
                lan = "ai-zh",
                lanDoc = "中文（自动生成）",
                subtitleUrl = "https://a",
                aiStatus = 1
            ),
            SubtitleTrackMeta(lan = "zh-CN", lanDoc = "中文（中国）", subtitleUrl = "https://b")
        )

        val selection = resolveDefaultSubtitleLanguages(tracks)

        assertEquals("zh-CN", selection.primaryLanguage)
    }

    @Test
    fun mapPlayerInfoSubtitleTracks_keepsAllTrustedLanguages() {
        val tracks = mapPlayerInfoSubtitleTracks(
            listOf(
                SubtitleItem(
                    id = 1L,
                    lan = "ar-SA",
                    lanDoc = "阿拉伯语",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/ar.json"
                ),
                SubtitleItem(
                    id = 2L,
                    lan = "ja-JP",
                    lanDoc = "日语",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/ja.json"
                ),
                SubtitleItem(
                    id = 3L,
                    lan = "zh-Hans",
                    lanDoc = "中文（简体）",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/zh.json"
                ),
                SubtitleItem(
                    id = 4L,
                    lan = "en-US",
                    lanDoc = "英语",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/en.json"
                )
            )
        )

        assertEquals(
            setOf("ar-SA", "ja-JP", "zh-Hans", "en-US"),
            tracks.map { it.lan }.toSet()
        )
    }

    @Test
    fun buildSubtitleTrackOptions_marksSelectedTrackAndUsesLanguageDocs() {
        val tracks = listOf(
            SubtitleTrackMeta(
                id = 1L,
                lan = "ar-SA",
                lanDoc = "阿拉伯语",
                subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/ar.json"
            ),
            SubtitleTrackMeta(
                id = 2L,
                lan = "ja-JP",
                lanDoc = "日语",
                subtitleUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/ja.json",
                aiStatus = 1
            )
        )

        val options = buildSubtitleTrackOptions(
            tracks = tracks,
            selectedTrackKey = tracks.last().trackKey
        )

        assertEquals(listOf("阿拉伯语", "日语 · AI"), options.map { it.label })
        assertFalse(options.first().selected)
        assertTrue(options.last().selected)
        assertTrue(options.last().likelyAi)
    }

    @Test
    fun resolveSubtitleTextAt_returnsMatchingCue() {
        val cues = listOf(
            SubtitleCue(startMs = 0L, endMs = 900L, content = "A"),
            SubtitleCue(startMs = 1000L, endMs = 1900L, content = "B")
        )

        assertEquals("B", resolveSubtitleTextAt(cues, 1200L))
        assertNull(resolveSubtitleTextAt(cues, 950L))
    }

    @Test
    fun subtitlePositionPollingIdentity_isStableOnBvidCidOnly() {
        assertEquals(
            "BV1_12",
            resolveSubtitlePositionPollingIdentity(bvid = "BV1", cid = 12L),
        )
        assertEquals(
            resolveSubtitlePositionPollingIdentity("BV1", 12L),
            resolveSubtitlePositionPollingIdentity(" BV1 ", 12L),
        )
        assertEquals("no-video", resolveSubtitlePositionPollingIdentity(null, 0L))
    }

    @Test
    fun subtitleOverlay_staysMountedWhenEnabledEvenIfCueBlank() {
        assertTrue(
            shouldKeepSubtitleOverlayMounted(
                overlayEnabled = true,
                isInPipMode = false,
                isAudioOnly = false,
                suppressOverlay = false,
            )
        )
        assertFalse(
            shouldKeepSubtitleOverlayMounted(
                overlayEnabled = true,
                isInPipMode = true,
                isAudioOnly = false,
                suppressOverlay = false,
            )
        )
    }

    @Test
    fun displayedSubtitleHoldUsesCueEndAndNeverDelaysTheNextCue() {
        val cues = listOf(
            SubtitleCue(1_000L, 2_000L, "第一句"),
            SubtitleCue(2_200L, 2_300L, "下一句")
        )
        val hold = com.android.purebilibili.core.subtitle.SUBTITLE_GAP_HOLD_MS
        assertNull(resolveSubtitleDisplayTextAt(cues, 999L))
        assertEquals("第一句", resolveSubtitleDisplayTextAt(cues, 2_100L))
        assertNull(resolveSubtitleTextAt(cues, 2_100L))
        assertEquals("下一句", resolveSubtitleDisplayTextAt(cues, 2_200L))
        assertEquals("下一句", resolveSubtitleDisplayTextAt(cues, 2_300L + hold))
        assertNull(resolveSubtitleDisplayTextAt(cues, 2_301L + hold))
    }

    @Test
    fun displayedSubtitleFollowsSeekTargetInsteadOfPreviouslyDisplayedText() {
        val cues = listOf(
            SubtitleCue(1_000L, 2_000L, "早期字幕"),
            SubtitleCue(10_000L, 11_000L, "后期字幕"),
            SubtitleCue(14_000L, 15_000L, "后续字幕")
        )
        assertEquals("后期字幕", resolveSubtitleDisplayTextAt(cues, 10_500L))
        assertNull(resolveSubtitleDisplayTextAt(cues, 12_000L))
        assertNull(resolveSubtitleDisplayTextAt(cues, 5_000L))
        assertEquals("早期字幕", resolveSubtitleDisplayTextAt(cues, 1_500L))
        assertNull(resolveSubtitleDisplayTextAt(cues, 2_500L))
        assertEquals("后续字幕", resolveSubtitleDisplayTextAt(cues, 14_500L))
        assertNull(resolveSubtitleDisplayTextAt(cues, 0L))
        assertNull(resolveSubtitleDisplayTextAt(cues, 4_111_000L))
    }

    @Test
    fun subtitleVerticalOffsetFraction_clampsToReadableFullscreenRange() {
        assertEquals(0.30f, normalizeSubtitleVerticalOffsetFraction(0.8f))
        assertEquals(-0.30f, normalizeSubtitleVerticalOffsetFraction(-0.8f))
        assertEquals(0.12f, normalizeSubtitleVerticalOffsetFraction(0.12f))
    }

    @Test
    fun subtitleTextSize_keepsExistingPhoneSizes() {
        assertEquals(
            SubtitleTextSizeSpec(primarySp = 16, secondarySp = 14),
            resolveSubtitleTextSizeSpec(playerWidthDp = 599, largeTextEnabled = false)
        )
        assertEquals(
            SubtitleTextSizeSpec(primarySp = 18, secondarySp = 16),
            resolveSubtitleTextSizeSpec(playerWidthDp = 599, largeTextEnabled = true)
        )
    }

    @Test
    fun subtitleTextSize_increasesForTabletViewingDistance() {
        assertEquals(
            SubtitleTextSizeSpec(primarySp = 20, secondarySp = 18),
            resolveSubtitleTextSizeSpec(playerWidthDp = 600, largeTextEnabled = false)
        )
        assertEquals(
            SubtitleTextSizeSpec(primarySp = 24, secondarySp = 21),
            resolveSubtitleTextSizeSpec(playerWidthDp = 600, largeTextEnabled = true)
        )
        assertEquals(
            SubtitleTextSizeSpec(primarySp = 26, secondarySp = 23),
            resolveSubtitleTextSizeSpec(playerWidthDp = 840, largeTextEnabled = true)
        )
    }

    @Test
    fun resolveDefaultSubtitleDisplayMode_prefersBilingualWhenBothTracksAvailable() {
        assertEquals(
            SubtitleDisplayMode.BILINGUAL,
            resolveDefaultSubtitleDisplayMode(
                hasPrimaryTrack = true,
                hasSecondaryTrack = true
            )
        )
        assertEquals(
            SubtitleDisplayMode.PRIMARY_ONLY,
            resolveDefaultSubtitleDisplayMode(
                hasPrimaryTrack = true,
                hasSecondaryTrack = false
            )
        )
        assertEquals(
            SubtitleDisplayMode.SECONDARY_ONLY,
            resolveDefaultSubtitleDisplayMode(
                hasPrimaryTrack = false,
                hasSecondaryTrack = true
            )
        )
        assertEquals(
            SubtitleDisplayMode.OFF,
            resolveDefaultSubtitleDisplayMode(
                hasPrimaryTrack = false,
                hasSecondaryTrack = false
            )
        )
    }

    @Test
    fun normalizeSubtitleDisplayMode_fallsBackWhenTargetTrackUnavailable() {
        assertEquals(
            SubtitleDisplayMode.PRIMARY_ONLY,
            normalizeSubtitleDisplayMode(
                preferredMode = SubtitleDisplayMode.BILINGUAL,
                hasPrimaryTrack = true,
                hasSecondaryTrack = false
            )
        )
        assertEquals(
            SubtitleDisplayMode.SECONDARY_ONLY,
            normalizeSubtitleDisplayMode(
                preferredMode = SubtitleDisplayMode.BILINGUAL,
                hasPrimaryTrack = false,
                hasSecondaryTrack = true
            )
        )
        assertEquals(
            SubtitleDisplayMode.OFF,
            normalizeSubtitleDisplayMode(
                preferredMode = SubtitleDisplayMode.PRIMARY_ONLY,
                hasPrimaryTrack = false,
                hasSecondaryTrack = false
            )
        )
    }

    @Test
    fun resolveSubtitleDisplayOptions_returnsOffChineseEnglishAndBilingual() {
        val options = resolveSubtitleDisplayOptions(
            primaryLabel = "中文",
            secondaryLabel = "英文",
            hasPrimaryTrack = true,
            hasSecondaryTrack = true
        )

        assertEquals(4, options.size)
        assertEquals(SubtitleDisplayMode.OFF, options[0].mode)
        assertEquals("关闭", options[0].label)
        assertEquals(SubtitleDisplayMode.PRIMARY_ONLY, options[1].mode)
        assertEquals("中文", options[1].label)
        assertEquals(SubtitleDisplayMode.SECONDARY_ONLY, options[2].mode)
        assertEquals("英文", options[2].label)
        assertEquals(SubtitleDisplayMode.BILINGUAL, options[3].mode)
        assertEquals("双语", options[3].label)
        assertTrue(options.all { it.enabled })
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_offAlwaysDisablesSubtitles() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.OFF,
            hasPrimaryTrack = true,
            hasSecondaryTrack = true,
            primaryTrackLikelyAi = false,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )

        assertEquals(SubtitleDisplayMode.OFF, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_onUsesDefaultTrackMode() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.ON,
            hasPrimaryTrack = true,
            hasSecondaryTrack = true,
            primaryTrackLikelyAi = false,
            secondaryTrackLikelyAi = true,
            isMuted = false,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )

        assertEquals(SubtitleDisplayMode.BILINGUAL, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_withoutAiSkipsWhenDefaultTrackIsAi() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.WITHOUT_AI,
            hasPrimaryTrack = true,
            hasSecondaryTrack = false,
            primaryTrackLikelyAi = true,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )

        assertEquals(SubtitleDisplayMode.OFF, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_withoutAiKeepsWhenDefaultTrackIsNonAi() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.WITHOUT_AI,
            hasPrimaryTrack = true,
            hasSecondaryTrack = false,
            primaryTrackLikelyAi = false,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )

        assertEquals(SubtitleDisplayMode.PRIMARY_ONLY, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_autoEnablesAiOnlyWhenMuted() {
        val unmuted = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.AUTO,
            hasPrimaryTrack = true,
            hasSecondaryTrack = false,
            primaryTrackLikelyAi = true,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )
        val muted = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.AUTO,
            hasPrimaryTrack = true,
            hasSecondaryTrack = false,
            primaryTrackLikelyAi = true,
            secondaryTrackLikelyAi = false,
            isMuted = true,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )

        assertEquals(SubtitleDisplayMode.OFF, unmuted)
        assertEquals(SubtitleDisplayMode.PRIMARY_ONLY, muted)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_chineseOnlyKeepsChinesePrimaryTrack() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.CHINESE_ONLY,
            hasPrimaryTrack = true,
            hasSecondaryTrack = true,
            primaryTrackLikelyAi = false,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "zh-Hans",
            secondaryTrackLanguage = "en-US"
        )

        // 只显示中文，不因为存在英文副轨就叠加双语
        assertEquals(SubtitleDisplayMode.PRIMARY_ONLY, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_chineseOnlyAcceptsAiChineseTrack() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.CHINESE_ONLY,
            hasPrimaryTrack = true,
            hasSecondaryTrack = false,
            primaryTrackLikelyAi = true,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "ai-zh",
            secondaryTrackLanguage = null
        )

        assertEquals(SubtitleDisplayMode.PRIMARY_ONLY, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_chineseOnlyDisablesWhenNoChineseTrack() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.CHINESE_ONLY,
            hasPrimaryTrack = true,
            hasSecondaryTrack = true,
            primaryTrackLikelyAi = false,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "en-US",
            secondaryTrackLanguage = "ja-JP"
        )

        assertEquals(SubtitleDisplayMode.OFF, mode)
    }

    @Test
    fun resolveSubtitleDisplayModeByAutoPreference_chineseOnlyFallsBackToChineseSecondaryTrack() {
        val mode = resolveSubtitleDisplayModeByAutoPreference(
            preference = SubtitleAutoPreference.CHINESE_ONLY,
            hasPrimaryTrack = true,
            hasSecondaryTrack = true,
            primaryTrackLikelyAi = false,
            secondaryTrackLikelyAi = false,
            isMuted = false,
            primaryTrackLanguage = "en-US",
            secondaryTrackLanguage = "zh-Hans"
        )

        assertEquals(SubtitleDisplayMode.SECONDARY_ONLY, mode)
    }

    @Test
    fun resolveSubtitleControlAvailability_usesTrackBindingWhenCueNotReady() {
        val availability = resolveSubtitleControlAvailability(
            primaryTrackBound = true,
            secondaryTrackBound = false,
            primaryCueAvailable = false,
            secondaryCueAvailable = false
        )

        assertTrue(availability.trackAvailable)
        assertTrue(availability.primarySelectable)
        assertFalse(availability.secondarySelectable)
    }

    @Test
    fun resolveSubtitleControlAvailability_combinesBindingAndCueAvailability() {
        val availability = resolveSubtitleControlAvailability(
            primaryTrackBound = false,
            secondaryTrackBound = true,
            primaryCueAvailable = true,
            secondaryCueAvailable = false
        )

        assertTrue(availability.trackAvailable)
        assertTrue(availability.primarySelectable)
        assertTrue(availability.secondarySelectable)
    }
}
