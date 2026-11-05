package com.android.purebilibili.feature.video

import android.os.Bundle
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import com.android.purebilibili.navigation3.toLegacyRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VideoOpenRequestTest {
    @Test
    fun routeAndActivityExtrasPreservePlaybackAndCommentIntent() {
        val request = VideoOpenRequest(
            bvid = "BV1test", cid = 934L, coverUrl = "https://example.com/cover?a=1&b=2",
            startAudio = true, autoPortrait = true, fullscreen = true,
            resumePositionMs = 123456L, commentRootRpid = 987L, commentTargetRpid = 654L,
            initialVertical = true, directPortraitEntry = true,
        )
        val fromActivity = requireNotNull(VideoOpenRequest.fromBundle(request.toBundle()))
        assertEquals(request, fromActivity)
        val key = fromActivity.toNavigationKey()
        assertEquals(key, legacyRouteToBiliPaiNavKey(key.toLegacyRoute()))
        assertEquals(request, VideoOpenRequest.fromKey(key))
    }

    @Test
    fun legacyBvidOnlyEntryUsesNormalPlaybackDefaults() {
        val extras = Bundle().apply { putString("bvid", "BV1legacy") }
        assertEquals(VideoOpenRequest("BV1legacy"), VideoOpenRequest.fromBundle(extras))
        assertNull(VideoOpenRequest.fromBundle(Bundle()))
    }
}
