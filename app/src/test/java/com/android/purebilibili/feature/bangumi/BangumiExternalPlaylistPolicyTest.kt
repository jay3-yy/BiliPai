package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BangumiExternalPlaylistPolicyTest {

    @Test
    fun `build external bangumi playlist should return null when no episodes`() {
        val detail = BangumiDetail(
            seasonId = 100L,
            title = "测试番剧",
            episodes = emptyList()
        )

        assertNull(buildExternalPlaylistFromBangumi(detail, currentEpisodeId = 1L))
    }

    @Test
    fun `build external bangumi playlist should map episodes and current index`() {
        val episodes = listOf(
            BangumiEpisode(
                id = 11L,
                title = "第1话",
                duration = 24 * 60 * 1000L
            ),
            BangumiEpisode(
                id = 22L,
                title = "第2话",
                duration = 25 * 60 * 1000L
            )
        )
        val detail = BangumiDetail(
            seasonId = 100L,
            title = "测试番剧",
            cover = "cover",
            episodes = episodes
        )

        val external = buildExternalPlaylistFromBangumi(detail, currentEpisodeId = 22L)
        assertNotNull(external)
        assertEquals(1, external.startIndex)
        assertEquals(2, external.playlistItems.size)
        assertTrue(external.playlistItems.all { it.isBangumi })
        assertEquals(100L, external.playlistItems[0].seasonId)
        assertEquals(11L, external.playlistItems[0].epId)
        assertEquals(22L, external.playlistItems[1].epId)
    }
    @Test
    fun `preview playback exposes its own section to the external playlist`() {
        val detail = BangumiDetail(
            seasonId = 100L,
            episodes = listOf(BangumiEpisode(id = 11L)),
            section = listOf(com.android.purebilibili.data.model.response.BangumiSection(
                episodes = listOf(BangumiEpisode(id = 21L), BangumiEpisode(id = 22L)),
            )),
        )
        val external = assertNotNull(buildExternalPlaylistFromBangumi(detail, 22L))
        assertEquals(listOf(21L, 22L), external.playlistItems.map { it.epId })
        assertEquals(1, external.startIndex)
    }

}
