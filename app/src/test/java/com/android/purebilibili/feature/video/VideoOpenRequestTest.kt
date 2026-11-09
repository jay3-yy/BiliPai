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
            title = "标题 + 空格 & 问号? 100% / 第一期",
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

    @Test
    fun systemVerticalEntryPreservesTheFullscreenIntentThroughWindowAndRouteRestoration() {
        val source = VideoOpenRequest(
            bvid = "BV1vertical", cid = 42L, coverUrl = "https://example.com/cover.jpg",
            initialVertical = false, fullscreen = true, resumePositionMs = 12_345L, title = "竖屏视频",
        )
        val request = source.forSystemCardTransition(isVerticalVideo = true, directPortraitEntryEnabled = true)
        val restored = requireNotNull(VideoOpenRequest.fromBundle(request.toBundle()))
        val key = legacyRouteToBiliPaiNavKey(restored.toInitialRoute()) as com.android.purebilibili.navigation3.BiliPaiNavKey.VideoDetail
        assertEquals(source.copy(initialVertical = true, autoPortrait = true, directPortraitEntry = true, fullscreen = false), restored)
        assertEquals(true, key.directPortraitEntry)
        assertEquals(true, key.initialVertical)
        assertEquals(false, key.fullscreen)
        assertEquals(12_345L, key.resumePositionMs)
        assertEquals(true, com.android.purebilibili.feature.video.screen.shouldStartInPortraitFullscreenFromRouteHint(
            autoEnterPortraitFromRoute = key.autoPortrait,
            startAudioFromRoute = key.startAudio,
            initialVerticalFromRoute = key.initialVertical,
            directPortraitEntryFromRoute = key.directPortraitEntry,
        ))
        assertEquals(
            com.android.purebilibili.feature.video.screen.VideoDetailLocalBackTarget.NAVIGATE_BACK,
            com.android.purebilibili.feature.video.screen.resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = key.fullscreen,
                isPortraitFullscreen = true,
                directPortraitEntry = key.directPortraitEntry,
            ),
        )
    }

    @Test
    fun systemVerticalEntryStaysInlineWhenDirectPortraitSettingIsOff() {
        val source = VideoOpenRequest(
            bvid = "BV1vertical", cid = 42L, coverUrl = "https://example.com/cover.jpg",
            fullscreen = true, resumePositionMs = 12_345L, title = "竖屏视频",
        )
        val request = source.forSystemCardTransition(
            isVerticalVideo = true,
            directPortraitEntryEnabled = false,
        )
        assertEquals(source.copy(initialVertical = true, autoPortrait = false, directPortraitEntry = false, fullscreen = false), request)
        val key = request.toNavigationKey()
        assertEquals(true, key.initialVertical)
        assertEquals(false, key.directPortraitEntry)
        assertEquals(false, com.android.purebilibili.feature.video.screen.shouldStartInPortraitFullscreenFromRouteHint(
            autoEnterPortraitFromRoute = key.autoPortrait,
            startAudioFromRoute = key.startAudio,
            initialVerticalFromRoute = key.initialVertical,
            directPortraitEntryFromRoute = key.directPortraitEntry,
        ))
    }

    @Test
    fun systemEntryKeepsAudioCommentAndNonVideoDestinations() {
        val vertical = VideoOpenRequest(bvid = "BV1vertical", initialVertical = true)
        listOf(
            vertical.copy(initialVertical = false),
            vertical.copy(startAudio = true),
            vertical.copy(commentRootRpid = 123L),
            vertical.copy(commentTargetRpid = 456L),
            vertical.copy(initialRoute = "audio_mode?bvid=BV1vertical"),
        ).forEach { request -> assertEquals(request, request.forSystemCardTransition()) }
    }
}
