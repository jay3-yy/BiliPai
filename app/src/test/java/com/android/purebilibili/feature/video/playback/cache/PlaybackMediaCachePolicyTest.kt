package com.android.purebilibili.feature.video.playback.cache

import com.android.purebilibili.core.player.buildPlaybackCacheKey
import com.android.purebilibili.core.player.shouldUsePlaybackMediaCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackMediaCachePolicyTest {

    @Test
    fun cacheKeyPrefersExplicitKey() {
        val uri = "https://upos-sz-mirrorcos.bilivideo.com/video.m4s?deadline=1&sig=abc"

        assertEquals(
            "explicit-video-key",
            buildPlaybackCacheKey(rawUri = uri, explicitKey = "explicit-video-key")
        )
    }

    @Test
    fun cacheKeyDropsSignedQueryForHttpMediaUrl() {
        val first = "https://upos-sz-mirrorcos.bilivideo.com/path/video.m4s?deadline=1&sig=abc"
        val second = "https://upos-sz-mirrorcos.bilivideo.com/path/video.m4s?deadline=2&sig=def"

        assertEquals(
            buildPlaybackCacheKey(rawUri = first, explicitKey = null),
            buildPlaybackCacheKey(rawUri = second, explicitKey = null)
        )
        assertEquals(
            "https://upos-sz-mirrorcos.bilivideo.com/path/video.m4s",
            buildPlaybackCacheKey(rawUri = first, explicitKey = null)
        )
    }

    @Test
    fun cacheKeyKeepsDifferentPathsSeparate() {
        val video = "https://upos-sz-mirrorcos.bilivideo.com/video/track.m4s?sig=abc"
        val audio = "https://upos-sz-mirrorcos.bilivideo.com/audio/track.m4s?sig=abc"

        assertTrue(
            buildPlaybackCacheKey(rawUri = video, explicitKey = null) !=
                buildPlaybackCacheKey(rawUri = audio, explicitKey = null)
        )
    }

    @Test
    fun cacheKeyFallsBackToStringForHostlessUri() {
        val uri = "content://media/external/video/media/42"

        assertEquals(
            "content://media/external/video/media/42",
            buildPlaybackCacheKey(rawUri = uri, explicitKey = null)
        )
    }

    @Test
    fun playbackMediaCacheOnlyAppliesToHttpStreams() {
        assertTrue(shouldUsePlaybackMediaCache("https://example.com/video.m4s"))
        assertTrue(shouldUsePlaybackMediaCache("http://example.com/video.m4s"))
        assertFalse(shouldUsePlaybackMediaCache("file:///tmp/local.mpd"))
        assertFalse(shouldUsePlaybackMediaCache("content://media/external/video/media/42"))
    }

}
