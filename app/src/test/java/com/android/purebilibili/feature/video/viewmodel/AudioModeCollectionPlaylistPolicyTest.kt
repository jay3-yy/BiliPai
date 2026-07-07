package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcEpisodeArc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioModeCollectionPlaylistPolicyTest {

    @Test
    fun `audio mode collection playlist uses collection episodes only`() {
        val result = buildAudioModeCollectionPlaylist(
            episodes = listOf(
                UgcEpisode(bvid = "BV1", cid = 11L, title = "合集 1"),
                UgcEpisode(
                    bvid = "BV2",
                    cid = 22L,
                    title = "合集 2",
                    arc = UgcEpisodeArc(pic = "cover2", duration = 60)
                ),
                UgcEpisode(bvid = "")
            ),
            currentBvid = "BV2",
            currentCid = 22L
        )

        assertNotNull(result)
        assertEquals(listOf("BV1", "BV2"), result.items.map { it.bvid })
        assertEquals(1, result.startIndex)
        assertEquals("合集 2", result.items[1].title)
        assertEquals("cover2", result.items[1].cover)
        assertEquals(60L, result.items[1].duration)
    }

    @Test
    fun `audio mode collection playlist falls back to bvid when cid is not available`() {
        val result = buildAudioModeCollectionPlaylist(
            episodes = listOf(
                UgcEpisode(bvid = "BV1", cid = 11L),
                UgcEpisode(bvid = "BV2", cid = 22L)
            ),
            currentBvid = "BV2",
            currentCid = 0L
        )

        assertNotNull(result)
        assertEquals(1, result.startIndex)
    }

    @Test
    fun `audio mode collection playlist is skipped when external queue is active`() {
        assertFalse(
            shouldApplyAudioModeCollectionPlaylist(
                isInAudioMode = true,
                keepExternalPlaylist = true
            )
        )
    }

    @Test
    fun `audio mode collection playlist applies without external queue`() {
        assertTrue(
            shouldApplyAudioModeCollectionPlaylist(
                isInAudioMode = true,
                keepExternalPlaylist = false
            )
        )
        assertFalse(
            shouldApplyAudioModeCollectionPlaylist(
                isInAudioMode = false,
                keepExternalPlaylist = false
            )
        )
    }
}
