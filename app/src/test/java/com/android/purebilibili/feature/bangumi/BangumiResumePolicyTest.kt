package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BangumiResumePolicyTest {

    @Test
    fun `bangumi heartbeat allows initial zero progress while playing`() {
        assertTrue(
            shouldSendBangumiPlaybackHeartbeat(
                isPlaying = true,
                bvid = "BV1pgc",
                cid = 1122L,
                currentPositionMs = 0L
            )
        )
    }

    @Test
    fun `bangumi heartbeat still rejects missing identifiers`() {
        assertFalse(
            shouldSendBangumiPlaybackHeartbeat(
                isPlaying = true,
                bvid = "",
                cid = 1122L,
                currentPositionMs = 1000L
            )
        )
        assertFalse(
            shouldSendBangumiPlaybackHeartbeat(
                isPlaying = true,
                bvid = "BV1pgc",
                cid = 0L,
                currentPositionMs = 1000L
            )
        )
    }

    @Test
    fun `bangumi heartbeat allows pgc episode without bvid when epid and sid present`() {
        assertTrue(
            shouldSendBangumiPlaybackHeartbeat(
                isPlaying = true,
                bvid = "",
                cid = 1122L,
                currentPositionMs = 1000L,
                epid = 42L,
                sid = 7L
            )
        )
        assertFalse(
            shouldSendBangumiPlaybackHeartbeat(
                isPlaying = true,
                bvid = "",
                cid = 1122L,
                currentPositionMs = 1000L,
                epid = 42L,
                sid = 0L
            )
        )
    }

    @Test
    fun `bangumi detail request prefers episode id from pgc history`() {
        val request = resolveBangumiDetailRequest(seasonId = 114514L, epId = 1919810L)

        assertEquals(0L, request.seasonId)
        assertEquals(1919810L, request.epId)
    }

    @Test
    fun `bangumi playback restores local episode progress when the route has none`() {
        assertEquals(
            45_000L,
            resolveBangumiPlaybackStartPositionMs(
                routeResumePositionMs = 0L,
                savedEpisodePositionMs = 45_000L
            )
        )
    }

    @Test
    fun `bangumi playback route progress overrides local episode progress`() {
        assertEquals(
            12_000L,
            resolveBangumiPlaybackStartPositionMs(
                routeResumePositionMs = 12_000L,
                savedEpisodePositionMs = 45_000L
            )
        )
    }

    @Test
    fun `course entry prefers aid then explicit episode then remembered progress`() {
        val detail = BangumiDetail(
            seasonId = 9L,
            episodes = listOf(
                BangumiEpisode(id = 11L, aid = 101L),
                BangumiEpisode(id = 12L, aid = 102L)
            ),
            userStatus = com.android.purebilibili.data.model.response.UserStatus(
                progress = com.android.purebilibili.data.model.response.WatchProgress(lastEpId = 12L)
            )
        )

        assertEquals(12L, resolveBangumiInitialEpisode(detail, 102L, 11L, true)?.epId)
        assertEquals(11L, resolveBangumiInitialEpisode(detail, 0L, 11L, true)?.epId)
        assertEquals(12L, resolveBangumiInitialEpisode(detail, 0L, 0L, true)?.epId)
    }

    @Test
    fun `explicit last watched episode restores its server position only when resume is enabled`() {
        val detail = BangumiDetail(
            seasonId = 9L,
            episodes = listOf(BangumiEpisode(id = 11L, aid = 101L), BangumiEpisode(id = 12L, aid = 102L)),
            userStatus = com.android.purebilibili.data.model.response.UserStatus(
                progress = com.android.purebilibili.data.model.response.WatchProgress(lastEpId = 12L, lastTime = 42L)
            )
        )
        assertEquals(42_000L, resolveBangumiInitialEpisode(detail, 102L, 12L, true)?.resumePositionMs)
        assertEquals(42_000L, resolveBangumiInitialEpisode(detail, 0L, 12L, true)?.resumePositionMs)
        assertEquals(0L, resolveBangumiInitialEpisode(detail, 0L, 11L, true)?.resumePositionMs)
        assertEquals(0L, resolveBangumiInitialEpisode(detail, 0L, 12L, false)?.resumePositionMs)
    }

    @Test
    fun `reattached player restores cached dash playback for the same episode`() {
        assertTrue(
            shouldRestoreBangumiCachedPlayback(
                requestedSeasonId = 1L,
                requestedEpisodeId = 2L,
                loadedSeasonId = 1L,
                loadedEpisodeId = 2L,
                cachedVideoUrl = "https://video",
                cachedAudioUrl = "https://audio",
                attachedPlayerMediaItemCount = 0
            )
        )
    }

    @Test
    fun `cached bangumi durl does not bypass a reload without its segment list`() {
        assertFalse(
            shouldRestoreBangumiCachedPlayback(
                requestedSeasonId = 1L,
                requestedEpisodeId = 2L,
                loadedSeasonId = 1L,
                loadedEpisodeId = 2L,
                cachedVideoUrl = "https://video",
                cachedAudioUrl = null,
                attachedPlayerMediaItemCount = 0
            )
        )
    }
    @Test
    fun `explicit trailer and history select episodes from additional sections`() {
        val trailer = BangumiEpisode(id = 21L, aid = 201L)
        val detail = BangumiDetail(
            episodes = listOf(BangumiEpisode(id = 11L)),
            section = listOf(com.android.purebilibili.data.model.response.BangumiSection(episodes = listOf(trailer))),
            userStatus = com.android.purebilibili.data.model.response.UserStatus(
                progress = com.android.purebilibili.data.model.response.WatchProgress(lastEpId = 21L, lastTime = 9L)
            ),
        )
        assertEquals(21L, resolveBangumiInitialEpisode(detail, 0L, 21L, true)?.epId)
        assertEquals(9_000L, resolveBangumiInitialEpisode(detail, 0L, 0L, true)?.resumePositionMs)
        assertEquals(11L, resolveBangumiInitialEpisode(detail, 0L, 999L, true)?.epId)
    }

    @Test
    fun `season without main episodes can open its first preview`() {
        val detail = BangumiDetail(
            section = listOf(com.android.purebilibili.data.model.response.BangumiSection(
                episodes = listOf(BangumiEpisode(id = 21L))
            )),
        )
        assertEquals(21L, resolveBangumiInitialEpisode(detail, 0L, 0L, true)?.epId)
        assertEquals(null, resolveBangumiInitialEpisode(BangumiDetail(), 0L, 0L, true))
    }

    @Test
    fun `continuous playback stays in the currently selected section`() {
        val main = listOf(BangumiEpisode(id = 11L), BangumiEpisode(id = 12L))
        val previews = listOf(BangumiEpisode(id = 21L), BangumiEpisode(id = 22L))
        val extras = listOf(BangumiEpisode(id = 31L))
        val detail = BangumiDetail(
            episodes = main,
            section = listOf(
                com.android.purebilibili.data.model.response.BangumiSection(episodes = previews),
                com.android.purebilibili.data.model.response.BangumiSection(episodes = extras),
            ),
        )
        assertEquals(main, resolveBangumiPlaybackEpisodes(detail, 12L))
        assertEquals(previews, resolveBangumiPlaybackEpisodes(detail, 21L))
        assertEquals(extras, resolveBangumiPlaybackEpisodes(detail, 31L))
    }

}
